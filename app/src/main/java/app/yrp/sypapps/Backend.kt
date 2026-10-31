package app.yrp.sypapps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Calls the dispatch site's existing session-authenticated JSON APIs (/api/officer/...) using
 * the web-login session cookie — see WebSession. Separate from Api.kt, which is the
 * device-token-authenticated radio pairing API and deliberately knows nothing about the web
 * session. */
object Backend {
    private val JSON = "application/json".toMediaType()
    private val base = BuildConfig.BASE_URL

    class Unauthorized : IOException("Not logged in")

    data class StatusDef(val code: String, val label: String, val colour: String)

    data class OfficerState(
        val displayName: String,
        val booked: Boolean,
        val statusCode: String?,
        val statusLabel: String?,
        val callsign: String?,
        val defs: List<StatusDef>,
    )

    data class PncCharacter(
        val id: Int, val name: String, val dob: String, val phone: String,
        val markers: List<String>, val wm: Boolean, val warrantCount: Int,
    )

    data class Message(val id: Long, val from: String, val by: String, val text: String, val ts: Double)

    data class MapOfficer(val name: String, val callsign: String, val colour: String, val team: String, val x: Double, val z: Double, val street: String)
    data class MapCall(val callNumber: Long, val description: String, val grade: String, val x: Double, val z: Double, val positionDescriptor: String)
    data class MapData(val officers: List<MapOfficer>, val calls: List<MapCall>)

    data class Camera(val id: Int, val name: String, val x: Double, val z: Double, val radius: Double)
    data class TrackedBolo(val name: String, val x: Double, val z: Double, val reason: String, val addedBy: String)
    data class AnprData(val cameras: List<Camera>, val tracked: List<TrackedBolo>)

    private fun getJson(url: String): JSONObject {
        WebSession.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw Unauthorized()
            val text = r.body?.string().orEmpty()
            return try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        }
    }

    private fun postJson(url: String, body: JSONObject) {
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(JSON)).build()
        WebSession.client.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw Unauthorized()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        }
    }

    /**
     * The ERLC map background image, shared by the CAD/Map and ANPR screens. Bundled into the
     * app (app/src/main/assets/map.png) rather than fetched from the server — it never
     * changes at runtime, and pulling a ~17MB image off a Pi Zero over mobile data was exactly
     * what made the map screens "stuck loading" before. Reading it is local-only, no network,
     * no timeout to worry about.
     *
     * The source file is 5355x5355 (full ERLC-coordinate resolution, see GameMap) — far more
     * than a phone screen needs even pinch-zoomed in, and decoded at full size it's ~108MB of
     * raw RGBA, so this downsamples while decoding rather than loading it uncapped.
     */
    suspend fun loadMapBitmap(ctx: android.content.Context): android.graphics.Bitmap = withContext(Dispatchers.IO) {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.assets.open("map.png").use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        val target = 2048   // plenty for a phone screen even pinch-zoomed in — see MapCanvasView
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2

        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.assets.open("map.png").use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Couldn't decode bundled map image")
    }

    private fun parseDefs(arr: JSONArray): List<StatusDef> =
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StatusDef(o.getString("code"), o.getString("label"), o.optString("colour", "#666666"))
        }

    /** Throws Unauthorized if the session cookie is missing/expired — callers use that to
     * decide whether to send the person back to LoginActivity. */
    suspend fun officerState(): OfficerState = withContext(Dispatchers.IO) {
        val j = getJson("$base/api/officer/state")
        val defs = parseDefs(j.optJSONArray("status_defs") ?: JSONArray())
        val display = j.optString("display_name", "")
        if (!j.optBoolean("booked", false)) {
            OfficerState(display, false, null, null, null, defs)
        } else {
            val unit = j.getJSONObject("unit")
            val code = unit.optString("status", "")
            OfficerState(display, true, code, defs.find { it.code == code }?.label ?: code,
                unit.optString("callsign", null), defs)
        }
    }

    suspend fun setStatus(code: String) = withContext(Dispatchers.IO) {
        postJson("$base/api/officer/status", JSONObject().put("status", code))
    }

    /** Messages are already included in officerState()'s raw payload; this fetches just that
     * list for the Messages screen without pulling in the tasks/unit fields it doesn't need. */
    suspend fun messages(): List<Message> = withContext(Dispatchers.IO) {
        val j = getJson("$base/api/officer/state")
        val arr = j.optJSONArray("messages") ?: JSONArray()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Message(o.optLong("id"), o.optString("from", ""), o.optString("by", ""),
                o.optString("text", ""), o.optDouble("ts", 0.0))
        }
    }

    suspend fun sendMessage(text: String) = withContext(Dispatchers.IO) {
        postJson("$base/api/officer/messages", JSONObject().put("text", text))
    }

    suspend fun markMessagesRead() = withContext(Dispatchers.IO) {
        postJson("$base/api/officer/messages/read", JSONObject())
    }

    suspend fun mapData(): MapData = withContext(Dispatchers.IO) {
        val j = getJson("$base/api/data")
        val officers = j.optJSONArray("officers") ?: JSONArray()
        val calls = j.optJSONArray("calls") ?: JSONArray()
        MapData(
            (0 until officers.length()).map {
                val o = officers.getJSONObject(it)
                MapOfficer(o.optString("name", ""), o.optString("callsign", ""), o.optString("colour", "police"),
                    o.optString("team", ""), o.optDouble("x", 0.0), o.optDouble("z", 0.0), o.optString("street", ""))
            },
            (0 until calls.length()).map {
                val c = calls.getJSONObject(it)
                MapCall(c.optLong("callNumber"), c.optString("description", ""), c.optString("grade", "I"),
                    c.optDouble("x", 0.0), c.optDouble("z", 0.0), c.optString("positionDescriptor", ""))
            },
        )
    }

    suspend fun anprData(): AnprData = withContext(Dispatchers.IO) {
        val j = getJson("$base/api/officer/anpr")
        val cams = j.optJSONArray("cameras") ?: JSONArray()
        val tracked = j.optJSONArray("tracked") ?: JSONArray()
        AnprData(
            (0 until cams.length()).map {
                val o = cams.getJSONObject(it)
                Camera(o.optInt("id"), o.optString("name", ""), o.optDouble("x", 0.0), o.optDouble("z", 0.0), o.optDouble("radius", 60.0))
            },
            (0 until tracked.length()).map {
                val o = tracked.getJSONObject(it)
                TrackedBolo(o.optString("name", ""), o.optDouble("x", 0.0), o.optDouble("z", 0.0),
                    o.optString("reason", ""), o.optString("added_by", ""))
            },
        )
    }

    suspend fun pncList(): List<PncCharacter> = withContext(Dispatchers.IO) {
        val j = getJson("$base/api/officer/pnc")
        val arr = j.optJSONArray("characters") ?: JSONArray()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val markers = o.optJSONArray("markers") ?: JSONArray()
            PncCharacter(
                id = o.optInt("id"), name = o.optString("name", "Unknown"),
                dob = o.optString("dob", ""), phone = o.optString("phone", ""),
                markers = (0 until markers.length()).map { i -> markers.getString(i) },
                wm = o.optBoolean("wm", false),
                warrantCount = (o.optJSONArray("warrants") ?: JSONArray()).length(),
            )
        }
    }
}
