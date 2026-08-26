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
import com.ambientcompanion.domain.behavior.QuickAction
import com.ambientcompanion.domain.rule.RuleEngine
import com.ambientcompanion.domain.rule.CompanionEvent
import com.ambientcompanion.domain.rule.CompanionEventType
import com.ambientcompanion.domain.rule.EventCooldowns
import com.ambientcompanion.domain.rule.EventQueue
import com.ambientcompanion.domain.schedule.SchedulePolicy
import com.ambientcompanion.domain.schedule.OutsideHoursBehavior
import com.ambientcompanion.renderer.AnimationId
import com.ambientcompanion.renderer.CompanionRenderer
import com.ambientcompanion.renderer.AnimatedAssetRenderer
import com.ambientcompanion.renderer.EmojiRenderer
import com.ambientcompanion.animation.AnimationStateMachine
import com.ambientcompanion.animation.AnimationPhase
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.screenshot.ScreenshotPermissionActivity
import com.ambientcompanion.data.screen.AccessibilityEventProcessor
import com.ambientcompanion.data.screen.ContextRefreshLevel
import com.ambientcompanion.data.screen.ScreenSnapshotBuilder
import com.ambientcompanion.domain.screen.CompanionDisplayMode
import com.ambientcompanion.domain.screen.ScreenAction
import com.ambientcompanion.domain.screen.ScreenBounds
import com.ambientcompanion.domain.screen.ScreenContext
import com.ambientcompanion.domain.wellbeing.AppOpenTracker
import com.ambientcompanion.domain.wellbeing.ScrollTracker
import com.ambientcompanion.domain.wellbeing.SessionTracker
import com.ambientcompanion.domain.wellbeing.WellbeingContext
import com.ambientcompanion.domain.wellbeing.WellbeingReaction
import com.ambientcompanion.domain.wellbeing.WellbeingReactionEngine
import com.ambientcompanion.domain.attention.AttentionEngine
import com.ambientcompanion.domain.attention.AttentionInput
import com.ambientcompanion.domain.attention.AttentionLevel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalTime
import java.time.ZonedDateTime

