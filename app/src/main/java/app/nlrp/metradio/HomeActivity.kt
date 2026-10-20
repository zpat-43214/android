package app.nlrp.metradio

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlinx.coroutines.*

/** The home dashboard (signed in.png): "Welcome / name" banner, duty-status bar, and the
 * 2x3 grid of feature tiles. */
class HomeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var nameLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private data class Tile(val emoji: String, val label: String, val launch: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.HEADER_BLUE
        window.navigationBarColor = Theme.BG_DARK

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ── "Welcome / name" banner ──
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.HEADER_BLUE)
            setPadding(dp(18), dp(28), dp(18), dp(18))
            addView(TextView(this@HomeActivity).apply {
                text = "Welcome"; textSize = 40f; setTextColor(Theme.TEXT_LIGHT); typeface = Fonts.black(this@HomeActivity)
            })
            nameLabel = TextView(this@HomeActivity).apply {
                text = "…"; textSize = 24f; setTextColor(Theme.TEXT_LIGHT); typeface = Fonts.regular(this@HomeActivity)
            }
            addView(nameLabel)
        })

        // ── duty status bar (no HOME button here — we ARE home — gear -> Settings instead) ──
        root.addView(Chrome.subHeader(this, scope, rightLabel = "⚙", rightDestination = SettingsActivity::class.java))

        // ── tile grid ──
        val tiles = listOf(
            Tile("📋", "PNC") { startActivity(Intent(this, PncActivity::class.java)) },
            Tile("🚗", "ANPR") { startActivity(Intent(this, AnprActivity::class.java)) },
            Tile("💬", "Messages") { startActivity(Intent(this, MessagesActivity::class.java)) },
            Tile("📻", "Radio") { startActivity(Intent(this, RadioActivity::class.java)) },
            Tile("📱", "Phone") { openWeb("Phone", "/phone") },
            Tile("🗺️", "CAD / Map") { startActivity(Intent(this, CadMapActivity::class.java)) },
        )
        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val tileSize = resources.displayMetrics.widthPixels / 2 - dp(27)
        tiles.forEach { t -> grid.addView(tileView(t, tileSize)) }

        root.addView(ScrollView(this).apply {
            setBackgroundColor(Theme.BG_DARK)
            isFillViewport = true
            addView(grid)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        })

        setContentView(root)
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun refresh() = scope.launch {
        try {
            val state = Backend.officerState()
            nameLabel.text = state.displayName.ifBlank { "Officer" }
        } catch (e: Backend.Unauthorized) {
            startActivity(Intent(this@HomeActivity, LoginActivity::class.java)); finish()
        } catch (e: Exception) { /* leave the placeholder — no need to block the dashboard on this */ }
    }

    private fun openWeb(title: String, path: String) {
        startActivity(Intent(this, WebViewActivity::class.java)
            .putExtra(WebViewActivity.EXTRA_TITLE, title)
            .putExtra(WebViewActivity.EXTRA_PATH, path))
    }

    private fun tileView(t: Tile, size: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat(); setColor(Theme.TILE_BG)
        }
        layoutParams = GridLayout.LayoutParams().apply {
            width = size; height = size
            setMargins(dp(9), dp(9), dp(9), dp(9))
        }
        isClickable = true; isFocusable = true
        setOnClickListener { t.launch() }
        addView(TextView(this@HomeActivity).apply {
            text = t.emoji; textSize = 34f; gravity = Gravity.CENTER
        })
        addView(TextView(this@HomeActivity).apply {
            text = t.label; textSize = 14f; setTextColor(Theme.TEXT_DARK)
            typeface = Fonts.bold(this@HomeActivity); gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
    }
}
