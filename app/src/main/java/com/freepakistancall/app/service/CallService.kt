package com.freepakistancall.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.freepakistancall.app.R
import com.freepakistancall.app.data.S

/** Foreground service so the microphone keeps working while the app is in the background. */
class CallService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, Notifs.CH_ONGOING).setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(S.t("app_name")).setContentText(S.t("calling")).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(Notifs.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(Notifs.ID_ONGOING, n)
        return START_NOT_STICKY
    }
    companion object {
        fun start(c: Context) { try { ContextCompat.startForegroundService(c, Intent(c, CallService::class.java)) } catch (_: Exception) {} }
        fun stop(c: Context) { c.stopService(Intent(c, CallService::class.java)) }
    }
}
