package app.yrp.sypapps

import android.app.Activity
import android.content.Intent
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

    private data class Tile(val name: String, val description: String, val launch: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.HEADER_BLUE
        window.navigationBarColor = Theme.TILE_BG

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

        // ── tile grid (2 columns) ──
        val tiles = listOf(
            Tile("PNC", "Look up persons & vehicles") { startActivity(Intent(this, PncActivity::class.java)) },
            Tile("ANPR", "Live camera tracking") { startActivity(Intent(this, AnprActivity::class.java)) },
            Tile("Messages", "Chat with dispatch") { startActivity(Intent(this, MessagesActivity::class.java)) },
            Tile("Radio", "Push-to-talk overlay") { startActivity(Intent(this, RadioActivity::class.java)) },
            Tile("Phone", "In-game calls") { openWeb("Phone", "/phone") },
            Tile("CAD / Map", "Live units & calls") { startActivity(Intent(this, CadMapActivity::class.java)) },
        )
        val columns = 2
        val grid = GridLayout(this).apply {
            columnCount = columns
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val tileSize = resources.displayMetrics.widthPixels / columns - dp(27)
        val rows = (tiles.size + columns - 1) / columns
        tiles.forEachIndexed { i, t ->
            val row = i / columns; val col = i % columns
            grid.addView(tileView(
                t, tileSize,
                borderTop = row > 0, borderBottom = row < rows - 1,
                borderLeft = col > 0, borderRight = col < columns - 1,
            ))
        }

        root.addView(ScrollView(this).apply {
            setBackgroundColor(Theme.TILE_BG)
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

    /** Tiles sit flush against each other (no gaps/margins) so the 1px border only ever shows
     * on the sides facing another tile — e.g. a top-right tile gets a border on its left and
     * bottom only, never on the top or right where it meets the screen edge. */
    private fun tileView(
        t: Tile, size: Int,
        borderTop: Boolean, borderBottom: Boolean, borderLeft: Boolean, borderRight: Boolean,
    ): View {
        val borderColor = 0xFFBDBDBD.toInt()
        val borderPx = Math.max(1, dp(1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.TILE_BG)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true; isFocusable = true
            setOnClickListener { t.launch() }
            addView(TextView(this@HomeActivity).apply {
                text = t.name; textSize = 24f; setTextColor(Theme.TEXT_DARK)
                typeface = Fonts.bold(this@HomeActivity)
            })
            addView(TextView(this@HomeActivity).apply {
                text = t.description; textSize = 12f; setTextColor(Theme.TEXT_DARK)
                typeface = Fonts.regular(this@HomeActivity)
                setPadding(0, dp(4), 0, 0)
            })
        }

        return FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply { width = size; height = size }
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            if (borderTop) addView(View(this@HomeActivity).apply { setBackgroundColor(borderColor) },
                FrameLayout.LayoutParams(MATCH_PARENT, borderPx).apply { gravity = Gravity.TOP })
            if (borderBottom) addView(View(this@HomeActivity).apply { setBackgroundColor(borderColor) },
                FrameLayout.LayoutParams(MATCH_PARENT, borderPx).apply { gravity = Gravity.BOTTOM })
            if (borderLeft) addView(View(this@HomeActivity).apply { setBackgroundColor(borderColor) },
                FrameLayout.LayoutParams(borderPx, MATCH_PARENT).apply { gravity = Gravity.START })
            if (borderRight) addView(View(this@HomeActivity).apply { setBackgroundColor(borderColor) },
                FrameLayout.LayoutParams(borderPx, MATCH_PARENT).apply { gravity = Gravity.END })
        }
    }
}
