package com.sajid.studytracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        if (id == "reset") { // রাত ৩টা: সব স্নুজ মুছে ফেলো, পরের রাতের রিসেট সেট করো
            Scheduler.clearSnoozes(c); Scheduler.scheduleReset(c); return
        }
        val title = i.getStringExtra("title") ?: "রুটিন রিমাইন্ডার"
        Scheduler.note(c, "lastFired",
            java.text.SimpleDateFormat("dd MMM hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date()) + " (" + title + ")")
        Scheduler.addHistory(c, title)
        if (!id.startsWith("snooze:")) { Scheduler.rescheduleOne(c, id); Scheduler.resetSnoozeCount(c, title) } else Scheduler.removeSnooze(c, title)
        val (ring, vib) = Scheduler.resolve(c, id, title)
        try {
            ContextCompat.startForegroundService(
                c, Intent(c, AlarmService::class.java).putExtra("title", title)
                    .putExtra("repeat", id.startsWith("snooze:"))
                    .putExtra("ring", ring ?: "").putExtra("vib", vib)
            )
        } catch (e: Exception) {
            Scheduler.note(c, "lastError", "service: " + e.javaClass.simpleName + ": " + e.message)
            fallback(c, title)
        }
    }

    // সার্ভিস চালু না হলে সরাসরি নোটিফিকেশন + রিংটোন
    private fun fallback(c: Context, title: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26)
                nm.createNotificationChannel(NotificationChannel("alarm_fb", "রুটিন অ্যালার্ম (ব্যাকআপ)", NotificationManager.IMPORTANCE_HIGH))
            val full = PendingIntent.getActivity(
                c, 5, Intent(c, AlarmActivity::class.java).putExtra("text", title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(c, "alarm_fb")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ রুটিন রিমাইন্ডার")
                .setContentText(title)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setAutoCancel(true)
                .build()
            nm.notify(77, n)
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(c, uri)?.play()
        } catch (e: Exception) {
            Scheduler.note(c, "lastError", "fallback: " + e.message)
        }
    }
}
