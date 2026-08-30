package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts the current overlay state into a video-resolution ARGB bitmap.
 * The bitmap is later uploaded by OverlayCompositor to the GPU and blended into
 * the actual StreamPack video frame before encoding.
 */
object OverlayRenderer {

    private val logoCache = HashMap<String, Bitmap>()
    private val webViews = ConcurrentHashMap<String, WebView>()
    private val webBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val initializingWebViews = ConcurrentHashMap<String, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var webLoopRunning = false

    @Volatile
    private var lastContext: Context? = null

    @Volatile
    private var lastItems: List<OverlayItem> = emptyList()

    @Volatile
    private var lastContainerWidth = 0

    @Volatile
    private var lastContainerHeight = 0

    @Volatile
    private var lastVideoWidth = 0

    @Volatile
    private var lastVideoHeight = 0

    fun render(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {
        lastContext = context
        lastItems = items.toList()
        lastContainerWidth = containerWidthPx
        lastContainerHeight = containerHeightPx
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight

        if (videoWidth <= 0 || videoHeight <= 0) return null

        if (items.isEmpty() || containerWidthPx <= 0 || containerHeightPx <= 0) {
            stopWebCaptureLoop()
            return null
        }

        val scaleX = videoWidth.toFloat() / containerWidthPx.toFloat()
        val scaleY = videoHeight.toFloat() / containerHeightPx.toFloat()
        val positionScale = (scaleX + scaleY) * 0.5f
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val bitmap = Bitmap.createBitmap(
            videoWidth,
            videoHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(bitmap)
        var containsWeb = false

        items.forEach { item ->
            val x = item.x * scaleX
            val y = item.y * scaleY
            val itemScale = item.scale.coerceIn(0.05f, 10f) * positionScale

            when (item.type) {
                OverlayType.TEXT -> {
                    drawText(canvas, item, x, y, itemScale, scaledDensity)
                }

                OverlayType.LOGO -> {
                    drawLogo(context, canvas, item, x, y, itemScale, density)
                }

                OverlayType.LOWER_THIRD -> {
                    drawLowerThird(canvas, item, x, y, itemScale, density, scaledDensity)
                }

                OverlayType.WEB -> {
                    containsWeb = true
                    drawWeb(canvas, item, x, y, itemScale, density)
                }
            }
        }

        if (containsWeb) {
            startWebCaptureLoop()
        } else {
            stopWebCaptureLoop()
        }

        return bitmap
    }

    private fun drawText(
        canvas: Canvas,
        item: OverlayItem,
        x: Float,
        y: Float,
        scale: Float,
        scaledDensity: Float
    ) {
        val fontSize = 22f * scaledDensity * scale
        val padding = 12f * scaledDensity * scale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            isSubpixelText = true
        }

        val textWidth = textPaint.measureText(item.content)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 128
        }

        canvas.drawRoundRect(
            RectF(
                x,
                y,
                x + textWidth + padding * 2f,
                y + fontSize + padding * 2f
            ),
            padding * 0.5f,
            padding * 0.5f,
            backgroundPaint
        )

        canvas.drawText(
            item.content,
            x + padding,
            y + padding + fontSize * 0.82f,
            textPaint
        )
    }

