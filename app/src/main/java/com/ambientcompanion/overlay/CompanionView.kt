package com.ambientcompanion.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator
import com.ambientcompanion.data.preferences.CompanionAppearance
import com.ambientcompanion.data.preferences.CompanionArtwork
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.R
import com.ambientcompanion.renderer.AccessoryId
import com.ambientcompanion.renderer.AnimationId
import com.ambientcompanion.renderer.CompanionRenderer
import com.ambientcompanion.ui.drawableRes

class CompanionView(context: Context) : View(context), CompanionRenderer {
    private var state: CompanionState = CompanionState.DAY_CLEAR
    private var appearance = CompanionAppearance.AMBIENT
    private var emoji = "😊"
    private var selectedArtwork = CompanionArtwork.BIRD
    private var idleOpacity = .72f
    private var reducedMotion = false
    private var accessory: AccessoryId? = null
    private var theme: String = "default"
    private val mascot = BitmapFactory.decodeResource(resources, R.drawable.companion_mascot)
    private var artwork = BitmapFactory.decodeResource(resources, selectedArtwork.drawableRes())
    private val mascotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mascotBounds = RectF()
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
    init {
        contentDescription = "Ambient Companion. Tap for a message, drag to move, or long press for actions."
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        mascotBounds.set(0f, 0f, width.toFloat(), height.toFloat())
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
        mascotPaint.colorFilter = if (appearance == CompanionAppearance.AMBIENT) stateTint(state) else null
        canvas.drawBitmap(if (appearance == CompanionAppearance.ARTWORK) artwork else mascot, null, mascotBounds, mascotPaint)
        drawAccessory(canvas, cx, cy, radius)
    }

    override fun performClick(): Boolean {
        super.performClick()
        wake()
        return true
    }

