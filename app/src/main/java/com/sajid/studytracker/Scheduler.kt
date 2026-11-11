package com.sajid.studytracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.util.Calendar

object Scheduler {
    private const val PREF = "alarm"

    private fun sp(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun ringtone(c: Context): String? = sp(c).getString("ringtone", null)
    fun setRingtone(c: Context, uri: String) = sp(c).edit().putString("ringtone", uri).apply()
    // ডিফল্ট ভাইব্রেশন (সেটিং থেকে) — বন্ধ থাকলে শুধু সেই রুটিনেই ভাইব্রেশন হবে যেখানে আলাদা করে চালু করা আছে
    fun defaultVibrate(c: Context): Boolean = sp(c).getBoolean("defVib", false)
    fun setDefaultVibrate(c: Context, v: Boolean) = sp(c).edit().putBoolean("defVib", v).apply()

    // ===== অ্যালার্ম সেটিং: কতক্ষণ বাজবে / স্নুজ কত মিনিট / কতবার স্নুজ =====
    fun ringMin(c: Context): Int = sp(c).getInt("ringMin", 2)
    fun snoozeMin(c: Context): Int = sp(c).getInt("snoozeMin", 5)
    fun snoozeMax(c: Context): Int = sp(c).getInt("snoozeMax", 3)
    fun setAlarmCfg(c: Context, ring: Int, sMin: Int, sMax: Int) {
        sp(c).edit().putInt("ringMin", ring.coerceIn(1, 30)).putInt("snoozeMin", sMin.coerceIn(1, 60)).putInt("snoozeMax", sMax.coerceIn(0, 10)).apply()
    }
    private fun snoozeCounts(c: Context): org.json.JSONObject =
        try { org.json.JSONObject(sp(c).getString("snoozeCounts", "{}")) } catch (e: Exception) { org.json.JSONObject() }
    fun snoozeCount(c: Context, title: String): Int = snoozeCounts(c).optInt(title, 0)
    fun bumpSnoozeCount(c: Context, title: String) {
        try { sp(c).edit().putString("snoozeCounts", snoozeCounts(c).apply { put(title, optInt(title, 0) + 1) }.toString()).apply() } catch (e: Exception) { }
    }
    fun resetSnoozeCount(c: Context, title: String) {
        try { sp(c).edit().putString("snoozeCounts", snoozeCounts(c).apply { remove(title) }.toString()).apply() } catch (e: Exception) { }
    }

    // রিংটোন ফাইল অ্যাপের নিজের ফোল্ডারে কপি করা থাকে (key = "default" অথবা রুটিনের নিজস্ব id)
    fun ringFile(c: Context, key: String): java.io.File = java.io.File(c.filesDir, "ring_" + key.replace(Regex("[^A-Za-z0-9_]"), "_"))
    fun hasRing(c: Context, key: String): Boolean = ringFile(c, key).let { it.exists() && it.length() > 0 }

    // এই অ্যালার্মের জন্য কোন রিংটোন ফাইল (null = ডিফল্ট) এবং ভাইব্রেশন হবে কি না
    fun resolve(c: Context, id: String, title: String): Pair<String?, Boolean> {
        var ring: String? = null
        var vibMode = ""
        try {
            val a = JSONArray(sp(c).getString("alarms", "[]"))
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                val match = if (id.startsWith("snooze:")) o.optString("task") == title else o.optString("id") == id
                if (match) {
                    val r = o.optString("ring", "")
                    if (r.isNotEmpty() && hasRing(c, r)) ring = ringFile(c, r).absolutePath
                    vibMode = o.optString("vib", "")
                    break
                }
            }
        } catch (e: Exception) { }
        // রুটিনে "চালু"/"বন্ধ" বাছা থাকলে সেটাই, নইলে সেটিংয়ের ডিফল্ট
        val vib = when (vibMode) { "on" -> true; "off" -> false; else -> defaultVibrate(c) }
        return Pair(ring, vib)
    }

