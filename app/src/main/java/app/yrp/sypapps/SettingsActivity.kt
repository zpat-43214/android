package app.yrp.sypapps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlinx.coroutines.*

/** The detailed permission + pairing cards that used to be the whole app (MainActivity). Radio
 * tab now handles day-to-day start/stop and asks for whatever's missing itself — this screen
 * is for double-checking status or re-pairing/unpairing deliberately. */
class SettingsActivity : android.app.Activity() {
    private companion object {
        const val NEUTRAL = 0xFF222633.toInt()
        const val OK = 0xFF2ECC71.toInt()
        const val BAD = 0xFFFF5C5C.toInt()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayRow: TextView
    private lateinit var micRow: TextView
    private lateinit var pairRow: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnMic: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(c); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Theme.CARD, 16, Theme.CARD_STROKE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }
    private fun heading(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(Theme.MUTED)
        letterSpacing = 0.12f; setPadding(0, 0, 0, dp(10))
    }
    private fun button(text: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; stateListAnimator = null
        textSize = 15f; setTextColor(Theme.TEXT_LIGHT); background = rounded(bg, 12)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }
    private fun style(b: Button, done: Boolean, doneText: String, todoText: String) {
        b.text = if (done) doneText else todoText
        b.background = rounded(if (done) 0xFF16392A.toInt() else NEUTRAL, 12)
        b.setTextColor(if (done) OK else Theme.TEXT_LIGHT)
    }
    private fun setRow(row: TextView, ok: Boolean, text: String) {
        row.text = (if (ok) "●  " else "○  ") + text
        row.setTextColor(if (ok) OK else BAD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }

        overlayRow = TextView(this).apply { textSize = 15f; setPadding(0, dp(4), 0, dp(4)) }
        micRow = TextView(this).apply { textSize = 15f; setPadding(0, dp(4), 0, dp(4)) }
        pairRow = TextView(this).apply { textSize = 15f; setPadding(0, dp(4), 0, dp(4)) }

        col.addView(card().apply {
            addView(heading("Radio overlay status"))
            addView(overlayRow); addView(micRow); addView(pairRow)
        })

        btnOverlay = button("1. Allow display over other apps", NEUTRAL) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnMic = button("2. Allow microphone & notifications", NEUTRAL) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(perms.toTypedArray(), 1)
        }
        col.addView(card().apply {
            addView(heading("Permissions"))
            addView(btnOverlay.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = 0 })
            addView(btnMic)
        })

        col.addView(card().apply {
            addView(heading("Pairing"))
            addView(TextView(this@SettingsActivity).apply {
                text = "Unpairing stops the radio overlay from working until you pair again from the Radio tab."
                textSize = 13f; setTextColor(Theme.MUTED); setPadding(0, 0, 0, dp(8))
            })
            addView(button("Unpair this device", 0xFF992222.toInt()) {
                Prefs.clear(this@SettingsActivity); refresh()
            })
        })

        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val micOk = hasMic()
        val paired = Prefs.token(this) != null
        setRow(overlayRow, overlayOk, "Overlay permission")
        setRow(micRow, micOk, "Microphone permission")
        setRow(pairRow, paired, if (paired) "Paired as ${Prefs.name(this).ifBlank { "dispatcher" }}" else "Not paired")
        style(btnOverlay, overlayOk, "✓  Display over other apps allowed", "1. Allow display over other apps")
        style(btnMic, micOk, "✓  Microphone allowed", "2. Allow microphone & notifications")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }
}
