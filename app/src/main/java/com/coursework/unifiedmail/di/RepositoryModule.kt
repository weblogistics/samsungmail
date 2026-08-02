package com.coursework.unifiedmail.di

import com.coursework.unifiedmail.data.remote.ImapClient
import com.coursework.unifiedmail.data.remote.ImapClientImpl
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.data.remote.SmtpSenderImpl
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.AccountRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Interface -> impl bindings so consumers (chiefly MailRepository) can be unit-tested against
// hand-written fakes instead of real Room/EncryptedSharedPreferences/IMAP/SMTP backends.
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindImapClient(impl: ImapClientImpl): ImapClient

    @Binds
    @Singleton
    abstract fun bindSmtpSender(impl: SmtpSenderImpl): SmtpSender
}
