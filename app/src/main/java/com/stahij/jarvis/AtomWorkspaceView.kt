package com.stahij.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class LauncherApp(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable
)

class AtomWorkspaceView(
    context: Context,
    private val installedApps: List<LauncherApp>,
    private val onPageChanged: (Int, Int) -> Unit
) : View(context) {
    private data class OrbitalItem(
        val app: LauncherApp,
        var angle: Float,
        var orbit: Float,
        var page: Int,
        var rect: RectF = RectF()
    )

    private val prefs = context.getSharedPreferences("jarvis_atom_workspace", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val items = mutableListOf<OrbitalItem>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page = 0
    private var pageCount = 1
    private var running = true
    private var freezeUntil = 0L
    private var downX = 0f
    private var downY = 0f
    private var selected: OrbitalItem? = null
    private var dragging = false
    private var lastFrame = 0L
    private val stars = List(64) { i ->
        val x = ((i * 73 + 19) % 997) / 997f
        val y = ((i * 137 + 41) % 991) / 991f
        Triple(x, y, 0.35f + ((i * 17) % 60) / 100f)
    }

    init {
        isFocusable = true
        isClickable = true
        loadLayout()
    }

    private fun loadLayout() {
        val saved = prefs.getString("layout_v1", null)
        val savedMap = HashMap<String, Triple<Float, Float, Int>>()
        if (saved != null) {
            try {
                val array = org.json.JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    savedMap[item.getString("id")] = Triple(
                        item.optDouble("angle", 0.0).toFloat(),
                        item.optDouble("orbit", 0.30).toFloat(),
                        item.optInt("page", 0)
                    )
                }
            } catch (_: Exception) { }
        }
        val pageSize = 12
        installedApps.forEachIndexed { index, app ->
            val id = app.packageName + "/" + app.className
            val old = savedMap[id]
            val targetPage = old?.third ?: (index / pageSize)
            val inPage = index % pageSize
            val angle = old?.first ?: ((inPage.toFloat() / pageSize) * (Math.PI * 2.0)).toFloat()
            val orbit = old?.second ?: (0.25f + (inPage % 3) * 0.095f)
            items.add(OrbitalItem(app, angle, orbit.coerceIn(.20f, .44f), targetPage.coerceAtLeast(0)))
        }
        pageCount = maxOf(1, (items.maxOfOrNull { it.page } ?: 0) + 1, (installedApps.size + pageSize - 1) / pageSize)
        page = prefs.getInt("current_page", 0).coerceIn(0, pageCount - 1)
        onPageChanged(page, pageCount)
    }

    fun changePage(delta: Int) {
        if (pageCount <= 1 && delta > 0) {
            pageCount++
        }
        page = (page + delta).coerceIn(0, pageCount - 1)
        prefs.edit().putInt("current_page", page).apply()
        onPageChanged(page, pageCount)
        invalidate()
    }

    fun restoreSavedPositions() {
        freezeUntil = SystemClock.uptimeMillis() + 3500L
        invalidate()
    }

    fun resumeMotion() {
        running = true
        lastFrame = 0L
        invalidate()
    }

    fun pauseMotion() {
        running = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(Color.rgb(3, 8, 20))
        drawStars(canvas, w, h)
        val cx = w * .5f
        val cy = h * .49f
        val base = min(w, h)
        val now = SystemClock.uptimeMillis()
        val frozen = now < freezeUntil || !running
        val elapsed = if (frozen) 0f else now / 1000f
        drawOrbit(canvas, cx, cy, base * .27f, -22f, 2.0f)
        drawOrbit(canvas, cx, cy, base * .37f, 54f, 1.5f)
        drawOrbit(canvas, cx, cy, base * .45f, -66f, 1.1f)
        drawCore(canvas, cx, cy, base * .115f)

        val visible = items.filter { it.page == page }
        visible.forEachIndexed { index, item ->
            val angle = item.angle + if (frozen) 0f else elapsed * (.11f + (index % 4) * .012f)
            val radius = base * item.orbit
            val x = cx + cos(angle.toDouble()).toFloat() * radius
            val y = cy + sin(angle.toDouble()).toFloat() * radius * .57f
            val depth = (sin(angle.toDouble()).toFloat() + 1f) * .5f
            val scale = .72f + depth * .38f
            val iconSize = 34f * density * scale
            val alpha = (155 + depth * 100).toInt()
            paint.color = Color.argb(alpha, 12, 30, 48)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, iconSize * .67f, paint)
            paint.color = Color.argb(90 + (depth * 90).toInt(), 80, 203, 239)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.1f * density
            canvas.drawCircle(x, y, iconSize * .67f, paint)
            paint.style = Paint.Style.FILL
            val rect = RectF(x - iconSize / 2f, y - iconSize / 2f, x + iconSize / 2f, y + iconSize / 2f)
            item.rect.set(rect)
            val oldAlpha = item.app.icon.alpha
            item.app.icon.alpha = alpha
            item.app.icon.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            item.app.icon.draw(canvas)
            item.app.icon.alpha = oldAlpha
            textPaint.alpha = alpha
            canvas.drawText(item.app.label.take(16), x, y + iconSize * .78f + 12f * density, textPaint)
        }
        drawPageDots(canvas, w, h)
        if (running) postInvalidateOnAnimation()
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        stars.forEachIndexed { i, star ->
            starPaint.color = Color.argb((star.third * 95).toInt(), 123, 185, 221)
            val radius = if (i % 7 == 0) 1.5f else .75f
            canvas.drawCircle(star.first * w, star.second * h, radius * density, starPaint)
        }
    }

    private fun drawOrbit(canvas: Canvas, cx: Float, cy: Float, radius: Float, tilt: Float, alpha: Float) {
        canvas.save()
        canvas.rotate(tilt, cx, cy)
        paint.color = Color.argb((30 * alpha).toInt(), 65, 175, 221)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        canvas.drawOval(RectF(cx - radius, cy - radius * .48f, cx + radius, cy + radius * .48f), paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius * 1.9f,
            intArrayOf(Color.argb(0, 31, 195, 255), Color.argb(48, 20, 175, 237), Color.argb(0, 4, 12, 28)),
            floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.9f, paint)
        paint.shader = null
        paint.color = Color.argb(210, 16, 85, 119)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = Color.argb(180, 78, 220, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        canvas.drawCircle(cx, cy, radius * .82f, paint)
        paint.color = Color.argb(225, 140, 239, 255)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - radius * .22f, cy - radius * .24f, radius * .24f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f * density
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText("JARVIS", cx, cy + radius * .18f, paint)
        paint.typeface = Typeface.DEFAULT
    }

    private fun drawPageDots(canvas: Canvas, w: Float, h: Float) {
        val totalWidth = pageCount * 13f * density
        val start = (w - totalWidth) / 2f
        for (i in 0 until pageCount) {
            paint.color = if (i == page) Color.rgb(96, 220, 255) else Color.rgb(52, 79, 103)
            canvas.drawCircle(start + i * 13f * density + 5f * density, h - 9f * density,
                (if (i == page) 3f else 2f) * density, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                selected = items.firstOrNull { it.page == page && it.rect.contains(event.x, event.y) }
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (selected != null && (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 14f * density)) {
                    dragging = true
                    val item = selected!!
                    val cx = width * .5f
                    val cy = height * .49f
                    val x = event.x - cx
                    val y = (event.y - cy) / .57f
                    item.angle = kotlin.math.atan2(y, x)
                    item.orbit = (kotlin.math.sqrt(x * x + y * y) / min(width, height)).coerceIn(.20f, .44f)
                    item.rect.offsetTo(event.x - item.rect.width() / 2f, event.y - item.rect.height() / 2f)
                    freezeUntil = SystemClock.uptimeMillis() + 1000L
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && selected != null) {
                    selected?.let { launch(it.app) }
                } else if (!dragging && selected == null) {
                    val cx = width * .5f
                    val cy = height * .49f
                    val dx = event.x - cx
                    val dy = event.y - cy
                    if (kotlin.math.sqrt(dx * dx + dy * dy) < min(width, height) * .14f) {
                        restoreSavedPositions()
                    } else {
                        val delta = event.x - downX
                        if (kotlin.math.abs(delta) > 55f * density) changePage(if (delta < 0) 1 else -1)
                    }
                }
                if (dragging) saveLayout()
                selected = null
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                selected = null
                dragging = false
                return true
            }
        }
        return true
    }

    private fun launch(app: LauncherApp) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    private fun saveLayout() {
        val array = org.json.JSONArray()
        items.forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("id", item.app.packageName + "/" + item.app.className)
                put("angle", item.angle.toDouble())
                put("orbit", item.orbit.toDouble())
                put("page", item.page)
            })
        }
        prefs.edit().putString("layout_v1", array.toString()).apply()
    }
}
