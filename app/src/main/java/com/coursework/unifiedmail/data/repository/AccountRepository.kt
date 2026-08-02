package com.coursework.unifiedmail.data.repository

import com.coursework.unifiedmail.data.crypto.CredentialStore
import com.coursework.unifiedmail.data.local.AccountDao
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.MailSecurity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

data class NewAccount(
    val displayName: String,
    val emailAddress: String,
    val username: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: MailSecurity,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: MailSecurity,
)

/**
 * Single source of truth for accounts: account metadata lives in Room, the password lives in
 * the encrypted credential store, keyed by the same account id — the two are always written
 * and read together so callers never see one without the other.
 *
 * An interface (bound to [AccountRepositoryImpl] in RepositoryModule) so consumers like
 * MailRepository can be unit-tested against a fake instead of a real Room/EncryptedSharedPrefs
 * backend.
 */
interface AccountRepository {
    fun observeAccounts(): Flow<List<AccountEntity>>
    fun observeActiveAccounts(): Flow<List<AccountEntity>>
    suspend fun getAccount(accountId: String): AccountEntity?
    suspend fun getAllAccountsOnce(): List<AccountEntity>
    suspend fun addAccount(newAccount: NewAccount): AccountEntity
    /** [newPassword] only updates the stored credential when non-blank — never clears it. */
    suspend fun updateAccount(account: AccountEntity, newPassword: String?)
    suspend fun deleteAccount(account: AccountEntity)
    fun getPassword(accountId: String): String?
}

class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val credentialStore: CredentialStore,
) : AccountRepository {
    override fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()

    override suspend fun getAccount(accountId: String): AccountEntity? = accountDao.getById(accountId)

    override suspend fun getAllAccountsOnce(): List<AccountEntity> = accountDao.getAllOnce()

    override suspend fun addAccount(newAccount: NewAccount): AccountEntity {
        val id = UUID.randomUUID().toString()
        val entity = AccountEntity(
            id = id,
            displayName = newAccount.displayName,
            emailAddress = newAccount.emailAddress,
            username = newAccount.username,
            imapHost = newAccount.imapHost,
            imapPort = newAccount.imapPort,
            imapSecurity = newAccount.imapSecurity,
            smtpHost = newAccount.smtpHost,
            smtpPort = newAccount.smtpPort,
            smtpSecurity = newAccount.smtpSecurity,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        credentialStore.savePassword(id, newAccount.password)
        accountDao.insert(entity)
        return entity
    }

    override suspend fun updateAccount(account: AccountEntity, newPassword: String?) {
        if (!newPassword.isNullOrBlank()) {
            credentialStore.savePassword(account.id, newPassword)
        }
        accountDao.update(account)
    }

    override suspend fun deleteAccount(account: AccountEntity) {
        accountDao.delete(account)
        credentialStore.deletePassword(account.id)
    }

    override fun getPassword(accountId: String): String? = credentialStore.getPassword(accountId)
}
