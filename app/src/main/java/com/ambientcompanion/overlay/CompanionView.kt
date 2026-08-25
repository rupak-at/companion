package com.ambientcompanion.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import com.ambientcompanion.data.preferences.CompanionAppearance
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.R
import com.ambientcompanion.renderer.AccessoryId
import com.ambientcompanion.renderer.AnimationId
import com.ambientcompanion.renderer.CompanionRenderer

class CompanionView(context: Context) : View(context), CompanionRenderer {
    private var state: CompanionState = CompanionState.DAY_CLEAR
    private var appearance = CompanionAppearance.AMBIENT
    private var emoji = "😊"
    private var idleOpacity = .72f
    private var reducedMotion = false
    private var accessory: AccessoryId? = null
    private val mascot = BitmapFactory.decodeResource(resources, R.drawable.companion_mascot)
    private val mascotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(58, 46, 56)
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 35, 20, 30)
    }
    private val emojiPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.EMBEDDED_BITMAP_TEXT_FLAG,
    ).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
        isFilterBitmap = false
        isDither = false
        hinting = Paint.HINTING_ON
    }
    private val fadeRunnable = Runnable {
        animate().alpha(idleOpacity).setDuration(if (reducedMotion) 0 else 450).start()
    }

    init {
        contentDescription = "Ambient Companion. Tap for a message, drag to move, or long press for actions."
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateShader(width, height)
    }

    private fun updateShader(width: Int = this.width, height: Int = this.height) {
        if (width == 0 || height == 0) return
        val radius = minOf(width, height) * 0.37f
        val (highlight, base) = colorsFor(state)
        bodyPaint.shader = RadialGradient(
            width / 2f - radius * 0.3f,
            height / 2f - radius * 0.35f,
            radius * 1.5f,
            intArrayOf(highlight, base),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (appearance == CompanionAppearance.EMOJI) {
            emojiPaint.textSize = (minOf(width, height) * .82f).toInt().toFloat()
            val centerY = height / 2f - (emojiPaint.ascent() + emojiPaint.descent()) / 2f
            val centerX = (width / 2f).toInt().toFloat()
            canvas.drawText(emoji, centerX, centerY.toInt().toFloat(), emojiPaint)
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.37f
        mascotPaint.colorFilter = stateTint(state)
        canvas.drawBitmap(mascot, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), mascotPaint)
        drawAccessory(canvas, cx, cy, radius)
    }

    override fun performClick(): Boolean {
        super.performClick()
        wake()
        if (appearance == CompanionAppearance.EMOJI) return true
        animate().cancel()
        scaleX = 0.9f
        scaleY = 0.9f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotationBy(8f)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator())
            .withEndAction { animate().rotation(0f).setDuration(160).start() }
            .start()
        return true
    }

    fun playSurprisedReaction() {
        wake()
        if (appearance == CompanionAppearance.EMOJI) return
        animate().cancel()
        animate()
            .scaleX(1.22f)
            .scaleY(0.82f)
            .setDuration(140)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(OvershootInterpolator())
                    .setDuration(320)
                    .start()
            }
            .start()
    }

    fun setDragging(dragging: Boolean) {
        wake()
        animate().cancel()
        if (appearance == CompanionAppearance.EMOJI) {
            scaleX = 1f
            scaleY = 1f
            rotation = 0f
            translationY = 0f
            if (!dragging) scheduleIdleFade()
            return
        }
        animate()
            .scaleX(if (dragging) 0.92f else 1f)
            .scaleY(if (dragging) 1.08f else 1f)
            .alpha(1f)
            .setDuration(150)
            .withEndAction { if (!dragging) scheduleIdleFade() }
            .start()
    }

    fun startIdleAnimation(reducedMotion: Boolean = false) {
        animate().cancel()
        this.reducedMotion = reducedMotion
        if (reducedMotion || appearance == CompanionAppearance.EMOJI) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
            scheduleIdleFade()
            return
        }
        scheduleIdleFade()
        animate()
            .translationY(-resources.displayMetrics.density * 3f)
            .setDuration(1_800)
            .withEndAction {
                animate()
                    .translationY(0f)
                    .setDuration(1_800)
                    .withEndAction { startIdleAnimation(false) }
                    .start()
            }
            .start()
    }

    fun pauseAnimation() {
        removeCallbacks(fadeRunnable)
        animate().cancel()
    }

    override fun setState(state: CompanionState) = applyState(state, reducedMotion)

    override fun play(animation: AnimationId) {
        when (animation) {
            AnimationId.TAP_HAPPY -> performClick()
            AnimationId.DOUBLE_TAP_SURPRISED -> playSurprisedReaction()
            AnimationId.DRAG -> setDragging(true)
            AnimationId.EDGE_LAND -> setDragging(false)
            else -> startIdleAnimation(reducedMotion)
        }
    }

    override fun setAccessory(accessory: AccessoryId?) { this.accessory = accessory; invalidate() }
    override fun setOpacity(value: Float) { idleOpacity = value.coerceIn(.35f, 1f) }
    override fun pause() = pauseAnimation()
    override fun resume() = startIdleAnimation(reducedMotion)

    fun applyState(next: CompanionState, reducedMotion: Boolean) {
        if (next == state) return
        val apply = {
            state = next
            updateShader()
            invalidate()
        }
        if (reducedMotion || appearance == CompanionAppearance.EMOJI) {
            apply()
        } else {
            animate().alpha(0f).scaleX(.86f).scaleY(.86f).setDuration(150).withEndAction {
                apply()
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            }.start()
        }
    }

    fun configureAppearance(
        appearance: CompanionAppearance,
        emoji: String,
        idleOpacity: Float,
        reducedMotion: Boolean,
    ) {
        this.appearance = appearance
        this.emoji = emoji.ifBlank { "😊" }
        this.idleOpacity = idleOpacity.coerceIn(.35f, 1f)
        this.reducedMotion = reducedMotion
        if (appearance == CompanionAppearance.EMOJI) {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            rotation = 0f
            translationY = 0f
        }
        contentDescription = if (appearance == CompanionAppearance.EMOJI) {
            "$emoji floating emoji. Tap for a message, drag to move, or long press for actions."
        } else {
            "Ambient Companion. Tap for a message, drag to move, or long press for actions."
        }
        invalidate()
        startIdleAnimation(reducedMotion)
    }

    fun wake() {
        removeCallbacks(fadeRunnable)
        animate().cancel()
        alpha = 1f
    }

    private fun scheduleIdleFade() {
        removeCallbacks(fadeRunnable)
        postDelayed(fadeRunnable, IDLE_DELAY_MS)
    }

    private fun drawAccessory(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = radius * .07f
        when (accessory ?: state.defaultAccessory()) {
            AccessoryId.UMBRELLA -> {
                facePaint.color = Color.rgb(64, 108, 145)
                canvas.drawArc(cx - radius * .65f, cy - radius * 1.35f, cx + radius * .65f, cy - radius * .5f, 185f, 170f, false, facePaint)
            }
            AccessoryId.SCARF -> {
                facePaint.color = Color.rgb(128, 70, 105)
                canvas.drawLine(cx - radius * .65f, cy + radius * .48f, cx + radius * .65f, cy + radius * .48f, facePaint)
            }
            AccessoryId.CHARGING_SPARK -> {
                facePaint.color = Color.rgb(255, 225, 92)
                canvas.drawLine(cx + radius * .65f, cy - radius, cx + radius * .35f, cy - radius * .45f, facePaint)
            }
            else -> Unit
        }
        facePaint.color = Color.rgb(58, 46, 56)
        facePaint.style = Paint.Style.FILL
    }

    private fun CompanionState.defaultAccessory(): AccessoryId? = when (this) {
        CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN,
        CompanionState.NIGHT_RAIN -> AccessoryId.UMBRELLA
        CompanionState.COLD, CompanionState.SNOW -> AccessoryId.SCARF
        CompanionState.STORM -> AccessoryId.CHARGING_SPARK
        CompanionState.NIGHT_SLEEP -> AccessoryId.SLEEP_CAP
        else -> null
    }

    private fun stateTint(state: CompanionState): android.graphics.ColorFilter? = when (state) {
        CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN,
        CompanionState.NIGHT_SLEEP -> android.graphics.PorterDuffColorFilter(
            Color.argb(205, 170, 160, 230), android.graphics.PorterDuff.Mode.MULTIPLY,
        )
        CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN ->
            android.graphics.PorterDuffColorFilter(Color.rgb(205, 225, 235), android.graphics.PorterDuff.Mode.MULTIPLY)
        else -> null
    }

    private fun colorsFor(state: CompanionState): Pair<Int, Int> = when (state) {
        CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN, CompanionState.NIGHT_SLEEP -> Color.rgb(220, 213, 255) to Color.rgb(119, 95, 169)
        CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN, CompanionState.STORM -> Color.rgb(211, 238, 245) to Color.rgb(101, 145, 166)
        CompanionState.COLD, CompanionState.SNOW, CompanionState.FOG -> Color.rgb(239, 249, 249) to Color.rgb(151, 196, 199)
        CompanionState.EVENING_CLEAR, CompanionState.EVENING_CLOUDY -> Color.rgb(255, 220, 198) to Color.rgb(199, 123, 132)
        else -> Color.rgb(255, 231, 168) to Color.rgb(255, 178, 82)
    }

    companion object {
        private const val IDLE_DELAY_MS = 2_500L
    }
}
