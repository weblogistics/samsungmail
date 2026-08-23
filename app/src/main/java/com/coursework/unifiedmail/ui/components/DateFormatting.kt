package com.coursework.unifiedmail.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Shared by message list rows and the thread-expansion screen. */
fun formatRelativeDate(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val date = dateTime.toLocalDate()

    return when {
        date == today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
