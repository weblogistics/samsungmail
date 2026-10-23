package com.coursework.unifiedmail.di

import android.content.Context
import androidx.room.Room
import com.coursework.unifiedmail.data.local.AccountDao
import com.coursework.unifiedmail.data.local.AppDatabase
import com.coursework.unifiedmail.data.local.AttachmentDao
import com.coursework.unifiedmail.data.local.DraftDao
import com.coursework.unifiedmail.data.local.FolderDao
import com.coursework.unifiedmail.data.local.MessageDao
import com.coursework.unifiedmail.data.local.OutboxAttachmentDao
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
            // The app is now installed with real accounts, so schema changes from version 14 on
            // need a real Migration — a destructive fallback would also wipe the accounts table.
            // The fallback stays only for pre-14 development builds, which never held real data.
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .fallbackToDestructiveMigrationFrom(*(1..13).toList().toIntArray())
            .build()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideOutboxDao(database: AppDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideAttachmentDao(database: AppDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideOutboxAttachmentDao(database: AppDatabase): OutboxAttachmentDao = database.outboxAttachmentDao()

    @Provides
    fun provideDraftDao(database: AppDatabase): DraftDao = database.draftDao()
}
