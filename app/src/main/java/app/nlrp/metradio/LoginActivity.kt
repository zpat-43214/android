package app.nlrp.metradio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * There's no username/password API — the dispatch site logs in via Auth0 (a redirect to
 * Auth0's hosted login page, then back to /callback, which sets the session cookie). So this
 * just drives that same flow in a plain WebView, exactly like a browser would, and watches for
 * the landing page the flow ends on. Once there, the cookie is already sitting in Android's
 * global CookieManager — every other WebView (and, via WebSession, OkHttp) in the app shares
 * it automatically, so this is the only screen that ever has to think about logging in.
 */
class LoginActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG_DARK

        val base = BuildConfig.BASE_URL
        val baseHost = Uri.parse(base).host

        CookieManager.getInstance().setAcceptCookie(true)

        val web = WebView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url?.let { Uri.parse(it) } ?: return
                // Lands back on our own site's index ("/") once Auth0 -> /callback has finished.
                if (u.host == baseHost && (u.path.isNullOrEmpty() || u.path == "/")) {
                    WebSession.flush()
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                }
            }
        }

        setContentView(web)
        web.loadUrl("$base/login")
    }

    override fun onBackPressed() {
        // Nothing to go "back" to — Login is the start of the stack when signed out.
        finishAffinity()
    }
}
