package app.nlrp.metradio

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import kotlinx.coroutines.*
import java.util.Date

/** Messages tab: a real chat thread with dispatch (/api/officer/state's `messages`, posted via
 * /api/officer/messages) — bubbles, not a WebView of the whole officer app. */
class MessagesActivity : android.app.Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var thread: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var input: EditText
    private var pollJob: Job? = null
    private var lastCount = -1

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(c: Int, r: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SUBHEADER_BLU

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.BG_DARK) }
        root.addView(Chrome.subHeader(this, scope))

        thread = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroller = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            addView(thread)
        }
        root.addView(scroller)

        input = EditText(this).apply {
            hint = "Message dispatch…"; setTextColor(Theme.TEXT_LIGHT); setHintTextColor(0xFF555B75.toInt())
            background = rounded(0xFF0E111A.toInt(), 20)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val sendBtn = Button(this).apply {
            text = "Send"; isAllCaps = false; stateListAnimator = null
            setTextColor(Theme.TEXT_LIGHT); background = rounded(0xFF0047CC.toInt(), 20)
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(8) }
            setOnClickListener { send() }
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Theme.CARD)
            addView(input); addView(sendBtn)
        })

        setContentView(root)
        load(initial = true)
        pollJob = scope.launch { while (isActive) { delay(4000); load(initial = false) } }
    }

    override fun onDestroy() { pollJob?.cancel(); scope.cancel(); super.onDestroy() }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.setText("")
        scope.launch {
            try { Backend.sendMessage(text); load(initial = false) }
            catch (e: Exception) { Toast.makeText(this@MessagesActivity, "Couldn't send: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun load(initial: Boolean) = scope.launch {
        try {
            val msgs = Backend.messages()
            if (msgs.size == lastCount) return@launch   // nothing new — don't rebuild/rescroll
            lastCount = msgs.size
            thread.removeAllViews()
            if (msgs.isEmpty()) {
                thread.addView(TextView(this@MessagesActivity).apply {
                    text = "No messages yet"; setTextColor(Theme.MUTED); gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, 0)
                })
            }
            msgs.forEach { thread.addView(bubble(it)) }
            if (!initial) Backend.markMessagesRead()
            scroller.post { scroller.fullScroll(ScrollView.FOCUS_DOWN) }
        } catch (e: Backend.Unauthorized) {
            startActivity(Intent(this@MessagesActivity, LoginActivity::class.java)); finish()
        } catch (e: Exception) { /* transient — next poll will retry */ }
    }

    private fun bubble(m: Backend.Message): View {
        val mine = m.from == "unit"
        val maxBubbleWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(if (mine) 0xFF0047CC.toInt() else Theme.CARD, 14)
            addView(TextView(this@MessagesActivity).apply {
                text = m.text; setTextColor(Theme.TEXT_LIGHT); textSize = 15f
                maxWidth = maxBubbleWidth
            })
            addView(TextView(this@MessagesActivity).apply {
                text = DateFormat.format("HH:mm", Date((m.ts * 1000).toLong())).toString() +
                    if (!mine && m.by.isNotBlank()) "  ·  ${m.by}" else ""
                setTextColor(0xFFB9C0DE.toInt()); textSize = 11f; setPadding(0, dp(4), 0, 0)
            })
        }
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
            addView(bubble, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START
            })
        }
    }
}
