package com.coursework.unifiedmail.data.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores account passwords outside of Room, in a keystore-backed encrypted file.
 * Never persist mail passwords in the plain Room database.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun savePassword(accountId: String, password: String) {
        prefs.edit().putString(passwordKey(accountId), password).apply()
    }

    fun getPassword(accountId: String): String? = prefs.getString(passwordKey(accountId), null)

    fun deletePassword(accountId: String) {
        prefs.edit().remove(passwordKey(accountId)).apply()
    }

    private fun passwordKey(accountId: String) = "password_$accountId"

    private companion object {
        const val PREFS_FILE_NAME = "unified_mail_credentials"
    }
}
