package com.stahij.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_INSTALL_RESULT) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmation != null) context.startActivity(confirmation)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "Jarvis успешно обновлён", Toast.LENGTH_LONG).show()
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Не удалось установить обновление"
                Toast.makeText(context, "Ошибка обновления Jarvis: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.stahij.jarvis.INSTALL_RESULT"
    }
}
