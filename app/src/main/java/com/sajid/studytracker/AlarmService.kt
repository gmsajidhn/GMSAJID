package com.sajid.studytracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wl: PowerManager.WakeLock? = null
    private val titles = linkedSetOf<String>()
    private val h = Handler(Looper.getMainLooper())
    private var ringPath: String? = null   // রুটিনের নিজস্ব রিংটোন (না থাকলে ডিফল্ট)
    private var wantVib = false            // এই রুটিনে (বা ডিফল্ট সেটিংয়ে) ভাইব্রেশন চালু কি না
    private var vibrating = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) {
            "STOP" -> {
                titles.forEach { Scheduler.cancelSnooze(this, it) }
                stopAll(); return START_NOT_STICKY
            }
            "SNOOZE" -> {
                // ইউজারের বেছে নেওয়া সময়ে (at) শুধু এই রুটিনগুলো আবার বাজবে
                val given = i.getStringExtra("titles")?.split("\n")?.filter { it.isNotBlank() }
                val list = if (!given.isNullOrEmpty()) given else titles.toList()
                val now = System.currentTimeMillis()
                val at = i.getLongExtra("at", 0L).let { if (it > now) it else now + Scheduler.snoozeMin(this) * 60_000L }
                // সেটিংয়ে ঠিক করা সর্বোচ্চ স্নুজ-সংখ্যা পার হলে আর স্নুজ হবে না
                list.forEach {
                    if (Scheduler.snoozeCount(this, it) < Scheduler.snoozeMax(this)) {
                        Scheduler.bumpSnoozeCount(this, it)
                        Scheduler.scheduleSnoozeAt(this, it, at)
                    }
                }
                stopAll(); return START_NOT_STICKY
            }
        }
        i?.getStringExtra("ring")?.let { if (it.isNotEmpty() && ringPath == null) ringPath = it }
        val newVib = i?.getBooleanExtra("vib", false) == true
        i?.getStringExtra("title")?.let {
            titles.add(it)
            // নোটিফিকেশন শুধু প্রথমবার বাজার সময়; স্নুজ/বারবার বাজলে নতুন নোটিফিকেশন নয়
            if (!i.getBooleanExtra("repeat", false)) postMissed(it)
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, 1, buildNotif(), type)
        } catch (e: Exception) {
            android.util.Log.e("SajidAlarm", "startForeground failed", e)
            Scheduler.note(this, "lastError", "startForeground: " + e.javaClass.simpleName + ": " + e.message)
            try { startForeground(1, buildNotif()) } catch (e2: Exception) { }
        }
        // অ্যাপের ভেতরে থাকলে আলাদা ফুল-স্ক্রিন পপআপ নেই — শুধু রিংটোন + নোটিফিকেশন (Stop/Snooze বাটনসহ)
        if (player == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sajid:alarm").apply { acquire((Scheduler.ringMin(this@AlarmService) + 1) * 60_000L) }
            wakeScreenAndShow()
            if (newVib) wantVib = true
            play()
            if (wantVib) vibrate()
            h.postDelayed({
                // সেটিংয়ে ঠিক করা সময় (Ring duration) পর শুধু বাজা বন্ধ হবে — নিজে নিজে আর বাজবে না (স্নুজ করলেই শুধু পরে বাজবে)
                stopAll()
            }, Scheduler.ringMin(this) * 60_000L)
        }
        // আগে থেকে বাজছে, এমন সময় অন্য রুটিন এল যার ভাইব্রেশন চালু — তখন ভাইব্রেশন শুরু হবে
        if (newVib && !wantVib) wantVib = true
        if (wantVib && !vibrating && player != null) vibrate()
        return START_NOT_STICKY
    }

    // স্ক্রিন বন্ধ বা ফোন লক থাকলে স্ক্রিন জ্বালিয়ে সরাসরি ফুল-স্ক্রিন অ্যালার্ম পেজ খোলে
    // (নোটিফিকেশনের ফুল-স্ক্রিন ইন্টেন্ট কিছু ফোনে আটকে গেলেও যেন পেজ আসে)
    @Suppress("DEPRECATION")
    private fun wakeScreenAndShow() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!pm.isInteractive || km.isKeyguardLocked) {
                val sw = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "sajid:alarmscreen")
                sw.acquire(15_000L)
                startActivity(
                    Intent(this, AlarmActivity::class.java).putExtra("text", titles.joinToString("\n"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        } catch (e: Exception) {
            Scheduler.note(this, "lastError", "wakeScreen: " + e.javaClass.simpleName + ": " + e.message)
        }
    }

    private fun buildNotif(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("alarm_v2", "রুটিন অ্যালার্ম", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val text = titles.joinToString("\n")
        val full = PendingIntent.getActivity(
            this, 2, Intent(this, AlarmActivity::class.java).putExtra("text", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun act(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, AlarmService::class.java).setAction(a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // স্নুজ চাপলে সময় বেছে নেওয়ার পপআপ খুলবে
        val snoozePick = PendingIntent.getActivity(
            this, 3, Intent(this, AlarmActivity::class.java).putExtra("text", text).putExtra("pick", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "alarm_v2")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ রুটিন রিমাইন্ডার")
            .setContentText(text.replace("\n", ", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Snooze", snoozePick)
            .addAction(0, "Stop", act("STOP", 4))
            .build()
    }

    private fun play() {
        try {
            val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val defFile = Scheduler.ringFile(this, "default")
            val custom: Uri? = when {
                ringPath != null && java.io.File(ringPath!!).exists() -> Uri.fromFile(java.io.File(ringPath!!))
                Scheduler.hasRing(this, "default") -> Uri.fromFile(defFile)
                else -> Scheduler.ringtone(this)?.let { Uri.parse(it) }
            }
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            try { mp.setDataSource(this, custom ?: def) }
            catch (e: Exception) { mp.reset(); mp.setDataSource(this, def) }
            mp.isLooping = true
            mp.prepare(); mp.start()
            player = mp
        } catch (e: Exception) { Scheduler.note(this, "lastError", "play: " + e.message) }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        vibrating = true
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pat = longArrayOf(0, 800, 600)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, 0))
        else v.vibrate(pat, 0)
    }

    @Suppress("DEPRECATION")
    private fun stopAll() {
        h.removeCallbacksAndMessages(null)
        try { player?.stop(); player?.release() } catch (e: Exception) { }
        player = null
        vibrating = false; wantVib = false; ringPath = null
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        try { if (wl?.isHeld == true) wl?.release() } catch (e: Exception) { }
        titles.clear()
        sendBroadcast(Intent("com.sajid.studytracker.ALARM_STOPPED").setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // প্রতিটি রুটিনের প্রথমবার বাজলে "Missed Routine" নোটিফিকেশন (সাইলেন্ট) থেকে যায়
    private fun postMissed(t: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("alarm_missed", "Missed Routine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        setSound(null, null); enableVibration(false)
                    }
                )
            }
            val open = PendingIntent.getActivity(
                this, 6, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val now = System.currentTimeMillis()
            run {
                val n = NotificationCompat.Builder(this, "alarm_missed")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Missed Routine")
                    .setContentText(t)
                    .setSubText(java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)))
                    .setWhen(now).setShowWhen(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(false)
                    .setContentIntent(open)
                    .build()
                nm.notify(Scheduler.missedId(t + (now / 60000)), n)
            }
        } catch (e: Exception) { }
    }

    override fun onDestroy() { stopAll2(); super.onDestroy() }
    private fun stopAll2() {
        try { player?.release() } catch (e: Exception) { }
        player = null
    }
}
