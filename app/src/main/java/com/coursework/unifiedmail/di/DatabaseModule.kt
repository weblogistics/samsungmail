package com.coursework.unifiedmail.di

import android.content.Context
import androidx.room.Room
import com.coursework.unifiedmail.data.local.AccountDao
import com.coursework.unifiedmail.data.local.AppDatabase
import com.coursework.unifiedmail.data.local.FolderDao
import com.coursework.unifiedmail.data.local.MessageDao
import com.coursework.unifiedmail.data.local.OutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "unified_mail.db")
            // Deliberately no Migration objects: this schema has never shipped to a real device
            // (no build of this app has been installed anywhere), so there is no installed data
            // anywhere to preserve — a destructive fallback costs nothing right now. Once this
            // is actually installed somewhere with real cached mail, replace this with real
            // Migrations before the next schema change, since at that point a wipe is a real
            // (if low-stakes — everything here is re-downloadable from the mail server) loss.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideOutboxDao(database: AppDatabase): OutboxDao = database.outboxDao()
}
