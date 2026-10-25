package com.stahij.jarvis

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

class OrbService : Service() {
    private lateinit var wm: WindowManager
    private var orb: OrbView? = null
    private lateinit var params: WindowManager.LayoutParams
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши Jarvis отображаться поверх других приложений", Toast.LENGTH_LONG).show()
            stopSelf(); return START_NOT_STICKY
        }
        if (orb == null) showOrb()
        return START_STICKY
    }
    private fun showOrb() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (76 * resources.displayMetrics.density).toInt()
        params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels-size)/2
            y = resources.displayMetrics.heightPixels/3
        }
        val view = OrbView(this); orb = view
        var downX=0f; var downY=0f; var startX=0; var startY=0; var moved=false
        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { downX=ev.rawX; downY=ev.rawY; startX=params.x; startY=params.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(ev.rawX-downX).toInt(); val dy=(ev.rawY-downY).toInt()
                    if (abs(dx)+abs(dy)>8) moved=true
                    params.x=startX+dx; params.y=startY+dy
                    try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    true
                }
                else -> true
            }
        }
        wm.addView(view, params)
    }
    override fun onDestroy() { orb?.let { try { wm.removeView(it) } catch (_: Exception) {} }; orb=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
