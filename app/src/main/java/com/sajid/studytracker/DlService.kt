package com.sajid.studytracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// ভিডিও/ফাইল ডাউনলোড চলার সময় অ্যাপ ব্যাকগ্রাউন্ডে গেলেও যেন বন্ধ না হয় — তাই ফোরগ্রাউন্ড সার্ভিস
class DlService : Service() {
    companion object {
        private const val CH = "sajid_downloads"
        private const val ID = 7301
        private var count = 0
        @Volatile private var title = "Downloading"
        @Volatile private var text = "Starting..."
        @Volatile private var pct = -1
        private var lastAt = 0L

        private fun ensureChannel(c: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }
        private fun build(c: Context): Notification {
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val b = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title).setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            if (pct in 0..100) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
            return b.build()
        }

        fun begin(c: Context, name: String) {
            val ctx = c.applicationContext
            synchronized(this) { count++; title = name; text = "Starting..."; pct = -1 }
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, DlService::class.java)) } catch (e: Throwable) { }
        }

        fun progress(c: Context, msg: String, p: Int) {
            val now = System.currentTimeMillis()
            if (now - lastAt < 700 && p in 1..99) return
            lastAt = now
            text = msg; pct = p
            try { (c.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build(c.applicationContext)) } catch (e: Throwable) { }
        }

        fun end(c: Context) {
            val ctx = c.applicationContext
            var stop = false
            synchronized(this) { count = maxOf(0, count - 1); if (count == 0) stop = true }
            if (stop) try { ctx.stopService(Intent(ctx, DlService::class.java)) } catch (e: Throwable) { }
        }

        // শেষ হলে আলাদা নোটিফিকেশন (ব্যাকগ্রাউন্ডে থাকলেও দেখা যায়)
        fun finished(c: Context, name: String, uri: Uri?, mime: String, error: String?) {
            try {
                val ctx = c.applicationContext
                ensureChannel(ctx)
                val b = NotificationCompat.Builder(ctx, CH).setAutoCancel(true)
                    .setSmallIcon(if (error == null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                    .setContentTitle(if (error == null) "Download complete" else "Download failed")
                    .setContentText(if (error == null) name else error)
                if (error == null && uri != null) {
                    val v = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    b.setContentIntent(PendingIntent.getActivity(ctx, name.hashCode(), v, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID + 1 + (System.currentTimeMillis() % 1000).toInt(), b.build())
            } catch (e: Throwable) { }
        }
    }

    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, build(this), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, build(this))
        } catch (e: Throwable) { }
        if (wl == null) try {
            wl = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sajid:download").apply { acquire(3 * 60 * 60 * 1000L) }
        } catch (e: Throwable) { }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { wl?.let { if (it.isHeld) it.release() } } catch (e: Throwable) { }
        wl = null
        super.onDestroy()
    }
}
