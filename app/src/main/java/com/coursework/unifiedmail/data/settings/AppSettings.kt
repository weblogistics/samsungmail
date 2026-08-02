package com.coursework.unifiedmail.data.settings

data class AppSettings(
    // Names match Samsung Email's own "swipe left" / "swipe right" settings terminology.
    // swipeRightAction = SwipeToDismissBoxValue.StartToEnd, swipeLeftAction = EndToStart.
    val swipeRightAction: SwipeAction = SwipeAction.TOGGLE_READ,
    val swipeLeftAction: SwipeAction = SwipeAction.REMOVE,
    val syncIntervalMinutes: Long = 15L,
    val confirmBeforeDelete: Boolean = true,
)
