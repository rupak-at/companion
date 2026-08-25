package com.ambientcompanion.overlay

import android.animation.ValueAnimator
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ambientcompanion.MainActivity
import com.ambientcompanion.AmbientApplication
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.accessibility.AssistiveAction
import com.ambientcompanion.domain.message.MessageEngine
import com.ambientcompanion.domain.message.MessagePackId
import com.ambientcompanion.domain.message.MessageRequest
import com.ambientcompanion.domain.context.AmbientContext
import com.ambientcompanion.domain.context.CompanionPreferences
import com.ambientcompanion.domain.context.BatteryState
import com.ambientcompanion.domain.context.BatteryStateTracker
import com.ambientcompanion.domain.context.ResourceMode
import com.ambientcompanion.domain.rule.RuleEngine
import com.ambientcompanion.domain.rule.CompanionEvent
import com.ambientcompanion.domain.rule.CompanionEventType
import com.ambientcompanion.domain.rule.EventCooldowns
import com.ambientcompanion.domain.rule.EventQueue
import com.ambientcompanion.domain.schedule.SchedulePolicy
import com.ambientcompanion.renderer.AnimationId
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.screenshot.ScreenshotPermissionActivity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalTime

class CompanionOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var companionView: CompanionView? = null
    private var bubbleView: View? = null
    private var quickMenuView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app by lazy { application as AmbientApplication }
    private val messageEngine = MessageEngine()
    private val ruleEngine = RuleEngine()
    private val eventQueue = EventQueue()
    private val eventCooldowns = EventCooldowns()
    private val batteryStateTracker = BatteryStateTracker()
    private var lastDeviceContext: com.ambientcompanion.domain.context.DeviceContext? = null
    private var settings = UserSettings()
    private var currentState = CompanionState.DAY_CLEAR
    private var previewUntil = 0L
    private var receiverRegistered = false
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> companionView?.pauseAnimation()
                Intent.ACTION_SCREEN_ON -> { companionView?.startIdleAnimation(settings.reducedMotion); refreshContext() }
                Intent.ACTION_CONFIGURATION_CHANGED -> clampToScreen()
                ACTION_CONTEXT_UPDATED -> refreshContext(true)
                ACTION_SETTINGS_UPDATED -> syncVisibility()
                ACTION_HIDE -> hideCompanion(true)
                ACTION_RESET_POSITION -> resetPosition()
                ACTION_PREVIEW -> intent.getStringExtra(EXTRA_STATE)?.let(::previewState)
            }
        }
    }
    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerSystemReceiver()
        observeDeviceContext()
        syncVisibility()
    }

    private fun previewState(name: String) {
        runCatching { CompanionState.valueOf(name) }.getOrNull()?.let {
            previewUntil = android.os.SystemClock.uptimeMillis() + PREVIEW_DURATION_MS
            applyState(it)
            handler.postDelayed({ refreshContext() }, PREVIEW_DURATION_MS)
        }
    }

    private fun syncVisibility() = serviceScope.launch {
        settings = app.preferences.currentSettings()
        if (settings.companionEnabled && settings.hiddenUntil <= System.currentTimeMillis()) {
            if (companionView == null) showCompanion()
            refreshContext()
        } else hideCompanion()
    }

    private fun hideCompanion(updatePreference: Boolean = false) {
        companionView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        quickMenuView?.let { runCatching { windowManager.removeView(it) } }
        companionView = null; bubbleView = null; quickMenuView = null; isRunning = false
        if (updatePreference) serviceScope.launch { app.preferences.updateSettings { it.copy(companionEnabled = false) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideCompanion()
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(systemReceiver)
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun showCompanion() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val screen = screenSize()
        val size = dp(84)
        val maxX = (screen.x - size).coerceAtLeast(0)
        val maxY = (screen.y - size).coerceAtLeast(0)
        val savedX = preferences.getFloat(KEY_X, 1f).coerceIn(0f, 1f)
        val savedY = preferences.getFloat(KEY_Y, 0.45f).coerceIn(0f, 1f)

        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (savedX * maxX).roundToInt()
            y = (savedY * maxY).roundToInt()
        }

        companionView = CompanionView(this).also { view ->
            installDragHandling(view)
            windowManager.addView(view, layoutParams)
            view.startIdleAnimation()
        }
        isRunning = true
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(ACTION_CONTEXT_UPDATED)
            addAction(ACTION_SETTINGS_UPDATED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun refreshContext(force: Boolean = false) {
        serviceScope.launch {
            settings = app.preferences.currentSettings()
            resizeCompanion(settings.companionSizeDp)
            val device = app.deviceContextSource.state.value
            companionView?.configureAppearance(
                if (settings.resourceMode == ResourceMode.MINIMAL) com.ambientcompanion.data.preferences.CompanionAppearance.EMOJI else settings.companionAppearance,
                settings.selectedEmoji,
                settings.idleOpacity,
                settings.reducedMotion || device.isPowerSaveMode || settings.resourceMode != ResourceMode.NORMAL,
            )
            val snapshot = app.contextRepository.refresh(force)
            if (android.os.SystemClock.uptimeMillis() >= previewUntil) applyAmbientContext(snapshot.context)
        }
    }

    private fun observeDeviceContext() = serviceScope.launch {
        app.deviceContextSource.state.collectLatest { device ->
            val previous = lastDeviceContext
            lastDeviceContext = device
            if (previous != null) enqueueDeviceEvents(previous, device)
            if (companionView != null) refreshContext()
        }
    }

    private suspend fun applyAmbientContext(environment: com.ambientcompanion.domain.model.CompanionContext) {
        val now = LocalTime.now()
        val quiet = settings.quietHoursEnabled && SchedulePolicy.contains(now, settings.quietStartMinutes, settings.quietEndMinutes)
        val outsideActive = settings.activeHoursEnabled && !SchedulePolicy.contains(now, settings.activeStartMinutes, settings.activeEndMinutes)
        val rawDevice = app.deviceContextSource.state.value
        val device = rawDevice.copy(
            isWeekend = SchedulePolicy.isWeekend(app.deviceContextSource.state.value.dayOfWeek, settings.weekendDays),
            classifiedBatteryState = batteryStateTracker.update(rawDevice.batteryPercent, rawDevice.isBatteryFull),
        )
        val context = AmbientContext(environment, device, CompanionPreferences(
            personality = settings.personality,
            quietHoursActive = quiet,
            outsideActiveHours = outsideActive,
            connectivityReactions = settings.connectivityReactions,
            headphoneReactions = settings.headphoneReactions,
            batteryReactions = settings.batteryReactions,
            chargingReactions = settings.chargingReactions,
            weekendReactions = settings.weekendReactions,
            resourceMode = settings.resourceMode,
        ))
        val resolved = ruleEngine.resolve(context)
        currentState = resolved.behavior.visualState
        companionView?.apply {
            applyState(currentState, settings.reducedMotion || device.isPowerSaveMode)
            setAccessory(resolved.behavior.accessory)
        }
        val event = eventQueue.poll()
        if (event != null && !quiet) playEvent(event)
        else if (resolved.behavior.automaticMessageAllowed) maybeShowAutomaticMessage(currentState)
    }

    private fun enqueueDeviceEvents(old: com.ambientcompanion.domain.context.DeviceContext, new: com.ambientcompanion.domain.context.DeviceContext) {
        fun offer(type: CompanionEventType, cooldown: Long, priority: Int = 40) {
            if (eventCooldowns.allow(type, cooldown)) eventQueue.offer(CompanionEvent(type, System.currentTimeMillis(), priority))
        }
        if (!old.isCharging && new.isCharging && settings.chargingReactions) offer(CompanionEventType.CHARGING_STARTED, 1_000, 90)
        if (old.isCharging && !new.isCharging && settings.chargingReactions) offer(CompanionEventType.CHARGING_STOPPED, 1_000, 90)
        if (!old.isBatteryFull && new.isBatteryFull && settings.batteryReactions) offer(CompanionEventType.BATTERY_FULL, 60_000, 95)
        if (!old.isHeadphonesConnected && new.isHeadphonesConnected && settings.headphoneReactions) offer(CompanionEventType.HEADPHONES_CONNECTED, 30 * 60_000L)
        if (old.networkState != new.networkState && settings.connectivityReactions) {
            val type = if (new.networkState == com.ambientcompanion.domain.context.NetworkState.ONLINE) CompanionEventType.NETWORK_RESTORED else CompanionEventType.NETWORK_LOST
            offer(type, 10 * 60_000L)
        }
    }

    private fun playEvent(event: CompanionEvent) {
        val (animation, message) = when (event.type) {
            CompanionEventType.CHARGING_STARTED -> AnimationId.CHARGING to "Charging up!"
            CompanionEventType.CHARGING_STOPPED -> AnimationId.STATE_TRANSITION to "Unplugged"
            CompanionEventType.BATTERY_FULL -> AnimationId.BATTERY_FULL to "All full!"
            CompanionEventType.HEADPHONES_CONNECTED -> AnimationId.HEADPHONES to "Music time?"
            CompanionEventType.NETWORK_LOST -> AnimationId.NETWORK_LOST to "Lost connection?"
            CompanionEventType.NETWORK_RESTORED -> AnimationId.NETWORK_RESTORED to "Back online!"
        }
        companionView?.play(animation)
        if (settings.messagesEnabled) showMessage(message)
    }

    private fun applyState(state: CompanionState) {
        if (state == currentState) return
        currentState = state
        companionView?.applyState(state, settings.reducedMotion)
    }

    private fun resizeCompanion(sizeDp: Int) {
        val view = companionView ?: return
        val params = layoutParams ?: return
        val pixels = dp(sizeDp)
        if (params.width == pixels) return
        params.width = pixels
        params.height = pixels
        windowManager.updateViewLayout(view, params)
        clampToScreen()
    }

    private fun clampToScreen() {
        val view = companionView ?: return
        val params = layoutParams ?: return
        val screen = screenSize()
        params.x = params.x.coerceIn(0, (screen.x - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screen.y - params.height).coerceAtLeast(0))
        windowManager.updateViewLayout(view, params)
    }

    private fun resetPosition() {
        val view = companionView ?: return
        val params = layoutParams ?: return
        val screen = screenSize()
        params.x = (screen.x - params.width).coerceAtLeast(0)
        params.y = ((screen.y - params.height) * .45f).roundToInt().coerceAtLeast(0)
        windowManager.updateViewLayout(view, params)
        snapToNearestEdge(view)
    }

    private suspend fun maybeShowAutomaticMessage(state: CompanionState) {
        if (!settings.messagesEnabled || !settings.automaticMessages) return
        val (lastId, lastAt) = app.preferences.lastMessage()
        if (System.currentTimeMillis() - lastAt < messageEngine.automaticIntervalMs(settings.personality)) return
        val selected = messageEngine.select(MessageRequest(state, settings.personality, settings.messagePackId(), lastId, millisSinceLastTap = 0))
        showMessage(selected.text)
        app.preferences.saveLastMessage(selected.id, System.currentTimeMillis())
    }

    private fun installDragHandling(view: View) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        var longPressed = false
        var tapCount = 0
        lateinit var tapAction: Runnable
        fun runTapAction() {
            when (tapCount) {
                1 -> {
                    view.performClick()
                    if (settings.messagesEnabled) serviceScope.launch {
                        val (lastId) = app.preferences.lastMessage()
                        val tap = app.preferences.recordTap()
                        val selected = messageEngine.select(MessageRequest(currentState, settings.personality, settings.messagePackId(), lastId, tap.tapsToday, tap.lastTapAt?.let { System.currentTimeMillis() - it }))
                        showMessage(selected.text)
                        app.preferences.saveLastMessage(selected.id, System.currentTimeMillis())
                    }
                }
                2 -> {
                    (view as CompanionView).playSurprisedReaction()
                    showMessage(playfulMessages.random())
                }
                3 -> requestScreenshot()
            }
            tapCount = 0
        }
        tapAction = Runnable(::runTapAction)
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onLongPress(event: MotionEvent) {
                longPressed = true
                showQuickMenu()
            }
        })

        view.setOnTouchListener { _, event ->
            gestures.onTouchEvent(event)
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    longPressed = false
                    (view as CompanionView).wake()
                    view.pauseAnimation()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screen = screenSize()
                    dragged = dragged || abs(event.rawX - touchX) > view.touchSlop() ||
                        abs(event.rawY - touchY) > view.touchSlop()
                    params.x = (initialX + event.rawX - touchX).roundToInt()
                        .coerceIn(0, (screen.x - view.width).coerceAtLeast(0))
                    params.y = (initialY + event.rawY - touchY).roundToInt()
                        .coerceIn(0, (screen.y - view.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(view, params)
                    if (dragged) (view as CompanionView).setDragging(true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (view as CompanionView).setDragging(false)
                    if (!dragged && !longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                        tapCount++
                        handler.removeCallbacks(tapAction)
                        if (tapCount >= 3) runTapAction() else handler.postDelayed(tapAction, TAP_WINDOW_MS)
                    }
                    if (dragged) {
                        if (settings.edgeSnapEnabled) snapToNearestEdge(view) else savePosition(view)
                    } else {
                        view.startIdleAnimation()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun requestScreenshot() {
        showMessage("Preparing screenshot…")
        startActivity(
            Intent(this, ScreenshotPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun snapToNearestEdge(view: View) {
        val params = layoutParams ?: return
        val screen = screenSize()
        val maxX = (screen.x - view.width).coerceAtLeast(0)
        val maxY = (screen.y - view.height).coerceAtLeast(0)
        val targetX = if (params.x < maxX / 2) 0 else maxX

        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                windowManager.updateViewLayout(view, params)
            }
            doOnEnd {
                preferences.edit {
                    putFloat(KEY_X, if (maxX == 0) 0f else params.x.toFloat() / maxX)
                    putFloat(KEY_Y, if (maxY == 0) 0f else params.y.toFloat() / maxY)
                }
                (view as? CompanionView)?.startIdleAnimation()
            }
            start()
        }
    }

    private fun savePosition(view: CompanionView) {
        val params = layoutParams ?: return
        val screen = screenSize()
        val maxX = (screen.x - view.width).coerceAtLeast(0)
        val maxY = (screen.y - view.height).coerceAtLeast(0)
        preferences.edit {
            putFloat(KEY_X, if (maxX == 0) 0f else params.x.toFloat() / maxX)
            putFloat(KEY_Y, if (maxY == 0) 0f else params.y.toFloat() / maxY)
        }
        view.startIdleAnimation(settings.reducedMotion)
    }

    private fun showMessage(message: String) {
        bubbleView?.let(windowManager::removeView)
        val companionParams = layoutParams ?: return
        val screen = screenSize()
        val bubble = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(48, 40, 50))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(242, 255, 252, 248))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(24, 48, 40, 50))
            }
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        val width = dp(190)
        val params = overlayParams(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            x = (companionParams.x - width / 2 + dp(42)).coerceIn(0, (screen.x - width).coerceAtLeast(0))
            y = (companionParams.y - dp(58)).coerceAtLeast(0)
        }
        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        handler.removeCallbacksAndMessages(BUBBLE_TOKEN)
        handler.postAtTime({ hideMessage() }, BUBBLE_TOKEN, android.os.SystemClock.uptimeMillis() + 3_000)
    }

    private fun hideMessage() {
        val bubble = bubbleView ?: return
        bubble.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(160).withEndAction {
            runCatching { windowManager.removeView(bubble) }
            if (bubbleView === bubble) bubbleView = null
        }.start()
    }

    private fun showQuickMenu() {
        quickMenuView?.let(windowManager::removeView)
        val companionParams = layoutParams ?: return
        val menu = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(255, 252, 248))
                cornerRadius = dp(22).toFloat()
            }
        }
        fun action(label: String, block: () -> Unit) {
            menu.addView(Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(245, 238, 242))
                setOnClickListener {
                    dismissQuickMenu()
                    block()
                }
            }, GridLayout.LayoutParams().apply {
                width = dp(140)
                height = dp(48)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        fun assistive(label: String, assistiveAction: AssistiveAction) {
            action(label) {
                if (!perform(assistiveAction)) showMessage("Action isn't available here")
            }
        }

        if (instance != null) {
            assistive("←  Back", AssistiveAction.BACK)
            assistive("⌂  Home", AssistiveAction.HOME)
            assistive("▣  Recents", AssistiveAction.RECENTS)
            assistive("Notifications", AssistiveAction.NOTIFICATIONS)
            assistive("Quick Settings", AssistiveAction.QUICK_SETTINGS)
            assistive("Lock screen", AssistiveAction.LOCK_SCREEN)
            assistive("Power dialog", AssistiveAction.POWER_DIALOG)
            action("Refresh") { refreshContext(true); showMessage("All fresh ✨") }
        } else {
            action("Enable assistive controls") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            action("Why permission?") {
                showMessage("Enables only user-requested system controls")
            }
        }
        action("Open Ambient") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        action("Hide") { hideCompanion(true) }

        val menuWidth = dp(304)
        val estimatedHeight = dp(if (instance != null) 260 else 116)
        val params = overlayParams(menuWidth, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            x = (companionParams.x - menuWidth / 2).coerceIn(0, (screenSize().x - menuWidth).coerceAtLeast(0))
            y = if (companionParams.y >= estimatedHeight) companionParams.y - estimatedHeight else companionParams.y + dp(64)
        }
        windowManager.addView(menu, params)
        quickMenuView = menu
        handler.postDelayed(::dismissQuickMenu, 6_000)
    }

    private fun dismissQuickMenu() {
        quickMenuView?.let { runCatching { windowManager.removeView(it) } }
        quickMenuView = null
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun screenSize(): Point = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    } else {
        Point().also(windowManager.defaultDisplay::getRealSize)
    }

    private fun View.touchSlop(): Int = android.view.ViewConfiguration.get(context).scaledTouchSlop

    companion object {
        @Volatile private var instance: CompanionOverlayService? = null
        @Volatile
        var isRunning: Boolean = false
            private set

        val isConnected: Boolean get() = instance != null

        private const val PREFS_NAME = "overlay_position"
        private const val KEY_X = "normalized_x"
        private const val KEY_Y = "normalized_y"
        private const val BUBBLE_TOKEN = "message_bubble"
        const val ACTION_PREVIEW = "com.ambientcompanion.action.PREVIEW"
        const val ACTION_CONTEXT_UPDATED = "com.ambientcompanion.action.CONTEXT_UPDATED"
        const val ACTION_SETTINGS_UPDATED = "com.ambientcompanion.action.SETTINGS_UPDATED"
        const val EXTRA_STATE = "companion_state"
        const val ACTION_HIDE = "com.ambientcompanion.action.HIDE"
        const val ACTION_RESET_POSITION = "com.ambientcompanion.action.RESET_POSITION"
        private const val PREVIEW_DURATION_MS = 10_000L
        private const val TAP_WINDOW_MS = 360L
        private val contextMessages = listOf("You've got this ✨", "Nice to see you!", "Hope your day's going well")
        private val playfulMessages = listOf("Hey! 😳", "That tickles!", "I'm awake 👀")

        fun perform(action: AssistiveAction): Boolean {
            val service = instance ?: return false
            val globalAction = when (action) {
                AssistiveAction.BACK -> GLOBAL_ACTION_BACK
                AssistiveAction.HOME -> GLOBAL_ACTION_HOME
                AssistiveAction.RECENTS -> GLOBAL_ACTION_RECENTS
                AssistiveAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                AssistiveAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                AssistiveAction.LOCK_SCREEN -> GLOBAL_ACTION_LOCK_SCREEN
                AssistiveAction.POWER_DIALOG -> GLOBAL_ACTION_POWER_DIALOG
            }
            return service.performGlobalAction(globalAction)
        }
    }
}

private fun UserSettings.messagePackId(): MessagePackId = runCatching {
    MessagePackId.valueOf(messagePack.uppercase())
}.getOrDefault(MessagePackId.DEFAULT)
