package com.coursework.unifiedmail.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class, FolderEntity::class, MessageEntity::class, OutboxEntity::class,
        AttachmentEntity::class, OutboxAttachmentEntity::class, DraftEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun outboxAttachmentDao(): OutboxAttachmentDao
    abstract fun draftDao(): DraftDao
}
