package app.nlrp.metradio

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

        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
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
            }
        }
        root.addView(web)
        setContentView(root)
        web.loadUrl(BuildConfig.BASE_URL + path)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
