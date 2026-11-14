package app.yrp.sypapps

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

/** Launcher screen (loading.png): logo, spinner, then straight to Login or Home depending on
 * whether we're still holding a valid Auth0 session from a previous login. */
class SplashActivity : Activity() {
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

        val next = if (Auth0Session.hasValidCredentials()) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this@SplashActivity, next))
        finish()
    }
}
