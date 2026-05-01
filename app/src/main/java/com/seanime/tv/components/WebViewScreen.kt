package com.seanime.tv.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "SeanimeTV"

// Cursor movement constants
private const val BASE_SPEED_PX_PER_SEC = 450f
private const val MAX_SPEED_MULTIPLIER = 3.5f
private const val ACCELERATION_RATE = 4f
private const val EDGE_SCROLL_THRESHOLD = 100f
private const val FRAME_DELAY_MS = 16L // ~60 FPS
private const val CURSOR_OUTER_RADIUS = 16f
private const val CURSOR_INNER_RADIUS = 12f
private const val INTENT_DEBOUNCE_MS = 1000L

private fun simulateTouch(view: View?, action: Int, x: Float, y: Float, downTime: Long) {
    view ?: return
    val eventTime = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    try {
        view.dispatchTouchEvent(event)
    } finally {
        event.recycle()
    }
}

private fun simulateScroll(view: View?, x: Float, y: Float, scrollX: Float, scrollY: Float) {
    view ?: return
    val eventTime = SystemClock.uptimeMillis()

    val properties = arrayOf(MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_MOUSE
    })

    val coords = arrayOf(MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        setAxisValue(MotionEvent.AXIS_VSCROLL, scrollY)
        setAxisValue(MotionEvent.AXIS_HSCROLL, scrollX)
    })

    val event = MotionEvent.obtain(
        eventTime, eventTime,
        MotionEvent.ACTION_SCROLL,
        1, properties, coords,
        0, 0, 0f, 0f, 0, 0,
        InputDevice.SOURCE_CLASS_POINTER, 0
    )

    try {
        view.dispatchGenericMotionEvent(event)
    } finally {
        event.recycle()
    }
}

/**
 * Checks whether the given URL points to a local/private network address.
 * Used to restrict SSL bypass to trusted local servers only.
 */
private fun isLocalNetworkUrl(url: String?): Boolean {
    if (url == null) return false
    return try {
        val host = Uri.parse(url).host ?: return false
        host == "localhost" ||
            host == "127.0.0.1" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            // 172.16.0.0 - 172.31.255.255
            (host.startsWith("172.") && run {
                val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: return@run false
                second in 16..31
            })
    } catch (_: Exception) {
        false
    }
}

private class MouseState {
    var up = false
    var down = false
    var left = false
    var right = false
    var center = false
    var downTime = 0L

    val isMoving: Boolean
        get() = up || down || left || right
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val cursorX = remember { mutableFloatStateOf(screenWidthPx / 2f) }
    val cursorY = remember { mutableFloatStateOf(screenHeightPx / 2f) }

    val mouseState = remember { MouseState() }

