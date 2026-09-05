package com.coursework.unifiedmail.ui.message

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders a message's raw HTML. JavaScript stays permanently disabled — the actual security
 * boundary here, not just a default — and remote images are blocked unless [showRemoteContent]
 * is true (the "Show remote content" toggle: privacy/tracking-pixel protection, same default
 * every real email client ships with). Tapping a link opens the system browser instead of
 * navigating this WebView away from the message; long-pressing one reports its target URL via
 * [onLinkLongPress] instead (see MessageDetailScreen's link-preview dialog) so the user can see
 * where it goes before deciding to open it.
 */
@Composable
fun HtmlMessageBody(
    html: String,
    showRemoteContent: Boolean,
    textZoomPercent: Int = 100,
    modifier: Modifier = Modifier,
    onLinkLongPress: (String) -> Unit = {},
) {
    val context = LocalContext.current
    // The WebView factory below only runs once; rememberUpdatedState lets the long-click listener
    // it installs always see the latest lambda without needing to recreate the WebView itself.
    val currentOnLinkLongPress by rememberUpdatedState(onLinkLongPress)
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = !showRemoteContent
                settings.textZoom = textZoomPercent
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        }
                    }
                }
                // WebView's own hit test — native, so it works fine with JavaScript disabled —
                // tells us whether a long-press landed on a link (or a linked image) and, if so,
                // what URL it points to.
                setOnLongClickListener {
                    val result = hitTestResult
                    if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                    ) {
                        result.extra?.let { url -> currentOnLinkLongPress(url) }
                        true
                    } else {
                        false
                    }
                }
            }
        },
        update = { webView ->
            webView.settings.blockNetworkImage = !showRemoteContent
            webView.settings.textZoom = textZoomPercent
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
    )
}
