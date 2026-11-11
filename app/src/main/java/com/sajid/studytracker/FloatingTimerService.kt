package com.sajid.studytracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

// অ্যাপের কোন স্ক্রিন সামনে আছে এবং টাইমারের সর্বশেষ অবস্থা (JS থেকে আসে)
object TimerState {
    @Volatile var mainVisible = false
    @Volatile var visibleCount = 0   // অ্যাপের (মূল স্ক্রিন বা ব্রাউজার) কতগুলো স্ক্রিন এখন চোখের সামনে
    @Volatile var kind = ""          // "study" (কাউন্টডাউন), "todo" (স্টাডি স্টপওয়াচ), "screen" (স্ক্রিন টাইম), "" = কিছু নেই
    @Volatile var running = false
    @Volatile var mode = ""          // "down" = কাউন্টডাউন, "up" = স্টপওয়াচ
    @Volatile var baseMs = 0L
    @Volatile var syncedAt = 0L
    @Volatile var ignoreUntil = 0L   // পপআপ থেকে কমান্ড দেওয়ার পর কিছুক্ষণ পুরোনো খবর উপেক্ষা
    @Volatile var shownKind = ""     // পপআপে সর্বশেষ কোনটা দেখানো হচ্ছিল (থামানোর/রিসেটের পরও ৫ মিনিট থাকে)
    @Volatile var lastActiveAt = 0L  // সর্বশেষ কখন টাইমার চলছিল / থামল / রিসেট হল

    fun set(k: String, run: Boolean, m: String, ms: Long) {
        val now = System.currentTimeMillis()
        if (running || run) lastActiveAt = now
        baseMs = ms; syncedAt = now; mode = m; running = run; kind = k; shownKind = k
    }
    // রিসেট/থামানো: পপআপ ৫ মিনিট থেকে যাবে (০ সময় দেখিয়ে)
    fun clear() {
        if (kind.isNotEmpty() || running) lastActiveAt = System.currentTimeMillis()
        kind = ""; running = false; baseMs = 0L
    }

    fun currentMs(): Long {
        if (!running) return baseMs
        val el = System.currentTimeMillis() - syncedAt
        return if (mode == "down") maxOf(0L, baseMs - el) else baseMs + el
    }

    fun label(): String = if (shownKind == "screen") "SCREEN" else "STUDY"

    fun format(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}

// টাইমার চলাকালে অ্যাপের বাইরে গেলে (বা অ্যাপের ভেতরের ব্রাউজারে গেলে) ছোট ভাসমান টাইমার দেখায়
// চাপ দিলে ডান দিকে বাড়ে: ১) স্টার্ট/পজ ২) রিসেট ৩) অ্যাপ খোলা
class FloatingTimerService : Service() {
    companion object {
        @Volatile var running = false
        fun canOverlay(c: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)
    }

    private val GOLD = Color.parseColor("#F6C85F")
    private val CYAN = Color.parseColor("#5EE0E8")

    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var pill: LinearLayout? = null
    private var timeTv: TextView? = null
    private var clockTv: TextView? = null
    private val clockFmt = java.text.SimpleDateFormat("h:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka") }
    private var labelTv: TextView? = null
    private var panel: LinearLayout? = null
    private var btnToggle: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var closeTarget: TextView? = null
    private var dismissed = false   // ক্লোজ করা হলে অ্যাপে ফেরা পর্যন্ত লুকানো থাকে (টাইমার চলতেই থাকে)
    private var expanded = false
    private var collapseAt = 0L
    private var lastCatchUp = 0L
    private var lastKind = ""

