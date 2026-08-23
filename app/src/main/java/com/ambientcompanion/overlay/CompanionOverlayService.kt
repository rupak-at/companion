package com.ambientcompanion.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ambientcompanion.MainActivity
import com.ambientcompanion.AmbientApplication
import com.ambientcompanion.R
import com.ambientcompanion.data.preferences.UserSettings
import com.ambientcompanion.accessibility.AssistiveAction
import com.ambientcompanion.accessibility.AssistiveControlService
import com.ambientcompanion.domain.engine.MessageSelector
import com.ambientcompanion.domain.model.CompanionState
import com.ambientcompanion.screenshot.ScreenshotPermissionActivity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CompanionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var companionView: CompanionView? = null
    private var bubbleView: View? = null
    private var quickMenuView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app by lazy { application as AmbientApplication }
    private val messageSelector = MessageSelector()
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
                ACTION_SETTINGS_UPDATED -> refreshContext()
            }
        }
    }
    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        registerSystemReceiver()
        showCompanion()
        refreshContext()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> serviceScope.launch {
                app.preferences.updateSettings { it.copy(companionEnabled = false) }
                stopSelf()
            }
            ACTION_PREVIEW -> intent.getStringExtra(EXTRA_STATE)?.let { name ->
                runCatching { CompanionState.valueOf(name) }.getOrNull()?.let {
                    previewUntil = android.os.SystemClock.uptimeMillis() + PREVIEW_DURATION_MS
                    applyState(it)
                    handler.postDelayed({ refreshContext() }, PREVIEW_DURATION_MS)
                }
            }
            ACTION_CONTEXT_UPDATED -> refreshContext(true)
            ACTION_SETTINGS_UPDATED -> refreshContext()
            ACTION_RESET_POSITION -> resetPosition()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        companionView?.let { windowManager.removeView(it) }
        bubbleView?.let { windowManager.removeView(it) }
        quickMenuView?.let { windowManager.removeView(it) }
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(systemReceiver)
        serviceScope.cancel()
        companionView = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            companionView?.configureAppearance(
                settings.companionAppearance,
                settings.selectedEmoji,
                settings.idleOpacity,
                settings.reducedMotion,
            )
            val snapshot = app.contextRepository.refresh(force)
            if (android.os.SystemClock.uptimeMillis() >= previewUntil) applyState(snapshot.state)
            maybeShowAutomaticMessage(snapshot.state)
        }
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
        if (System.currentTimeMillis() - lastAt < AUTO_MESSAGE_INTERVAL_MS) return
        val selected = messageSelector.select(state, lastId)
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
                        val selected = messageSelector.select(currentState, lastId)
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
                if (!AssistiveControlService.perform(assistiveAction)) showMessage("Action isn't available here")
            }
        }

        if (AssistiveControlService.isConnected) {
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
        action("Hide") { serviceScope.launch { app.preferences.updateSettings { it.copy(companionEnabled = false) }; stopSelf() } }

        val menuWidth = dp(304)
        val estimatedHeight = dp(if (AssistiveControlService.isConnected) 260 else 116)
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
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_ambient)
        .setContentTitle("Ambient Companion is active")
        .setContentText("Tap to manage your companion")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Hide",
            PendingIntent.getService(
                this,
                1,
                Intent(this, CompanionOverlayService::class.java).setAction(ACTION_HIDE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating companion",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "companion_overlay"
        private const val NOTIFICATION_ID = 1001
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
        private const val AUTO_MESSAGE_INTERVAL_MS = 60 * 60 * 1000L
        private const val PREVIEW_DURATION_MS = 10_000L
        private const val TAP_WINDOW_MS = 360L
        private val contextMessages = listOf("You've got this ✨", "Nice to see you!", "Hope your day's going well")
        private val playfulMessages = listOf("Hey! 😳", "That tickles!", "I'm awake 👀")
    }
}
