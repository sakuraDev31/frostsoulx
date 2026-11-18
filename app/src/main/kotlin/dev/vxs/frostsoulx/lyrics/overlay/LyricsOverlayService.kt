/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.lyrics.overlay

import dev.vxs.frostsoulx.utils.reportException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.EntryPointAccessors
import dev.vxs.frostsoulx.di.LyricsHelperEntryPoint
import dev.vxs.frostsoulx.lyrics.core.LyricsLine
import dev.vxs.frostsoulx.lyrics.core.LyricsSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * User-controlled system overlay for synchronized lyrics. It is deliberately a thin rendering
 * consumer: playback time, offset correction, word interpolation, and document ownership remain
 * in [dev.vxs.frostsoulx.lyrics.sync.LyricsSynchronizationEngine].
 */
class LyricsOverlayService : Service() {
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: LyricsOverlayPreferences
    private lateinit var root: FrameLayout
    private lateinit var currentLineView: TextView
    private lateinit var nextLineView: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayAttached = false
    private var stateJob: Job? = null
    private var latestState = LyricsSyncState()
    private var interactionX = 0f
    private var interactionY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var lastInteractionAtMs = 0L
    private val hideOverlayRunnable = Runnable { hideOverlay() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = LyricsOverlayPreferences(this)
        createOverlayView()
        subscribeToSharedLyricsState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ActionShow -> showOverlay()
            ActionHide -> hideOverlay()
            ActionToggle -> if (overlayAttached) hideOverlay() else showOverlay()
            ActionUpdateAppearance -> {
                preferences.update(intent.extras?.let(LyricsOverlayAppearance::fromExtras) ?: preferences.appearance)
                applyAppearance()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateJob?.cancel()
        mainHandler.removeCallbacks(hideOverlayRunnable)
        hideOverlay()
        overlayScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createOverlayView() {
        val density = resources.displayMetrics.density
        val appearance = preferences.appearance
        root = FrameLayout(this)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * density).roundToInt(), (10 * density).roundToInt(), (16 * density).roundToInt(), (10 * density).roundToInt())
            }
        currentLineView = lyricTextView(primary = true)
        nextLineView = lyricTextView(primary = false)
        content.addView(currentLineView)
        content.addView(nextLineView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4 * density).roundToInt()
        })
        root.addView(content)
        root.setOnTouchListener(createTouchListener())
        layoutParams =
            WindowManager.LayoutParams(
                appearance.widthPx(resources),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = appearance.xPx
                y = appearance.yPx
                title = "FrostSoulLyricsOverlay"
            }
        applyAppearance()
    }

    private fun lyricTextView(primary: Boolean): TextView =
        TextView(this).apply {
            setTextColor(if (primary) Color.WHITE else Color.rgb(145, 185, 185))
            gravity = Gravity.CENTER
            maxLines = if (primary) 2 else 1
            setLineSpacing(0f, 1.12f)
            typeface = Typeface.create("sans-serif", if (primary) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun subscribeToSharedLyricsState() {
        val engine =
            EntryPointAccessors
                .fromApplication(applicationContext, LyricsHelperEntryPoint::class.java)
                .lyricsSynchronizationEngine()
        stateJob =
            overlayScope.launch {
                engine.state.collectLatest { state ->
                    latestState = state
                    render(state)
                }
            }
    }

    private fun render(state: LyricsSyncState) {
        val current = state.currentLine
        currentLineView.text = current?.toHighlightedText(state) ?: "Lyrics are waiting for playback"
        nextLineView.text = state.nextLine?.displayText().orEmpty()
        nextLineView.visibility = if (preferences.appearance.dualLine && state.nextLine != null) View.VISIBLE else View.GONE
    }

    private fun LyricsLine.toHighlightedText(state: LyricsSyncState): CharSequence {
        if (words.isEmpty() || state.currentWordIndex !in words.indices) return text
        val highlighted = SpannableString(text)
        var cursor = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, cursor).takeIf { it >= 0 } ?: return@forEachIndexed
            val end = (start + word.text.length).coerceAtMost(text.length)
            cursor = end
            val isCompleted = index < state.currentWordIndex
            val isCurrent = index == state.currentWordIndex
            if (isCompleted || isCurrent) {
                val cyanAlpha = if (isCompleted) 255 else (110 + (145 * state.wordProgress.coerceIn(0f, 1f))).roundToInt()
                highlighted.setSpan(
                    ForegroundColorSpan(Color.argb(cyanAlpha, 0, 139, 139)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                highlighted.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return highlighted
    }

    private fun LyricsLine.displayText(): String = translation?.takeIf(String::isNotBlank) ?: text

    private fun applyAppearance() {
        val appearance = preferences.appearance
        val density = resources.displayMetrics.density
        root.background =
            GradientDrawable().apply {
                setColor(Color.argb((235 * appearance.opacity).roundToInt(), 0, 0, 0))
                cornerRadius = 22f * density
                setStroke((1 * density).roundToInt(), Color.argb((90 * appearance.opacity).roundToInt(), 0, 139, 139))
            }
        currentLineView.textSize = appearance.fontSizeSp
        nextLineView.textSize = (appearance.fontSizeSp * 0.73f).coerceAtLeast(12f)
        layoutParams.width = appearance.widthPx(resources)
        layoutParams.x = appearance.xPx
        layoutParams.y = appearance.yPx
        nextLineView.visibility = if (appearance.dualLine && latestState.nextLine != null) View.VISIBLE else View.GONE
        if (overlayAttached) updateOverlayLayout()
    }

    private fun createTouchListener(): View.OnTouchListener {
        val scaleDetector =
            ScaleGestureDetector(
                this,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        if (preferences.appearance.locked) return false
                        val appearance = preferences.appearance
                        preferences.update(
                            appearance.copy(
                                widthDp = (appearance.widthDp * detector.scaleFactor).roundToInt().coerceIn(MinimumWidthDp, MaximumWidthDp),
                            ),
                        )
                        applyAppearance()
                        lastInteractionAtMs = System.currentTimeMillis()
                        return true
                    }
                },
            )
        return View.OnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            val appearance = preferences.appearance
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    interactionX = event.rawX
                    interactionY = event.rawY
                    initialWindowX = layoutParams.x
                    initialWindowY = layoutParams.y
                    lastInteractionAtMs = System.currentTimeMillis()
                    mainHandler.removeCallbacks(hideOverlayRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!appearance.locked && !scaleDetector.isInProgress) {
                        layoutParams.x = initialWindowX + (event.rawX - interactionX).roundToInt()
                        layoutParams.y = initialWindowY + (event.rawY - interactionY).roundToInt()
                        updateOverlayLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    if (!appearance.locked) {
                        preferences.update(
                            appearance.copy(
                                xPx = layoutParams.x,
                                yPx = layoutParams.y,
                                widthDp = (layoutParams.width / resources.displayMetrics.density).roundToInt(),
                            ),
                        )
                    }
                    resetAutoHideTimer()
                    true
                }
                else -> true
            }
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) { hideOverlay(); stopSelf(); return }
        if (overlayAttached) return
        if (!windowOperation { windowManager.addView(root, layoutParams) }) return
        overlayAttached = true
        render(latestState)
        resetAutoHideTimer()
    }

    private fun hideOverlay() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        try {
            if (overlayAttached || root.isAttachedToWindow) {
                windowOperation { windowManager.removeViewImmediate(root) }
            }
        } finally { overlayAttached = false }
    }

    private fun updateOverlayLayout() {
        if (!Settings.canDrawOverlays(this)) { hideOverlay(); stopSelf(); return }
        if (overlayAttached) windowOperation { windowManager.updateViewLayout(root, layoutParams) }
    }

    private inline fun windowOperation(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: RuntimeException) {
        if (error !is WindowManager.BadTokenException && error !is WindowManager.InvalidDisplayException &&
            error !is SecurityException && error !is IllegalArgumentException && error !is IllegalStateException) throw error
        reportException(error)
        overlayAttached = root.isAttachedToWindow
        stopSelf()
        false
    }

    private fun resetAutoHideTimer() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        val timeoutMs = preferences.appearance.autoHideTimeoutMs
        if (overlayAttached && timeoutMs > 0L && System.currentTimeMillis() - lastInteractionAtMs >= 0L) {
            mainHandler.postDelayed(hideOverlayRunnable, timeoutMs)
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    companion object {
        const val ActionShow = "dev.vxs.frostsoulx.lyrics.overlay.SHOW"
        const val ActionHide = "dev.vxs.frostsoulx.lyrics.overlay.HIDE"
        const val ActionToggle = "dev.vxs.frostsoulx.lyrics.overlay.TOGGLE"
        const val ActionUpdateAppearance = "dev.vxs.frostsoulx.lyrics.overlay.UPDATE_APPEARANCE"
        private const val MinimumWidthDp = 180
        private const val MaximumWidthDp = 720

        fun permissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )

        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            return try {
                context.startService(Intent(context, LyricsOverlayService::class.java).setAction(ActionShow))
                true
            } catch (error: RuntimeException) {
                if (error !is SecurityException && error !is IllegalStateException) throw error
                reportException(error)
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LyricsOverlayService::class.java))
        }
    }
}

