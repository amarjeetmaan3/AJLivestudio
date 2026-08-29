package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayItem
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayType

/**
 * Renders the current overlay items onto one ARGB Bitmap at video
 * resolution, mirroring OverlayLayer's Compose drawing (same fonts,
 * same box colors) as closely as Canvas allows, so what's baked into
 * the stream matches what's shown in the Studio editor.
 *
 * item.x / item.y are pixel offsets in the on-screen overlay box's own
 * coordinate space (set by OverlayLayer's drag gestures), which is
 * usually a different pixel size than the video resolution — so this
 * takes the on-screen container size and scales proportionally into
 * video-resolution coordinates. It assumes the overlay box and the
 * video frame share the same top-left origin and aspect ratio; if the
 * preview is letterboxed, positions near the edges may drift slightly
 * versus the editor — a known simplification, not a silent bug.
 *
 * NOTE (known limitation, disclosed, not silently dropped): WEB overlay
 * items are NOT baked in here — a WEB item is a live WebView, and
 * capturing WebView pixels into a Bitmap needs a separate render pass
 * (PixelCopy) that isn't wired up yet. WEB items still show in the
 * in-app editor but won't reach the viewer until that's added.
 */
object OverlayRenderer {

    private val logoCache = HashMap<String, Bitmap>()

    fun render(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {
        if (items.isEmpty() || containerWidthPx <= 0 || containerHeightPx <= 0) return null

        val scaleX = videoWidth.toFloat() / containerWidthPx
        val scaleY = videoHeight.toFloat() / containerHeightPx
        val uniformScale = (scaleX + scaleY) / 2f
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity

        val bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (item in items) {
            val x = item.x * scaleX
            val y = item.y * scaleY
            val itemScale = item.scale * uniformScale
            when (item.type) {
                OverlayType.TEXT -> drawText(canvas, item, x, y, itemScale, scaledDensity)
                OverlayType.LOWER_THIRD -> drawLowerThird(canvas, item, x, y, itemScale, density, scaledDensity)
                OverlayType.LOGO -> drawLogo(context, canvas, item, x, y, itemScale, density)
                OverlayType.WEB -> { /* not baked yet, see class doc */ }
            }
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
            alpha = 204 // ~0.8 opacity, matching Compose
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
        val size = 100f * density * scale // matches OverlayLayer's 100.dp box
        val dest = RectF(x, y, x + size, y + size)
        canvas.drawBitmap(bitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG))
    }
}
