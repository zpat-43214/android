package app.yrp.sypapps

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * Generic tile screen: subheader chrome (status dropdown + HOME) above a WebView loading a
 * path on the existing dispatch site. Shares the same login session as everything else via
 * the app-wide CookieManager, so no separate auth step is needed here.
 *
 * Only Phone uses this now — Messages, CAD/Map and ANPR got proper native screens
 * (MessagesActivity, CadMapActivity, AnprActivity). Phone stays a WebView since it's its own
 * substantial, separately-authed mini-app (Roblox OAuth) rather than something worth
 * reimplementing natively.
 */
class WebViewActivity : Activity() {
    companion object { const val EXTRA_TITLE = "title"; const val EXTRA_PATH = "path" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val path = intent.getStringExtra(EXTRA_PATH) ?: "/"

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Chrome.subHeader(this, scope))

        // See LoginActivity's comment: WebView defaults to a black background until content
        // paints, so a slow/failed load reads as a dead black screen with no explanation.
        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(view: WebView?, req: android.webkit.WebResourceRequest?, err: android.webkit.WebResourceResponse?) {
                    super.onReceivedHttpError(view, req, err)
                    if (req?.isForMainFrame == true && err?.statusCode == 401) {
                        Toast.makeText(this@WebViewActivity, "Signed out — log in again", Toast.LENGTH_SHORT).show()
                        startActivity(android.content.Intent(this@WebViewActivity, LoginActivity::class.java))
                        finish()
                    }
                }
                override fun onReceivedError(view: WebView?, req: android.webkit.WebResourceRequest?, err: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, req, err)
                    if (req?.isForMainFrame == true) {
                        Toast.makeText(this@WebViewActivity, "Couldn't load $title: ${err?.description}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        root.addView(web)
        setContentView(root)
        web.loadUrl(BuildConfig.BASE_URL + path)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
