package com.amarjeetmaan.ajlivestudio.streaming

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
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayItem
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayType
import java.util.concurrent.ConcurrentHashMap

object OverlayRenderer {

    private val logoCache = HashMap<String, Bitmap>()
    
    // Web Overlay Background Capture System
    private val webViews = ConcurrentHashMap<String, WebView>()
    private val initializingWebViews = ConcurrentHashMap<String, Boolean>()
    private val webBitmaps = ConcurrentHashMap<String, Bitmap>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var isWebLoopRunning = false
    
    private var lastContext: Context? = null
    private var lastItems: List<OverlayItem> = emptyList()
    private var lastVideoWidth = 0
    private var lastVideoHeight = 0
    private var lastContainerW = 0
    private var lastContainerH = 0

    fun render(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {
        lastContext = context
        lastItems = items
        lastContainerW = containerWidthPx
        lastContainerH = containerHeightPx
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight

        if (items.isEmpty() || containerWidthPx <= 0 || containerHeightPx <= 0) {
            isWebLoopRunning = false
            return null
        }

        val scaleX = videoWidth.toFloat() / containerWidthPx
        val scaleY = videoHeight.toFloat() / containerHeightPx
        val uniformScale = (scaleX + scaleY) / 2f
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        var hasWeb = false

        for (item in items) {
            val x = item.x * scaleX
            val y = item.y * scaleY
            val itemScale = item.scale * uniformScale
            when (item.type) {
                OverlayType.TEXT -> drawText(canvas, item, x, y, itemScale, scaledDensity)
                OverlayType.LOWER_THIRD -> drawLowerThird(canvas, item, x, y, itemScale, density, scaledDensity)
                OverlayType.LOGO -> drawLogo(context, canvas, item, x, y, itemScale, density)
                OverlayType.WEB -> {
                    hasWeb = true
                    drawWeb(context, canvas, item, x, y, itemScale, density)
                }
            }
        }
        
        if (hasWeb && !isWebLoopRunning) {
            isWebLoopRunning = true
            startWebCaptureLoop()
        } else if (!hasWeb) {
            isWebLoopRunning = false
        }

        return bitmap
    }

    private fun drawText(canvas: Canvas, item: OverlayItem, x: Float, y: Float, scale: Float, scaledDensity: Float) {
        val fontSize = 22f * scaledDensity * scale
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
        }
        val padding = 12f * scaledDensity * scale
        val textWidth = textPaint.measureText(item.content)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 128
        }
        canvas.drawRect(
            RectF(x, y, x + textWidth + padding * 2, y + fontSize + padding * 2),
            bgPaint
        )
        canvas.drawText(item.content, x + padding, y + padding + fontSize * 0.8f, textPaint)
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
        val parts = item.content.split("||")
        val title = parts.getOrNull(0) ?: ""
        val subtitle = parts.getOrNull(1) ?: ""

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

        val contentWidth = maxOf(titlePaint.measureText(title), subtitlePaint.measureText(subtitle))
        val contentHeight = titleSize + subtitleSize + padding

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            alpha = 204
        }
        canvas.drawRect(
            RectF(x, y, x + contentWidth + padding * 2, y + contentHeight + padding * 2),
            bgPaint
        )
        canvas.drawText(title, x + padding, y + padding + titleSize * 0.8f, titlePaint)
        canvas.drawText(subtitle, x + padding, y + padding + titleSize + subtitleSize * 0.8f, subtitlePaint)
    }

    private fun drawLogo(context: Context, canvas: Canvas, item: OverlayItem, x: Float, y: Float, scale: Float, density: Float) {
        val bitmap = logoCache.getOrPut(item.content) {
            val uri = Uri.parse(item.content)
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return
        }
        val size = 100f * density * scale
        val dest = RectF(x, y, x + size, y + size)
        canvas.drawBitmap(bitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawWeb(context: Context, canvas: Canvas, item: OverlayItem, x: Float, y: Float, scale: Float, density: Float) {
        val url = item.content
        val targetWidth = (400f * density * scale).toInt().coerceAtLeast(100)
        val targetHeight = (300f * density * scale).toInt().coerceAtLeast(100)

        if (!webViews.containsKey(url) && !initializingWebViews.containsKey(url)) {
            initializingWebViews[url] = true
            mainHandler.post {
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
                initializingWebViews.remove(url)
            }
        }

        val bmp = webBitmaps[url]
        if (bmp != null && !bmp.isRecycled) {
            val dest = RectF(x, y, x + targetWidth, y + targetHeight)
            canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG))
        }
    }

    private fun startWebCaptureLoop() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isWebLoopRunning) return
                var capturedAny = false

                for ((url, view) in webViews) {
                    if (view.width <= 0 || view.height <= 0) continue
                    try {
                        var bmp = webBitmaps[url]
                        if (bmp == null || bmp.width != view.width || bmp.height != view.height) {
                            bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                            webBitmaps[url] = bmp
                        }
                        bmp.eraseColor(Color.TRANSPARENT)
                        val vCanvas = Canvas(bmp)
                        view.draw(vCanvas)
                        capturedAny = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (capturedAny) {
                    val ctx = lastContext
                    if (ctx != null) {
                        render(ctx, lastItems, lastContainerW, lastContainerH, lastVideoWidth, lastVideoHeight)
                    }
                }

                if (isWebLoopRunning) {
                    mainHandler.postDelayed(this, 100)
                }
            }
        }, 100)
    }
}
