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
 * Renders Text, Logo, Lower Third and Web overlays into a transparent
 * video-resolution Bitmap.
 *
 * This class does NOT use MediaProjection or screen sharing.
 *
 * The resulting Bitmap is intended to be consumed by the video compositor
 * and blended directly with the camera frame before encoding.
 */
object OverlayRenderer {

    private val logoCache = HashMap<String, Bitmap>()

    private val webViews =
        ConcurrentHashMap<String, WebView>()

    private val webBitmaps =
        ConcurrentHashMap<String, Bitmap>()

    private val initializingWebViews =
        ConcurrentHashMap<String, Boolean>()

    private val mainHandler =
        Handler(Looper.getMainLooper())

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

    /**
     * Creates a transparent ARGB bitmap containing all enabled overlays.
     *
     * Coordinates supplied by the Compose editor are scaled from the
     * preview/container coordinate system into the actual video resolution.
     */
    fun render(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {

        lastContext = context.applicationContext
        lastItems = items.toList()
        lastContainerWidth = containerWidthPx
        lastContainerHeight = containerHeightPx
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight

        if (videoWidth <= 0 || videoHeight <= 0) {
            stopWebCaptureLoop()
            return null
        }

        if (
            items.isEmpty() ||
            containerWidthPx <= 0 ||
            containerHeightPx <= 0
        ) {
            stopWebCaptureLoop()
            return null
        }

        val scaleX =
            videoWidth.toFloat() / containerWidthPx.toFloat()

        val scaleY =
            videoHeight.toFloat() / containerHeightPx.toFloat()

        val positionScale =
            (scaleX + scaleY) * 0.5f

        val density =
            context.resources.displayMetrics.density

        val scaledDensity =
            context.resources.displayMetrics.scaledDensity

        val bitmap = try {
            Bitmap.createBitmap(
                videoWidth,
                videoHeight,
                Bitmap.Config.ARGB_8888
            )
        } catch (_: Throwable) {
            return null
        }

        bitmap.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(bitmap)

        var containsWeb = false

        for (item in items) {

            val x = item.x * scaleX
            val y = item.y * scaleY

            val itemScale =
                item.scale
                    .coerceIn(0.05f, 10f) *
                    positionScale

            when (item.type) {

                OverlayType.TEXT -> {
                    drawText(
                        canvas = canvas,
                        item = item,
                        x = x,
                        y = y,
                        scale = itemScale,
                        scaledDensity = scaledDensity
                    )
                }

                OverlayType.LOGO -> {
                    drawLogo(
                        context = context,
                        canvas = canvas,
                        item = item,
                        x = x,
                        y = y,
                        scale = itemScale,
                        density = density
                    )
                }

                OverlayType.LOWER_THIRD -> {
                    drawLowerThird(
                        canvas = canvas,
                        item = item,
                        x = x,
                        y = y,
                        scale = itemScale,
                        density = density,
                        scaledDensity = scaledDensity
                    )
                }

                OverlayType.WEB -> {
                    containsWeb = true

                    drawWeb(
                        canvas = canvas,
                        item = item,
                        x = x,
                        y = y,
                        scale = itemScale,
                        density = density
                    )
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

        val fontSize =
            22f *
                scaledDensity *
                scale

        val padding =
            12f *
                scaledDensity *
                scale

        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fontSize
                isSubpixelText = true
            }

        val text =
            item.content

        val textWidth =
            textPaint.measureText(text)

        val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = 128
            }

        val right =
            x +
                textWidth +
                padding * 2f

        val bottom =
            y +
                fontSize +
                padding * 2f

        canvas.drawRoundRect(
            RectF(
                x,
                y,
                right,
                bottom
            ),
            padding * 0.5f,
            padding * 0.5f,
            backgroundPaint
        )

        canvas.drawText(
            text,
            x + padding,
            y + padding + fontSize * 0.82f,
            textPaint
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

        val source =
            item.content.trim()

        if (source.isEmpty()) {
            return
        }

        val bitmap =
            synchronized(logoCache) {

                val cached =
                    logoCache[source]

                if (
                    cached != null &&
                    !cached.isRecycled
                ) {
                    cached
                } else {

                    val decoded =
                        runCatching {

                            val uri =
                                Uri.parse(source)

                            context
                                .contentResolver
                                .openInputStream(uri)
                                ?.use { input ->
                                    BitmapFactory.decodeStream(input)
                                }

                        }.getOrNull()

                    if (
                        decoded != null &&
                        !decoded.isRecycled
                    ) {
                        logoCache[source] = decoded
                    }

                    decoded
                }
            }

        if (
            bitmap == null ||
            bitmap.isRecycled
        ) {
            return
        }

        val size =
            (
                100f *
                    density *
                    scale
            ).coerceAtLeast(1f)

        val destination =
            RectF(
                x,
                y,
                x + size,
                y + size
            )

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        paint.isFilterBitmap = true

        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            paint
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

        val parts =
            item.content.split(
                "||",
                limit = 2
            )

        val title =
            parts
                .getOrNull(0)
                .orEmpty()

        val subtitle =
            parts
                .getOrNull(1)
                .orEmpty()

        val padding =
            12f *
                density *
                scale

        val titleSize =
            20f *
                scaledDensity *
                scale

        val subtitleSize =
            16f *
                scaledDensity *
                scale

        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = titleSize
                isFakeBoldText = true
                isSubpixelText = true
            }

        val subtitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.YELLOW
                textSize = subtitleSize
                isSubpixelText = true
            }

        val contentWidth =
            maxOf(
                titlePaint.measureText(title),
                subtitlePaint.measureText(subtitle)
            )

        val contentHeight =
            titleSize +
                subtitleSize +
                padding

        val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(
                    0,
                    70,
                    180
                )
                alpha = 204
            }

        canvas.drawRoundRect(
            RectF(
                x,
                y,
                x +
                    contentWidth +
                    padding * 2f,
                y +
                    contentHeight +
                    padding * 2f
            ),
            padding * 0.5f,
            padding * 0.5f,
            backgroundPaint
        )

        canvas.drawText(
            title,
            x + padding,
            y +
                padding +
                titleSize * 0.82f,
            titlePaint
        )

        canvas.drawText(
            subtitle,
            x + padding,
            y +
                padding +
                titleSize +
                subtitleSize * 0.82f,
            subtitlePaint
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

        val url =
            item.content.trim()

        if (url.isEmpty()) {
            return
        }

        ensureWebView(url)

        val bitmap =
            webBitmaps[url]
                ?: return

        if (bitmap.isRecycled) {
            return
        }

        val targetWidth =
            (
                400f *
                    density *
                    scale
            ).toInt()
                .coerceAtLeast(100)

        val targetHeight =
            (
                300f *
                    density *
                    scale
            ).toInt()
                .coerceAtLeast(100)

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                x,
                y,
                x + targetWidth,
                y + targetHeight
            ),
            paint
        )
    }

    private fun ensureWebView(
        url: String
    ) {

        if (webViews.containsKey(url)) {
            return
        }

        if (
            initializingWebViews.putIfAbsent(
                url,
                true
            ) != null
        ) {
            return
        }

        val context =
            lastContext

        if (context == null) {
            initializingWebViews.remove(url)
            return
        }

        mainHandler.post {

            try {

                if (webViews.containsKey(url)) {
                    return@post
                }

                val webView =
                    WebView(context).apply {

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        settings.mediaPlaybackRequiresUserGesture =
                            false

                        setBackgroundColor(
                            Color.TRANSPARENT
                        )

                        webViewClient =
                            WebViewClient()

                        layout(
                            0,
                            0,
                            1280,
                            720
                        )

                        loadUrl(url)
                    }

                webViews[url] = webView

            } catch (_: Throwable) {

            } finally {
                initializingWebViews.remove(url)
            }
        }
    }

    private fun startWebCaptureLoop() {

        if (webLoopRunning) {
            return
        }

        webLoopRunning = true

        mainHandler.post(
            webCaptureRunnable
        )
    }

    private fun stopWebCaptureLoop() {

        webLoopRunning = false

        mainHandler.removeCallbacks(
            webCaptureRunnable
        )
    }

    private val webCaptureRunnable =
        object : Runnable {

            override fun run() {

                if (!webLoopRunning) {
                    return
                }

                var changed = false

                for (
                    entry in webViews.entries
                ) {

                    val url =
                        entry.key

                    val webView =
                        entry.value

                    if (
                        webView.width <= 0 ||
                        webView.height <= 0
                    ) {
                        continue
                    }

                    try {

                        val old =
                            webBitmaps[url]

                        val bitmap =
                            if (
                                old == null ||
                                old.isRecycled ||
                                old.width != webView.width ||
                                old.height != webView.height
                            ) {

                                val newBitmap =
                                    Bitmap.createBitmap(
                                        webView.width,
                                        webView.height,
                                        Bitmap.Config.ARGB_8888
                                    )

                                webBitmaps[url] =
                                    newBitmap

                                if (
                                    old != null &&
                                    !old.isRecycled
                                ) {
                                    old.recycle()
                                }

                                newBitmap

                            } else {
                                old
                            }

                        bitmap.eraseColor(
                            Color.TRANSPARENT
                        )

                        webView.draw(
                            Canvas(bitmap)
                        )

                        changed = true

                    } catch (_: Throwable) {
                    }
                }

                if (changed) {

                    val context =
                        lastContext

                    val items =
                        lastItems

                    if (
                        context != null &&
                        items.isNotEmpty()
                    ) {

                        render(
                            context = context,
                            items = items,
                            containerWidthPx =
                                lastContainerWidth,
                            containerHeightPx =
                                lastContainerHeight,
                            videoWidth =
                                lastVideoWidth,
                            videoHeight =
                                lastVideoHeight
                        )
                    }
                }

                if (webLoopRunning) {

                    mainHandler.postDelayed(
                        this,
                        100L
                    )
                }
            }
        }

    /**
     * Releases WebView and bitmap resources.
     */
    fun release() {

        stopWebCaptureLoop()

        mainHandler.post {

            for (webView in webViews.values) {
                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.removeAllViews()
                    webView.destroy()
                }
            }

            webViews.clear()
            initializingWebViews.clear()

            for (bitmap in webBitmaps.values) {
                runCatching {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }

            webBitmaps.clear()

            synchronized(logoCache) {
                for (bitmap in logoCache.values) {
                    runCatching {
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }

                logoCache.clear()
            }
        }

        lastContext = null
        lastItems = emptyList()
        lastContainerWidth = 0
        lastContainerHeight = 0
        lastVideoWidth = 0
        lastVideoHeight = 0
    }
}