    fun step(c: Context): Int = sp(c).getInt("step", 0)
    fun setStep(c: Context, v: Int) = sp(c).edit().putInt("step", v).apply()

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pi(c: Context, id: String, title: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("title", title)
        return PendingIntent.getBroadcast(
            c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPi(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun set(c: Context, id: String, title: String, at: Long) {
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, showPi(c)), pi(c, id, title))
    }

    fun nextTrigger(time: String, days: List<Int>): Long? {
        val p = time.split(":")
        val h = p[0].toInt(); val m = p[1].toInt()
        val now = System.currentTimeMillis()
        for (i in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, i)
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > now && (days.isEmpty() || days.contains(c.get(Calendar.DAY_OF_WEEK) - 1)))
                return c.timeInMillis
        }
        return null
    }

    private fun scheduleOne(c: Context, o: org.json.JSONObject) {
        try {
            val days = ArrayList<Int>()
            val da = o.optJSONArray("days") ?: JSONArray()
            for (k in 0 until da.length()) days.add(da.getInt(k))
            val at = nextTrigger(o.getString("time"), days) ?: return
            set(c, o.getString("id"), o.optString("task", "রুটিন"), at)
            Log.d("SajidAlarm", "scheduled ${o.getString("id")} at ${java.util.Date(at)}")
        } catch (e: Exception) { Log.e("SajidAlarm", "schedule failed: $o", e) }
    }

    fun saveAndScheduleAll(c: Context, json: String) {
        cancelAll(c)
        sp(c).edit().putString("alarms", json).apply()
        rescheduleAll(c)
    }

    fun cancelAll(c: Context) {
        try {
            val a = JSONArray(sp(c).getString("alarms", "[]"))
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                am(c).cancel(pi(c, o.getString("id"), o.optString("task")))
            }
        } catch (e: Exception) { }
    }

    fun rescheduleAll(c: Context) {
        scheduleReset(c)
        try {
            val a = JSONArray(sp(c).getString("alarms", "[]"))
            for (k in 0 until a.length()) scheduleOne(c, a.getJSONObject(k))
        } catch (e: Exception) { }
        // বাকি থাকা স্নুজগুলোও আবার সেট (রিবুট/টাইম বদলের পর)
        try {
            val sn = snoozes(c); val now = System.currentTimeMillis()
            for (t in sn.keys().asSequence().toList()) {
                val at = sn.optLong(t)
                if (at > now) set(c, "snooze:$t", t, at) else removeSnooze(c, t)
            }
        } catch (e: Exception) { }
    }

    fun rescheduleOne(c: Context, id: String) {
        try {
            val a = JSONArray(sp(c).getString("alarms", "[]"))
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                if (o.getString("id") == id) scheduleOne(c, o)
            }
        } catch (e: Exception) { }
    }

    private fun snoozes(c: Context): org.json.JSONObject =
        try { org.json.JSONObject(sp(c).getString("snoozes", "{}")) } catch (e: Exception) { org.json.JSONObject() }

    fun removeSnooze(c: Context, title: String) {
        try { sp(c).edit().putString("snoozes", snoozes(c).apply { remove(title) }.toString()).apply() } catch (e: Exception) { }
    }

    fun scheduleSnooze(c: Context, title: String, minutes: Int, extraSeconds: Int = 0) {
        scheduleSnoozeAt(c, title, System.currentTimeMillis() + minutes * 60_000L + extraSeconds * 1000L)
    }

    // স্নুজের সময় সেভ থাকে, যাতে তালিকায় দেখানো যায় এবং রিবুটের পর আবার সেট করা যায়
    fun scheduleSnoozeAt(c: Context, title: String, at: Long) {
        set(c, "snooze:$title", title, at)
        scheduleReset(c)
        try { sp(c).edit().putString("snoozes", snoozes(c).apply { put(title, at) }.toString()).apply() } catch (e: Exception) { }
    }

    // স্নুজ শুধু একদিনের জন্য: প্রতি রাত ৩টায় সব স্নুজ রিসেট
    fun nextReset(from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 3); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleReset(c: Context) {
        try { am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReset(), pi(c, "reset", "")) } catch (e: Exception) { }
    }

    fun clearSnoozes(c: Context) {
        try {
            for (t in snoozes(c).keys().asSequence().toList()) cancelSnooze(c, t)
            sp(c).edit().putString("snoozes", "{}").putString("snoozeCounts", "{}").apply()
        } catch (e: Exception) { }
    }

    fun cancelSnooze(c: Context, title: String) {
        try { am(c).cancel(pi(c, "snooze:$title", title)) } catch (e: Exception) { }
        removeSnooze(c, title)
    }

    fun missedId(title: String): Int = 1000 + (title.hashCode() and 0x7fffffff) % 100000

    fun note(c: Context, key: String, v: String) = sp(c).edit().putString(key, v).apply()

    private fun bn(n: Int): String {
        val d = "০১২৩৪৫৬৭৮৯"
        return n.toString().map { d[it - '0'] }.joinToString("")
    }

    // শেষ ৫টি বেজে যাওয়া অ্যালার্মের নাম ও সময় সংরক্ষণ (নতুনটা আগে)
    fun addHistory(c: Context, title: String) {
        try {
            val old = JSONArray(sp(c).getString("history", "[]"))
            val neu = JSONArray()
            val e = org.json.JSONObject().put("n", title).put("t", System.currentTimeMillis())
            neu.put(e)
            for (k in 0 until minOf(old.length(), 4)) neu.put(old.getJSONObject(k))
            sp(c).edit().putString("history", neu.toString()).apply()
        } catch (e: Exception) { }
    }

    fun status(c: Context): String {
        val sb = StringBuilder()
        val fmt = java.text.SimpleDateFormat("EEE dd MMM, hh:mm a", java.util.Locale.getDefault())
        try {
            val a = JSONArray(sp(c).getString("alarms", "[]"))
            sb.append("সেভ করা অ্যালার্ম: ").append(bn(a.length())).append(" টি\n\n")

            sb.append("🔔 শেষ বেজে যাওয়া ৫টি অ্যালার্ম:\n")
            val h = JSONArray(sp(c).getString("history", "[]"))
            if (h.length() == 0) sb.append("এখনো কোনোটা বাজেনি\n")
            for (k in 0 until h.length()) {
                val o = h.getJSONObject(k)
                sb.append(bn(k + 1)).append(") ").append(o.optString("n")).append(" — ")
                    .append(fmt.format(java.util.Date(o.optLong("t")))).append("\n")
            }

            sb.append("\n⏰ পরবর্তী ৫টি অ্যালার্ম:\n")
            val list = ArrayList<Pair<Long, String>>()
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                val days = ArrayList<Int>()
                val da = o.optJSONArray("days") ?: JSONArray()
                for (j in 0 until da.length()) days.add(da.getInt(j))
                val t = nextTrigger(o.getString("time"), days)
                if (t != null) list.add(Pair(t, o.optString("task", "রুটিন")))
            }
            try {
                val sn = snoozes(c); val now = System.currentTimeMillis()
                for (t in sn.keys().asSequence().toList()) {
                    val at = sn.optLong(t)
                    if (at > now) list.add(Pair(at, t + " (স্নুজ)"))
                }
            } catch (e: Exception) { }
            list.sortBy { it.first }
            if (list.isEmpty()) sb.append("কোনো অ্যালার্ম সেট নেই")
            for (k in 0 until minOf(5, list.size)) {
                sb.append(bn(k + 1)).append(") ").append(list[k].second).append(" — ")
                    .append(fmt.format(java.util.Date(list[k].first)))
                if (k < minOf(5, list.size) - 1) sb.append("\n")
            }
        } catch (e: Exception) { sb.append("অ্যালার্ম পড়তে সমস্যা: ").append(e.message) }
        return sb.toString()
    }
}