class CompanionOverlayService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var companionView: CompanionView? = null
    private var bubbleView: View? = null
    private var quickMenuView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var renderer: CompanionRenderer? = null
    private val animationStateMachine = AnimationStateMachine()
    private val handler = Handler(Looper.getMainLooper())
    private val boundaryRefresh = Runnable { refreshContext() }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app by lazy { application as AmbientApplication }
    private val messageEngine = MessageEngine()
    private val ruleEngine = RuleEngine()
    private val eventQueue = EventQueue()
    private val eventCooldowns = EventCooldowns()
    private val batteryStateTracker = BatteryStateTracker()
    private val accessibilityEventProcessor = AccessibilityEventProcessor()
    private val screenSnapshotBuilder = ScreenSnapshotBuilder()
    private val smartPositionController = SmartPositionController()
    private val sessionTracker = SessionTracker()
    private val scrollTracker = ScrollTracker()
    private val appOpenTracker = AppOpenTracker()
    private val wellbeingReactionEngine = WellbeingReactionEngine()
    private val attentionEngine = AttentionEngine()
    private var pendingScreenRefresh = ContextRefreshLevel.FULL
    private var foregroundPackage: String? = null
    private var currentScreenContext = ScreenContext.EMPTY
    private var smartPositionActive = false
    private var positionBeforeSmartMove: Pair<Int, Int>? = null
    private var positionAnimator: ValueAnimator? = null
    private var manuallyPositionedPackage: String? = null
    private var manualPositionOverride = false
    private var screenQuietUntil = 0L
    private var wellbeingContext = WellbeingContext.EMPTY
    private var lastWellbeingReaction: String? = null
    private var attentionExplanation = "No V3 attention decision yet"
    private val dailyActiveMs = mutableMapOf<String, Long>()
    private var recordedSessionActiveMs = 0L
    private val screenContextRefresh = Runnable { rebuildScreenContext() }
    private var lastDeviceContext: com.ambientcompanion.domain.context.DeviceContext? = null
    private var lastBatteryState: BatteryState? = null
    private var eventInFlight = false
    private var winningRuleId = "environment"
    private var activeRuleIds = emptyList<String>()
    private var currentAnimation = AnimationId.IDLE
    private var currentAccessory: com.ambientcompanion.renderer.AccessoryId? = null
    private var settings = UserSettings()
    private var currentState = CompanionState.DAY_CLEAR
    private var previewUntil = 0L
    private var receiverRegistered = false
    private var screenActive = true
    private var lastAppGreetingAt = 0L
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenActive = false
                    eventQueue.clear()
                    eventInFlight = false
                    animationStateMachine.pause()
                    renderer?.pause()
                    sessionTracker.screenOff(android.os.SystemClock.elapsedRealtime())
                    updateWellbeingSnapshot()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenActive = true
                    animationStateMachine.resume()
                    renderer?.resume()
                    sessionTracker.foreground(foregroundPackage, true, android.os.SystemClock.elapsedRealtime())
                    refreshContext()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> { clampToScreen(); refreshContext() }
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> refreshContext()
                ACTION_CONTEXT_UPDATED -> refreshContext(true)
                ACTION_SETTINGS_UPDATED -> syncVisibility()
                ACTION_CLEAR_ACTIVITY_DATA -> clearActivityData()
                ACTION_HIDE -> hideCompanion(true)
                ACTION_RESET_POSITION -> resetPosition()
                ACTION_PREVIEW -> intent.getStringExtra(EXTRA_STATE)?.let(::previewState)
                ACTION_APP_OPENED -> showAppGreeting()
            }
        }
    }
    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerSystemReceiver()
        observeDeviceContext()
        restoreWellbeingState()
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
        } else {
            hideCompanion()
            if (settings.companionEnabled && settings.hiddenUntil > System.currentTimeMillis()) {
                handler.postDelayed(
                    ::syncVisibility,
                    (settings.hiddenUntil - System.currentTimeMillis()).coerceAtMost(24 * 60 * 60_000L),
                )
            }
        }
    }

    private fun hideCompanion(updatePreference: Boolean = false) {
        companionView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        quickMenuView?.let { runCatching { windowManager.removeView(it) } }
        companionView = null; bubbleView = null; quickMenuView = null; isRunning = false
        renderer = null
        if (updatePreference) serviceScope.launch { app.preferences.updateSettings { it.copy(companionEnabled = false) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val previousPackage = foregroundPackage
        if (foregroundPackage == null || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            foregroundPackage = event.packageName?.toString() ?: foregroundPackage
        }
        if (foregroundPackage != previousPackage) manualPositionOverride = false
        trackWellbeingEvent(event, previousPackage)
        if (!settings.screenAwarenessEnabled || foregroundPackage in settings.excludedScreenApps) {
            app.screenContextSource.clear()
            currentScreenContext = ScreenContext.EMPTY
            return
        }
        val level = accessibilityEventProcessor.refreshLevel(event.eventType) ?: return
        if (level == ContextRefreshLevel.LIGHT) return
        if (level.ordinal > pendingScreenRefresh.ordinal) pendingScreenRefresh = level
        handler.removeCallbacks(screenContextRefresh)
        handler.postDelayed(
            screenContextRefresh,
            accessibilityEventProcessor.debounceMs(level, app.deviceContextSource.state.value.isPowerSaveMode),
        )
    }

    private fun restoreWellbeingState() {
        val today = java.time.LocalDate.now()
        val stored = app.wellbeingRepository.load(today)
        appOpenTracker.restore(today, stored.appOpenCounts)
        dailyActiveMs.putAll(stored.activeMinutes.mapValues { it.value * 60_000L })
    }

    private fun clearActivityData() {
        app.wellbeingRepository.clear()
        sessionTracker.reset()
        scrollTracker.reset()
        appOpenTracker.clear()
        dailyActiveMs.clear()
        recordedSessionActiveMs = 0L
        wellbeingContext = WellbeingContext.EMPTY
        lastWellbeingReaction = null
        attentionExplanation = "Local V3 activity data cleared"
    }

    private fun trackWellbeingEvent(event: AccessibilityEvent, previousPackage: String?) {
        if (!settings.screenAwarenessEnabled || !settings.wellbeingTrackingEnabled) return
        val packageName = foregroundPackage ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (packageName in settings.excludedWellbeingApps) {
            recordActiveTime(now)
            sessionTracker.foreground(null, screenActive, now)
            scrollTracker.reset()
            wellbeingContext = WellbeingContext.EMPTY
            return
        }
        val today = java.time.LocalDate.now()
        if (packageName != previousPackage) {
            recordActiveTime(now)
            sessionTracker.foreground(packageName, screenActive, now)
            recordedSessionActiveMs = 0L
            val count = appOpenTracker.foreground(packageName, today)
            app.wellbeingRepository.saveOpens(today, appOpenTracker.snapshot(today))
            if (settings.appOpenReactionsEnabled) {
                wellbeingReactionEngine.appOpenReaction(count, settings.wellbeingReactionStyle)?.let(::offerWellbeingReaction)
            }
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                sessionTracker.interaction(now)
                val scroll = scrollTracker.scroll(now)
                updateWellbeingSnapshot(scroll.durationMs, scroll.eventCount)
                if (settings.longScrollRemindersEnabled) wellbeingReactionEngine.scrollReaction(
                    scroll.durationMs,
                    settings.wellbeingReactionStyle,
                    settings.firstScrollReminderMinutes,
                    settings.strongScrollReminderMinutes,
                )?.let(::offerWellbeingReaction)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> sessionTracker.interaction(now)
        }
        updateWellbeingSnapshot()
    }

    private fun updateWellbeingSnapshot(scrollDuration: Long? = null, scrollCount: Int? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        recordActiveTime(now)
        val session = sessionTracker.snapshot(now)
        val scroll = scrollTracker.snapshot(now)
        wellbeingContext = session.copy(
            continuousScrollDurationMs = scrollDuration ?: scroll.durationMs,
            scrollEventCount = scrollCount ?: scroll.eventCount,
            appOpenCountToday = appOpenTracker.count(session.currentAppPackage, java.time.LocalDate.now()),
            appActiveMinutesToday = (dailyActiveMs[session.currentAppPackage] ?: 0L).div(60_000).toInt(),
        )
    }

    private fun recordActiveTime(now: Long) {
        val snapshot = sessionTracker.snapshot(now)
        val packageName = snapshot.currentAppPackage ?: return
        val delta = (snapshot.activeSessionDurationMs - recordedSessionActiveMs).coerceAtLeast(0)
        if (delta == 0L) return
        dailyActiveMs[packageName] = (dailyActiveMs[packageName] ?: 0L) + delta
        recordedSessionActiveMs = snapshot.activeSessionDurationMs
        app.wellbeingRepository.saveActiveMinutes(
            java.time.LocalDate.now(),
            dailyActiveMs.mapValues { (_, value) -> (value / 60_000).toInt() },
        )
    }

    private fun offerWellbeingReaction(reaction: WellbeingReaction) {
        val packageName = foregroundPackage ?: return
        val today = java.time.LocalDate.now()
        val reactionKey = "$packageName:${reaction.id}"
        if (reactionKey in app.wellbeingRepository.load(today).deliveredReactionIds) return
        val profile = app.appProfileRepository.profileFor(packageName, currentScreenContext.appCategory)
        if (!profile.allowWellbeingReactions) return
        val decision = attentionDecision(reaction.attention, profile.allowMessages)
        attentionExplanation = if (decision.suppressionReasons.isEmpty()) {
            "${reaction.attention}: allowed"
        } else {
            "Suppressed: ${decision.suppressionReasons.joinToString()}"
        }
        if (decision.allowAnimation) playAnimation(
            if (reaction.thresholdMinutes >= 90) AnimationId.EXHAUSTED else AnimationId.TIRED,
            AnimationPhase.AUTOMATIC,
        )
        if (decision.allowMessage) reaction.message?.let(::showMessage)
        if (decision.allowMessage || decision.allowAnimation) {
            lastWellbeingReaction = reactionKey
            app.wellbeingRepository.markReaction(today, reactionKey)
        }
    }

    private fun attentionDecision(level: AttentionLevel, profileAllowsMessages: Boolean = true) = attentionEngine.decide(
        AttentionInput(
            requestedLevel = level,
            quietHours = settings.quietHoursEnabled && SchedulePolicy.contains(
                LocalTime.now(), settings.quietStartMinutes, settings.quietEndMinutes,
            ),
            sensitive = currentScreenContext.isSensitive,
            profileAllowsMessages = profileAllowsMessages,
            personality = settings.personality,
            resourceMode = settings.resourceMode,
            screenOn = screenActive,
        ),
    )

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideCompanion()
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(systemReceiver)
        serviceScope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun rebuildScreenContext() {
        if (!settings.screenAwarenessEnabled || foregroundPackage in settings.excludedScreenApps) {
            app.screenContextSource.clear()
            return
        }
        val screen = screenSize()
        val keyboardVisible = windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val root = rootInActiveWindow
        foregroundPackage = root?.packageName?.toString() ?: foregroundPackage
        val baseSnapshot = screenSnapshotBuilder.build(
            root = root,
            packageName = foregroundPackage,
            screenWidth = screen.x,
            screenHeight = screen.y,
            keyboardVisible = keyboardVisible,
            fullScreen = isLikelyFullscreen(root),
            secureWindow = root == null && foregroundPackage != null,
        )
        val cutoutBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            companionView?.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().map {
                ScreenBounds(it.left, it.top, it.right, it.bottom)
            }
        } else emptyList()
        val snapshot = baseSnapshot.copy(importantBounds = (baseSnapshot.importantBounds + cutoutBounds).take(12))
        currentScreenContext = app.screenContextSource.update(snapshot, settings.sensitiveScreenModeEnabled)
        applyScreenBehavior(currentScreenContext)
        pendingScreenRefresh = ContextRefreshLevel.LIGHT
    }

    private fun isLikelyFullscreen(root: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        root ?: return false
        if (windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) return false
        val view = companionView ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = view.rootWindowInsets ?: return false
            !insets.isVisible(android.view.WindowInsets.Type.statusBars()) ||
                !insets.isVisible(android.view.WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            val flags = view.systemUiVisibility
            @Suppress("DEPRECATION")
            flags and (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
        }
    }

    private fun applyScreenBehavior(context: ScreenContext) {
        val view = companionView ?: return
        val params = layoutParams ?: return
        val category = context.appCategory
        val profile = context.packageName?.let { app.appProfileRepository.profileFor(it, category) }
        val requestedMode = when {
            context.isSensitive -> CompanionDisplayMode.PRIVACY
            context.isFullScreen -> CompanionDisplayMode.EDGE_PEEK
            else -> profile?.displayMode ?: CompanionDisplayMode.NORMAL
        }
        view.visibility = if (requestedMode == CompanionDisplayMode.HIDDEN) View.INVISIBLE else View.VISIBLE
        if (requestedMode == CompanionDisplayMode.HIDDEN) return
        val targetSize = when (requestedMode) {
            CompanionDisplayMode.SMALL, CompanionDisplayMode.PRIVACY -> (settings.companionSizeDp * .72f).roundToInt()
            else -> settings.companionSizeDp
        }
        resizeCompanion(targetSize)
        view.alpha = when (requestedMode) {
            CompanionDisplayMode.QUIET -> .58f
            CompanionDisplayMode.EDGE_PEEK, CompanionDisplayMode.PRIVACY -> .52f
            else -> 1f
        }
        if (requestedMode in setOf(CompanionDisplayMode.EDGE_PEEK, CompanionDisplayMode.PRIVACY)) {
            val screen = screenSize()
            val targetX = if (params.x + params.width / 2 < screen.x / 2) -params.width / 2 else screen.x - params.width / 2
            animatePosition(targetX, params.y.coerceIn(0, (screen.y - params.height).coerceAtLeast(0)))
            smartPositionActive = true
            return
        }
        if (manualPositionOverride && context.packageName == manuallyPositionedPackage) return
        if (!settings.smartRepositioningEnabled) return
        val screen = screenSize()
        val resolution = smartPositionController.resolve(
            ScreenBounds(params.x, params.y, params.x + params.width, params.y + params.height),
            ScreenBounds(0, 0, screen.x, screen.y),
            context,
        )
        if (resolution.moved) {
            if (!smartPositionActive) positionBeforeSmartMove = params.x to params.y
            smartPositionActive = true
            animatePosition(resolution.x, resolution.y)
        } else if (smartPositionActive) {
            positionBeforeSmartMove?.let { animatePosition(it.first, it.second) }
            smartPositionActive = false
            positionBeforeSmartMove = null
        }
    }

    private fun animatePosition(targetX: Int, targetY: Int) {
        val view = companionView ?: return
        val params = layoutParams ?: return
        val startX = params.x
        val startY = params.y
        positionAnimator?.cancel()
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).roundToInt()
                params.y = (startY + (targetY - startY) * fraction).roundToInt()
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
    }

    private fun takeManualPositionControl() {
        positionAnimator?.cancel()
        positionAnimator = null
        smartPositionActive = false
        positionBeforeSmartMove = null
        manuallyPositionedPackage = currentScreenContext.packageName
        manualPositionOverride = true
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
            renderer = AnimatedAssetRenderer(view)
            installDragHandling(view)
            windowManager.addView(view, layoutParams)
            view.startIdleAnimation()
        }
        isRunning = true
        handler.postDelayed(::showAppGreeting, 500L)
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(ACTION_CONTEXT_UPDATED)
            addAction(ACTION_SETTINGS_UPDATED)
            addAction(ACTION_CLEAR_ACTIVITY_DATA)
            addAction(ACTION_HIDE)
            addAction(ACTION_RESET_POSITION)
            addAction(ACTION_PREVIEW)
            addAction(ACTION_APP_OPENED)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun refreshContext(force: Boolean = false) {
        serviceScope.launch {
            settings = app.preferences.currentSettings()
            resizeCompanion(settings.companionSizeDp)
            val device = app.deviceContextSource.state.value
            val effectiveAppearance = if (settings.resourceMode == ResourceMode.MINIMAL) com.ambientcompanion.data.preferences.CompanionAppearance.EMOJI else settings.companionAppearance
            companionView?.configureAppearance(
                effectiveAppearance,
                settings.selectedArtwork,
                settings.selectedEmoji,
                settings.idleOpacity,
                settings.reducedMotion || device.isPowerSaveMode || settings.resourceMode != ResourceMode.NORMAL,
            )
            companionView?.let { renderer = if (effectiveAppearance == com.ambientcompanion.data.preferences.CompanionAppearance.EMOJI) EmojiRenderer(it) else AnimatedAssetRenderer(it) }
            companionView?.setTheme(settings.theme)
            val snapshot = app.contextRepository.refresh(force)
            if (android.os.SystemClock.uptimeMillis() >= previewUntil) applyAmbientContext(snapshot.context)
            if (settings.screenAwarenessEnabled) applyScreenBehavior(currentScreenContext)
        }
    }

    private fun observeDeviceContext() = serviceScope.launch {
        app.deviceContextSource.state.collectLatest { device ->
            val previous = lastDeviceContext
            lastDeviceContext = device
            if (previous != null) enqueueDeviceEvents(previous, device)
            if (companionView != null && screenActive) refreshContext()
        }
    }

    private suspend fun applyAmbientContext(environment: com.ambientcompanion.domain.model.CompanionContext) {
        val now = LocalTime.now()
        scheduleNextBoundary(environment)
        val quiet = settings.quietHoursEnabled && SchedulePolicy.contains(now, settings.quietStartMinutes, settings.quietEndMinutes)
        val outsideActive = settings.activeHoursEnabled && !SchedulePolicy.contains(now, settings.activeStartMinutes, settings.activeEndMinutes)
        if (outsideActive && settings.outsideHoursBehavior == OutsideHoursBehavior.HIDE_COMPLETELY) {
            hideCompanion()
            handler.postDelayed(::syncVisibility, millisUntil(settings.activeStartMinutes))
            return
        }
        val rawDevice = app.deviceContextSource.state.value
        val device = rawDevice.copy(
            isWeekend = SchedulePolicy.isWeekend(app.deviceContextSource.state.value.dayOfWeek, settings.weekendDays),
            classifiedBatteryState = batteryStateTracker.update(rawDevice.batteryPercent, rawDevice.isBatteryFull),
        )
        val previousBattery = lastBatteryState
        lastBatteryState = device.batteryState
        if (previousBattery != device.batteryState) {
            when (device.batteryState) {
                BatteryState.CRITICAL -> eventQueue.offer(CompanionEvent(CompanionEventType.BATTERY_CRITICAL, System.currentTimeMillis(), 100))
                BatteryState.LOW -> eventQueue.offer(CompanionEvent(CompanionEventType.BATTERY_LOW, System.currentTimeMillis(), 95))
                else -> Unit
            }
        }
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
        winningRuleId = resolved.winningRuleId
        activeRuleIds = resolved.activeRuleIds
        currentState = resolved.behavior.visualState
        animationStateMachine.request(AnimationId.STATE_TRANSITION, AnimationPhase.TRANSITION)
        renderer?.setState(currentState)
        renderer?.setAccessory(resolved.behavior.accessory)
        animationStateMachine.finish()
        playAnimation(resolved.behavior.idleAnimation, AnimationPhase.AUTOMATIC)
        currentAccessory = resolved.behavior.accessory
        if (outsideActive && settings.outsideHoursBehavior == OutsideHoursBehavior.PEEK_FROM_EDGE) companionView?.alpha = .48f
        val event = if (eventInFlight) null else eventQueue.poll()
        if (event != null && !quiet) playEvent(event)
        else if (resolved.behavior.automaticMessageAllowed) maybeShowAutomaticMessage(currentState)
    }

    private fun enqueueDeviceEvents(old: com.ambientcompanion.domain.context.DeviceContext, new: com.ambientcompanion.domain.context.DeviceContext) {
        if (!screenActive) return
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
        val (animation, message, visual) = when (event.type) {
            CompanionEventType.BATTERY_LOW -> Triple(AnimationId.BATTERY_LOW, "Feed me 🔌", CompanionState.LOW_BATTERY)
            CompanionEventType.BATTERY_CRITICAL -> Triple(AnimationId.BATTERY_LOW, "Need power...", CompanionState.CRITICAL_BATTERY)
            CompanionEventType.CHARGING_STARTED -> Triple(AnimationId.CHARGING, "Charging up!", CompanionState.CHARGING)
            CompanionEventType.CHARGING_STOPPED -> Triple(AnimationId.STATE_TRANSITION, "Unplugged", currentState)
            CompanionEventType.BATTERY_FULL -> Triple(AnimationId.BATTERY_FULL, "All full!", CompanionState.BATTERY_FULL)
            CompanionEventType.HEADPHONES_CONNECTED -> Triple(AnimationId.HEADPHONES, "Music time?", CompanionState.HEADPHONES)
            CompanionEventType.NETWORK_LOST -> Triple(AnimationId.NETWORK_LOST, "Lost connection?", CompanionState.NETWORK_LOST)
            CompanionEventType.NETWORK_RESTORED -> Triple(AnimationId.NETWORK_RESTORED, "Back online!", CompanionState.NETWORK_RESTORED)
        }
        eventInFlight = true
        currentAnimation = animation
        renderer?.setState(visual)
        val temporaryAccessory = when (event.type) {
            CompanionEventType.HEADPHONES_CONNECTED -> com.ambientcompanion.renderer.AccessoryId.HEADPHONES
            CompanionEventType.CHARGING_STARTED, CompanionEventType.BATTERY_FULL -> com.ambientcompanion.renderer.AccessoryId.CHARGING_SPARK
            else -> null
        }
        renderer?.setAccessory(temporaryAccessory)
        currentAccessory = temporaryAccessory
        playAnimation(animation, AnimationPhase.AUTOMATIC)
        val attention = if (event.type == CompanionEventType.BATTERY_CRITICAL) AttentionLevel.IMPORTANT else AttentionLevel.NORMAL
        val decision = attentionDecision(attention)
        attentionExplanation = if (decision.suppressionReasons.isEmpty()) "$attention: allowed" else "Suppressed: ${decision.suppressionReasons.joinToString()}"
        if (settings.messagesEnabled && decision.allowMessage) showMessage(message)
        handler.postDelayed({
            eventInFlight = false
            currentAnimation = AnimationId.IDLE
            animationStateMachine.finish()
            refreshContext()
        }, EVENT_DURATION_MS)
    }

    private fun playAnimation(animation: AnimationId, phase: AnimationPhase) {
        val snapshot = animationStateMachine.request(animation, phase)
        if (snapshot.animation == animation) {
            currentAnimation = animation
            renderer?.play(animation)
        }
    }

    private fun millisUntil(minutes: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(minutes / 60).withMinute(minutes % 60).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis().coerceAtLeast(1_000L)
    }

    private fun nextOccurrenceMillis(minutes: Int): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(minutes / 60).withMinute(minutes % 60).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    private fun scheduleNextBoundary(environment: com.ambientcompanion.domain.model.CompanionContext) {
        handler.removeCallbacks(boundaryRefresh)
        val nowMillis = System.currentTimeMillis()
        val minuteBoundaries = buildList {
            addAll(listOf(5 * 60, 11 * 60, 17 * 60, 21 * 60))
            if (settings.quietHoursEnabled) addAll(listOf(settings.quietStartMinutes, settings.quietEndMinutes))
            if (settings.activeHoursEnabled) addAll(listOf(settings.activeStartMinutes, settings.activeEndMinutes))
        }
        val candidates = minuteBoundaries.map(::nextOccurrenceMillis).toMutableList()
        environment.sunrise?.times(1_000)?.takeIf { it > nowMillis }?.let(candidates::add)
        environment.sunset?.times(1_000)?.takeIf { it > nowMillis }?.let(candidates::add)
        val delay = candidates.minOrNull()?.minus(nowMillis)?.coerceAtLeast(1_000L) ?: return
        handler.postDelayed(boundaryRefresh, delay)
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
        val profile = currentScreenContext.packageName?.let { app.appProfileRepository.profileFor(it, currentScreenContext.appCategory) }
        if (!settings.messagesEnabled || !settings.automaticMessages || currentScreenContext.isSensitive ||
            System.currentTimeMillis() < screenQuietUntil || profile?.allowMessages == false
        ) return
        val decision = attentionDecision(AttentionLevel.NORMAL, profile?.allowMessages != false)
        if (!decision.allowMessage) {
            attentionExplanation = "Automatic message suppressed: ${decision.suppressionReasons.joinToString()}"
            return
        }
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
        val gestureCoordinator = OverlayGestureCoordinator(TAP_WINDOW_MS)
        lateinit var tapAction: Runnable
        fun runTapAction() {
            val tapCount = gestureCoordinator.consumeTaps()
            when (tapCount) {
                1 -> {
                    view.performClick()
                    playAnimation(AnimationId.TAP_HAPPY, AnimationPhase.INTERACTION)
                    if (settings.messagesEnabled) serviceScope.launch {
                        val (lastId) = app.preferences.lastMessage()
                        val tap = app.preferences.recordTap()
                        val selected = messageEngine.select(MessageRequest(currentState, settings.personality, settings.messagePackId(), lastId, tap.tapsToday, tap.lastTapAt?.let { System.currentTimeMillis() - it }))
                        showMessage(selected.text)
                        app.preferences.saveLastMessage(selected.id, System.currentTimeMillis())
                    }
                }
                2 -> {
                    playAnimation(AnimationId.DOUBLE_TAP_SURPRISED, AnimationPhase.INTERACTION)
                    showMessage(playfulMessages.random())
                }
                3 -> requestScreenshot()
            }
            if (tapCount in 1..2) handler.postDelayed({
                animationStateMachine.finish()
                currentAnimation = AnimationId.IDLE
            }, 700L)
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
                    takeManualPositionControl()
                    hideMessage()
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
                    val becameDragged = !dragged && gestureCoordinator.isDrag(
                        event.rawX - touchX, event.rawY - touchY, view.touchSlop(),
                    )
                    dragged = dragged || becameDragged
                    params.x = (initialX + event.rawX - touchX).roundToInt()
                        .coerceIn(0, (screen.x - view.width).coerceAtLeast(0))
                    params.y = (initialY + event.rawY - touchY).roundToInt()
                        .coerceIn(0, (screen.y - view.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(view, params)
                    if (becameDragged) playAnimation(AnimationId.DRAG, AnimationPhase.DRAGGING)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) { renderer?.play(AnimationId.EDGE_LAND); animationStateMachine.finish() }
                    if (!dragged && !longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                        val tapCount = gestureCoordinator.registerTap(android.os.SystemClock.uptimeMillis())
                        handler.removeCallbacks(tapAction)
                        if (tapCount >= 3) runTapAction() else handler.postDelayed(tapAction, TAP_WINDOW_MS)
                    }
                    if (dragged) {
                        if (settings.screenAwarenessEnabled && settings.edgeSnapEnabled) {
                            snapToNearestEdge(view)
                        } else {
                            savePosition(view as CompanionView)
                        }
                    } else {
                        (view as CompanionView).startIdleAnimation()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun requestScreenshot() {
        if (currentScreenContext.isSensitive) {
            showMessage("Screenshots are unavailable in Privacy Mode")
            return
        }
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

        positionAnimator?.cancel()
        positionAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                windowManager.updateViewLayout(view, params)
            }
            doOnEnd {
                positionAnimator = null
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
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        val companionParams = layoutParams ?: return
        val screen = screenSize()
        val margin = dp(16)
        val gap = dp(10)
        val iconCenterX = companionParams.x + companionParams.width / 2
        val placeToRight = iconCenterX < screen.x / 2
        val bubble = TextView(this).apply {
            text = message
            textSize = 14.5f
            setTextColor(Color.rgb(255, 247, 239))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(20), dp(21), dp(20), dp(22))
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            elevation = dp(12).toFloat()
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
        }
        val maxWidth = minOf(dp(280), (screen.x - margin * 2).coerceAtLeast(dp(96)))
        val minWidth = minOf(dp(96), maxWidth)
        val desiredWidth = bubble.paint.measureText(message).roundToInt() + bubble.paddingLeft + bubble.paddingRight
        val width = desiredWidth.coerceIn(minWidth, maxWidth)
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bubbleHeight = bubble.measuredHeight
        val placeAbove = companionParams.y >= bubbleHeight + gap + margin
        bubble.background = CompanionMessageDrawable(
            pointerX = if (placeToRight) dp(18).toFloat() else width - dp(18).toFloat(),
            pointerAtTop = !placeAbove,
            tailHeight = dp(10).toFloat(),
            cornerRadius = dp(22).toFloat(),
            fillColor = Color.argb(250, 35, 27, 39),
            strokeColor = Color.argb(150, 102, 76, 91),
            strokeWidth = dp(1).toFloat(),
        )
        val targetX = if (placeToRight) {
            companionParams.x + companionParams.width - dp(14)
        } else {
            companionParams.x - width + dp(14)
        }
        val targetY = if (placeAbove) {
            companionParams.y - bubbleHeight - gap
        } else {
            companionParams.y + companionParams.height + gap
        }
        bubble.pivotX = if (placeToRight) 0f else width.toFloat()
        bubble.pivotY = if (placeAbove) bubbleHeight.toFloat() else 0f
        val params = overlayParams(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            x = targetX.coerceIn(margin, (screen.x - width - margin).coerceAtLeast(margin))
            y = targetY.coerceIn(margin, (screen.y - bubbleHeight - margin).coerceAtLeast(margin))
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

    private fun showAppGreeting() {
        val now = android.os.SystemClock.uptimeMillis()
        if (!settings.messagesEnabled || companionView == null ||
            (lastAppGreetingAt != 0L && now - lastAppGreetingAt < APP_GREETING_COOLDOWN_MS)
        ) return
        lastAppGreetingAt = now
        showMessage(contextMessages.random())
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

        val profile = currentScreenContext.packageName?.let { app.appProfileRepository.profileFor(it, currentScreenContext.appCategory) }
        if (settings.screenAwarenessEnabled && settings.contextActionsEnabled && profile?.allowContextActions != false) {
            currentScreenContext.availableActions.forEach { screenAction ->
                action(screenAction.label()) { performScreenAction(screenAction) }
            }
        }

        action("Hide 15 minutes") { hideTemporarily(System.currentTimeMillis() + 15 * 60_000L) }
        action("Hide 1 hour") { hideTemporarily(System.currentTimeMillis() + 60 * 60_000L) }
        action("Until evening") { hideTemporarily(nextOccurrenceMillis(18 * 60)) }
        action("Until tomorrow") {
            val now = ZonedDateTime.now()
            hideTemporarily(now.plusDays(1).toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli())
        }
        action(if (settings.quietHoursEnabled) "Quiet mode off" else "Quiet mode on") {
            serviceScope.launch {
                app.preferences.updateSettings { it.copy(quietHoursEnabled = !it.quietHoursEnabled) }
                syncVisibility()
            }
        }
        settings.quickActions.take(4).forEach { quick ->
            action(quick.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) {
                performQuickAction(quick)
            }
        }
        action("Open Ambient") { performQuickAction(QuickAction.OPEN_APP) }

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

    private fun ScreenAction.label(): String = when (this) {
        ScreenAction.SCROLL_TOP -> "Scroll to top"
        ScreenAction.SCROLL_BOTTOM -> "Scroll to bottom"
        ScreenAction.PREVIOUS_FIELD -> "Previous field"
        ScreenAction.NEXT_FIELD -> "Next field"
        ScreenAction.HIDE_KEYBOARD -> "Hide keyboard"
        ScreenAction.QUIET_30_MINUTES -> "Quiet 30 minutes"
        ScreenAction.EDGE_PEEK -> "Edge peek"
        else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    private fun performScreenAction(action: ScreenAction) {
        when (action) {
            ScreenAction.BACK -> perform(AssistiveAction.BACK)
            ScreenAction.HOME -> perform(AssistiveAction.HOME)
            ScreenAction.RECENTS -> perform(AssistiveAction.RECENTS)
            ScreenAction.NOTIFICATIONS -> perform(AssistiveAction.NOTIFICATIONS)
            ScreenAction.QUICK_SETTINGS -> perform(AssistiveAction.QUICK_SETTINGS)
            ScreenAction.HIDE -> hideTemporarily(System.currentTimeMillis() + 15 * 60_000L)
            ScreenAction.REFRESH -> { rebuildScreenContext(); refreshContext(true) }
            ScreenAction.OPEN_APP -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ScreenAction.SCREENSHOT -> requestScreenshot()
            ScreenAction.HIDE_KEYBOARD -> if (currentScreenContext.isKeyboardVisible) perform(AssistiveAction.BACK)
            ScreenAction.QUIET_30_MINUTES -> {
                screenQuietUntil = System.currentTimeMillis() + 30 * 60_000L
                showMessage("Quiet for 30 minutes")
            }
            ScreenAction.EDGE_PEEK -> applyScreenBehavior(currentScreenContext.copy(isFullScreen = true))
            ScreenAction.SCROLL_TOP -> performNodeAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, repeat = true)
            ScreenAction.SCROLL_BOTTOM -> performNodeAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, repeat = true)
            ScreenAction.PREVIOUS_FIELD -> focusAdjacent(android.view.View.FOCUS_BACKWARD)
            ScreenAction.NEXT_FIELD -> focusAdjacent(android.view.View.FOCUS_FORWARD)
        }
    }

    private fun performNodeAction(action: Int, repeat: Boolean) {
        val root = rootInActiveWindow ?: return
        val target = findNode(root) { it.isScrollable && it.actionList.any { info -> info.id == action } } ?: return
        var remaining = if (repeat) 16 else 1
        while (remaining-- > 0 && target.performAction(action)) Unit
    }

    private fun focusAdjacent(direction: Int) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        focused.focusSearch(direction)?.takeIf { it.isEditable }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun findNode(
        root: android.view.accessibility.AccessibilityNodeInfo,
        predicate: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean,
    ): android.view.accessibility.AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 500) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun hideTemporarily(until: Long) {
        serviceScope.launch {
            app.preferences.updateSettings { it.copy(hiddenUntil = until) }
            syncVisibility()
        }
    }

    private fun performQuickAction(action: QuickAction) {
        val assistive = when (action) {
            QuickAction.BACK -> AssistiveAction.BACK
            QuickAction.HOME -> AssistiveAction.HOME
            QuickAction.RECENTS -> AssistiveAction.RECENTS
            QuickAction.NOTIFICATIONS -> AssistiveAction.NOTIFICATIONS
            QuickAction.QUICK_SETTINGS -> AssistiveAction.QUICK_SETTINGS
            QuickAction.LOCK -> AssistiveAction.LOCK_SCREEN
            QuickAction.POWER_DIALOG -> AssistiveAction.POWER_DIALOG
            else -> null
        }
        when (action) {
            QuickAction.SCREENSHOT -> requestScreenshot()
            QuickAction.HIDE -> hideTemporarily(System.currentTimeMillis() + 15 * 60_000L)
            QuickAction.REFRESH -> { refreshContext(true); showMessage("All fresh ✨") }
            QuickAction.OPEN_APP -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> if (assistive != null && !perform(assistive)) showMessage("Action isn't available here")
        }
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
        const val ACTION_CLEAR_ACTIVITY_DATA = "com.ambientcompanion.action.CLEAR_ACTIVITY_DATA"
        const val EXTRA_STATE = "companion_state"
        const val ACTION_HIDE = "com.ambientcompanion.action.HIDE"
        const val ACTION_RESET_POSITION = "com.ambientcompanion.action.RESET_POSITION"
        const val ACTION_APP_OPENED = "com.ambientcompanion.action.APP_OPENED"
        private const val PREVIEW_DURATION_MS = 10_000L
        private const val TAP_WINDOW_MS = 360L
        private const val EVENT_DURATION_MS = 2_500L
        private const val APP_GREETING_COOLDOWN_MS = 30_000L
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

        fun requestSync() { instance?.syncVisibility() }

        fun debugSnapshot(): OverlayDebugSnapshot? = instance?.let { service ->
            OverlayDebugSnapshot(
                winningRule = service.winningRuleId,
                activeRules = service.activeRuleIds,
                queuedEvents = service.eventQueue.snapshot().map { it.type.name },
                animation = service.currentAnimation.name,
                accessory = service.currentAccessory?.name,
                renderer = if (service.settings.resourceMode == ResourceMode.MINIMAL) "EMOJI" else service.settings.companionAppearance.name,
                resourceMode = service.settings.resourceMode.name,
                screenContext = service.currentScreenContext,
                wellbeingContext = service.wellbeingContext,
                lastWellbeingReaction = service.lastWellbeingReaction,
                attentionExplanation = service.attentionExplanation,
            )
        }
    }
}

data class OverlayDebugSnapshot(
    val winningRule: String,
    val activeRules: List<String>,
    val queuedEvents: List<String>,
    val animation: String,
    val accessory: String?,
    val renderer: String,
    val resourceMode: String,
    val screenContext: ScreenContext,
    val wellbeingContext: WellbeingContext,
    val lastWellbeingReaction: String?,
    val attentionExplanation: String,
)

private fun UserSettings.messagePackId(): MessagePackId = runCatching {
    MessagePackId.valueOf(messagePack.uppercase())
}.getOrDefault(MessagePackId.DEFAULT)
