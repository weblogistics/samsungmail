package com.coursework.unifiedmail.ui.message

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val document = remember(html) { withMobileViewport(html) }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = !showRemoteContent
                settings.textZoom = textZoomPercent
                // Fit fixed-width (typically 600–700px) newsletter layouts to the pane rather than
                // clipping their right-hand side; pinch-zoom stays available for small print.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
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
            // update runs on every recomposition of the caller (an attachment download starting,
            // a snackbar...), and reloading the same HTML reset the scroll position and flashed.
            // Only reload when the content actually changed.
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
            }
        },
    )
}

/**
 * Most HTML mail has no viewport meta tag, so with a wide viewport WebView would lay simple
 * messages out at desktop width (~980px) and shrink the text. Declaring device-width keeps those
 * readable, while overview mode still zooms out fixed-width layouts that are wider than the pane.
 */
private fun withMobileViewport(html: String): String {
    if (Regex("<meta[^>]+name\\s*=\\s*[\"']?viewport", RegexOption.IGNORE_CASE).containsMatchIn(html)) return html
    val meta = "<meta name=\"viewport\" content=\"width=device-width\">"
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) html.replaceRange(head.range.last + 1, head.range.last + 1, meta) else meta + html
}
