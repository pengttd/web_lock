package local.lanwebkiosk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var store: KioskStore
    private lateinit var root: LinearLayout
    private var webView: KioskWebView? = null
    private var fullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = KioskStore(this)
        store.removeLegacyCredential()
        KioskPolicy(this).releaseLegacyLock()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime(),
                )
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::handleBack)
        }

        val endpoint = store.getEndpoint()
        val configuring = savedInstanceState?.getBoolean(STATE_CONFIGURING) ?: (intent.action == ACTION_CONFIGURE)
        if (endpoint == null || configuring) showEndpointForm(endpoint) else showPage(endpoint)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_CONFIGURE) showEndpointForm(store.getEndpoint())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CONFIGURING, !fullscreen)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreen) updateSystemBars()
    }

    // Android 13+ uses the platform callback registered in onCreate.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Back before Android 13 uses the Activity callback")
    override fun onBackPressed() = handleBack()

    override fun onDestroy() {
        webView?.dispose()
        super.onDestroy()
    }

    private fun handleBack() = Unit

    private fun showEndpointForm(previous: Endpoint?) {
        replaceScreen(fullscreen = false)
        val panel = addPanel(R.string.endpoint_title)
        val ip = addField(panel, R.string.ip_address, InputType.TYPE_CLASS_PHONE).apply {
            hint = getString(R.string.ip_hint)
            setText(previous?.ip)
        }
        val port = addField(panel, R.string.port, InputType.TYPE_CLASS_NUMBER).apply {
            hint = getString(R.string.port_hint)
            setText(previous?.port?.toString())
        }
        val https = Switch(this).apply {
            setText(R.string.https)
            isChecked = previous?.https == true
            minHeight = dp(48)
        }
        panel.addView(https, rowParams(top = 8))
        val error = addError(panel)
        panel.addView(actionButton(R.string.open_page, primary = true) {
            val endpoint = Endpoint.parse(ip.text.toString(), port.text.toString(), https.isChecked)
            if (endpoint == null) {
                error.showMessage(getString(R.string.endpoint_invalid))
            } else if (!store.saveEndpoint(endpoint)) {
                error.showMessage(getString(R.string.endpoint_save_error))
            } else {
                hideKeyboard(port)
                showPage(endpoint)
            }
        }, rowParams(top = 14))
        if (previous != null) {
            panel.addView(actionButton(R.string.cancel) {
                hideKeyboard(ip)
                showPage(previous)
            }, rowParams(top = 8))
        }
    }

    private fun showPage(endpoint: Endpoint) {
        replaceScreen(fullscreen = true)
        val page = FrameLayout(this)
        root.addView(page, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val overlay = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.background))
            isClickable = true
        }
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val message = text(getString(R.string.page_loading), 17, R.color.muted).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val progress = ProgressBar(this)
        lateinit var browser: KioskWebView
        val retry = actionButton(R.string.retry, primary = true) { browser.load(endpoint.url) }
        status.addView(progress, LinearLayout.LayoutParams(dp(36), dp(36)).apply { bottomMargin = dp(16) })
        status.addView(message)
        status.addView(retry, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).apply {
            topMargin = dp(16)
        })
        overlay.addView(status)
        browser = KioskWebView(
            this,
            onLoading = {
                progress.visibility = View.VISIBLE
                retry.visibility = View.GONE
                message.setText(R.string.page_loading)
                overlay.visibility = View.VISIBLE
            },
            onReady = { overlay.visibility = View.GONE },
            onError = { description ->
                progress.visibility = View.GONE
                retry.visibility = View.VISIBLE
                message.text = description
                overlay.visibility = View.VISIBLE
            },
        )
        webView = browser
        page.addView(browser, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        page.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        browser.load(endpoint.url)
    }

    private fun replaceScreen(fullscreen: Boolean) {
        webView?.dispose()
        webView = null
        root.removeAllViews()
        this.fullscreen = fullscreen
        updateSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                val bars = WindowInsets.Type.systemBars()
                if (fullscreen) {
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(bars)
                } else {
                    controller.show(bars)
                }
            }
        } else {
            val immersiveFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            val decor = window.decorView
            val flags = decor.systemUiVisibility
            decor.systemUiVisibility = if (fullscreen) flags or immersiveFlags else flags and immersiveFlags.inv()
        }
    }

    private fun addPanel(title: Int): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val frame = FrameLayout(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            isFocusableInTouchMode = true
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        frame.addView(panel, params)
        frame.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = min(right - left, dp(480))
            if (width > 0 && params.width != width) {
                params.width = width
                panel.layoutParams = params
            }
        }
        scroll.addView(frame)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.requestFocus()
        panel.addView(text(getString(title), 26, R.color.ink).apply {
            setTypeface(typeface, Typeface.BOLD)
            isAccessibilityHeading = true
        }, rowParams(bottom = 12))
        return panel
    }

    private fun addField(panel: LinearLayout, label: Int, type: Int): EditText {
        val field = EditText(this).apply {
            id = View.generateViewId()
            setSingleLine(true)
            inputType = type
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setTextSize(17f)
            minHeight = dp(48)
        }
        panel.addView(text(getString(label), 15, R.color.ink).apply {
            labelFor = field.id
        }, rowParams(top = 14))
        panel.addView(field, rowParams())
        return field
    }

    private fun addError(panel: LinearLayout): TextView = text("", 15, R.color.error).apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        panel.addView(this, rowParams(top = 10))
    }

    private fun actionButton(label: Int, primary: Boolean = false, action: () -> Unit): Button =
        Button(this, null, if (primary) android.R.attr.buttonStyle else android.R.attr.borderlessButtonStyle).apply {
            setText(label)
            isAllCaps = false
            minHeight = dp(48)
            if (primary) {
                backgroundTintList = ColorStateList.valueOf(getColor(R.color.accent))
                setTextColor(getColor(R.color.surface))
            } else {
                setTextColor(getColor(R.color.accent))
            }
            setOnClickListener { action() }
        }

    private fun text(value: CharSequence, size: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(getColor(color))
    }

    private fun TextView.showMessage(value: String) {
        text = value
        visibility = View.VISIBLE
    }

    private fun hideKeyboard(view: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun rowParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()

    private companion object {
        const val ACTION_CONFIGURE = "local.lanwebkiosk.CONFIGURE"
        const val STATE_CONFIGURING = "configuring"
    }
}
