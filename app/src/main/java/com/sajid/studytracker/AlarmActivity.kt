package com.sajid.studytracker

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.view.ContextThemeWrapper
import android.view.Window
import java.util.Calendar
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

class AlarmActivity : Activity() {
    private lateinit var names: LinearLayout
    private var currentText = ""
    private var pickDialog: Dialog? = null
    private val stopped = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { finish() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(fill: Int, stroke: Int = 0, r: Int = 40) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#0A0E27")
        window.navigationBarColor = Color.parseColor("#1A0F3C")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0A0E27"), Color.parseColor("#1B1050"), Color.parseColor("#3A1466"))
            )
            setPadding(dp(28), dp(48), dp(28), dp(40))
        }

        root.addView(TextView(this).apply {
            text = "ROUTINE REMINDER"; textSize = 12f; letterSpacing = 0.3f
            setTextColor(Color.parseColor("#B9A7FF")); gravity = Gravity.CENTER
            background = pill(Color.parseColor("#26FFFFFF"), Color.parseColor("#33FFFFFF"), 20)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }, LinearLayout.LayoutParams(-2, -2))

        root.addView(TextClock(this).apply {
            format12Hour = "hh:mm"; format24Hour = "HH:mm"
            textSize = 76f; typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(36) })

        root.addView(TextClock(this).apply {
            format12Hour = "EEEE, dd MMMM"; format24Hour = "EEEE, dd MMMM"
            textSize = 15f; setTextColor(Color.parseColor("#C9C2E8")); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2))

        // স্পন্দিত রিং + আইকন
        val bell = FrameLayout(this)
        val ring = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33FF6B81"))
            }
        }
        val core = TextView(this).apply {
            text = "⏰"; textSize = 44f; gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FF6B81"), Color.parseColor("#7C4DFF"))
            ).apply { shape = GradientDrawable.OVAL }
        }
        bell.addView(ring, FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER))
        bell.addView(core, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
        root.addView(bell, LinearLayout.LayoutParams(dp(170), dp(170)).apply { topMargin = dp(28) })
        for (pr in listOf("scaleX", "scaleY")) ObjectAnimator.ofFloat(ring, pr, 0.7f, 1.15f).apply {
            duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        ObjectAnimator.ofFloat(ring, "alpha", 1f, 0.35f).apply {
            duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }

        // রুটিনের নাম কার্ড
        names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = pill(Color.parseColor("#1FFFFFFF"), Color.parseColor("#33FFFFFF"), 22)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        root.addView(names, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        fun btn(label: String, fill: Int, stroke: Int, action: String) = Button(this).apply {
            text = label; textSize = 17f; setTextColor(Color.WHITE); isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = pill(fill, stroke, 32); stateListAnimator = null
            setOnClickListener {
                startService(Intent(this@AlarmActivity, AlarmService::class.java).setAction(action))
                finish()
            }
        }
        root.addView(Button(this).apply {
            text = "Snooze"; textSize = 17f; setTextColor(Color.WHITE); isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = pill(Color.parseColor("#26FFFFFF"), Color.parseColor("#55FFFFFF"), 32); stateListAnimator = null
            setOnClickListener { showSnoozePicker() }
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(14) })
        root.addView(btn("Stop", Color.parseColor("#FF4D6D"), 0, "STOP"), LinearLayout.LayoutParams(-1, dp(62)))

        setContentView(root)
        show(intent.getStringExtra("text") ?: "")
        if (intent.getBooleanExtra("pick", false)) showSnoozePicker()
        val f = IntentFilter("com.sajid.studytracker.ALARM_STOPPED")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stopped, f, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(stopped, f)
    }

    private fun showSnoozePicker() {
        if (pickDialog?.isShowing == true) return
        // সেটিংয়ে ঠিক করা সর্বোচ্চ স্নুজ-সংখ্যা শেষ হলে আর স্নুজ নয়
        val canSnooze = currentText.split("\n").filter { it.isNotBlank() }.any { Scheduler.snoozeCount(this, it) < Scheduler.snoozeMax(this) }
        if (!canSnooze) {
            Toast.makeText(this, "Snooze limit reached (" + Scheduler.snoozeMax(this) + ")", Toast.LENGTH_LONG).show()
            return
        }
        val sm = Scheduler.snoozeMin(this)
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = pill(Color.parseColor("#1B1050"), Color.parseColor("#55FFFFFF"), 24)
            setPadding(dp(18), dp(20), dp(18), dp(16))
        }
        box.addView(TextView(this).apply {
            text = "কখন আবার বাজবে?"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })

        // Holo থিমে TimePicker স্পিনার (ঘুরিয়ে সময় বাছা) মোডে থাকে
        val tp = TimePicker(ContextThemeWrapper(this, android.R.style.Theme_Holo)).apply { setIs24HourView(false) }
        fun setFromNow(min: Int) {
            val c = Calendar.getInstance().apply { add(Calendar.MINUTE, min) }
            tp.hour = c.get(Calendar.HOUR_OF_DAY); tp.minute = c.get(Calendar.MINUTE)
        }
        setFromNow(sm)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("+$sm মি" to sm, "+১০ মি" to 10, "+৩০ মি" to 30, "+১ ঘণ্টা" to 60).forEach { (label, m) ->
            chips.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = pill(Color.parseColor("#26FFFFFF"), Color.parseColor("#55FFFFFF"), 18)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { setFromNow(m) }
            }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(14), dp(4), dp(6)) })
        }
        box.addView(chips)
        box.addView(tp, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Cancel"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            background = pill(Color.parseColor("#26FFFFFF"), Color.parseColor("#55FFFFFF"), 28); stateListAnimator = null
            setOnClickListener { d.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) })
        row.addView(Button(this).apply {
            text = "Snooze"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            background = pill(Color.parseColor("#7C4DFF"), 0, 28); stateListAnimator = null
            setOnClickListener {
                val now = System.currentTimeMillis()
                val c = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, tp.hour); set(Calendar.MINUTE, tp.minute)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1) // বেছে নেওয়া সময় পেরিয়ে গেলে পরের দিন
                // স্নুজ একদিনের জন্য — রাত ৩টার পরের সময় রাখা যাবে না
                if (c.timeInMillis > Scheduler.nextReset(now)) {
                    Toast.makeText(this@AlarmActivity, "স্নুজ শুধু রাত ৩টা পর্যন্ত রাখা যাবে (একদিনের জন্য)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                startService(
                    Intent(this@AlarmActivity, AlarmService::class.java).setAction("SNOOZE")
                        .putExtra("at", c.timeInMillis).putExtra("titles", currentText)
                )
                Toast.makeText(
                    this@AlarmActivity,
                    "⏰ " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(c.time) + " এ আবার বাজবে",
                    Toast.LENGTH_LONG
                ).show()
                d.dismiss(); finish()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(8) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        d.setContentView(box)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        pickDialog = d
        d.show()
    }

    private fun show(text: String) {
        currentText = text
        names.removeAllViews()
        text.split("\n").filter { it.isNotBlank() }.forEach {
            names.addView(TextView(this).apply {
                this.text = it; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(4), 0, dp(4))
            })
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i); show(i.getStringExtra("text") ?: "")
        if (i.getBooleanExtra("pick", false)) showSnoozePicker()
    }

    override fun onDestroy() {
        try { pickDialog?.dismiss() } catch (e: Exception) { }
        try { unregisterReceiver(stopped) } catch (e: Exception) { }
        super.onDestroy()
    }
}
