package app.yrp.sypapps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/**
 * Radio tab. Smart about setup: tapping Start just directly asks for whatever's missing
 * (overlay permission, mic permission, pairing) one step at a time rather than making you go
 * find a settings screen first. The full manual permission/pairing cards still live in
 * Settings for anyone who wants to double check or re-pair.
 */
class RadioActivity : android.app.Activity() {
    private companion object {
        const val GREEN = 0xFF1E9E57.toInt()
        const val RED = 0xFF992222.toInt()
        const val BLUE = 0xFF0047CC.toInt()
        const val OK = 0xFF2ECC71.toInt()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var statusRow: TextView
    private lateinit var pairCard: LinearLayout
    private lateinit var code: EditText
    private lateinit var startStopBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(c: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat(); setColor(c); if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun paired() = Prefs.token(this) != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        statusRow = TextView(this).apply { textSize = 15f; setPadding(0, 0, 0, dp(16)) }
        col.addView(statusRow)

        // Pairing card — only ever visible when not yet paired.
        code = EditText(this).apply {
            hint = "6-digit code"; inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            textSize = 22f; letterSpacing = 0.3f
            setTextColor(Theme.TEXT_LIGHT); setHintTextColor(0xFF555B75.toInt())
            background = rounded(0xFF0E111A.toInt(), 12, Theme.CARD_STROKE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val pairBtn = Button(this).apply {
            text = "Pair with dispatch"; isAllCaps = false; stateListAnimator = null
            setTextColor(Theme.TEXT_LIGHT); background = rounded(BLUE, 12)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(10) }
            setOnClickListener { pair() }
        }
        pairCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Theme.CARD, 16, Theme.CARD_STROKE)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) }
            addView(TextView(this@RadioActivity).apply {
                text = "On the dispatch site: Radio → Generate Pairing Code, then enter it here."
                textSize = 13f; setTextColor(Theme.MUTED); setPadding(0, 0, 0, dp(10))
            })
            addView(code); addView(pairBtn)
        }
        col.addView(pairCard)

        startStopBtn = Button(this).apply {
            text = "▶   Start radio overlay"; isAllCaps = false; stateListAnimator = null
            textSize = 17f; setTextColor(Theme.TEXT_LIGHT); background = rounded(GREEN, 12)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(58))
            setOnClickListener { onStartStopTapped() }
        }
        col.addView(startStopBtn)

        col.addView(Button(this).apply {
            text = "■   Stop radio overlay"; isAllCaps = false; stateListAnimator = null
            setTextColor(Theme.TEXT_LIGHT); background = rounded(RED, 12)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(10) }
            setOnClickListener { startService(Intent(this@RadioActivity, RadioService::class.java).setAction(RadioService.ACTION_STOP)) }
        })

        root.addView(ScrollView(this).apply { addView(col) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun refresh() {
        pairCard.visibility = if (paired()) android.view.View.GONE else android.view.View.VISIBLE
        statusRow.text = when {
            !paired() -> "● Pair with dispatch to use the radio"
            else -> "● Paired as ${Prefs.name(this).ifBlank { "dispatcher" }}"
        }
        statusRow.setTextColor(if (paired()) OK else 0xFFFF5C5C.toInt())
    }

    /** The whole point of this screen: don't make the person hunt for a permission toggle —
     * just ask for exactly what's missing, in order, and let them tap Start again each time. */
    private fun onStartStopTapped() {
        when {
            !Settings.canDrawOverlays(this) -> {
                toast("Allow SYP Apps to display over other apps, then come back and tap Start again")
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            !hasMic() -> {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                requestPermissions(perms.toTypedArray(), 1)
            }
            !paired() -> toast("Enter your 6-digit pairing code below first")
            else -> {
                ContextCompat.startForegroundService(this, Intent(this, RadioService::class.java))
                toast("Overlay running — now press Home and open Roblox")
            }
        }
    }

    private fun pair() {
        val c = code.text.toString().trim()
        if (c.length != 6) { toast("Enter the 6-digit code"); return }
        scope.launch {
            try {
                val (token, name) = Api.pair(c)
                Prefs.save(this@RadioActivity, token, name)
                code.setText(""); toast("Paired! Tap Start to go live."); refresh()
            } catch (e: Exception) { toast(e.message ?: "Pairing failed") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
