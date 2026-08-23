package com.coursework.unifiedmail.di

import com.coursework.unifiedmail.data.remote.ImapClient
import com.coursework.unifiedmail.data.remote.ImapClientImpl
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.data.remote.SmtpSenderImpl
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.AccountRepositoryImpl
import com.coursework.unifiedmail.data.settings.AppSettingsProvider
import com.coursework.unifiedmail.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Binds
    @Singleton
    abstract fun bindAppSettingsProvider(impl: SettingsRepository): AppSettingsProvider

    companion object {
        // Outlives any single screen's viewModelScope — for best-effort background work (e.g.
        // pushing a read/unread flag to the server) that shouldn't make its caller wait on a
        // network round trip, but also shouldn't be cancelled just because the screen that
        // triggered it was navigated away from.
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