    private fun playHappyReaction() {
        if (appearance == CompanionAppearance.EMOJI) return
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
            return
        }
        animate()
            .scaleX(if (dragging) 0.92f else 1f)
            .scaleY(if (dragging) 1.08f else 1f)
            .alpha(1f)
            .setDuration(150)
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
            return
        }
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
        animate().cancel()
    }

    override fun setState(state: CompanionState) = applyState(state, reducedMotion)

    override fun play(animation: AnimationId) {
        if (appearance == CompanionAppearance.EMOJI && animation !in setOf(
                AnimationId.TAP_HAPPY, AnimationId.DOUBLE_TAP_SURPRISED,
                AnimationId.DRAG, AnimationId.EDGE_LAND,
            )
        ) {
            startIdleAnimation(true)
            return
        }
        when (animation) {
            AnimationId.BLINK -> animate().scaleY(.94f).setDuration(90).withEndAction { animate().scaleY(1f).setDuration(110).start() }.start()
            AnimationId.LOOK_LEFT -> animate().translationX(-resources.displayMetrics.density * 4f).setDuration(220).withEndAction { animate().translationX(0f).setDuration(220).start() }.start()
            AnimationId.LOOK_RIGHT -> animate().translationX(resources.displayMetrics.density * 4f).setDuration(220).withEndAction { animate().translationX(0f).setDuration(220).start() }.start()
            AnimationId.TAP_HAPPY -> playHappyReaction()
            AnimationId.DOUBLE_TAP_SURPRISED -> playSurprisedReaction()
            AnimationId.DRAG -> setDragging(true)
            AnimationId.EDGE_LAND -> setDragging(false)
            AnimationId.SLEEP -> animate().rotation(-5f).scaleY(.94f).alpha(.78f).setDuration(500).start()
            AnimationId.WAKE_UP -> { wake(); animate().rotation(0f).scaleY(1f).alpha(1f).setDuration(350).start() }
            AnimationId.BATTERY_LOW -> animate().translationY(resources.displayMetrics.density * 4f).rotation(-4f).setDuration(450).start()
            AnimationId.RAIN -> animate().rotationBy(-3f).setDuration(180).withEndAction { animate().rotation(0f).setDuration(180).start() }.start()
            AnimationId.COLD -> animate().translationX(resources.displayMetrics.density * 2f).setDuration(80).withEndAction { animate().translationX(-resources.displayMetrics.density * 2f).setDuration(80).withEndAction { animate().translationX(0f).setDuration(80).start() }.start() }.start()
            AnimationId.HOT -> animate().scaleY(.95f).scaleX(1.04f).setDuration(350).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(350).start() }.start()
            AnimationId.MESSAGE_SHOW -> animate().scaleX(1.06f).scaleY(1.06f).setDuration(160).start()
            AnimationId.MESSAGE_HIDE -> animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            AnimationId.STATE_TRANSITION -> animate().alpha(.7f).setDuration(120).withEndAction { animate().alpha(1f).setDuration(180).start() }.start()
            AnimationId.TIRED -> animate().scaleY(.92f).rotation(-3f).setDuration(500).start()
            AnimationId.EXHAUSTED -> animate().scaleY(.82f).rotation(-8f).translationY(resources.displayMetrics.density * 5f).setDuration(650).start()
            AnimationId.CHARGING, AnimationId.BATTERY_FULL, AnimationId.HEADPHONES,
            AnimationId.NETWORK_LOST, AnimationId.NETWORK_RESTORED, AnimationId.WEEKEND,
            AnimationId.TINY_JUMP, AnimationId.WAVE -> {
                wake(); animate().cancel(); animate().translationY(-resources.displayMetrics.density * 8f)
                    .rotationBy(8f).setDuration(180).withEndAction {
                        animate().translationY(0f).rotation(0f).setDuration(260).withEndAction { startIdleAnimation(reducedMotion) }.start()
                    }.start()
            }
            else -> startIdleAnimation(reducedMotion)
        }
    }

    override fun setAccessory(accessory: AccessoryId?) { this.accessory = accessory; invalidate() }
    override fun setOpacity(value: Float) { idleOpacity = value.coerceIn(.35f, 1f) }
    override fun pause() = pauseAnimation()
    override fun resume() = startIdleAnimation(reducedMotion)
    fun setTheme(theme: String) { this.theme = theme; invalidate() }

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
        artwork: CompanionArtwork,
        emoji: String,
        idleOpacity: Float,
        reducedMotion: Boolean,
    ) {
        this.appearance = appearance
        if (artwork != selectedArtwork) {
            selectedArtwork = artwork
            this.artwork = BitmapFactory.decodeResource(resources, artwork.drawableRes())
        }
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
        contentDescription = when (appearance) {
            CompanionAppearance.EMOJI -> "$emoji floating emoji. Tap for a message, drag to move, or long press for actions."
            CompanionAppearance.ARTWORK -> "${artwork.label} floating companion. Tap for a message, drag to move, or long press for actions."
            CompanionAppearance.AMBIENT -> "Ambient Companion. Tap for a message, drag to move, or long press for actions."
        }
        invalidate()
        startIdleAnimation(reducedMotion)
    }

    fun wake() {
        animate().cancel()
        alpha = 1f
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
        CompanionState.CRITICAL_BATTERY -> android.graphics.PorterDuffColorFilter(Color.rgb(210, 120, 130), android.graphics.PorterDuff.Mode.MULTIPLY)
        CompanionState.LOW_BATTERY -> android.graphics.PorterDuffColorFilter(Color.rgb(235, 185, 125), android.graphics.PorterDuff.Mode.MULTIPLY)
        CompanionState.CHARGING -> android.graphics.PorterDuffColorFilter(Color.rgb(180, 235, 190), android.graphics.PorterDuff.Mode.MULTIPLY)
        CompanionState.BATTERY_FULL -> android.graphics.PorterDuffColorFilter(Color.rgb(190, 245, 205), android.graphics.PorterDuff.Mode.MULTIPLY)
        CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN,
        CompanionState.NIGHT_SLEEP -> android.graphics.PorterDuffColorFilter(
            Color.argb(205, 170, 160, 230), android.graphics.PorterDuff.Mode.MULTIPLY,
        )
        CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN ->
            android.graphics.PorterDuffColorFilter(Color.rgb(205, 225, 235), android.graphics.PorterDuff.Mode.MULTIPLY)
        else -> when (theme) {
            "night glow" -> android.graphics.PorterDuffColorFilter(Color.rgb(195, 180, 240), android.graphics.PorterDuff.Mode.MULTIPLY)
            "warm sunset" -> android.graphics.PorterDuffColorFilter(Color.rgb(255, 205, 175), android.graphics.PorterDuff.Mode.MULTIPLY)
            "cloud" -> android.graphics.PorterDuffColorFilter(Color.rgb(215, 235, 240), android.graphics.PorterDuff.Mode.MULTIPLY)
            "mono" -> android.graphics.PorterDuffColorFilter(Color.rgb(205, 205, 205), android.graphics.PorterDuff.Mode.MULTIPLY)
            else -> null
        }
    }

    private fun colorsFor(state: CompanionState): Pair<Int, Int> = when (state) {
        CompanionState.NIGHT_CLEAR, CompanionState.NIGHT_CLOUDY, CompanionState.NIGHT_RAIN, CompanionState.NIGHT_SLEEP -> Color.rgb(220, 213, 255) to Color.rgb(119, 95, 169)
        CompanionState.MORNING_RAIN, CompanionState.DAY_RAIN, CompanionState.EVENING_RAIN, CompanionState.STORM -> Color.rgb(211, 238, 245) to Color.rgb(101, 145, 166)
        CompanionState.COLD, CompanionState.SNOW, CompanionState.FOG -> Color.rgb(239, 249, 249) to Color.rgb(151, 196, 199)
        CompanionState.EVENING_CLEAR, CompanionState.EVENING_CLOUDY -> Color.rgb(255, 220, 198) to Color.rgb(199, 123, 132)
        else -> Color.rgb(255, 231, 168) to Color.rgb(255, 178, 82)
    }

    companion object {
    }
}
