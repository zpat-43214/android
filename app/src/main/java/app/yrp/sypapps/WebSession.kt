package app.yrp.sypapps

import android.webkit.CookieManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app logs in once, in a WebView, via the normal Auth0 web flow (see LoginActivity) — there
 * is no username/password API to call directly. That login leaves a session cookie sitting in
 * Android's global [CookieManager], which every WebView in the app already shares automatically.
 * This bridges the *same* cookie into OkHttp, so native screens (duty status, the PNC list) can
 * call the site's existing JSON APIs without a second, separate login.
 */
object WebSession {

    private val cookieInterceptor = okhttp3.Interceptor { chain ->
        val url = chain.request().url.toString()
        val cookie = CookieManager.getInstance().getCookie(url)
        val req = if (!cookie.isNullOrBlank())
            chain.request().newBuilder().header("Cookie", cookie).build()
        else chain.request()
        val resp = chain.proceed(req)
        // In case the server ever rotates/refreshes the session cookie on a plain API call.
        resp.headers("Set-Cookie").forEach { CookieManager.getInstance().setCookie(url, it) }
        resp
    }

    // Explicit (rather than relying on OkHttp's defaults) because the dispatch server runs on
    // modest hardware over sometimes-slow mobile connections — generous enough that a normal
    // slow load still succeeds, but callers doing something time-sensitive (the map image)
    // wrap their own withTimeout() on top of this rather than waiting the full 30s.
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(cookieInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun flush() = CookieManager.getInstance().flush()

    /** Cheap, local check — doesn't confirm the session is still valid server-side, just that
     * we have *some* cookie for this site. Callers that need to be sure (e.g. the splash
     * screen deciding Login vs Home) should back this up with a real API call. */
    fun hasAnyCookie(baseUrl: String): Boolean =
        !CookieManager.getInstance().getCookie(baseUrl).isNullOrBlank()

    fun clear() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
