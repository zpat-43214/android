package app.nlrp.metradio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

enum class State { CONNECTING, READY, TX, OPEN, MUTED, OFFLINE }

/**
 * Floating radio control drawn over other apps (Roblox).
 *   [ ⋮ ][ PTT ]   - hold PTT to talk; drag the ⋮ handle to move it; tap ⋮ for channels.
 */
@SuppressLint("ClickableViewAccessibility")
class Overlay(private val ctx: Context, private val cb: Callbacks) {

    interface Callbacks {
        fun onPttDown()
        fun onPttUp()
        fun onPick(index: Int)
        fun onClose()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dm = ctx.resources.displayMetrics
    private fun dp(v: Int) = (v * dm.density).toInt()

    private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val menu = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val label = TextView(ctx)
    private val ptt = TextView(ctx)
    private val grip = TextView(ctx)
    private var menuShift = 0
    private var shown = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val sx = Prefs.overlayX(ctx); val sy = Prefs.overlayY(ctx)
        x = if (sx >= 0) sx else dp(16)
        y = if (sy >= 0) sy else dm.heightPixels / 2
    }

    private fun shape(color: Int, oval: Boolean, radius: Int = 12) = GradientDrawable().apply {
        this.shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        setStroke(dp(2), 0x66FFFFFF)
    }

    init {
        label.apply {
            textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2)); background = shape(0xB0000000.toInt(), false, 8)
        }
        grip.apply {
            text = "⋮"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = shape(0xB0222633.toInt(), false, 14)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(76)).apply { rightMargin = dp(6) }
        }
        ptt.apply {
            text = "PTT"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(grip); row.addView(ptt)

        root.addView(menu); root.addView(label); root.addView(row)

        ptt.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); cb.onPttDown() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cb.onPttUp()
            }
            true
        }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = (sx + dx).toInt().coerceIn(0, maxOf(0, dm.widthPixels - root.width))
                        params.y = (sy + dy).toInt().coerceIn(0, maxOf(0, dm.heightPixels - root.height))
                        wm.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) Prefs.saveOverlayPos(ctx, params.x, params.y) else toggleMenu()
            }
            true
        }
        setStatus("Connecting…", State.CONNECTING)
    }

    fun show() { if (!shown) { wm.addView(root, params); shown = true } }
    fun hide() { if (shown) { runCatching { wm.removeView(root) }; shown = false } }

    fun setStatus(text: String, state: State) {
        label.text = text
        val (color, caption) = when (state) {
            State.CONNECTING -> 0xCC666666.toInt() to "…"
            State.READY      -> 0xDD003399.toInt() to "PTT"
            State.TX         -> 0xFFFF3B30.toInt() to "LIVE"
            State.OPEN       -> 0xDD1E9E57.toInt() to "MIC\nON"
            State.MUTED      -> 0xDD8A6D00.toInt() to "MIC\nOFF"
            State.OFFLINE    -> 0xCC444444.toInt() to "OFF"
        }
        ptt.text = caption
        ptt.background = shape(color, true)
    }

    fun setLabel(text: String) { label.text = text }

    fun setChannels(labels: List<String>) {
        menu.removeAllViews()
        fun item(text: String, bg: Int, onClick: () -> Unit) = TextView(ctx).apply {
            this.text = text; textSize = 14f; setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = shape(bg, false, 10)
            layoutParams = LinearLayout.LayoutParams(dp(190), LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
            setOnClickListener { onClick() }
        }
        labels.forEachIndexed { i, l -> menu.addView(item(l, 0xF0222633.toInt()) { toggleMenu(); cb.onPick(i) }) }
        menu.addView(item("Stop radio overlay", 0xF0992222.toInt()) { cb.onClose() })
    }

    private fun toggleMenu() {
        if (menu.visibility == View.VISIBLE) {
            menu.visibility = View.GONE
            params.y += menuShift; menuShift = 0
        } else {
            menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val newY = (params.y - menu.measuredHeight).coerceAtLeast(0)
            menuShift = params.y - newY; params.y = newY
            menu.visibility = View.VISIBLE
        }
        if (shown) wm.updateViewLayout(root, params)
    }
}
