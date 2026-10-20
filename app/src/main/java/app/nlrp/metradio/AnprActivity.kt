package app.nlrp.metradio

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.*

/**
 * ANPR tab: the same native map engine as CAD/Map, showing fixed camera coverage circles plus
 * the live position of any currently BOLO'd *person* (vehicle BOLOs aren't position-tracked —
 * see /api/officer/anpr's docstring server-side).
 *
 * Direction-of-travel arrows aren't implemented yet — that needs trailing-position tracking
 * per target, which is a separate piece of backend work, not just a rendering change. The
 * banner below says so rather than silently showing nothing.
 */
class AnprActivity : android.app.Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var map: MapCanvasView
    private lateinit var spinner: ProgressBar
    private lateinit var errorText: TextView
    private var pollJob: Job? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        root.addView(TextView(this).apply {
            text = "Camera coverage + live BOLO'd people. Direction-of-travel arrows: coming soon."
            textSize = 12f; setTextColor(Theme.MUTED)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        })

        map = MapCanvasView(this)
        spinner = ProgressBar(this)
        errorText = TextView(this).apply {
            setTextColor(0xFFFF8A80.toInt()); textSize = 14f; visibility = android.view.View.GONE
        }

        val stack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(spinner, FrameLayout.LayoutParams(dp(40), dp(40)).apply { gravity = android.view.Gravity.CENTER })
            addView(errorText, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER; leftMargin = dp(24); rightMargin = dp(24)
            })
        }
        root.addView(stack)
        setContentView(root)

        scope.launch {
            try {
                map.bitmap = Backend.loadMapBitmap()
                spinner.visibility = android.view.View.GONE
                startPolling()
            } catch (e: Backend.Unauthorized) {
                startActivity(Intent(this@AnprActivity, LoginActivity::class.java)); finish()
            } catch (e: Exception) {
                spinner.visibility = android.view.View.GONE
                errorText.visibility = android.view.View.VISIBLE
                errorText.text = "Couldn't load the map: ${e.message}"
            }
        }
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val data = Backend.anprData()
                    map.circles = data.cameras.map {
                        MapCanvasView.Circle(GameMap.frac(it.x), GameMap.frac(it.z),
                            (it.radius / 5355.0).toFloat(), 0x806FD1FF.toInt(), it.name)
                    }
                    map.dots = data.tracked.map {
                        MapCanvasView.Dot(GameMap.frac(it.x), GameMap.frac(it.z), 0xFFFF3B30.toInt(), it.name)
                    }
                } catch (e: Backend.Unauthorized) {
                    startActivity(Intent(this@AnprActivity, LoginActivity::class.java)); finish(); return@launch
                } catch (e: Exception) { /* keep last-known state on a transient error */ }
                delay(5000)
            }
        }
    }

    override fun onDestroy() { pollJob?.cancel(); scope.cancel(); super.onDestroy() }
}
