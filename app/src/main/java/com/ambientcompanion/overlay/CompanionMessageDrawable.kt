package com.ambientcompanion.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class CompanionMessageDrawable(
    private val pointerX: Float,
    private val pointerAtTop: Boolean,
    private val tailHeight: Float,
    private val cornerRadius: Float,
    fillColor: Int,
    strokeColor: Int,
    strokeWidth: Float,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val inset = stroke.strokeWidth / 2f
        val top = if (pointerAtTop) tailHeight else inset
        val bottom = if (pointerAtTop) bounds.height() - inset else bounds.height() - tailHeight
        val left = inset
        val right = bounds.width() - inset
        path.reset()
        path.addRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, Path.Direction.CW)
        val halfTail = tailHeight * .72f
        path.moveTo((pointerX - halfTail).coerceAtLeast(left + cornerRadius), if (pointerAtTop) top else bottom)
        path.lineTo(pointerX.coerceIn(left + cornerRadius, right - cornerRadius), if (pointerAtTop) inset else bounds.height() - inset)
        path.lineTo((pointerX + halfTail).coerceAtMost(right - cornerRadius), if (pointerAtTop) top else bottom)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
