package app.yrp.sypapps

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.*

/** CAD/Map tab: a real pinch-zoom-pan map (MapCanvasView) with live officer + call markers,
 * polling /api/data every 5s — the same feed and the same coordinate system the web app's map
 * uses, just rendered natively instead of in a WebView. */
class CadMapActivity : android.app.Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var map: MapCanvasView
    private lateinit var spinner: ProgressBar
    private lateinit var errorText: TextView
    private var pollJob: Job? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val gradeColours = mapOf(
        "I" to 0xFFE53935.toInt(), "S" to 0xFFFFB300.toInt(), "E" to 0xFF43A047.toInt(), "U" to 0xFF607D8B.toInt(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        map = MapCanvasView(this)
        spinner = ProgressBar(this)
        errorText = TextView(this).apply {
            setTextColor(0xFFFF8A80.toInt()); textSize = 14f; visibility = android.view.View.GONE
        }

        errorText.setOnClickListener { loadMap() }

        val stack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).apply { gravity = android.view.Gravity.CENTER })
            addView(errorText, FrameLayout.LayoutParams(MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER; leftMargin = dp(24); rightMargin = dp(24)
            })
        }
        root.addView(stack)
        setContentView(root)
        loadMap()
    }

    /** The map image itself is bundled in the app (see Backend.loadMapBitmap) — this can't
     * hang on the network. Still catches Throwable, not just Exception, since a bitmap decode
     * failure (OutOfMemoryError is an Error, not an Exception) should show a clear error
     * rather than leave the spinner sitting there with nothing to show for it. */
    private fun loadMap() {
        pollJob?.cancel()
        spinner.visibility = android.view.View.VISIBLE
        errorText.visibility = android.view.View.GONE
        scope.launch {
            try {
                map.bitmap = Backend.loadMapBitmap(this@CadMapActivity)
                spinner.visibility = android.view.View.GONE
                startPolling()
            } catch (e: Backend.Unauthorized) {
                startActivity(Intent(this@CadMapActivity, LoginActivity::class.java)); finish()
            } catch (e: Throwable) {
                showError("Couldn't load the map: ${e.message} — tap to retry")
            }
        }
    }

    private fun showError(msg: String) {
        spinner.visibility = android.view.View.GONE
        errorText.visibility = android.view.View.VISIBLE
        errorText.text = msg
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val data = Backend.mapData()
                    map.dots = data.officers.map {
                        MapCanvasView.Dot(GameMap.frac(it.x), GameMap.frac(it.z),
                            if (it.colour == "sheriff") 0xFF003399.toInt() else 0xFF4FA3FF.toInt(), it.callsign.ifBlank { it.name })
                    }
                    map.boxes = data.calls.map {
                        MapCanvasView.Box(GameMap.frac(it.x), GameMap.frac(it.z), it.grade.uppercase(),
                            gradeColours[it.grade.uppercase()] ?: 0xFF757575.toInt())
                    }
                } catch (e: Backend.Unauthorized) {
                    startActivity(Intent(this@CadMapActivity, LoginActivity::class.java)); finish(); return@launch
                } catch (e: Exception) { /* keep showing the last-known markers on a transient error */ }
                delay(5000)
            }
        }
    }

    override fun onDestroy() { pollJob?.cancel(); scope.cancel(); super.onDestroy() }
}
