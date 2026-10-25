package com.stahij.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

class OrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null); contentDescription = "Jarvis energy core"; animator.start() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) * (0.31f + 0.025f * sin(phase * Math.PI * 2).toFloat())
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, r * 1.75f, intArrayOf(0x5539F5FF, 0x2233BFFF, 0x0033BFFF), floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.75f, paint)
        paint.shader = RadialGradient(cx-r*.25f, cy-r*.3f, r*1.35f, intArrayOf(0xFFEEFFFF.toInt(), 0xFF38E8FF.toInt(), 0xFF0874C9.toInt(), 0xFF041A48.toInt()), floatArrayOf(0f, .22f, .68f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f; paint.color = 0xAA8BF8FF.toInt()
        canvas.save(); canvas.rotate(phase*360f, cx, cy); canvas.drawOval(cx-r*1.18f, cy-r*.42f, cx+r*1.18f, cy+r*.42f, paint); canvas.restore()
        paint.strokeWidth = 1.4f; paint.color = 0x7770EFFF
        canvas.save(); canvas.rotate(-phase*270f, cx, cy); canvas.drawOval(cx-r*.42f, cy-r*1.18f, cx+r*.42f, cy+r*1.18f, paint); canvas.restore()
        paint.style = Paint.Style.FILL
    }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
}
