package com.flexi.jarvis.network

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GeneratedImage(
    val bytes: ByteArray,
    val mimeType: String
)

/**
 * Runs Puter.js inside an isolated Android WebView. Puter authenticates the
 * current user in that WebView and meters image generation to that user's
 * own Puter account. No Puter API key or shared developer account is used.
 */
class ImageGenerationApi(
    private val context: Context,
    private val baseUrl: String
) {
    suspend fun generate(prompt: String): GeneratedImage {
        require(prompt.isNotBlank() && prompt.length <= 4000) {
            "Please provide a shorter image description."
        }
        val source = generateSource(prompt)
        return withContext(Dispatchers.IO) { downloadImage(source) }
    }

    /**
     * Clear the embedded browser session when the JARVIS account signs out,
     * preventing the next JARVIS user on this device from inheriting it.
     */
    suspend fun signOut() = withContext(Dispatchers.Main.immediate) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private suspend fun generateSource(prompt: String): String =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val completed = AtomicBoolean(false)
                val endpoint = baseUrl.trim().trimEnd('/')
                if (!endpoint.startsWith("https://")) {
                    continuation.resumeWithException(
                        IOException("Puter image generation is not configured.")
                    )
                    return@suspendCancellableCoroutine
                }

                val webView = WebView(context)
                var popup: WebView? = null
                var popupDialog: Dialog? = null

                fun finish(result: Result<String>) {
                    handler.post {
                        if (!completed.compareAndSet(false, true)) return@post
                        runCatching { popupDialog?.dismiss() }
                        runCatching { popup?.stopLoading(); popup?.destroy() }
                        runCatching { webView.stopLoading() }
                        runCatching { webView.removeJavascriptInterface("JarvisPuterBridge") }
                        runCatching { webView.destroy() }
                        if (continuation.isActive) {
                            result.fold(
                                onSuccess = { continuation.resume(it) },
                                onFailure = { continuation.resumeWithException(it) }
                            )
                        }
                    }
                }

                class Bridge {
                    @JavascriptInterface
                    fun onImageReady(source: String) {
                        if (source.isBlank()) {
                            finish(Result.failure(IOException("Puter returned an empty image.")))
                        } else {
                            finish(Result.success(source))
                        }
                    }

                    @JavascriptInterface
                    fun onImageError(message: String) {
                        val safeMessage = message.take(350).ifBlank {
                            "Puter could not generate this image. Please try again."
                        }
                        finish(Result.failure(IOException(safeMessage)))
                    }
                }

                continuation.invokeOnCancellation {
                    handler.post {
                        if (completed.compareAndSet(false, true)) {
                            runCatching { popupDialog?.dismiss() }
                            runCatching { popup?.destroy() }
                            runCatching { webView.stopLoading(); webView.destroy() }
                        }
                    }
                }

                CookieManager.getInstance().setAcceptCookie(true)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(true)
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        safeBrowsingEnabled = true
                    }
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.addJavascriptInterface(Bridge(), "JarvisPuterBridge")

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val authWindow = WebView(context)
                        authWindow.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            allowFileAccess = false
                            allowContentAccess = false
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(authWindow, true)
                        val dialog = Dialog(context)
                        popup = authWindow
                        popupDialog = dialog
                        authWindow.webViewClient = WebViewClient()
                        authWindow.webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView?) {
                                dialog.dismiss()
                            }
                        }
                        dialog.setContentView(authWindow)
                        dialog.setOnDismissListener {
                            runCatching { authWindow.stopLoading(); authWindow.destroy() }
                            if (popup === authWindow) popup = null
                            if (popupDialog === dialog) popupDialog = null
                        }
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                            ?: return false
                        transport.webView = authWindow
                        resultMsg.sendToTarget()
                        dialog.show()
                        dialog.window?.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        return true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val script = "window.generateJarvisImage && window.generateJarvisImage(" +
                            JSONObject.quote(prompt) + ");"
                        view?.evaluateJavascript(script, null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            finish(
                                Result.failure(
                                    IOException("Could not connect to Puter. Check your internet connection.")
                                )
                            )
                        }
                    }
                }

                webView.loadUrl("$endpoint/puter-image")
            }
        }

    private fun downloadImage(source: String): GeneratedImage {
        if (source.startsWith("data:", ignoreCase = true)) {
            val comma = source.indexOf(',')
            if (comma < 0) throw IOException("Puter returned an invalid image.")
            val header = source.substring(0, comma)
            val mime = Regex("""data:([^;,]+)""").find(header)?.groupValues?.get(1)
                ?: "image/png"
            val payload = source.substring(comma + 1)
            val bytes = if (header.contains(";base64", ignoreCase = true)) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8.name())
                    .toByteArray(StandardCharsets.UTF_8)
            }
            validateImage(bytes, mime)
            return GeneratedImage(bytes, mime)
        }

        if (!source.startsWith("https://", ignoreCase = true)) {
            throw IOException("Puter returned an unsupported image link.")
        }
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "JARVIS-AI-Android")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Could not retrieve the generated image (HTTP $status).")
            }
            val mime = connection.contentType?.substringBefore(';')?.trim() ?: "image/png"
            if (!mime.startsWith("image/")) {
                throw IOException("Puter returned a file that is not an image.")
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 25 * 1024 * 1024) {
                        throw IOException("The generated image is too large to save.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            validateImage(bytes, mime)
            return GeneratedImage(bytes, mime)
        } finally {
            connection.disconnect()
        }
    }

    private fun validateImage(bytes: ByteArray, mime: String) {
        if (!mime.startsWith("image/") || bytes.isEmpty()) {
            throw IOException("Puter returned an invalid image.")
        }
        if (bytes.size > 25 * 1024 * 1024) {
            throw IOException("The generated image is too large to save.")
        }
    }
}
