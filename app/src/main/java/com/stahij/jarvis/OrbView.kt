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
import kotlin.math.cos

class OrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        contentDescription = "Jarvis energy core"
        // Keep the default hardware-accelerated rendering for smoother animation.
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val pulse = sin(phase * Math.PI * 2).toFloat()
        val r = min(width, height) * (0.30f + 0.026f * pulse)
        val halo = r * (1.72f + 0.08f * pulse)

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, halo,
            intArrayOf(0x6639F5FF, 0x2233BFFF, 0x0033BFFF),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, halo, paint)

        // Soft outer corona.
        paint.shader = RadialGradient(
            cx, cy, r * 1.25f,
            intArrayOf(0x3339F5FF, 0x1139F5FF, 0x0039F5FF),
            floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.25f, paint)

        // Glass-like luminous core.
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.30f, r * 1.35f,
            intArrayOf(
                0xFFEEFFFF.toInt(), 0xFF65F3FF.toInt(),
                0xFF1599E8.toInt(), 0xFF041A48.toInt()
            ),
            floatArrayOf(0f, 0.22f, 0.68f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null

        // Three thin orbital rings, rotating at different speeds.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.color = 0xAA8BF8FF.toInt()
        canvas.save()
        canvas.rotate(phase * 360f, cx, cy)
        canvas.drawOval(cx - r * 1.23f, cy - r * 0.40f, cx + r * 1.23f, cy + r * 0.40f, paint)
        canvas.restore()

        paint.strokeWidth = 1.35f
        paint.color = 0x8870EFFF.toInt()
        canvas.save()
        canvas.rotate(-phase * 275f + 22f, cx, cy)
        canvas.drawOval(cx - r * 0.43f, cy - r * 1.19f, cx + r * 0.43f, cy + r * 1.19f, paint)
        canvas.restore()

        paint.strokeWidth = 0.9f
        paint.color = 0x557DFBFF
        canvas.save()
        canvas.rotate(phase * 190f + 45f, cx, cy)
        canvas.drawOval(cx - r * 1.08f, cy - r * 0.66f, cx + r * 1.08f, cy + r * 0.66f, paint)
        canvas.restore()

        // A bright particle travels around the outside of the core.
        paint.style = Paint.Style.FILL
        paint.color = 0xFFE8FFFF.toInt()
        val angle = phase * Math.PI * 2
        val particleX = cx + cos(angle).toFloat() * r * 1.16f
        val particleY = cy + sin(angle).toFloat() * r * 0.48f
        canvas.drawCircle(particleX, particleY, 1.8f, paint)

        paint.color = 0x997DFBFF.toInt()
        canvas.drawCircle(cx - r * 0.42f, cy - r * 0.47f, 1.25f, paint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
