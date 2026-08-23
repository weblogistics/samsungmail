package com.coursework.unifiedmail.data.settings

/** [scalePercent] drives both plain-text Compose font scaling and HtmlMessageBody's WebView `textZoom`, which is itself a percentage. */
enum class MessageTextSize(val scalePercent: Int) {
    SMALL(85),
    MEDIUM(100),
    LARGE(115),
    EXTRA_LARGE(130),
}
