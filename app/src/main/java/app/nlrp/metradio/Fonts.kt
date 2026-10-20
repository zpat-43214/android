package app.nlrp.metradio

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** Lato, bundled as ttf files under app/src/main/res/font (Android has no system Lato). */
object Fonts {
    fun thin(c: Context): Typeface    = ResourcesCompat.getFont(c, R.font.lato_thin)!!
    fun light(c: Context): Typeface   = ResourcesCompat.getFont(c, R.font.lato_light)!!
    fun regular(c: Context): Typeface = ResourcesCompat.getFont(c, R.font.lato_regular)!!
    fun bold(c: Context): Typeface    = ResourcesCompat.getFont(c, R.font.lato_bold)!!
    fun black(c: Context): Typeface   = ResourcesCompat.getFont(c, R.font.lato_black)!!
}
