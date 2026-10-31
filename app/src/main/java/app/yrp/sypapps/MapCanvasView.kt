package app.yrp.sypapps

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * A pinch-zoom-and-pan canvas for /static/map.png, with markers positioned by fraction
 * (0..1) across the image rather than raw pixels — callers convert ERLC game coordinates to
 * fractions once (see [GameMap]) and this just draws dots/boxes/circles on top, at a fixed
 * on-screen size regardless of zoom level. Shared by the CAD/Map and ANPR screens so there's
 * one real map implementation, not two.
 */
class MapCanvasView(context: Context) : View(context) {

    data class Dot(val xFrac: Float, val yFrac: Float, val color: Int, val label: String?)
    data class Box(val xFrac: Float, val yFrac: Float, val text: String, val color: Int)
    data class Circle(val xFrac: Float, val yFrac: Float, val radiusFrac: Float, val color: Int, val label: String?)

    var bitmap: Bitmap? = null
        set(v) { field = v; resetTransform = true; invalidate() }
    var dots: List<Dot> = emptyList(); set(v) { field = v; invalidate() }
    var boxes: List<Box> = emptyList(); set(v) { field = v; invalidate() }
    var circles: List<Circle> = emptyList(); set(v) { field = v; invalidate() }

    private val matrix = Matrix()
    private var resetTransform = true
    private val density = resources.displayMetrics.density

    private val bgPaint = Paint().apply { color = 0xFF1A1A1A.toInt() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = density }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * density }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11 * density; setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }
    private val boxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11 * density; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val f = d.scaleFactor
            matrix.postScale(f, f, d.focusX, d.focusY)
            invalidate(); return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            matrix.postTranslate(-dx, -dy); invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            matrix.postScale(1.6f, 1.6f, e.x, e.y); invalidate(); return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); resetTransform = true
    }

    /** Centers the image and fits it to the view on first layout / whenever a new bitmap is set. */
    private fun resetIfNeeded() {
        val bmp = bitmap ?: return
        if (!resetTransform || width == 0 || height == 0) return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((width - bmp.width * scale) / 2f, (height - bmp.height * scale) / 2f)
        resetTransform = false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val bmp = bitmap ?: return
        resetIfNeeded()
        canvas.drawBitmap(bmp, matrix, null)

        val pt = floatArrayOf(0f, 0f)
        fun toScreen(xFrac: Float, yFrac: Float): FloatArray {
            pt[0] = xFrac * bmp.width; pt[1] = yFrac * bmp.height
            matrix.mapPoints(pt)
            return pt
        }
        // Rough on-screen scale, used only to size camera-radius circles sensibly as you zoom.
        val scaleVals = FloatArray(9); matrix.getValues(scaleVals)
        val curScale = scaleVals[Matrix.MSCALE_X]

        for (c in circles) {
            val p = toScreen(c.xFrac, c.yFrac)
            circlePaint.color = c.color
            canvas.drawCircle(p[0], p[1], c.radiusFrac * bmp.width * curScale, circlePaint)
            if (c.label != null) canvas.drawText(c.label, p[0] + 8 * density, p[1] - 8 * density, labelPaint)
        }
        for (d in dots) {
            val p = toScreen(d.xFrac, d.yFrac)
            dotPaint.color = d.color
            canvas.drawCircle(p[0], p[1], 6 * density, dotPaint)
            canvas.drawCircle(p[0], p[1], 6 * density, dotStroke)
            if (d.label != null) canvas.drawText(d.label, p[0] + 10 * density, p[1] + 4 * density, labelPaint)
        }
        for (b in boxes) {
            val p = toScreen(b.xFrac, b.yFrac)
            boxPaint.color = b.color
            val r = 11 * density
            canvas.drawRoundRect(p[0] - r, p[1] - r, p[0] + r, p[1] + r, 4 * density, 4 * density, boxPaint)
            canvas.drawText(b.text, p[0], p[1] + 4 * density, boxTextPaint)
        }
    }
}

/** ERLC's game coordinates run 0..5355 on both axes (matches the web map's own constants) —
 * converting to a 0..1 fraction is all MapCanvasView needs, independent of the actual image's
 * pixel dimensions. */
object GameMap {
    private const val MIN = 0.0
    private const val MAX = 5355.0
    fun frac(v: Double): Float = ((v - MIN) / (MAX - MIN)).toFloat().coerceIn(0f, 1f)
}
