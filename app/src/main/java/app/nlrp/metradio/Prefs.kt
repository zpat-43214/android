package app.nlrp.metradio

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("metradio", Context.MODE_PRIVATE)

    fun token(c: Context): String? = sp(c).getString("token", null)
    fun name(c: Context): String = sp(c).getString("name", "") ?: ""
    fun save(c: Context, token: String, name: String) =
        sp(c).edit().putString("token", token).putString("name", name).apply()
    fun clear(c: Context) = sp(c).edit().remove("token").remove("name").apply()

    fun lastChannel(c: Context): String? = sp(c).getString("channel", null)
    fun saveChannel(c: Context, key: String) = sp(c).edit().putString("channel", key).apply()

    fun overlayX(c: Context) = sp(c).getInt("ox", -1)
    fun overlayY(c: Context) = sp(c).getInt("oy", -1)
    fun saveOverlayPos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("ox", x).putInt("oy", y).apply()
}
