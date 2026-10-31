package app.yrp.sypapps

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

/** PNC tab: read-only, scrollable list of PNC characters (name, DOB/phone, markers, W/M flag,
 * open-warrant count). Dispatcher-only edit/create/markers stay on the dispatch site — this is
 * deliberately look-up-only. */
class PncActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var list: LinearLayout
    private lateinit var spinner: ProgressBar

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        spinner = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(40)
            }
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            addView(LinearLayout(this@PncActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(spinner); addView(list)
            })
        })
        setContentView(root)
        load()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun load() = scope.launch {
        spinner.visibility = View.VISIBLE
        try {
            val chars = Backend.pncList()
            spinner.visibility = View.GONE
            list.removeAllViews()
            if (chars.isEmpty()) {
                list.addView(TextView(this@PncActivity).apply {
                    text = "No PNC records yet"; setTextColor(Theme.MUTED); gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, 0)
                })
            }
            chars.forEach { list.addView(row(it)) }
        } catch (e: Backend.Unauthorized) {
            startActivity(Intent(this@PncActivity, LoginActivity::class.java)); finish()
        } catch (e: Exception) {
            spinner.visibility = View.GONE
            list.addView(TextView(this@PncActivity).apply {
                text = "Couldn't load PNC: ${e.message}"; setTextColor(0xFFFF8A80.toInt())
                setPadding(0, dp(24), 0, 0); gravity = Gravity.CENTER
            })
        }
    }

    private fun chip(text: String, bg: Int) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Theme.TEXT_LIGHT)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(bg) }
    }

    private fun row(c: Backend.PncCharacter): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(Theme.CARD); setStroke(dp(1), if (c.wm) 0xFF992222.toInt() else Theme.CARD_STROKE)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) }

        addView(LinearLayout(this@PncActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@PncActivity).apply {
                text = c.name; textSize = 17f; setTextColor(Theme.TEXT_LIGHT); typeface = Fonts.bold(this@PncActivity)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            if (c.wm) addView(chip("W/M", 0xFF992222.toInt()))
        })

        val meta = listOfNotNull(
            c.dob.takeIf { it.isNotBlank() }?.let { "DOB $it" },
            c.phone.takeIf { it.isNotBlank() }?.let { "☎ $it" },
        ).joinToString("   ")
        if (meta.isNotBlank()) addView(TextView(this@PncActivity).apply {
            text = meta; textSize = 13f; setTextColor(Theme.MUTED); setPadding(0, dp(4), 0, 0)
        })

        if (c.warrantCount > 0) addView(TextView(this@PncActivity).apply {
            text = "⚠ ${c.warrantCount} open warrant${if (c.warrantCount > 1) "s" else ""}"
            textSize = 13f; setTextColor(0xFFFFC107.toInt()); setPadding(0, dp(6), 0, 0)
        })

        if (c.markers.isNotEmpty()) addView(LinearLayout(this@PncActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            c.markers.filter { it != "W/M" }.forEach {
                addView(chip(it, 0xFF222633.toInt()).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(6) }
                })
            }
        })
    }
}
