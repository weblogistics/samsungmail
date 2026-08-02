package com.coursework.unifiedmail.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMailSecurity(value: MailSecurity): String = value.name

    @TypeConverter
    fun toMailSecurity(value: String): MailSecurity = MailSecurity.valueOf(value)

    @TypeConverter
    fun fromOutboxStatus(value: OutboxStatus): String = value.name

    @TypeConverter
    fun toOutboxStatus(value: String): OutboxStatus = OutboxStatus.valueOf(value)
}
