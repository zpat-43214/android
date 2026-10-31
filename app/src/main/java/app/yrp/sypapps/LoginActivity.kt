package app.yrp.sypapps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*

/**
 * There's no username/password API — the dispatch site logs in via Auth0 (a redirect to
 * Auth0's hosted login page, then back to /callback, which sets the session cookie). So this
 * just drives that same flow in a plain WebView, exactly like a browser would, and watches for
 * the landing page the flow ends on. Once there, the cookie is already sitting in Android's
 * global CookieManager — every other WebView (and, via WebSession, OkHttp) in the app shares
 * it automatically, so this is the only screen that ever has to think about logging in.
 *
 * We don't try to guess which exact path the site lands on after Auth0 hands control back
 * (it might be "/", "/callback", "/dashboard", a client-side route swap, etc. — and guessing
 * wrong just leaves the WebView sitting on the live website forever). Instead, every time the
 * WebView settles on our own host outside of the login-initiating page, we ask the backend
 * directly — the same check SplashActivity uses — and only hand off to HomeActivity once that
 * confirms the session cookie is actually valid.
 */
class LoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var handedOff = false
    private var checking = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG_DARK

        val base = BuildConfig.BASE_URL
        val baseHost = Uri.parse(base).host

        CookieManager.getInstance().setAcceptCookie(true)

        // WebView defaults to a BLACK background until the page actually paints — if the page
        // is slow, or fails to load outright (wrong/unreachable domain, Auth0 misconfigured),
        // that reads as a dead black screen with zero feedback. Force a sane background and
        // surface failures explicitly instead.
        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        val errorText = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE); textSize = 14f
            setPadding(dp(24), 0, dp(24), dp(16)); gravity = android.view.Gravity.CENTER
            visibility = android.view.View.GONE
        }
        val retryBtn = Button(this).apply {
            text = "Retry"; visibility = android.view.View.GONE
            setOnClickListener {
                visibility = android.view.View.GONE; errorText.visibility = android.view.View.GONE
                web.loadUrl("$base/login")
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url?.let { Uri.parse(it) } ?: return
                // Still on the page that *starts* the login flow (or a retry of it) — nothing
                // to check yet. Auth0 itself lives on a different host, so it's naturally
                // skipped by the host check below without needing special-casing here.
                if (u.host != baseHost || u.path?.startsWith("/login") == true) return
                if (handedOff || checking) return
                checking = true
                scope.launch {
                    val loggedIn = try { Backend.officerState(); true } catch (e: Exception) { false }
                    checking = false
                    if (loggedIn && !handedOff) {
                        handedOff = true
                        WebSession.flush()
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                    // Not logged in yet (e.g. still mid-flow, or a page that happens to live on
                    // our own host but isn't the final landing page) — just keep showing the
                    // WebView and let it carry on; we'll re-check on the next page it settles on.
                }
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                errorText.visibility = android.view.View.VISIBLE
                errorText.text = "Couldn't load the login page: ${error?.description}\n($base/login)"
                retryBtn.visibility = android.view.View.VISIBLE
            }
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Theme.BG_DARK)
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(LinearLayout(this@LoginActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                addView(errorText); addView(retryBtn)
            }, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { gravity = android.view.Gravity.CENTER })
        }

        setContentView(root)
        web.loadUrl("$base/login")
    }

    override fun onBackPressed() {
        // Nothing to go "back" to — Login is the start of the stack when signed out.
        finishAffinity()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
