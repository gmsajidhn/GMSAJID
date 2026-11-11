package com.sajid.studytracker

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    companion object { @Volatile var inst: MainActivity? = null }
    private lateinit var web: WebView
    var browser: BrowserLayer? = null
    fun evalJs(js: String) { runOnUiThread { try { web.evaluateJavascript(js, null) } catch (e: Exception) { } } }
    override fun onDestroy() { if (inst === this) inst = null; try { browser?.release() } catch (e: Exception) { }; try { browser?.destroyAll() } catch (e: Exception) { }; super.onDestroy() }
    private var imageCb: ((Uri?) -> Unit)? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = imageCb; imageCb = null; cb?.invoke(uri)
    }
    // প্রোফাইল ছবি / শর্টকাট আইকনের জন্য ছবি বাছাই
    fun pickImage(cb: (Uri?) -> Unit) {
        imageCb = cb
        try { imagePicker.launch("image/*") } catch (e: Exception) { imageCb = null; cb(null) }
    }
    // Lens: ক্যামেরায় ছবি তোলা (পূর্ণ সাইজ, অ্যাপের ক্যাশ ফোল্ডারে)
    private var camCb: ((Uri?) -> Unit)? = null
    private var camUri: Uri? = null
    private val camLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val cb = camCb; camCb = null; cb?.invoke(if (ok) camUri else null)
    }
    fun takePhoto(cb: (Uri?) -> Unit) {
        requestPerms(arrayOf(Manifest.permission.CAMERA), true) { granted ->
            if (!granted) { Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show(); cb(null); return@requestPerms }
            try {
                val dir = java.io.File(cacheDir, "lens").apply { mkdirs() }
                val f = java.io.File.createTempFile("shot", ".jpg", dir)
                val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                camUri = u; camCb = cb; camLauncher.launch(u)
            } catch (e: Exception) { camCb = null; cb(null) }
        }
    }
    // এক্সটেনশন ফাইল বাছাই
    private var docCb: ((Uri?, String) -> Unit)? = null
    private val docPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = docCb; docCb = null
        var n = ""
        if (uri != null) try { contentResolver.query(uri, null, null, null, null)?.use { c -> val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && ix >= 0) n = c.getString(ix) ?: "" } } catch (e: Exception) { }
        cb?.invoke(uri, n)
    }
    fun pickDoc(cb: (Uri?, String) -> Unit) { docCb = cb; try { docPicker.launch(arrayOf("*/*")) } catch (e: Exception) { docCb = null; cb(null, "") } }
    // সেভ করা পাসওয়ার্ড দেখার আগে ফোনের লক (পিন/প্যাটার্ন/ফিঙ্গারপ্রিন্ট) যাচাই
    private var lockCb: ((Boolean) -> Unit)? = null
    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = lockCb; lockCb = null; cb?.invoke(r.resultCode == android.app.Activity.RESULT_OK)
    }
    fun confirmDeviceLock(title: String, cb: (Boolean) -> Unit) {
        try {
            val km = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (!km.isDeviceSecure) { cb(true); return }
            val i = km.createConfirmDeviceCredentialIntent(title, null)
            if (i == null) { cb(true); return }
            lockCb = cb; lockLauncher.launch(i)
        } catch (e: Exception) { lockCb = null; cb(false) }
    }
    // অন্য অ্যাপ থেকে লিংক/ফাইল খুললে এই ব্রাউজারে
    private fun handleIntent(i: Intent?) {
        i ?: return
        val u = i.data
        if (i.action == Intent.ACTION_VIEW && u != null) web.post { browser?.open(u.toString()) }
        else if (i.action == Intent.ACTION_WEB_SEARCH) i.getStringExtra("query")?.let { q -> web.post { browser?.open("https://www.google.com/search?q=" + Uri.encode(q)) } }
        else if (i.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.let { t -> web.post { browser?.open(if (t.startsWith("http")) t else "https://www.google.com/search?q=" + Uri.encode(t)) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    override fun onPause() { try { browser?.persist() } catch (e: Exception) { }; try { web.evaluateJavascript("try{flushAllTimersToStorage()}catch(e){}", null) } catch (e: Exception) { }; super.onPause() }
    // ব্রাউজারের ফাইল আপলোড
    fun pickFile(cb: ValueCallback<Array<Uri>>, intent: Intent): Boolean {
        fileCb?.onReceiveValue(null); fileCb = cb
        return try { chooser.launch(intent); true } catch (e: Exception) { fileCb = null; false }
    }
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCb = null
    }
    private var pendingKey = "default"
    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val key = pendingKey
            // ফাইলটা অ্যাপের নিজের ফোল্ডারে কপি করে রাখা হয়, তাই পরে মূল ফাইল সরালেও রিংটোন বাজবে
            Thread {
                var ok = false
                var name = ""
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && ix >= 0) name = c.getString(ix) ?: ""
                    }
                    val tmp = java.io.File(filesDir, "ring_tmp")
                    contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    val dst = Scheduler.ringFile(this, key)
                    ok = tmp.length() > 0 && (!dst.exists() || dst.delete()) && tmp.renameTo(dst)
                } catch (e: Exception) { }
                runOnUiThread {
                    Toast.makeText(this, if (ok) "✓ রিংটোন সেট হয়েছে" else "রিংটোন সেভ করা যায়নি", Toast.LENGTH_SHORT).show()
                    val js = "window.onRingtonePicked && window.onRingtonePicked(" +
                        org.json.JSONObject.quote(key) + "," + ok + "," + org.json.JSONObject.quote(name) + ")"
                    web.evaluateJavascript(js, null)
                }
            }.start()
        }
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // সাইট চাইলে লোকেশন/ক্যামেরা/মাইক পারমিশন (all=true হলে সবগুলো লাগবে, নইলে একটা হলেই চলবে)
    private var permCb: ((Boolean) -> Unit)? = null
    private var permAll = true
    private var permList: Array<String> = emptyArray()
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val cb = permCb; permCb = null
        val ok = if (permAll) permList.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
                 else permList.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        cb?.invoke(ok)
    }
    fun requestPerms(perms: Array<String>, all: Boolean, cb: (Boolean) -> Unit) {
        fun granted(x: String) = ContextCompat.checkSelfPermission(this, x) == PackageManager.PERMISSION_GRANTED
        if (if (all) perms.all { granted(it) } else perms.any { granted(it) }) { cb(true); return }
        permCb = cb; permAll = all; permList = perms
        try { permLauncher.launch(perms.filter { !granted(it) }.toTypedArray()) } catch (e: Exception) { permCb = null; cb(false) }
    }

    inner class Bridge {
        @JavascriptInterface fun syncAlarms(json: String) { Scheduler.saveAndScheduleAll(this@MainActivity, json) }
        // key = "default" (সেটিংয়ের ডিফল্ট রিংটোন) অথবা কোনো রুটিনের নিজস্ব রিংটোন id
        @JavascriptInterface fun pickRingtone(key: String) { pendingKey = key; runOnUiThread { pickAudio.launch(arrayOf("audio/*")) } }
        @JavascriptInterface fun hasRingtone(key: String): Boolean = Scheduler.hasRing(this@MainActivity, key)
        @JavascriptInterface fun deleteRingtone(key: String) { try { Scheduler.ringFile(this@MainActivity, key).delete() } catch (e: Exception) { } }
        @JavascriptInterface fun setDefaultVibrate(on: Boolean) { Scheduler.setDefaultVibrate(this@MainActivity, on) }
        // অ্যাপ খোলা অবস্থায় রুটিনের সময় হলে পেজ নিজেই এটা ডাকে — যাতে রিংটোন + পপআপ নিশ্চিতভাবে আসে
        // (সার্ভিস আগে থেকে বাজতে থাকলে এটা দ্বিতীয়বার বাজায় না)
        @JavascriptInterface fun alarmNow(titles: String) {
            titles.split("\n").filter { it.isNotBlank() }.forEach { t ->
                val (ring, vib) = Scheduler.resolve(this@MainActivity, "snooze:x", t)
                try {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, AlarmService::class.java).putExtra("title", t)
                            .putExtra("ring", ring ?: "").putExtra("vib", vib)
                    )
                } catch (e: Exception) { Scheduler.note(this@MainActivity, "lastError", "alarmNow: " + e.message) }
            }
        }
        @JavascriptInterface fun overlayGranted(): Boolean = FloatingTimerService.canOverlay(this@MainActivity)
        @JavascriptInterface fun openOverlaySettings() { runOnUiThread { openOverlay() } }
        // চলমান টাইমারের অবস্থা: kind = "study" / "todo" / "screen" / "" (কিছু নেই), running = চলছে কি না, mode = "down" / "up"
        @JavascriptInterface fun timerInfo(kind: String, running: Boolean, mode: String, ms: Long) {
            if (System.currentTimeMillis() < TimerState.ignoreUntil) return
            if (kind.isEmpty()) { TimerState.clear(); return }
            TimerState.set(kind, running, mode, ms)
            if (!running || FloatingTimerService.running) return
            runOnUiThread {
                if (!FloatingTimerService.canOverlay(this@MainActivity) && tries("overlay") < 3) {
                    bump("overlay")
                    Toast.makeText(this@MainActivity, "\"অন্য অ্যাপের উপরে দেখানো\" Allow করো, নইলে ভাসমান টাইমার দেখা যাবে না", Toast.LENGTH_LONG).show()
                    openOverlay()
                }
                try { ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, FloatingTimerService::class.java)) }
                catch (e: Exception) { }
            }
        }
        @JavascriptInterface fun openBrowser() { runOnUiThread { browser?.openHome() } }
        @JavascriptInterface fun stopAlarm() {
            try { startService(Intent(this@MainActivity, AlarmService::class.java).setAction("STOP")) } catch (e: Exception) { }
        }
        @JavascriptInterface fun status(): String = Scheduler.status(this@MainActivity)
        // অ্যালার্ম সেটিং: Ring duration (মিনিট), Snooze duration (মিনিট), Number of snoozes
        @JavascriptInterface fun alarmCfg(): String = "" + Scheduler.ringMin(this@MainActivity) + "," + Scheduler.snoozeMin(this@MainActivity) + "," + Scheduler.snoozeMax(this@MainActivity)
        @JavascriptInterface fun setAlarmCfg(ring: Int, sn: Int, cnt: Int) { Scheduler.setAlarmCfg(this@MainActivity, ring, sn, cnt) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = this
        // অ্যাপ খোলা থাকলে স্ক্রিন সবসময় অন থাকবে (স্ক্রিন টাইম-আউট হবে না)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        val frame = android.widget.FrameLayout(this)
        frame.addView(web, android.widget.FrameLayout.LayoutParams(-1, -1))
        browser = BrowserLayer(this)
        frame.addView(browser, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false; allowFileAccess = false
            setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(r.url)
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.host == "appassets.androidplatform.net") return false
                openInApp(r.url.toString())
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val tmp = WebView(this@MainActivity)
                var done = false
                fun grab(u: String?) {
                    if (done || u.isNullOrBlank() || u == "about:blank") return
                    done = true
                    openInApp(u)
                    Handler(Looper.getMainLooper()).post { try { tmp.destroy() } catch (e: Exception) { } }
                }
                tmp.settings.javaScriptEnabled = true
                tmp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(w: WebView, r: WebResourceRequest): Boolean { grab(r.url.toString()); return true }
                    override fun shouldInterceptRequest(w: WebView, r: WebResourceRequest): WebResourceResponse? {
                        if (r.isForMainFrame) { val u = r.url.toString(); Handler(Looper.getMainLooper()).post { grab(u) } }
                        return null
                    }
                    override fun onLoadResource(w: WebView, url: String?) { grab(url) }
                    override fun onPageStarted(w: WebView, url: String?, f: android.graphics.Bitmap?) { grab(url) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                resultMsg.sendToTarget()
                return true
            }
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                return try { chooser.launch(p.createIntent()); true } catch (e: Exception) { fileCb = null; false }
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        if (b == null) handleIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browser?.handleBack() == true) return
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    // http/https লিংক অ্যাপের ভেতরের ব্রাউজারে খোলে; intent:// / market:// / tg:// ইত্যাদি হলে সেই অ্যাপে নিয়ে যায়
    private fun openInApp(url: String) {
        val u = url.trim()
        val sch = (Uri.parse(u).scheme ?: "").lowercase()
        if (sch == "http" || sch == "https") browser?.open(u)
        else if (sch in listOf("about", "javascript", "data", "blob", "file", "content")) return
        else ExternalApp.open(this, u) { fb -> browser?.open(fb) }
    }

    // প্রতিবার অ্যাপ খুললে যে পারমিশন বাকি আছে সেটা চাওয়া হয় (সর্বোচ্চ ৩ বার করে)
    private fun tries(k: String) = getSharedPreferences("perm", MODE_PRIVATE).getInt(k, 0)
    private fun bump(k: String) = getSharedPreferences("perm", MODE_PRIVATE).edit().putInt(k, tries(k) + 1).apply()

    // "অন্য অ্যাপের উপরে দেখানো" সেটিং খোলা; না খুললে সাধারণ লিস্ট, তাও না হলে অ্যাপের ডিটেইলস পেজ
    private fun openOverlay() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return } catch (e: Exception) { }
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); return } catch (e: Exception) { }
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e: Exception) { }
    }

    // অ্যালার্ম বন্ধ হলে অ্যাপের ভেতরের রিমাইন্ডার পপআপও বন্ধ করা
    private val alarmStopped = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            try { web.evaluateJavascript("(function(){var m=document.getElementById('alarmModal');if(m)m.classList.remove('active');})()", null) } catch (e: Exception) { }
        }
    }
    override fun onStart() {
        super.onStart(); TimerState.mainVisible = true; TimerState.visibleCount++
        val f = android.content.IntentFilter("com.sajid.studytracker.ALARM_STOPPED")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(alarmStopped, f, RECEIVER_NOT_EXPORTED) else registerReceiver(alarmStopped, f)
    }
    override fun onStop() {
        TimerState.mainVisible = false; TimerState.visibleCount--
        try { unregisterReceiver(alarmStopped) } catch (e: Exception) { }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 1) নোটিফিকেশন — এটা বন্ধ থাকলে অ্যালার্ম/পপআপ কিছুই দেখা যায় না
        if (!nm.areNotificationsEnabled() && tries("notif") < 3) {
            bump("notif")
            if (Build.VERSION.SDK_INT >= 33 && tries("notif") <= 1 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "নোটিফিকেশন চালু করো, নইলে অ্যালার্ম দেখা যাবে না", Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (e: Exception) { }
            }
            return
        }

        // 2) ফুল-স্ক্রিন পপআপ (Android 14+)
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() && tries("fsi") < 3) {
            bump("fsi")
            Toast.makeText(this, "\"ফুল স্ক্রিন নোটিফিকেশন\" Allow করো, নইলে পপআপ আসবে না", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }

        // 3) ব্যাটারি
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName) && tries("batt") < 2) {
            bump("batt")
            Toast.makeText(this, "ব্যাটারি \"Allow/Unrestricted\" দাও, নইলে অ্যালার্ম দেরিতে বাজতে পারে", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
        }
    }
}