    private var prevRunning = false

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            // থামানো/রিসেটের ৫ মিনিট পর পপআপ নিজে সরে যায়
            val idleExpired = !TimerState.running && now - TimerState.lastActiveAt > 5 * 60_000L
            if (TimerState.shownKind.isEmpty() || idleExpired) { TimerState.shownKind = ""; stopSelf(); return }
            // নতুন করে টাইমার চালু হলে আগে ✕ দেওয়া পপআপ আবার আসে
            if (TimerState.running && !prevRunning) dismissed = false
            prevRunning = TimerState.running
            // টাইমার চালু থাকলে অ্যাপের ভেতরে বা বাইরে, যেখানেই থাকো পপআপ থাকে — ✕ না দেওয়া পর্যন্ত
            val show = !dismissed && canOverlay(this@FloatingTimerService)
            if (show) {
                ensureBubble()
                refresh()
                if (expanded && collapseAt > 0 && now > collapseAt) setExpanded(false)
            } else removeBubble()
            // অ্যাপের বাইরে টাইমার চললে সময় নিয়মিত অ্যাপের স্টোরেজে সেভ হতে থাকে
            if (TimerState.running && !TimerState.mainVisible && now - lastCatchUp > 1000) {
                lastCatchUp = now
                MainActivity.inst?.evalJs("try{nativeTimerCatchUp()}catch(e){}")
            }
            h.postDelayed(this, 250)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        TimerState.clear(); stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        running = true
        dismissed = false; prevRunning = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("timer_bubble", "চলমান টাইমার", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 8, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "timer_bubble")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏱ টাইমার চলছে")
            .setContentText("চাপ দিয়ে অ্যাপে ফিরে যাও")
            .setOngoing(true).setSilent(true).setContentIntent(open).build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, 21, n, type)
        } catch (e: Exception) { try { startForeground(21, n) } catch (e2: Exception) { } }
        h.removeCallbacks(tick); h.post(tick)
        return START_NOT_STICKY
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun accent() = if (TimerState.shownKind == "screen") CYAN else GOLD

    private fun refresh() {
        val run = TimerState.running
        timeTv?.text = TimerState.format(TimerState.currentMs())
        clockTv?.text = clockFmt.format(java.util.Date())   // বাংলাদেশ স্ট্যান্ডার্ড টাইম
        timeTv?.alpha = if (run) 1f else 0.6f
        val name = TimerState.label()
        labelTv?.text = if (run) "● $name" else "❚❚ $name"
        labelTv?.setTextColor(Color.parseColor("#C9CEFF"))
        clockTv?.setTextColor(accent())
        btnToggle?.text = if (run) "❚❚" else "▶"
        if (lastKind != TimerState.shownKind) {
            lastKind = TimerState.shownKind
            (pill?.background as? GradientDrawable)?.setStroke(dp(1.5f), accent())
        }
    }

    private fun actionBtn(txt: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = txt; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#FFE3A0"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F01A1D52"))
            setStroke(dp(1f), Color.parseColor("#88F6C85F"))
        }
        elevation = dp(6f).toFloat()
        setOnClickListener { collapseAt = System.currentTimeMillis() + 7000; onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f)).apply { marginStart = dp(6f) }
    }

    private fun ensureBubble() {
        if (root != null) return
        try {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

            // ছোট গোল পপআপ: ১) বাংলাদেশ সময় ২) কোনটা চলছে (STUDY/SCREEN) ৩) টাইমার/স্টপওয়াচের সময়
            val pl = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#2E3396"), Color.parseColor("#0F1238"))
                ).apply { shape = GradientDrawable.OVAL; setStroke(dp(1.5f), accent()) }
                elevation = dp(8f).toFloat()
            }
            val ck = TextView(this).apply {
                gravity = Gravity.CENTER; setTextColor(accent())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                includeFontPadding = false; maxLines = 1
            }
            val l = TextView(this).apply {
                gravity = Gravity.CENTER; setTextColor(Color.parseColor("#C9CEFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 6f)
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.08f; includeFontPadding = false; maxLines = 1
            }
            val t = TextView(this).apply {
                gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                includeFontPadding = false; maxLines = 1
            }
            pl.addView(ck, LinearLayout.LayoutParams(-2, -2))
            pl.addView(l, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1f) })
            pl.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1f) })

            val pn = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
            val bt = actionBtn("❚❚") { doToggle() }
            pn.addView(bt)
            pn.addView(actionBtn("↺") { doReset() })
            pn.addView(actionBtn("↗") { setExpanded(false); openApp() })

            r.addView(pl, LinearLayout.LayoutParams(dp(62f), dp(62f)))
            r.addView(pn, LinearLayout.LayoutParams(-2, -2))

            val sp = getSharedPreferences("bubble", Context.MODE_PRIVATE)
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                       else WindowManager.LayoutParams.TYPE_PHONE
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = sp.getInt("x", dp(16f)); y = sp.getInt("y", dp(140f))
            }
            pl.setOnTouchListener(object : View.OnTouchListener {
                var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
                override fun onTouch(v: View, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                            if (Math.abs(dx) > dp(6f) || Math.abs(dy) > dp(6f)) moved = true
                            if (moved) {
                                if (expanded) setExpanded(false)
                                val (sw, sh) = screenSize()
                                val w = maxOf(1, r.width); val hh = maxOf(1, r.height)
                                // পুরো স্ক্রিনের যেকোনো জায়গায় নেওয়া যাবে (কিনারা পর্যন্ত)
                                p.x = (ox + dx).coerceIn(0, maxOf(0, sw - w))
                                p.y = (oy + dy).coerceIn(0, maxOf(0, sh - hh))
                                try { wm?.updateViewLayout(r, p) } catch (x: Exception) { }
                                showCloseTarget(nearClose(p.x + w / 2, p.y + hh / 2))
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val w = maxOf(1, r.width); val hh = maxOf(1, r.height)
                            val onClose = moved && nearClose(p.x + w / 2, p.y + hh / 2)
                            hideCloseTarget()
                            if (onClose) { dismissed = true; removeBubble() }   // টাইমার চলতে থাকে, শুধু পপআপ সরে যায়
                            else if (moved) sp.edit().putInt("x", p.x).putInt("y", p.y).apply()
                            else if (e.action == MotionEvent.ACTION_UP) setExpanded(!expanded)
                        }
                    }
                    return true
                }
            })
            wm?.addView(r, p)
            root = r; pill = pl; timeTv = t; clockTv = ck; labelTv = l; panel = pn; btnToggle = bt; lp = p
            lastKind = ""
            refresh()
        } catch (e: Exception) { removeBubble() }
    }

    // চাপ দিলে ডান দিকে ৩টা বাটন বের হয়
    private fun setExpanded(b: Boolean) {
        expanded = b
        panel?.visibility = if (b) View.VISIBLE else View.GONE
        collapseAt = if (b) System.currentTimeMillis() + 7000 else 0L
        val r = root; val p = lp
        if (r != null && p != null) {
            try {
                r.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val (sw, _) = screenSize()
                if (b && p.x + r.measuredWidth > sw) p.x = maxOf(0, sw - r.measuredWidth)   // ডানে জায়গা না থাকলে একটু বাঁয়ে সরে আসে
                wm?.updateViewLayout(r, p)
            } catch (e: Exception) { }
        }
    }

    private fun doToggle() {
        val k = if (TimerState.kind.isNotEmpty()) TimerState.kind else TimerState.shownKind
        if (k.isEmpty()) return
        TimerState.set(k, !TimerState.running, TimerState.mode, TimerState.currentMs())
        TimerState.ignoreUntil = System.currentTimeMillis() + 700
        MainActivity.inst?.evalJs("try{nativeTimerCmd('toggle','$k')}catch(e){}")
        refresh()
    }

    private fun doReset() {
        val k = if (TimerState.kind.isNotEmpty()) TimerState.kind else TimerState.shownKind
        if (k.isEmpty()) return
        TimerState.ignoreUntil = System.currentTimeMillis() + 700
        TimerState.clear()
        MainActivity.inst?.evalJs("try{nativeTimerCmd('reset','$k')}catch(e){}")
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val m = android.util.DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }

    // ক্লোজ জায়গার কেন্দ্র: স্ক্রিনের নিচের মাঝখানে
    private fun nearClose(cx: Int, cy: Int): Boolean {
        val (sw, sh) = screenSize()
        val tx = sw / 2; val ty = sh - dp(60f) - dp(32f)
        val dx = (cx - tx).toDouble(); val dy = (cy - ty).toDouble()
        return Math.sqrt(dx * dx + dy * dy) < dp(80f)
    }

    private fun showCloseTarget(active: Boolean) {
        try {
            if (closeTarget == null) {
                val t = TextView(this).apply {
                    text = "✕"; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                }
                val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                           else WindowManager.LayoutParams.TYPE_PHONE
                val lp2 = WindowManager.LayoutParams(
                    dp(64f), dp(64f), type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(60f) }
                wm?.addView(t, lp2)
                closeTarget = t
            }
            closeTarget?.let {
                it.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(if (active) "#EEE53935" else "#99000000"))
                    setStroke(dp(2f), Color.WHITE)
                }
                val sc = if (active) 1.3f else 1f
                it.scaleX = sc; it.scaleY = sc
            }
        } catch (e: Exception) { }
    }

    private fun hideCloseTarget() {
        closeTarget?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        closeTarget = null
    }

    private fun removeBubble() {
        hideCloseTarget()
        root?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        root = null; pill = null; timeTv = null; clockTv = null; labelTv = null; panel = null; btnToggle = null
        expanded = false
    }

    // অ্যাপে ফেরা (ভেতরের ব্রাউজার খোলা থাকলে সেটা বন্ধ হয়ে যাবে)
    private fun openApp() {
        // ব্রাউজার খোলা থাকলে ছোট বারে নামিয়ে অ্যাপ দেখানো
        try { MainActivity.inst?.let { m -> m.runOnUiThread { m.browser?.minimize() } } } catch (e: Exception) { }
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacks(tick)
        removeBubble()
        super.onDestroy()
    }
}
