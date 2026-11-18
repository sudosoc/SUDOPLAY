package com.sudosoc.sudoplay.online

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sudosoc.sudoplay.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Only exists to keep the process alive (and show progress) while DownloadCenter has work; stops itself when idle. */
class DownloadService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loop: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    private fun notif(): Notification {
        val n = DownloadCenter.activeCount
        val pct = (DownloadCenter.avgProgress() * 100).toInt()
        return NotificationCompat.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_w_note)
            .setContentTitle(if (n > 0) "Downloading $n item${if (n == 1) "" else "s"}" else "Downloads finished")
            .setContentText(if (n > 0) "$pct%" else null)
            .setProgress(100, pct, n > 0 && pct == 0)
            .setOngoing(n > 0).setOnlyAlertOnce(true).setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, notif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(77, notif())
        if (loop?.isActive != true) loop = scope.launch {
            while (isActive && DownloadCenter.activeCount > 0) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(77, notif())
                delay(700)
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(77, notif())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { loop?.cancel(); super.onDestroy() }
}
