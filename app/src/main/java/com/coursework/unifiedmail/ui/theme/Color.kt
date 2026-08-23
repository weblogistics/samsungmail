package com.coursework.unifiedmail.ui.theme

import androidx.compose.ui.graphics.Color

// Values extracted directly from the reference Samsung Email APK's resource table
// (aapt2 dump resources) — see the GUI-redesign plan for the exact resource names these came
// from. Color values only; no artwork/icons/fonts were extracted or reused.
val SamsungPrimaryLight = Color(0xFF387AFF) // color/primary_color (light)
val SamsungPrimaryDark = Color(0xFF578FFF) // color/primary_color (night)

val SamsungBackgroundLight = Color(0xFFF1F1F3) // color/email_background_color (light)
val SamsungBackgroundDark = Color(0xFF010101) // color/email_background_color (night)

val SamsungOnBackgroundLight = Color(0xFF010101) // color/list_view_title_unread_text_color (light)
val SamsungOnBackgroundDark = Color(0xFFFAFAFA) // color/list_view_title_unread_text_color (night)

// color/list_list_swipe_bg_color / list_list_right_swipe_bg_color
val SwipeActionOrange = Color(0xFFFD905D)
val SwipeActionBlue = Color(0xFF4297FF) // TOGGLE_READ (unread)
val SwipeActionIconTint = Color(0xFFFAFAFA) // color/list_swipe_icon_tint_color

// Archive/star/delete aren't swipe actions in Samsung Email's extracted palette, so these have
// no extracted equivalent — plain Material colors, distinct from the two above.
val SwipeActionGreen = Color(0xFF4CAF50) // ARCHIVE
val SwipeActionGold = Color(0xFFFFB300) // TOGGLE_FLAG (star)
val SwipeActionRed = Color(0xFFD32F2F) // REMOVE (delete) — reads as destructive, unlike the neutral blue it used to share with unread