data class LyricsOverlayAppearance(
    val xPx: Int = 0,
    val yPx: Int = 180,
    val widthDp: Int = 320,
    val opacity: Float = 0.92f,
    val fontSizeSp: Float = 20f,
    val locked: Boolean = false,
    val dualLine: Boolean = true,
    val autoHideTimeoutMs: Long = 0L,
) {
    fun widthPx(resources: android.content.res.Resources): Int = (widthDp.coerceIn(180, 720) * resources.displayMetrics.density).roundToInt()

    fun toExtras(): android.os.Bundle =
        android.os.Bundle().apply {
            putInt("x", xPx)
            putInt("y", yPx)
            putInt("width_dp", widthDp)
            putFloat("opacity", opacity)
            putFloat("font_size_sp", fontSizeSp)
            putBoolean("locked", locked)
            putBoolean("dual_line", dualLine)
            putLong("auto_hide_timeout_ms", autoHideTimeoutMs)
        }

    companion object {
        fun fromExtras(extras: android.os.Bundle): LyricsOverlayAppearance =
            LyricsOverlayAppearance(
                xPx = extras.getInt("x", 0),
                yPx = extras.getInt("y", 180),
                widthDp = extras.getInt("width_dp", 320),
                opacity = extras.getFloat("opacity", 0.92f).coerceIn(0.25f, 1f),
                fontSizeSp = extras.getFloat("font_size_sp", 20f).coerceIn(12f, 42f),
                locked = extras.getBoolean("locked", false),
                dualLine = extras.getBoolean("dual_line", true),
                autoHideTimeoutMs = extras.getLong("auto_hide_timeout_ms", 0L).coerceIn(0L, 60_000L),
            )
    }
}

class LyricsOverlayPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("frostsoul_lyrics_overlay", Context.MODE_PRIVATE)

    var appearance: LyricsOverlayAppearance
        get() =
            LyricsOverlayAppearance(
                xPx = preferences.getInt("x", 0),
                yPx = preferences.getInt("y", 180),
                widthDp = preferences.getInt("width_dp", 320),
                opacity = preferences.getFloat("opacity", 0.92f).coerceIn(0.25f, 1f),
                fontSizeSp = preferences.getFloat("font_size_sp", 20f).coerceIn(12f, 42f),
                locked = preferences.getBoolean("locked", false),
                dualLine = preferences.getBoolean("dual_line", true),
                autoHideTimeoutMs = preferences.getLong("auto_hide_timeout_ms", 0L).coerceIn(0L, 60_000L),
            )
        private set(_) = Unit

    fun update(appearance: LyricsOverlayAppearance) {
        preferences.edit().putInt("x", appearance.xPx).putInt("y", appearance.yPx).putInt("width_dp", appearance.widthDp.coerceIn(180, 720)).putFloat("opacity", appearance.opacity.coerceIn(0.25f, 1f)).putFloat("font_size_sp", appearance.fontSizeSp.coerceIn(12f, 42f)).putBoolean("locked", appearance.locked).putBoolean("dual_line", appearance.dualLine).putLong("auto_hide_timeout_ms", appearance.autoHideTimeoutMs.coerceIn(0L, 60_000L)).apply()
    }
}