    val webView = remember {
        object : WebView(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                val keyCode = event.keyCode

                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { mouseState.up = true; return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { mouseState.down = true; return true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { mouseState.left = true; return true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { mouseState.right = true; return true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!mouseState.center) {
                                mouseState.center = true
                                mouseState.downTime = SystemClock.uptimeMillis()
                                simulateTouch(this, MotionEvent.ACTION_DOWN, cursorX.floatValue, cursorY.floatValue, mouseState.downTime)
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            this.evaluateJavascript(
                                """(function() {
                                    var escEvent = new KeyboardEvent('keydown', {
                                        key: 'Escape', code: 'Escape',
                                        keyCode: 27, which: 27,
                                        bubbles: true, cancelable: true
                                    });
                                    document.dispatchEvent(escEvent);
                                    return document.activeElement !== document.body;
                                })();"""
                            ) { result ->
                                if (result == "false") {
                                    if (this.canGoBack()) {
                                        this.goBack()
                                    } else {
                                        onDisconnect()
                                    }
                                }
                            }
                            return true
                        }
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { mouseState.up = false; return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { mouseState.down = false; return true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { mouseState.left = false; return true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { mouseState.right = false; return true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            mouseState.center = false
                            simulateTouch(this, MotionEvent.ACTION_UP, cursorX.floatValue, cursorY.floatValue, mouseState.downTime)
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Always use hardware layers. Realtek/TCL chipsets log GPU buffer warnings
            // (BufPoolFreeBuffer) but software rendering causes worse jank on these low-end SoCs.
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                userAgentString = "$userAgentString SeanimeAndroidTV"
            }

            // Debug mode - minSdk 26 so no version check needed (KITKAT = 19)
            WebView.setWebContentsDebuggingEnabled(true)

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "WebConsole: ${consoleMessage?.message().orEmpty()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Auto-configure VLC as external player if not already set
                    view?.evaluateJavascript(
                        """(function() {
                            var key = 'sea-playback-external-player-link';
                            var current = localStorage.getItem(key);
                            if (!current || current === '""') {
                                localStorage.setItem(key,
                                    '"intent://{url}#Intent;package=org.videolan.vlc;type=video;scheme={scheme};end"'
                                );
                            }
                        })();""", null
                    )
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return handleUri(view, request?.url?.toString().orEmpty())
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return handleUri(view, url.orEmpty())
                }

                private var lastIntentTime: Long = 0

                private fun handleUri(view: WebView?, url: String): Boolean {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastIntentTime < INTENT_DEBOUNCE_MS) return true
                    lastIntentTime = currentTime

                    // Let the WebView handle standard HTTP(S) URLs
                    if (url.startsWith("http://") || url.startsWith("https://")) return false

                    // Try parsing as an intent URI first, fall back to ACTION_VIEW
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        intent?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            view?.context?.startActivity(it)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse intent URI: $url", e)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            view?.context?.startActivity(intent)
                            return true
                        } catch (e2: ActivityNotFoundException) {
                            Log.w(TAG, "No activity found for URI: $url", e2)
                        }
                    }
                    return false
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // Only bypass SSL errors for local/private network addresses.
                    // Public URLs must go through proper certificate validation.
                    val requestUrl = error?.url
                    if (isLocalNetworkUrl(requestUrl)) {
                        Log.d(TAG, "Bypassing SSL error for local network URL: $requestUrl")
                        handler?.proceed()
                    } else {
                        Log.w(TAG, "SSL error on public URL, cancelling: $requestUrl — ${error?.primaryError}")
                        handler?.cancel()
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    if (detail?.didCrash() == true) {
                        Log.e(TAG, "WebView render process crashed, reloading")
                        view?.loadUrl(url)
                        return true
                    }
                    return false
                }
            }

            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            loadUrl(url)
        }
    }

    // Cursor movement loop — runs at ~60 FPS
    LaunchedEffect(Unit) {
        var lastTime = SystemClock.uptimeMillis()
        var speedMultiplier = 1f

        while (isActive) {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTime) / 1000f
            lastTime = now

            if (mouseState.isMoving) {
                speedMultiplier = (speedMultiplier + dt * ACCELERATION_RATE).coerceAtMost(MAX_SPEED_MULTIPLIER)
                val speed = BASE_SPEED_PX_PER_SEC * speedMultiplier * dt

                var newX = cursorX.floatValue
                var newY = cursorY.floatValue

                if (mouseState.up) newY -= speed
                if (mouseState.down) newY += speed
                if (mouseState.left) newX -= speed
                if (mouseState.right) newX += speed

                newX = newX.coerceIn(0f, screenWidthPx)
                newY = newY.coerceIn(0f, screenHeightPx)

                cursorX.floatValue = newX
                cursorY.floatValue = newY

                if (mouseState.center) {
                    // Hold center + move → drag gesture (for carousels)
                    simulateTouch(webView, MotionEvent.ACTION_MOVE, newX, newY, mouseState.downTime)
                } else {
                    // Edge scrolling when cursor reaches screen borders
                    if (newY < EDGE_SCROLL_THRESHOLD && mouseState.up) {
                        simulateScroll(webView, newX, newY, 0f, 1f)
                    } else if (newY > screenHeightPx - EDGE_SCROLL_THRESHOLD && mouseState.down) {
                        simulateScroll(webView, newX, newY, 0f, -1f)
                    }

                    if (newX < EDGE_SCROLL_THRESHOLD && mouseState.left) {
                        simulateScroll(webView, newX, newY, 1f, 0f)
                    } else if (newX > screenWidthPx - EDGE_SCROLL_THRESHOLD && mouseState.right) {
                        simulateScroll(webView, newX, newY, -1f, 0f)
                    }
                }
            } else {
                speedMultiplier = 1f
            }

            delay(FRAME_DELAY_MS)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { _ -> webView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White,
                radius = CURSOR_OUTER_RADIUS,
                center = Offset(cursorX.floatValue, cursorY.floatValue)
            )
            drawCircle(
                color = Color(0xFF3b82f6),
                radius = CURSOR_INNER_RADIUS,
                center = Offset(cursorX.floatValue, cursorY.floatValue)
            )
        }
    }
}
