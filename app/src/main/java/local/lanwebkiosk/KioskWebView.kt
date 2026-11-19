package local.lanwebkiosk

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

internal class KioskWebView(
    context: Context,
    private val onLoading: () -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { fail(context.getString(R.string.web_error_timeout)) }
    private var browser: WebView? = null
    private var mainFrameUrl: String? = null
    private var loading = false

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (browser !== view || !isWebAddress(request.url)) return true
            if (request.isForMainFrame) mainFrameUrl = request.url.toString()
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (browser !== view) return
            mainFrameUrl = url
            startLoading()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (browser !== view || !loading) return
            handler.removeCallbacks(timeout)
            loading = false
            onReady()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (browser !== view || !request.isForMainFrame) return
            val message = when (error.errorCode) {
                ERROR_TIMEOUT -> R.string.web_error_timeout
                ERROR_FAILED_SSL_HANDSHAKE -> R.string.web_error_tls
                else -> R.string.web_error_connection
            }
            fail(context.getString(message))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (browser !== view || !request.isForMainFrame) return
            fail(context.getString(R.string.web_error_http, errorResponse.statusCode))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (browser === view && error.url == mainFrameUrl) {
                fail(context.getString(R.string.web_error_tls))
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (browser === view) fail(context.getString(R.string.web_error_renderer))
            return true
        }
    }

    // The WebView loads web content without exposing any app objects to JavaScript.
    @SuppressLint("SetJavaScriptEnabled")
    fun load(url: String) {
        dispose()
        if (!isWebAddress(Uri.parse(url))) {
            onError(context.getString(R.string.web_error_connection))
            return
        }
        try {
            val view = WebView(context)
            browser = view
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                disabledActionModeMenuItems =
                    WebSettings.MENU_ITEM_SHARE or WebSettings.MENU_ITEM_WEB_SEARCH or WebSettings.MENU_ITEM_PROCESS_TEXT
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(true)
            }
            view.webViewClient = client
            view.webChromeClient = WebChromeClient()
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            mainFrameUrl = url
            startLoading()
            view.loadUrl(url)
        } catch (_: RuntimeException) {
            fail(context.getString(R.string.web_error_unavailable))
        }
    }

    fun dispose() {
        handler.removeCallbacks(timeout)
        loading = false
        mainFrameUrl = null
        val view = browser ?: return
        browser = null
        removeView(view)
        view.destroy()
    }

    private fun startLoading() {
        if (loading) return
        loading = true
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS)
        onLoading()
    }

    private fun fail(description: String) {
        dispose()
        onError(description)
    }

    private fun isWebAddress(uri: Uri): Boolean =
        uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)

    private companion object {
        const val LOAD_TIMEOUT_MS = 20_000L
    }
}
