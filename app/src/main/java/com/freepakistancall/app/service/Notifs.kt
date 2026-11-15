package com.freepakistancall.app.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.freepakistancall.app.MainActivity
import com.freepakistancall.app.R
import com.freepakistancall.app.call.CallManager
import com.freepakistancall.app.data.S

object Notifs {
    const val CH_CALLS = "calls"; const val CH_ONGOING = "ongoing"
    const val ID_IN = 1001; const val ID_ONGOING = 1002

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW))
    }

    @SuppressLint("MissingPermission")
    fun incoming(c: Context, who: String) {
        val open = Intent(c, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val f = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(c, 0, open, f)
        val acc = PendingIntent.getActivity(c, 1, Intent(open).putExtra("accept", true), f)
        val dec = PendingIntent.getBroadcast(c, 2, Intent(c, CallActionReceiver::class.java), f)
        val n = NotificationCompat.Builder(c, CH_CALLS).setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(who).setContentText(S.t("incoming"))
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).setTimeoutAfter(45_000)
            .addAction(0, S.t("decline"), dec).addAction(0, S.t("accept"), acc).build()
        try { NotificationManagerCompat.from(c).notify(ID_IN, n) } catch (_: Exception) {}
    }
    fun cancelIncoming(c: Context) = NotificationManagerCompat.from(c).cancel(ID_IN)
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { CallManager.init(c); CallManager.reject(); Notifs.cancelIncoming(c) }
}