    private fun drawLowerThird(
        canvas: Canvas,
        item: OverlayItem,
        x: Float,
        y: Float,
        scale: Float,
        density: Float,
        scaledDensity: Float
    ) {
        val parts = item.content.split("||", limit = 2)
        val title = parts.getOrNull(0).orEmpty()
        val subtitle = parts.getOrNull(1).orEmpty()

        val padding = 12f * density * scale
        val titleSize = 20f * scaledDensity * scale
        val subtitleSize = 16f * scaledDensity * scale

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleSize
            isFakeBoldText = true
        }

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = subtitleSize
        }

        val contentWidth = maxOf(
            titlePaint.measureText(title),
            subtitlePaint.measureText(subtitle)
        )
        val contentHeight = titleSize + subtitleSize + padding

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 70, 180)
            alpha = 204
        }

        canvas.drawRoundRect(
            RectF(
                x,
                y,
                x + contentWidth + padding * 2f,
                y + contentHeight + padding * 2f
            ),
            padding * 0.5f,
            padding * 0.5f,
            backgroundPaint
        )

        canvas.drawText(
            title,
            x + padding,
            y + padding + titleSize * 0.82f,
            titlePaint
        )

        canvas.drawText(
            subtitle,
            x + padding,
            y + padding + titleSize + subtitleSize * 0.82f,
            subtitlePaint
        )
    }

    private fun drawLogo(
        context: Context,
        canvas: Canvas,
        item: OverlayItem,
        x: Float,
        y: Float,
        scale: Float,
        density: Float
    ) {
        val bitmap = synchronized(logoCache) {
            logoCache[item.content] ?: run {
                val decoded = runCatching {
                    val uri = Uri.parse(item.content)
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()

                if (decoded != null) {
                    logoCache[item.content] = decoded
                }
                decoded
            }
        } ?: return

        if (bitmap.isRecycled) return

        val size = (100f * density * scale).coerceAtLeast(1f)
        val destination = RectF(x, y, x + size, y + size)

        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun drawWeb(
        canvas: Canvas,
        item: OverlayItem,
        x: Float,
        y: Float,
        scale: Float,
        density: Float
    ) {
        val url = item.content.trim()
        if (url.isEmpty()) return

        ensureWebView(url)

        val bitmap = webBitmaps[url] ?: return
        if (bitmap.isRecycled) return

        val targetWidth = (400f * density * scale).toInt().coerceAtLeast(100)
        val targetHeight = (300f * density * scale).toInt().coerceAtLeast(100)

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                x,
                y,
                x + targetWidth,
                y + targetHeight
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun ensureWebView(url: String) {
        if (webViews.containsKey(url)) return
        if (initializingWebViews.putIfAbsent(url, true) != null) return

        val context = lastContext ?: run {
            initializingWebViews.remove(url)
            return
        }

        mainHandler.post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(Color.TRANSPARENT)
                    webViewClient = WebViewClient()
                    layout(0, 0, 1280, 720)
                    loadUrl(url)
                }

                webViews[url] = webView
            } finally {
                initializingWebViews.remove(url)
            }
        }
    }

    private fun startWebCaptureLoop() {
        if (webLoopRunning) return
        webLoopRunning = true

        mainHandler.post(webCaptureRunnable)
    }

    private fun stopWebCaptureLoop() {
        webLoopRunning = false
        mainHandler.removeCallbacks(webCaptureRunnable)
    }

    private val webCaptureRunnable = object : Runnable {
        override fun run() {
            if (!webLoopRunning) return

            var changed = false

            for ((url, webView) in webViews) {
                if (webView.width <= 0 || webView.height <= 0) continue

                try {
                    val old = webBitmaps[url]
                    val bitmap = if (
                        old == null ||
                        old.isRecycled ||
                        old.width != webView.width ||
                        old.height != webView.height
                    ) {
                        Bitmap.createBitmap(
                            webView.width,
                            webView.height,
                            Bitmap.Config.ARGB_8888
                        ).also {
                            webBitmaps[url] = it
                            if (old != null && !old.isRecycled) old.recycle()
                        }
                    } else {
                        old
                    }

                    bitmap.eraseColor(Color.TRANSPARENT)
                    webView.draw(Canvas(bitmap))
                    changed = true
                } catch (_: Throwable) {
                    // Keep the capture loop alive; the next cycle can recover.
                }
            }

            if (changed) {
                val context = lastContext
                if (context != null && lastItems.isNotEmpty()) {
                    render(
                        context = context,
                        items = lastItems,
                        containerWidthPx = lastContainerWidth,
                        containerHeightPx = lastContainerHeight,
                        videoWidth = lastVideoWidth,
                        videoHeight = lastVideoHeight
                    )
                }
            }

            if (webLoopRunning) {
                mainHandler.postDelayed(this, 100L)
            }
        }
    }
}
