package app.nlrp.metradio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

/** Setup screen: grant overlay + mic permission, pair with a code, start/stop the overlay. */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private lateinit var code: EditText
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            setBackgroundColor(0xFF0A0C12.toInt())
        }
        col.addView(TextView(this).apply { text = "MET Radio Overlay"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()) })
        status = TextView(this).apply { textSize = 14f; setTextColor(0xFFC8D0E8.toInt()); setPadding(0, dp(12), 0, dp(12)) }
        col.addView(status)

        col.addView(button("1. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("2. Allow microphone & notifications") {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(perms.toTypedArray(), 1)
        })

        col.addView(TextView(this).apply {
            text = "3. On the dispatch site: Radio → Generate Pairing Code, then type it here"
            setTextColor(0xFF8892B8.toInt()); setPadding(0, dp(14), 0, dp(4))
        })
        code = EditText(this).apply {
            hint = "6-digit code"; inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF666666.toInt())
        }
        col.addView(code)
        col.addView(button("Pair") { pair() })
        col.addView(button("Unpair") { Prefs.clear(this); refresh() })

        col.addView(button("▶  Start radio overlay") { start() }.apply { setPadding(0, dp(18), 0, dp(18)) })
        col.addView(button("■  Stop radio overlay") {
            startService(Intent(this, RadioService::class.java).setAction(RadioService.ACTION_STOP))
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF0A0C12.toInt()); addView(col) })
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val paired = Prefs.token(this) != null
        status.text = buildString {
            append(if (Settings.canDrawOverlays(this@MainActivity)) "✅" else "❌").append("  Overlay permission\n")
            append(if (hasMic()) "✅" else "❌").append("  Microphone permission\n")
            append(if (paired) "✅  Paired as ${Prefs.name(this@MainActivity).ifBlank { "dispatcher" }}" else "❌  Not paired")
        }
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
            Prefs.token(this) == null -> toast("Pair first (step 3)")
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
