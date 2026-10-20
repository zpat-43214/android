package app.nlrp.metradio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import kotlinx.coroutines.*

/** Launcher screen (loading.png): logo, spinner, then straight to Login or Home depending on
 * whether the web session cookie from a previous login is still good. */
class SplashActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG_DARK
        window.navigationBarColor = Theme.BG_DARK

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Theme.BG_DARK)
        }
        col.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo_square)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(160), dp(160)).apply { topMargin = dp(140) }
        })
        col.addView(ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(48) }
        })

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Theme.BG_DARK)
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = Gravity.TOP })
        })

        scope.launch {
            val loggedIn = try { Backend.officerState(); true } catch (e: Exception) { false }
            val next = if (loggedIn) HomeActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
