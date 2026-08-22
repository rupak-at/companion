package com.ambientcompanion.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

class CompanionView(context: Context) : View(context) {
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(58, 46, 56)
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 35, 20, 30)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val radius = minOf(width, height) * 0.37f
        bodyPaint.shader = RadialGradient(
            width / 2f - radius * 0.3f,
            height / 2f - radius * 0.35f,
            radius * 1.5f,
            intArrayOf(Color.rgb(255, 231, 168), Color.rgb(255, 178, 82)),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.37f

        canvas.drawOval(cx - radius, cy + radius * 0.78f, cx + radius, cy + radius * 1.06f, shadowPaint)
        canvas.drawCircle(cx, cy, radius, bodyPaint)

        facePaint.strokeWidth = radius * 0.13f
        canvas.drawPoint(cx - radius * 0.34f, cy - radius * 0.1f, facePaint)
        canvas.drawPoint(cx + radius * 0.34f, cy - radius * 0.1f, facePaint)
        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = radius * 0.08f
        canvas.drawArc(
            cx - radius * 0.24f,
            cy,
            cx + radius * 0.24f,
            cy + radius * 0.38f,
            10f,
            160f,
            false,
            facePaint,
        )
        facePaint.style = Paint.Style.FILL
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
