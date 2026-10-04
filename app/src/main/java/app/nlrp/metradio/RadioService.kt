package app.nlrp.metradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import org.json.JSONObject

/** Owns the LiveKit radio connection, the dispatch-site socket and the floating overlay. */
class RadioService : Service(), Overlay.Callbacks {

    companion object { const val ACTION_STOP = "app.nlrp.metradio.STOP" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private var socket: Socket? = null
    private var overlay: Overlay? = null
    private var channels: List<Api.Channel> = emptyList()
    private var current: Api.Channel? = null
    private var micOpen = false
    private var watchdog: Job? = null
    private var booted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        goForeground()
        if (!booted) { booted = true; boot() }
        return START_NOT_STICKY   // a mic foreground service can't be restarted from the background on Android 14
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("radio", "Radio overlay", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RadioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "radio")
            .setContentTitle("MET Radio")
            .setContentText("Overlay running")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    private fun boot() = scope.launch {
        val token = Prefs.token(this@RadioService)
        if (token == null) { stopSelf(); return@launch }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission missing — open MET Radio and allow it"); stopSelf(); return@launch
        }
        try {
            channels = Api.channels(token)
        } catch (e: Api.Unauthorized) {
            Prefs.clear(this@RadioService); toast("Radio unpaired — open MET Radio and pair again"); stopSelf(); return@launch
        } catch (e: Exception) {
            toast("Can't reach the dispatch server: ${e.message}"); stopSelf(); return@launch
        }

        overlay = Overlay(this@RadioService, this@RadioService).also {
            it.setChannels(channels.map { c -> c.label }); it.show()
        }
        connectSocket(token)

        val last = Prefs.lastChannel(this@RadioService)
        (channels.find { it.key == last } ?: channels.firstOrNull())?.let { join(it) }
    }

    private fun connectSocket(token: String) {
        val opts = IO.Options().apply { auth = mapOf("device_token" to token); reconnection = true }
        val s = IO.socket(BuildConfig.BASE_URL, opts)
        s.on(Socket.EVENT_CONNECT) {
            current?.let { c -> s.emit("device_radio_channel", JSONObject().put("channel", c.key)) }
        }
        // A dispatcher moved us to another channel
        s.on("radio_forced_move") { args ->
            val j = args.firstOrNull() as? JSONObject ?: return@on
            val key = j.optString("channel"); val by = j.optString("moved_by", "Dispatch")
            scope.launch {
                channels.find { it.key == key }?.let { join(it); toast("$by moved you to ${it.label}") }
            }
        }
        s.connect()
        socket = s
    }

    private suspend fun join(ch: Api.Channel) {
        val token = Prefs.token(this) ?: return
        overlay?.setStatus(ch.label, State.CONNECTING)
        leaveRoom()
        try {
            val info = Api.join(token, ch.key)
            val r = LiveKit.create(applicationContext)
            r.connect(info.url, info.token)
            room = r; current = ch
            r.localParticipant.setMicrophoneEnabled(true)            // publish the mic track…
            if (ch.ptt) setMic(false) else micOpen = true             // PTT: silent until pressed. Pursuit Desk: open mic
            Prefs.saveChannel(this, ch.key)
            socket?.emit("device_radio_channel", JSONObject().put("channel", ch.key))
            overlay?.setStatus(ch.label, if (ch.ptt) State.READY else State.OPEN)
        } catch (e: Api.Unauthorized) {
            Prefs.clear(this); toast("Radio unpaired — pair again"); stopSelf()
        } catch (e: Exception) {
            overlay?.setStatus("Offline", State.OFFLINE); toast("Couldn't join ${ch.label}: ${e.message}")
        }
    }

    private fun leaveRoom() {
        room?.disconnect(); room?.release(); room = null
    }

    // Flips the published mic track on/off directly (instant, no server mute round-trip).
    private fun setMic(on: Boolean) {
        micOpen = on
        val lp = room?.localParticipant ?: return
        val track = lp.getTrackPublication(Track.Source.MICROPHONE)?.track as? LocalAudioTrack
        if (track != null) track.enabled = on
        else scope.launch { try { lp.setMicrophoneEnabled(on) } catch (e: Exception) { toast("Mic error: ${e.message}") } }
    }

    // ── Overlay callbacks ────────────────────────────────────────────────
    override fun onPttDown() {
        val ch = current
        if (ch == null || room == null) { toast("Not connected to a radio channel yet"); return }
        if (ch.ptt) {
            setMic(true); overlay?.setStatus(ch.label, State.TX)
            watchdog?.cancel()
            watchdog = scope.launch { delay(90_000); onPttUp() }   // never leave a mic stuck open
        } else {
            setMic(!micOpen); overlay?.setStatus(ch.label, if (micOpen) State.OPEN else State.MUTED)
        }
    }

    override fun onPttUp() {
        val ch = current ?: return
        if (!ch.ptt) return
        watchdog?.cancel()
        setMic(false); overlay?.setStatus(ch.label, State.READY)
    }

    override fun onPick(index: Int) { channels.getOrNull(index)?.let { scope.launch { join(it) } } }
    override fun onClose() { stopSelf() }

    override fun onDestroy() {
        socket?.emit("device_radio_channel", JSONObject().put("channel", JSONObject.NULL))
        socket?.disconnect(); socket = null
        leaveRoom()
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }
}
