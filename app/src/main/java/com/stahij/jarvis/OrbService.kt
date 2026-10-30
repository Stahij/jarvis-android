package com.stahij.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
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
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
            Toast.makeText(this, "Разреши Jarvis отображаться поверх других приложений", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        if (!enabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (orb == null) showOrb()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jarvis overlay", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Постоянная работа плавающего ядра Jarvis"
                }
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 11, Intent(this, OrbService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jarvis работает")
            .setContentText("Плавающее энергетическое ядро активно")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Остановить ядро", stopIntent).build())
            .build()
    }

    private fun showOrb() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val size = (76 * resources.displayMetrics.density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, screenWidth - size - (12 * resources.displayMetrics.density).toInt()).coerceIn(0, (screenWidth - size).coerceAtLeast(0))
            y = prefs.getInt(KEY_Y, (screenHeight * 0.72f).toInt()).coerceIn(0, (screenHeight - size).coerceAtLeast(0))
        }

        val view = OrbView(this)
        orb = view
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > 8) moved = true
                    val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - size).coerceAtLeast(0)
                    params.x = (startX + dx).coerceIn(0, maxX)
                    params.y = (startY + dy).coerceIn(0, maxY)
                    try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
                        params.x = if (params.x + size / 2 < resources.displayMetrics.widthPixels / 2) 0 else maxX
                        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply()
                    } else {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    }
                    true
                }
                else -> true
            }
        }
        wm.addView(view, params)
    }

    override fun onDestroy() {
        orb?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        orb = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.stahij.jarvis.STOP_ORB"
        private const val PREFS = "jarvis"
        private const val KEY_ENABLED = "orb_enabled"
        private const val KEY_X = "orb_x"
        private const val KEY_Y = "orb_y"
        private const val CHANNEL_ID = "jarvis_orb"
        private const val NOTIFICATION_ID = 1001
    }
}
