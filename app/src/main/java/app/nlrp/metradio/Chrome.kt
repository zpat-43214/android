package app.nlrp.metradio

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The light-blue bar every screen shows under its header: current duty status on the left
 * (tap -> pick a new one), "HOME" on the right (skipped on the Home screen itself, which IS
 * home). Matches open app.png / signed in.png.
 */
object Chrome {

    /** [rightLabel]/[rightDestination]: what to show on the right of the bar — e.g. "HOME" ->
     * HomeActivity on every tile screen, or "⚙" -> SettingsActivity on the Home screen itself.
     * Pass null for neither (not currently used, but keeps this generic). */
    fun subHeader(
        ctx: Activity, scope: CoroutineScope,
        rightLabel: String? = "HOME", rightDestination: Class<out Activity>? = HomeActivity::class.java,
    ): LinearLayout {
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

        val statusLabel = TextView(ctx).apply {
            text = "…"; textSize = 16f; setTextColor(Theme.TEXT_DARK)
            typeface = Fonts.bold(ctx)
            setPadding(dp(16), dp(12), dp(10), dp(12))
        }
        val caret = TextView(ctx).apply {
            text = "▾"; textSize = 14f; setTextColor(Theme.TEXT_DARK)
            typeface = Fonts.bold(ctx)
        }
        val statusBtn = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusLabel); addView(caret)
            isClickable = true; isFocusable = true
        }

        var currentDefs: List<Backend.StatusDef> = emptyList()
        var booked = false

        fun load() = scope.launch {
            try {
                val state = Backend.officerState()
                currentDefs = state.defs
                booked = state.booked
                statusLabel.text = if (state.booked) (state.statusLabel ?: "ON DUTY").uppercase() else "NOT BOOKED ON"
            } catch (e: Backend.Unauthorized) {
                ctx.startActivity(Intent(ctx, LoginActivity::class.java)); ctx.finish()
            } catch (e: Exception) {
                statusLabel.text = "OFFLINE"
            }
        }

        statusBtn.setOnClickListener {
            if (!booked) { Toast.makeText(ctx, "Book on to a unit on the web app first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (currentDefs.isEmpty()) return@setOnClickListener
            val popup = PopupMenu(ctx, statusBtn)
            currentDefs.forEachIndexed { i, d -> popup.menu.add(0, i, i, d.label) }
            popup.setOnMenuItemClickListener { item ->
                val def = currentDefs[item.itemId]
                statusLabel.text = def.label.uppercase()
                scope.launch {
                    try { Backend.setStatus(def.code) }
                    catch (e: Exception) { Toast.makeText(ctx, "Couldn't change status: ${e.message}", Toast.LENGTH_SHORT).show(); load() }
                }
                true
            }
            popup.show()
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.SUBHEADER_BLU)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        row.addView(statusBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (rightLabel != null && rightDestination != null) {
            row.addView(TextView(ctx).apply {
                text = rightLabel; textSize = 16f; setTextColor(Theme.TEXT_DARK)
                typeface = Fonts.bold(ctx)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    ctx.startActivity(Intent(ctx, rightDestination)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    if (rightDestination == HomeActivity::class.java) ctx.finish()
                }
            })
        }

        load()
        return row
    }
}
