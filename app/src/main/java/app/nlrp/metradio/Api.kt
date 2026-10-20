package app.nlrp.metradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object Api {
    class Unauthorized : IOException("Not paired any more")

    data class Channel(val key: String, val label: String, val ptt: Boolean)
    data class Join(val url: String, val token: String)

    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()
    private val base = BuildConfig.BASE_URL

    private fun call(req: Request): JSONObject = http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        val j = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        if (r.code == 401) throw Unauthorized()
        if (!r.isSuccessful) throw IOException(j.optString("error", "HTTP ${r.code}"))
        j
    }

    suspend fun pair(code: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code).toString().toRequestBody(JSON)
        val j = call(Request.Builder().url("$base/api/radio/pair/complete").post(body).build())
        j.getString("device_token") to j.optString("display_name", "")
    }

    suspend fun channels(token: String): List<Channel> = withContext(Dispatchers.IO) {
        val j = call(Request.Builder().url("$base/api/radio/device/channels")
            .header("Authorization", "Bearer $token").build())
        val arr = j.getJSONArray("channels")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Channel(o.getString("key"), o.getString("label"), o.getBoolean("ptt"))
        }
    }

    suspend fun join(token: String, channel: String): Join = withContext(Dispatchers.IO) {
        val body = JSONObject().put("channel", channel).toString().toRequestBody(JSON)
        val j = call(Request.Builder().url("$base/api/radio/device/token")
            .header("Authorization", "Bearer $token").post(body).build())
        Join(j.getString("url"), j.getString("token"))
    }
}
