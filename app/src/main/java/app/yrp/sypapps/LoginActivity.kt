package app.yrp.sypapps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.provider.WebAuthProvider
import kotlinx.coroutines.*

/**
 * Drives Auth0's native Universal Login via WebAuthProvider — opens the system browser
 * (Chrome Custom Tabs) rather than an in-app WebView, and gets handed back control via the
 * app.yrp.sypapps://yorkshire-rp.uk.auth0.com deep link once the user's done. The actual
 * tokens land in Auth0Session; this screen's only job is to kick the flow off, show something
 * sensible while it's in progress, and hand off to Home once that succeeds.
 */
class LoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var retryBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG_DARK

        status = TextView(this).apply {
            text = "Signing in…"; setTextColor(Theme.TEXT_LIGHT); textSize = 15f
            setPadding(dp(24), dp(16), dp(24), 0); gravity = Gravity.CENTER
        }
        spinner = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        retryBtn = Button(this).apply {
            text = "Retry"; visibility = View.GONE
            setOnClickListener { login() }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(spinner); addView(status); addView(retryBtn)
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Theme.BG_DARK)
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        })

        login()
    }

    private fun login() {
        spinner.visibility = View.VISIBLE
        retryBtn.visibility = View.GONE
        status.text = "Signing in…"

        scope.launch {
            try {
                var login = WebAuthProvider.login(Auth0Session.account)
                    .withScheme(BuildConfig.AUTH0_SCHEME)
                    .withScope("openid profile email offline_access")
                // Points the access token at the dispatch backend's own Auth0 API, so it's
                // something the server can actually verify — see Backend.kt / Auth0Session.
                if (BuildConfig.AUTH0_AUDIENCE.isNotBlank()) {
                    login = login.withAudience(BuildConfig.AUTH0_AUDIENCE)
                }
                val credentials = login.await(this@LoginActivity)
                Auth0Session.save(credentials)
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                finish()
            } catch (e: AuthenticationException) {
                spinner.visibility = View.GONE
                status.text = when {
                    e.isCanceled -> "Sign-in cancelled."
                    e.isBrowserAppNotAvailable -> "No browser found to sign in with."
                    else -> "Couldn't sign in: ${e.getDescription()}"
                }
                retryBtn.visibility = View.VISIBLE
            }
        }
    }

    override fun onBackPressed() {
        // Nothing to go "back" to — Login is the start of the stack when signed out.
        finishAffinity()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
