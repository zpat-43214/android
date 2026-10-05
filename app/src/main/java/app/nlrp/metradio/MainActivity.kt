package app.nlrp.metradio

import android.Manifest
import android.app.Activity
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

/** Setup screen: grant overlay + mic permission, pair with a code, start/stop the overlay. */
class MainActivity : Activity() {

    private companion object {
        const val BG = 0xFF0A0C12.toInt()
        const val CARD = 0xFF141824.toInt()
        const val CARD_STROKE = 0xFF232A3D.toInt()
        const val BLUE = 0xFF0047CC.toInt()
        const val GREEN = 0xFF1E9E57.toInt()
        const val RED = 0xFF992222.toInt()
        const val NEUTRAL = 0xFF222633.toInt()
        const val OK = 0xFF2ECC71.toInt()
        const val BAD = 0xFFFF5C5C.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
        const val MUTED = 0xFF8892B8.toInt()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayRow: TextView
    private lateinit var micRow: TextView
    private lateinit var pairRow: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnMic: Button
    private lateinit var code: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(CARD, 16, CARD_STROKE)
        layoutParams = lp(bottom = 14)
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(MUTED)
        letterSpacing = 0.12f; setPadding(0, 0, 0, dp(10))
    }

    private fun button(text: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; stateListAnimator = null
        textSize = 15f; setTextColor(TEXT); background = rounded(bg, 12)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun statusRow() = TextView(this).apply {
        textSize = 15f; setPadding(0, dp(4), 0, dp(4))
    }

    private fun style(b: Button, done: Boolean, doneText: String, todoText: String) {
        b.text = if (done) doneText else todoText
        b.background = rounded(if (done) 0xFF16392A.toInt() else NEUTRAL, 12)
        b.setTextColor(if (done) OK else TEXT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }

        // ── Header: horizontal logo (185:50) ──
        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_horizontal)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(65)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(TextView(this).apply {
            text = "Dispatch Radio Overlay"; textSize = 14f; setTextColor(MUTED)
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(20))
            layoutParams = lp()
        })

        // ── Status card ──
        overlayRow = statusRow(); micRow = statusRow(); pairRow = statusRow()
        col.addView(card().apply {
            addView(heading("Status"))
            addView(overlayRow); addView(micRow); addView(pairRow)
        })

        // ── Permissions card ──
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

        // ── Pairing card ──
        code = EditText(this).apply {
            hint = "6-digit code"; inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            textSize = 24f; letterSpacing = 0.3f
            setTextColor(TEXT); setHintTextColor(0xFF555B75.toInt())
            background = rounded(0xFF0E111A.toInt(), 12, CARD_STROKE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = lp(top = 4)
        }
        val pairButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(top = 4)
            addView(button("Pair", BLUE) { pair() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { topMargin = dp(8); rightMargin = dp(6) }
            })
            addView(button("Unpair", NEUTRAL) { Prefs.clear(this@MainActivity); refresh() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { topMargin = dp(8); leftMargin = dp(6) }
            })
        }
        col.addView(card().apply {
            addView(heading("Pair with dispatch"))
            addView(TextView(this@MainActivity).apply {
                text = "On the dispatch site: Radio → Generate Pairing Code, then type it here."
                textSize = 13f; setTextColor(MUTED); setPadding(0, 0, 0, dp(8))
            })
            addView(code); addView(pairButtons)
        })

        // ── Start / Stop ──
        col.addView(button("▶   Start radio overlay", GREEN) { start() }.apply {
            textSize = 17f
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { topMargin = dp(4) }
        })
        col.addView(button("■   Stop radio overlay", RED) {
            startService(Intent(this, RadioService::class.java).setAction(RadioService.ACTION_STOP))
        })

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) })
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setRow(row: TextView, ok: Boolean, text: String) {
        row.text = (if (ok) "●  " else "○  ") + text
        row.setTextColor(if (ok) OK else BAD)
    }

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val micOk = hasMic()
        val paired = Prefs.token(this) != null
        setRow(overlayRow, overlayOk, "Overlay permission")
        setRow(micRow, micOk, "Microphone permission")
        setRow(pairRow, paired,
            if (paired) "Paired as ${Prefs.name(this).ifBlank { "dispatcher" }}" else "Not paired")
        style(btnOverlay, overlayOk, "✓  Display over other apps allowed", "1. Allow display over other apps")
        style(btnMic, micOk, "✓  Microphone allowed", "2. Allow microphone & notifications")
    }

    private fun pair() {
        val c = code.text.toString().trim()
        if (c.length != 6) { toast("Enter the 6-digit code"); return }
        scope.launch {
            try {
                val (token, name) = Api.pair(c)
                Prefs.save(this@MainActivity, token, name)
                code.setText(""); toast("Paired!"); refresh()
            } catch (e: Exception) { toast(e.message ?: "Pairing failed") }
        }
    }

    private fun start() {
        when {
            !Settings.canDrawOverlays(this) -> toast("Do step 1 first")
            !hasMic() -> toast("Do step 2 first")
            Prefs.token(this) == null -> toast("Pair first")
            else -> {
                ContextCompat.startForegroundService(this, Intent(this, RadioService::class.java))
                toast("Overlay running — now press Home and open Roblox")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); refresh()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
