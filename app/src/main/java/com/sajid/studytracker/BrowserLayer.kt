package com.sajid.studytracker

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.widget.doAfterTextChanged
import org.json.JSONArray
import org.json.JSONObject

// নিচের বারের "^" তীর বাটন
private class ChevView(c: Context) : View(c) {
    var col = Color.WHITE
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    override fun onDraw(cv: android.graphics.Canvas) {
        val d = resources.displayMetrics.density
        p.strokeWidth = 2.5f * d; p.color = col
        val cx = width / 2f; val cy = height / 2f; val w = 9f * d; val h = 5f * d
        cv.drawPath(android.graphics.Path().apply { moveTo(cx - w, cy + h); lineTo(cx, cy - h); lineTo(cx + w, cy + h) }, p)
    }
}

// ===== আইকন (লেখার বদলে): Canvas দিয়ে আঁকা, কোনো ছবি/ফন্ট লাগে না =====
private class IconView(c: Context, private val kind: String) : View(c) {
    var col = Color.WHITE
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    override fun onMeasure(ws: Int, hs: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(resolveSize((44 * d).toInt(), ws), resolveSize((40 * d).toInt(), hs))
    }
    override fun onDraw(cv: android.graphics.Canvas) {
        val d = resources.displayMetrics.density
        p.color = col; p.strokeWidth = 2.2f * d; p.style = android.graphics.Paint.Style.STROKE
        val cx = width / 2f; val cy = height / 2f; val s = 8.5f * d
        val path = android.graphics.Path()
        when (kind) {
            "back" -> {
                cv.drawLine(cx + s, cy, cx - s, cy, p)
                path.moveTo(cx - s * 0.1f, cy - s * 0.9f); path.lineTo(cx - s, cy); path.lineTo(cx - s * 0.1f, cy + s * 0.9f)
                cv.drawPath(path, p)
            }
            "go" -> {
                cv.drawLine(cx - s, cy, cx + s, cy, p)
                path.moveTo(cx + s * 0.1f, cy - s * 0.9f); path.lineTo(cx + s, cy); path.lineTo(cx + s * 0.1f, cy + s * 0.9f)
                cv.drawPath(path, p)
            }
            "copy" -> {
                val a = s * 0.75f
                cv.drawRoundRect(android.graphics.RectF(cx - a * 0.2f, cy - a * 1.0f, cx + a * 1.0f, cy + a * 0.55f), 3f * d, 3f * d, p)
                cv.drawRoundRect(android.graphics.RectF(cx - a * 1.0f, cy - a * 0.55f, cx + a * 0.2f, cy + a * 1.0f), 3f * d, 3f * d, p)
            }
            "search" -> {
                val r = s * 0.62f
                cv.drawCircle(cx - s * 0.2f, cy - s * 0.2f, r, p)
                cv.drawLine(cx - s * 0.2f + r * 0.72f, cy - s * 0.2f + r * 0.72f, cx + s * 0.85f, cy + s * 0.85f, p)
            }
            "forward" -> {
                path.moveTo(cx - s * 0.4f, cy - s); path.lineTo(cx + s * 0.6f, cy); path.lineTo(cx - s * 0.4f, cy + s)
                cv.drawPath(path, p)
            }
            "close" -> {
                cv.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, p)
                cv.drawLine(cx + s * 0.8f, cy - s * 0.8f, cx - s * 0.8f, cy + s * 0.8f, p)
            }
            "plus" -> {
                cv.drawLine(cx - s, cy, cx + s, cy, p); cv.drawLine(cx, cy - s, cx, cy + s, p)
            }
            "menu" -> {
                p.style = android.graphics.Paint.Style.FILL
                cv.drawCircle(cx, cy - s * 0.85f, 2.2f * d, p); cv.drawCircle(cx, cy, 2.2f * d, p); cv.drawCircle(cx, cy + s * 0.85f, 2.2f * d, p)
            }
            "home" -> {
                path.moveTo(cx - s, cy - s * 0.1f); path.lineTo(cx, cy - s); path.lineTo(cx + s, cy - s * 0.1f)
                path.moveTo(cx - s * 0.7f, cy - s * 0.25f); path.lineTo(cx - s * 0.7f, cy + s); path.lineTo(cx + s * 0.7f, cy + s); path.lineTo(cx + s * 0.7f, cy - s * 0.25f)
                cv.drawPath(path, p)
            }
            "bookmark", "bookmarkOn" -> {
                if (kind == "bookmarkOn") p.style = android.graphics.Paint.Style.FILL_AND_STROKE
                path.moveTo(cx - s * 0.65f, cy - s); path.lineTo(cx + s * 0.65f, cy - s); path.lineTo(cx + s * 0.65f, cy + s)
                path.lineTo(cx, cy + s * 0.45f); path.lineTo(cx - s * 0.65f, cy + s); path.close()
                cv.drawPath(path, p)
            }
            "reload" -> {
                val r = s * 0.9f
                cv.drawArc(android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), 40f, 280f, false, p)
                val a = Math.toRadians(320.0)
                val ex = cx + (r * Math.cos(a)).toFloat(); val ey = cy + (r * Math.sin(a)).toFloat()
                val tx = (-Math.sin(a)).toFloat(); val ty = Math.cos(a).toFloat()
                val nx = -ty; val ny = tx
                p.style = android.graphics.Paint.Style.FILL
                val tri = android.graphics.Path()
                tri.moveTo(ex + tx * r * 0.55f, ey + ty * r * 0.55f)
                tri.lineTo(ex + nx * r * 0.5f, ey + ny * r * 0.5f)
                tri.lineTo(ex - nx * r * 0.5f, ey - ny * r * 0.5f)
                tri.close()
                cv.drawPath(tri, p)
            }
        }
    }
}

// নিচের দিকে টেনে ধরলে ব্রাউজার ছোট হয়ে নিচে নেমে যায়
private class DragBar(c: Context) : LinearLayout(c) {
    var onDrag: ((Float) -> Unit)? = null
    var onRelease: ((Float) -> Unit)? = null
    private var sy = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sy = e.rawY; dragging = false }
            MotionEvent.ACTION_MOVE -> if (!dragging && e.rawY - sy > slop * 2) { dragging = true; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sy = e.rawY }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && e.rawY - sy > slop) dragging = true
                if (dragging) onDrag?.invoke(maxOf(0f, e.rawY - sy))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onRelease?.invoke(e.rawY - sy)
                dragging = false
            }
        }
        return true
    }
}

// অ্যাপের ভেতরের ক্রোম-স্টাইল ব্রাউজার: হোম পেজ, একাধিক ট্যাব, ইনকগনিটো, প্রোফাইল, বুকমার্ক, ইতিহাস, সেটিং;
// নিচে টেনে ছোট করে রাখা যায় (মিনি বার), চাপ দিলে আবার পুরো স্ক্রিন
class BrowserLayer(private val act: MainActivity) : FrameLayout(act) {

    class Tab(val web: WebView, val profile: String, val incognito: Boolean) {
        var title = ""
        var url = ""
        var desktop = false
        var fromNtp = false
        var pending: String? = null
        var offlineOf: String? = null
        var ntp = false     // true = ক্রোমের মতো নতুন-ট্যাব হোম পেজ দেখাচ্ছে
        var touchAt = 0L    // শেষবার কখন স্ক্রিনে ছোঁয়া হয়েছে (নিজে নিজে অ্যাপ খোলা ঠেকাতে)
        var dlJs = ""
        var icon: Bitmap? = null    // সাইটের ফেভিকন (নিচের বারে দেখায়)
        var thumb: Bitmap? = null   // ট্যাবের ছোট প্রিভিউ ছবি (ট্যাব লিস্টে দেখায়)
        var transFrom: String? = null   // Google Translate চালু থাকলে মূল পেজের লিংক
    }

    private class Prof(val id: String, var name: String, var color: Int)
    private class Sc(val id: String, var name: String, var url: String)

    private val palette = intArrayOf(
        Color.parseColor("#1A73E8"), Color.parseColor("#E8710A"), Color.parseColor("#188038"),
        Color.parseColor("#A142F4"), Color.parseColor("#D93025"), Color.parseColor("#00897B")
    )
    private val profs = mutableListOf<Prof>()
    private var curProf = "p1"
    private val tabs = mutableListOf<Tab>()
    private var cur = -1
    private var mini = false
    private var warnedProfile = false
    private var mobileUa = ""
    private val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val hp = act.getSharedPreferences("browser", Context.MODE_PRIVATE)
    // ===== থিম: Dark (ডিফল্ট) / Light — Light এ সব রঙের উজ্জ্বলতা উল্টে যায় =====
    private val light: Boolean get() = when (hp.getString("theme", "dark")) {
        "light" -> true
        "system" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    private val dlgStyle: Int get() = if (light) R.style.PremiumDialogLight else R.style.PremiumDialog
    private val ink: Int get() = if (light) Color.parseColor("#14183A") else Color.WHITE
    private fun pc(hex: String): Int {
        val c = Color.parseColor(hex)
        if (!light) return c
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(c, hsl)
        hsl[2] = 1f - hsl[2]
        return androidx.core.graphics.ColorUtils.setAlphaComponent(androidx.core.graphics.ColorUtils.HSLToColor(hsl), Color.alpha(c))
    }
    private val muteJs = "(function(){function m(){document.querySelectorAll('video,audio').forEach(function(e){e.muted=true})}m();if(!window.__mo){window.__mo=new MutationObserver(m);window.__mo.observe(document.documentElement,{childList:true,subtree:true});setInterval(m,1500)}})()"
    // যেকোনো সাইটের লেখা সিলেক্ট/কপি করা যাবে (সাইট ব্লক করলেও)
    private val selectJs = "(function(){if(window.__selOn)return;window.__selOn=1;var s=document.createElement('style');s.textContent='*,*::before,*::after{-webkit-user-select:text!important;user-select:text!important;-webkit-touch-callout:default!important}';(document.head||document.documentElement).appendChild(s);['copy','cut','selectstart','contextmenu','dragstart','mousedown'].forEach(function(t){document.addEventListener(t,function(e){if(t=='mousedown')return;e.stopImmediatePropagation()},true)});try{document.oncopy=document.oncut=document.onselectstart=document.oncontextmenu=document.ondragstart=null;if(document.body){document.body.oncopy=document.body.oncut=document.body.onselectstart=document.body.oncontextmenu=null}}catch(e){}})()"
    private val darkJs = "(function(){if(document.getElementById('__dk'))return;var s=document.createElement('style');s.id='__dk';s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff}img,video,picture,canvas{filter:invert(1) hue-rotate(180deg)!important}';(document.head||document.documentElement).appendChild(s)})()"
    private val desktopJs = "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}m.setAttribute('content','width=1280,initial-scale=0.3,minimum-scale=0.1,maximum-scale=10,user-scalable=yes');})()"
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var menuPopup: PopupWindow? = null
    private val exts = ExtManager(act)
    private val pwds = PwdStore(act)
    private fun ex(p: String = curProfileId()): ExtManager { exts.prof = p; return exts }
    private var adOn: Boolean
        get() = hp.getBoolean("adblock", true)
        set(v) { hp.edit().putBoolean("adblock", v).apply() }
    // অফলাইন ক্যাশ ডিফল্টে বন্ধ। শুধু যে সাইটগুলোতে চালু করা হবে (সর্বোচ্চ ৩টা) সেগুলোর ক্যাশ জমবে
    private val maxCacheSites = 3
    private fun siteKey(host: String?) = (host ?: "").lowercase().removePrefix("www.")
    private fun cacheHosts(): MutableList<String> = try {
        val a = JSONArray(hp.getString("cacheHosts", "[]")); (0 until a.length()).map { a.getString(it) }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
    private fun saveCacheHosts(l: List<String>) { hp.edit().putString("cacheHosts", JSONArray(l).toString()).apply() }
    private fun cacheOnFor(host: String?): Boolean { val h = siteKey(host); return h.isNotEmpty() && cacheHosts().contains(h) }
    private val noCacheHosts = listOf("youtube.com", "googlevideo.com", "accounts.google.com", "facebook.com", "fbcdn.net", "instagram.com",
        "whatsapp.com", "tiktok.com", "netflix.com", "play.google.com")
    private val skipReqHeaders = setOf("accept-encoding", "range", "host", "connection", "content-length", "if-none-match", "if-modified-since", "if-range", "cookie")
    private val skipRespHeaders = setOf("content-encoding", "content-length", "transfer-encoding", "set-cookie", "connection", "keep-alive", "content-type")
    private var pwdAsking = false
    private val topAvatar = LinearLayout(act).apply { gravity = Gravity.CENTER }
    private var clearOverlay: View? = null
    private var pdfOverlay: View? = null
    private var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
    private var pdfFd: android.os.ParcelFileDescriptor? = null
    private val dlIds = HashMap<Long, String>()
    private val dlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val id = i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            val name = dlIds.remove(id) ?: return
            try {
                val dm = act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                var ok = false
                dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
                    if (cur.moveToFirst()) ok = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (!ok) { toast("Download failed: $name"); return }
                var mm = dm.getMimeTypeForDownloadedFile(id) ?: ""
                if (mm.isBlank() || mm.contains("octet", true)) mm = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: mm
                downloadedDialog(name, dm.getUriForDownloadedFile(id), mm)
            } catch (e: Exception) { toast("Downloaded: $name") }
        }
    }

    private fun AlertDialog.Builder.pshow(): AlertDialog { val d = create(); d.show(); premiumize(d.window!!.decorView); return d }
    private fun AlertDialog.pshow(): AlertDialog { show(); premiumize(window!!.decorView); return this }
    private fun premiumize(v: View) {
        when (v) {
            is EditText -> { v.setTextColor(ink); v.setHintTextColor(pc("#8890B0")); v.backgroundTintList = android.content.res.ColorStateList.valueOf(pc("#F6C85F")) }
            is Button -> if (v.id != android.R.id.button1 && v.id != android.R.id.button2 && v.id != android.R.id.button3) {
                v.setTextColor(pc("#F6C85F")); v.isAllCaps = false
                v.background = GradientDrawable().apply { setColor(pc("#2A2F7A")); cornerRadius = dp(12).toFloat(); setStroke(dp(1), pc("#66F6C85F")) }
            }
            is TextView -> if (!light && Color.luminance(v.currentTextColor) < 0.45f) v.setTextColor(pc("#F3F4FF"))
            else -> {}
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) premiumize(v.getChildAt(i))
    }
    private fun ptabs() = tabs.filter { it.profile == curProf }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(act, m, Toast.LENGTH_SHORT).show()

    // পুল-টু-রিফ্রেশের ঘুরন্ত রিলোড আইকন
    private val ptr = IconView(act, "reload").apply {
        visibility = View.GONE; elevation = dp(6).toFloat()
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pc("#2A2F7A")); setStroke(dp(1), pc("#88F6C85F")) }
    }
    private fun pullShow(dy: Float) {
        val f = (dy / dp(150)).coerceIn(0f, 1f)
        ptr.animate().cancel()
        ptr.col = Color.WHITE
        if (ptr.parent == null) content.addView(ptr, FrameLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL })
        ptr.visibility = View.VISIBLE
        ptr.translationY = dp(6) + f * dp(60)
        ptr.rotation = f * 360f
        ptr.alpha = 0.3f + 0.7f * f
    }
    private fun pullHide() {
        ptr.animate().cancel()
        ptr.animate().alpha(0f).setDuration(150).withEndAction { ptr.visibility = View.GONE; (ptr.parent as? ViewGroup)?.removeView(ptr) }.start()
    }

    private val panel = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pc("#0B0F24")) }
    private val content = FrameLayout(act)
    private val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
    private val urlText = EditText(act)
    private val tabCount = TextView(act)
    private val miniBar = LinearLayout(act)
    private val miniTitle = TextView(act)
    private val miniCount = TextView(act)
    private val ntpBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val ntpView = ScrollView(act).apply { addView(ntpBox, FrameLayout.LayoutParams(-1, -2)); isFillViewport = true }
    private val findInput = EditText(act)
    private val histView = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pc("#111511")); visibility = View.GONE; isClickable = true }
    private val histList = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val histSearch = EditText(act)
    private val findBar = LinearLayout(act)
    // নিচের বার: ^ (ট্যাব লিস্ট) | সব ট্যাব পাশাপাশি | + (নতুন ট্যাব)
    private val bottomBar = LinearLayout(act)
    private val chipsRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val chipsScroll = HorizontalScrollView(act)
    private var tabsOverlay: View? = null
    private var barSig = ""
    private var barCur = -2

    private fun makeDragBar(): DragBar = DragBar(act).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        onDrag = { dy -> panel.translationY = dy }
        onRelease = { dy ->
            if (dy > dp(110)) minimize()
            else panel.animate().translationY(0f).setDuration(150).start()
        }
    }
    private val topBar = makeDragBar()

    private fun btn(t: String, size: Float, pad: Int, click: (View) -> Unit) = TextView(act).apply {
        text = t; textSize = size; setTextColor(ink); gravity = Gravity.CENTER
        setPadding(dp(pad), 0, dp(pad), 0); setOnClickListener { click(it) }
    }

    // লেখার বদলে আইকন বাটন (back, go, forward, close, plus, menu, home, bookmark, reload)
    private fun ibtn(kind: String, col: Int = ink, click: (View) -> Unit): View =
        IconView(act, kind).apply { this.col = col; setOnClickListener { click(it) } }

    private fun squareCount(n: Int, onClick: () -> Unit): TextView = TextView(act).apply {
        text = n.toString(); gravity = Gravity.CENTER; setTextColor(ink); textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(2), ink); cornerRadius = dp(5).toFloat() }
        setOnClickListener { onClick() }
    }

    private fun avatar(label: String, color: Int, size: Int, tsp: Float): TextView = TextView(act).apply {
        text = label; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = tsp
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    init {
        visibility = View.GONE
        loadProfiles()
        // একবারের পরিষ্কার: আগের ভার্সনে সব সাইটের ক্যাশ জমেছিল — সেটা মুছে অ্যাপের সাইজ কমানো হয়
        if (!hp.getBoolean("cacheReset2", false)) {
            try { OfflineStore.clearAll(act) } catch (e: Exception) { }
            hp.edit().putBoolean("cacheReset2", true).apply()
        }

        topBar.setBackgroundColor(pc("#0B0F24")); topBar.setPadding(dp(4), 0, dp(4), 0)

        urlText.apply {
            textSize = 13f; setTextColor(pc("#DDE3FF")); setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            gravity = Gravity.CENTER_VERTICAL
            setSelectAllOnFocus(true)
            background = null; setPadding(dp(14), 0, dp(4), 0)
            setOnEditorActionListener { v, _, _ ->
                goTo(v.text.toString())
                v.clearFocus()
                hideKeyboard()
                true
            }
        }

        // কয়টা ট্যাব খোলা আছে সেটা চারকোনা চিহ্নে লেখা থাকে
        tabCount.apply {
            gravity = Gravity.CENTER; setTextColor(ink); textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(2), ink); cornerRadius = dp(5).toFloat() }
        }
        val tabHolder = LinearLayout(act).apply {
            gravity = Gravity.CENTER; setOnClickListener { showTabs() }
            addView(tabCount, LinearLayout.LayoutParams(dp(22), dp(22)))
        }
        val menu = ibtn("menu") { showChromeMenu(it) }
        val close = ibtn("close") { closeTab(cur) }
        topAvatar.setOnClickListener { showProfiles() }
        // লিংক বক্সের ভিতরে সার্চ/গো বাটন: লেখার পর চাপলে লিংকে নিয়ে যায়
        val go = ibtn("search", pc("#F6C85F")) { goTo(urlText.text.toString()); urlText.clearFocus(); hideKeyboard() }
        val urlBox = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(pc("#1A2040")); cornerRadius = dp(20).toFloat(); setStroke(dp(1), pc("#55F6C85F")) }
            addView(urlText, LinearLayout.LayoutParams(0, -1, 1f)); addView(go, LinearLayout.LayoutParams(-2, -1))
        }
        topBar.addView(close, LinearLayout.LayoutParams(-2, -1))
        topBar.addView(urlBox, LinearLayout.LayoutParams(0, dp(40), 1f))
        topBar.addView(topAvatar, LinearLayout.LayoutParams(dp(40), -1))
        topBar.addView(tabHolder, LinearLayout.LayoutParams(dp(44), -1))
        topBar.addView(menu, LinearLayout.LayoutParams(-2, -1))

        // পেজে খোঁজার বার
        findInput.apply {
            hint = "Find in page"; textSize = 14f; setSingleLine(true)
            setTextColor(ink); setHintTextColor(pc("#8890B0"))
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply { setColor(pc("#1A2040")); cornerRadius = dp(18).toFloat() }
            doAfterTextChanged { currentWeb()?.findAllAsync(it?.toString() ?: "") }
        }
        findBar.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            setBackgroundColor(pc("#0B0F24")); setPadding(dp(8), dp(4), dp(4), dp(4))
            addView(findInput, LinearLayout.LayoutParams(0, dp(38), 1f))
            addView(btn("Prev", 13f, 10) { currentWeb()?.findNext(false) }, LinearLayout.LayoutParams(-2, dp(38)))
            addView(btn("Next", 13f, 10) { currentWeb()?.findNext(true) }, LinearLayout.LayoutParams(-2, dp(38)))
            addView(btn("Done", 13f, 10) { closeFind() }, LinearLayout.LayoutParams(-2, dp(38)))
        }

        panel.addView(topBar, LinearLayout.LayoutParams(-1, dp(52)))
        panel.addView(bar, LinearLayout.LayoutParams(-1, dp(3)))
        panel.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(findBar, LinearLayout.LayoutParams(-1, -2))

        // নিচের বার
        val up = ChevView(act).apply { col = ink; setOnClickListener { showTabs() } }
        val plus = TextView(act).apply {
            text = "+"; textSize = 28f; setTextColor(ink); gravity = Gravity.CENTER
            setOnClickListener { openNtp(false) }
        }
        chipsScroll.apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(chipsRow, FrameLayout.LayoutParams(-2, -1)) }
        val barRow = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(up, LinearLayout.LayoutParams(dp(52), -1))
            addView(chipsScroll, LinearLayout.LayoutParams(0, -1, 1f))
            addView(plus, LinearLayout.LayoutParams(dp(52), -1))
        }
        bottomBar.apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(pc("#0B0F24"))
            addView(View(act).apply { setBackgroundColor(pc("#44F6C85F")) }, LinearLayout.LayoutParams(-1, dp(1)))
            addView(barRow, LinearLayout.LayoutParams(-1, dp(56)))
        }
        panel.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))
        // কিবোর্ড খুললে নিচের বার লুকায়
        viewTreeObserver.addOnGlobalLayoutListener {
            try {
                val r = android.graphics.Rect(); getWindowVisibleDisplayFrame(r)
                val full = rootView.height
                val kb = full > 0 && (full - r.bottom) > full * 0.15
                val want = if (kb) View.GONE else View.VISIBLE
                if (bottomBar.visibility != want) bottomBar.visibility = want
            } catch (e: Exception) { }
        }
        addView(panel, FrameLayout.LayoutParams(-1, -1))

        // ক্রোমের মতো ইতিহাস পেজ
        val hh = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(8), 0) }
        hh.addView(ibtn("back") { closeHistory() }, LinearLayout.LayoutParams(-2, -1))
        hh.addView(TextView(act).apply { text = "History"; textSize = 20f; setTextColor(ink) }, LinearLayout.LayoutParams(0, -2, 1f))
        hh.addView(btn("Clear", 14f, 10) { showClearData() }, LinearLayout.LayoutParams(-2, -1))
        histSearch.apply {
            hint = "Search history"; textSize = 14f; setSingleLine(true)
            setTextColor(ink); setHintTextColor(pc("#8C938C"))
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply { setColor(pc("#2B302B")); cornerRadius = dp(22).toFloat() }
            doAfterTextChanged { fillHistory(it?.toString() ?: "") }
        }
        histView.addView(hh, LinearLayout.LayoutParams(-1, dp(52)))
        histView.addView(histSearch, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(12), 0, dp(12), dp(6)) })
        histView.addView(ScrollView(act).apply { addView(histList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(histView, FrameLayout.LayoutParams(-1, -1))

        // ছোট করে নিচে রাখা অবস্থার বার
        miniTitle.apply { setTextColor(ink); textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        miniCount.apply {
            gravity = Gravity.CENTER; setTextColor(ink); textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(2), ink); cornerRadius = dp(5).toFloat() }
        }
        val miniClose = IconView(act, "close").apply {
            col = ink; setOnClickListener { closeAll() }
        }
        miniBar.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            setPadding(dp(16), 0, dp(4), 0)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pc("#2E3396"), pc("#0F1238")))
                .apply { cornerRadius = dp(27).toFloat(); setStroke(dp(1), pc("#F6C85F")) }
            elevation = dp(10).toFloat()
            addView(miniTitle, LinearLayout.LayoutParams(0, -2, 1f))
            addView(miniCount, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
            addView(miniClose, LinearLayout.LayoutParams(dp(48), -1))
            // চাপ দিলে বা উপরে টানলে আবার পুরো স্ক্রিন
            var sy = 0f
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> sy = e.rawY
                    MotionEvent.ACTION_UP -> {
                        val dy = sy - e.rawY
                        if (dy > dp(24) || Math.abs(dy) < dp(8)) {
                            // নিচের ছোট বারের নম্বর বক্সে চাপলে ট্যাব লিস্ট দেখাবে
                            val loc = IntArray(2); miniCount.getLocationOnScreen(loc)
                            val pad = dp(14)
                            val hitCount = e.rawX >= loc[0] - pad && e.rawX <= loc[0] + miniCount.width + pad &&
                                e.rawY >= loc[1] - pad && e.rawY <= loc[1] + miniCount.height + pad
                            show()
                            if (hitCount && Math.abs(dy) < dp(8)) postDelayed({ showTabs() }, 60)
                        }
                    }
                }
                true
            }
        }
        addView(miniBar, FrameLayout.LayoutParams(-1, dp(54)).apply { gravity = Gravity.BOTTOM; setMargins(dp(12), 0, dp(12), dp(14)) })
        try { ContextCompat.registerReceiver(act, dlReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED) } catch (e: Exception) { }
        restoreTabs()
        if (hp.getBoolean("reopen", false)) { hp.edit().remove("reopen").apply(); if (tabs.isNotEmpty()) show() }
    }

    fun release() {
        try { act.unregisterReceiver(dlReceiver) } catch (e: Exception) { }
        closePdf()
    }

    // ================= ট্যাব সেভ/রিস্টোর: ✕ দিয়ে বন্ধ না করলে অ্যাপে আবার ঢুকলে ট্যাব থাকবে =================
    fun persist() {
        try {
            val a = JSONArray(); var idx = 0
            tabs.forEachIndexed { i, t ->
                if (t.incognito) return@forEachIndexed
                if (i == cur) idx = a.length()
                a.put(JSONObject().put("p", t.profile).put("n", t.ntp).put("u", if (t.url.isBlank()) (t.pending ?: "") else t.url).put("t", t.title))
            }
            hp.edit().putString("savedTabs", a.toString()).putInt("savedCur", idx).apply()
        } catch (e: Exception) { }
    }

    private fun restoreTabs() {
        try {
            val a = JSONArray(hp.getString("savedTabs", "[]"))
            if (a.length() == 0) return
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val pf = if (profs.any { it.id == o.optString("p") }) o.optString("p") else "p1"
                val t = newTab(pf, false)
                t.title = o.optString("t")
                if (o.optBoolean("n")) t.ntp = true else { t.url = o.optString("u"); t.pending = t.url }
                tabs.add(t)
            }
            select(hp.getInt("savedCur", 0).coerceIn(0, tabs.size - 1))
            visibility = View.VISIBLE; panel.visibility = View.GONE
            mini = true; refreshMini(); miniBar.visibility = View.VISIBLE
        } catch (e: Exception) { }
    }

    // ================= বাইরে থেকে ব্যবহার =================
    // অ্যাপের ভেতর থেকে আসা লিংক নতুন ট্যাবে
    fun open(url: String) {
        val t = newTab(curProf, false)
        tabs.add(t)
        select(tabs.size - 1)
        loadSmart(t, url)
        show()
    }

    // অ্যাপের হোমের সার্চ বাটন: ক্রোমের মতো হোম পেজ
    fun openHome() {
        val i = tabs.indexOfFirst { it.ntp && !it.incognito && it.profile == curProf }
        if (i >= 0) { select(i); show() } else openNtp(false)
    }

    private fun openNtp(incognito: Boolean) {
        val t = newTab(curProf, incognito)
        t.ntp = true
        tabs.add(t)
        select(tabs.size - 1)
        show()
    }

    fun handleBack(): Boolean {
        if (customView != null) { hideCustom(); return true }
        if (tabsOverlay != null) { closeTabsOverlay(); return true }
        if (fmtOverlay != null) { closeFmt(); return true }
        if (sitesOverlay != null) { closeSites(); return true }
        if (srcOverlay != null) { closeSource(); return true }
        if (pdfOverlay != null) { closePdf(); return true }
        if (clearOverlay != null) { closeClear(); return true }
        if (histView.visibility == View.VISIBLE) { closeHistory(); return true }
        if (visibility != View.VISIBLE || mini || tabs.isEmpty()) return false
        if (findBar.visibility == View.VISIBLE) { closeFind(); return true }
        val t = tabs.getOrNull(cur)
        if (t != null && !t.ntp && t.web.canGoBack()) t.web.goBack()
        else if (t != null && !t.ntp && t.fromNtp) { t.fromNtp = false; t.ntp = true; select(cur) }
        else closeTab(cur)
        return true
    }

    fun destroyAll() {
        tabs.forEach { try { it.web.destroy() } catch (e: Exception) { } }
        tabs.clear(); cur = -1
    }

    // ================= অবস্থা: পুরো স্ক্রিন / ছোট বার / লুকানো =================
    fun show() {
        visibility = View.VISIBLE
        miniBar.visibility = View.GONE
        panel.visibility = View.VISIBLE
        if (mini) {
            panel.translationY = maxOf(height, dp(300)).toFloat()
            panel.animate().translationY(0f).setDuration(220).start()
        } else panel.translationY = 0f
        mini = false
    }

    fun minimize() {
        if (tabs.isEmpty() || visibility != View.VISIBLE || mini) return
        closeTabsOverlay()
        hideKeyboard()
        histView.visibility = View.GONE
        panel.animate().translationY(maxOf(height, dp(300)).toFloat()).setDuration(200).withEndAction {
            panel.visibility = View.GONE
            panel.translationY = 0f
            mini = true
            refreshMini()
            miniBar.visibility = View.VISIBLE
        }.start()
    }

    private fun hideAll() {
        menuPopup?.dismiss()
        closeTabsOverlay(); closeSource()
        histView.visibility = View.GONE
        mini = false
        miniBar.visibility = View.GONE
        panel.translationY = 0f
        visibility = View.GONE
        clearOverlay?.let { removeView(it) }; clearOverlay = null
        closePdf()
    }

    fun closeAll() {
        hp.edit().remove("savedTabs").apply()
        content.removeAllViews()
        destroyAll()
        deleteWebProfile("prof_incog")
        hideAll()
    }

    // ================= ট্যাব =================
    private fun currentWeb(): WebView? = tabs.getOrNull(cur)?.web
    private fun isCur(t: Tab) = tabs.getOrNull(cur) === t
    private fun curProfileId() = tabs.getOrNull(cur)?.profile ?: curProf
    private fun isIncog() = tabs.getOrNull(cur)?.incognito == true

    private fun updateCounts() {
        tabCount.text = ptabs().size.toString()
        miniCount.text = ptabs().size.toString()
        refreshBottomBar()
    }

    private fun tabLabel(t: Tab): String =
        if (t.ntp) "New tab" else if (t.title.isNotBlank()) t.title else (Uri.parse(t.url).host ?: t.url)

    private fun refreshMini() {
        val t = tabs.getOrNull(cur)
        miniTitle.text = (if (t?.incognito == true) "Incognito:  " else "") + (if (t == null) "" else tabLabel(t))
        updateCounts()
    }

    private fun select(i: Int) {
        if (i !in tabs.indices) return
        val prev = tabs.getOrNull(cur)
        cur = i
        val t = tabs[i]
        if (prev != null && prev !== t) snap(prev)      // ছেড়ে যাওয়া ট্যাবের প্রিভিউ ছবি রাখা
        curProf = t.profile
        closeFind()
        content.removeAllViews()
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        (ntpView.parent as? ViewGroup)?.removeView(ntpView)
        if (t.ntp) {
            topBar.visibility = View.GONE; bar.visibility = View.GONE
            refreshNtp()
            content.addView(ntpView, FrameLayout.LayoutParams(-1, -1))
        } else {
            topBar.visibility = View.VISIBLE
            topAvatar.removeAllViews()
            topAvatar.addView(if (t.incognito) avatar("I", pc("#5F6368"), 30, 13f) else profAvatar(profOf(t.profile), 30, 13f), LinearLayout.LayoutParams(dp(30), dp(30)))
            content.addView(t.web, FrameLayout.LayoutParams(-1, -1))
            if (!urlText.hasFocus()) urlText.setText(t.url)
            bar.visibility = View.GONE
            t.pending?.let { t.pending = null; loadSmart(t, it) }
        }
        refreshMini()
        persist()
    }

    private fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        val t = tabs.removeAt(i)
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        try { t.web.stopLoading(); t.web.destroy() } catch (e: Exception) { }
        if (t.incognito && tabs.none { it.incognito }) deleteWebProfile("prof_incog")   // ইনকগনিটোর সব ডেটা মুছে যায়
        val same = tabs.indices.filter { tabs[it].profile == t.profile }
        if (same.isEmpty()) { cur = -1; content.removeAllViews(); hideAll(); persist(); return }
        val nc = when { i < cur -> cur - 1; i == cur -> (same.firstOrNull { it >= i } ?: same.last()); else -> cur }
        select(nc)
    }

    // ================= নিচের বার ও ট্যাব লিস্ট পপআপ =================
    private fun hostLabel(t: Tab): String = (Uri.parse(t.url).host ?: "").removePrefix("www.")

    // WebView-র উপরের অংশের ছোট ছবি তোলা (ট্যাব লিস্টের প্রিভিউয়ের জন্য)
    private fun snap(t: Tab) {
        try {
            if (t.ntp) return
            val w = t.web
            if (w.parent == null || w.width <= 0 || w.height <= 0) return
            val tw = 320
            val sc = tw.toFloat() / w.width
            val th = minOf((w.height * sc).toInt(), 320).coerceAtLeast(1)
            val bm = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bm)
            cv.drawColor(Color.WHITE)
            cv.scale(sc, sc)
            w.draw(cv)
            t.thumb = bm
        } catch (e: Throwable) { }
    }

    private fun tabFace(t: Tab, size: Int): View {
        val lbl = if (t.incognito) "I" else if (t.ntp) "N" else (hostLabel(t).ifEmpty { t.title }.trim().take(1).uppercase().ifEmpty { "?" })
        val ic = t.icon
        return if (ic != null && !t.ntp && !t.incognito) ImageView(act).apply {
            setImageBitmap(ic); scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(size / 5), dp(size / 5), dp(size / 5), dp(size / 5))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#ECEFFF")) }
        } else TextView(act).apply {
            text = lbl; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = size * 0.4f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (t.ntp || t.incognito) pc("#2A2F7A") else colorFor(hostLabel(t) + t.title))
            }
        }
    }

    private fun makeChip(t: Tab, i: Int, active: Boolean): View {
        val f = FrameLayout(act)
        if (active) f.addView(View(act).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(2), pc("#F6C85F")) }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
        f.addView(tabFace(t, 34), FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        if (active) f.addView(TextView(act).apply {
            text = "x"; textSize = 10f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pc("#D93025")); setStroke(dp(1), pc("#0B0F24")) }
            setOnClickListener { closeTab(i) }
        }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(1), dp(1), 0) })
        f.setOnClickListener { if (!active) select(i) }
        return f
    }

    private fun refreshBottomBar() {
        val list = tabs.withIndex().filter { it.value.profile == curProf }
        val sig = list.joinToString("|") { (i, t) ->
            "${System.identityHashCode(t)}:${t.icon != null}:${t.ntp}:${t.incognito}:${hostLabel(t)}:${t.title.take(1)}:${i == cur}"
        }
        if (sig == barSig) return
        barSig = sig
        val sx = chipsScroll.scrollX
        chipsRow.removeAllViews()
        var activeView: View? = null
        list.forEach { (i, t) ->
            val chip = makeChip(t, i, i == cur)
            chipsRow.addView(chip, LinearLayout.LayoutParams(dp(52), dp(52)))
            if (i == cur) activeView = chip
        }
        val changed = cur != barCur
        barCur = cur
        chipsScroll.post {
            val v = activeView
            if (changed && v != null) chipsScroll.smoothScrollTo(maxOf(0, v.left - dp(60)), 0)
            else chipsScroll.scrollTo(sx, 0)
        }
    }

    private fun closeTabsOverlay() { tabsOverlay?.let { removeView(it) }; tabsOverlay = null }

    // ^ চাপলে: কয়টা ট্যাব, প্রতিটার নাম ও প্রিভিউ; চাপ দিলে সুইচ, ✕ দিলে বন্ধ, + দিলে নতুন ট্যাব
    private fun showTabs() {
        closeTabsOverlay()
        menuPopup?.dismiss()
        hideKeyboard()
        tabs.getOrNull(cur)?.let { snap(it) }
        val list = tabs.withIndex().filter { it.value.profile == curProf }
        val root = FrameLayout(act).apply { setBackgroundColor(Color.parseColor("#B3000000")); isClickable = true; setOnClickListener { closeTabsOverlay() } }
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true; setPadding(dp(10), dp(6), dp(10), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pc("#1B1F5E"), pc("#0B0F24")))
                .apply { cornerRadius = dp(28).toFloat(); setStroke(dp(1), pc("#88F6C85F")) }
        }
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ibtn("back") { closeTabsOverlay() }, LinearLayout.LayoutParams(-2, dp(52)))
        head.addView(View(act).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(profOf(curProf).color) } }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { setMargins(dp(4), 0, dp(10), 0) })
        head.addView(TextView(act).apply { text = "${list.size} tabs"; textSize = 20f; setTextColor(ink) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(ibtn("plus") { closeTabsOverlay(); openNtp(false) }, LinearLayout.LayoutParams(-2, dp(52)))
        card.addView(head, LinearLayout.LayoutParams(-1, -2))

        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun tabCard(i: Int, t: Tab): View {
            val active = i == cur
            val c = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(pc("#222862")); cornerRadius = dp(18).toFloat()
                    setStroke(dp(if (active) 2 else 1), pc(if (active) "#F6C85F" else "#44F6C85F"))
                }
                setOnClickListener { closeTabsOverlay(); select(i) }
            }
            val hd = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            hd.addView(tabFace(t, 22), LinearLayout.LayoutParams(dp(22), dp(22)))
            hd.addView(TextView(act).apply {
                text = tabLabel(t); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; textSize = 13f
                setTextColor(ink); setPadding(dp(8), 0, dp(4), 0)
                setTypeface(typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            hd.addView(ibtn("close") {
                closeTab(i)
                if (tabsOverlay != null) showTabs()
            }, LinearLayout.LayoutParams(dp(40), dp(36)))
            c.addView(hd, LinearLayout.LayoutParams(-1, dp(32)))
            val th = FrameLayout(act).apply {
                background = GradientDrawable().apply { setColor(pc("#0B0F24")); cornerRadius = dp(12).toFloat() }
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(12).toFloat()) }
                }
            }
            val bm = if (t.incognito) null else t.thumb
            if (bm != null) th.addView(ImageView(act).apply { setImageBitmap(bm); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
            else th.addView(tabFace(t, 54), FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
            c.addView(th, LinearLayout.LayoutParams(-1, dp(140)).apply { topMargin = dp(6) })
            return c
        }
        list.chunked(2).forEach { pair ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (i, t) -> row.addView(tabCard(i, t), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
            if (pair.size == 1) row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        card.addView(ScrollView(act).apply { addView(grid) }, LinearLayout.LayoutParams(-1, -2))
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(14), dp(40), dp(14), dp(40)) })
        addView(root, FrameLayout.LayoutParams(-1, -1))
        tabsOverlay = root
    }

    // ================= প্রোফাইল ও আলাদা স্টোরেজ =================
    private fun webProfileName(profile: String, incognito: Boolean): String? =
        if (incognito) "prof_incog" else if (profile == "p1") null else "prof_$profile"

    private fun storeObj(): Any? = try {
        Class.forName("androidx.webkit.ProfileStore").getMethod("getInstance").invoke(null)
    } catch (e: Throwable) { null }

    private fun applyProfile(w: WebView, name: String?) {
        if (name == null) return
        var ok = false
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported("MULTI_PROFILE")) {
                Class.forName("androidx.webkit.WebViewCompat").getMethod("setProfile", WebView::class.java, String::class.java).invoke(null, w, name)
                ok = true
            }
        } catch (e: Throwable) { }
        if (!ok && !warnedProfile) {
            warnedProfile = true
            toast("This phone's WebView has no separate storage: history and bookmarks stay separate, but login cookies may be shared")
        }
    }

    private fun deleteWebProfile(name: String) {
        try {
            val s = storeObj() ?: return
            s.javaClass.getMethod("deleteProfile", String::class.java).invoke(s, name)
        } catch (e: Throwable) { }
    }

    private fun cookieMgr(t: Tab?): CookieManager = if (t == null) CookieManager.getInstance() else cookieMgrFor(t.profile, t.incognito)

    private fun cookieMgrFor(profile: String, incognito: Boolean): CookieManager {
        val name = webProfileName(profile, incognito)
        if (name != null) {
            try {
                val s = storeObj()
                if (s != null) {
                    val p = s.javaClass.getMethod("getOrCreateProfile", String::class.java).invoke(s, name)
                    if (p != null) return p.javaClass.getMethod("getCookieManager").invoke(p) as CookieManager
                }
            } catch (e: Throwable) { }
        }
        return CookieManager.getInstance()
    }

    private fun loadProfiles() {
        profs.clear()
        try {
            val a = JSONArray(hp.getString("profiles", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                profs.add(Prof(o.getString("id"), o.getString("name"), o.getInt("color")))
            }
        } catch (e: Exception) { }
        if (profs.none { it.id == "p1" }) profs.add(0, Prof("p1", "Profile 1", palette[0]))
        curProf = hp.getString("curProf", "p1") ?: "p1"
        if (profs.none { it.id == curProf }) curProf = "p1"
    }

    private fun saveProfiles() {
        val a = JSONArray()
        profs.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        hp.edit().putString("profiles", a.toString()).putString("curProf", curProf).apply()
    }

    private fun profOf(id: String): Prof = profs.firstOrNull { it.id == id } ?: profs[0]
    private fun initial(p: Prof) = p.name.trim().take(1).uppercase().ifEmpty { "?" }

    private fun showProfiles() {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val sv = ScrollView(act).apply { addView(box) }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle("Profile").setView(sv)
            .setPositiveButton("Add profile") { _, _ -> addProfileDialog() }
            .setNegativeButton("Close", null).create()
        profs.forEach { p ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(8)) }
            val av = profAvatar(p, 38, 16f)
            val name = TextView(act).apply {
                text = p.name + (if (p.id == curProf) "  (current)" else "")
                textSize = 15f; setPadding(dp(12), 0, dp(8), 0)
                setTypeface(typeface, if (p.id == curProf) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { dlg.dismiss(); switchProfile(p.id) }
            }
            row.addView(av, LinearLayout.LayoutParams(dp(38), dp(38)))
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(act).apply {
                text = "Edit"; textSize = 13f; setPadding(dp(10), dp(6), dp(6), dp(6))
                setOnClickListener { dlg.dismiss(); editProfile(p) }
            })
            if (p.id != "p1") {
                val x = TextView(act).apply {
                    text = "Delete"; textSize = 13f; setPadding(dp(12), dp(6), dp(6), dp(6))
                    setOnClickListener { dlg.dismiss(); confirmDeleteProfile(p) }
                }
                row.addView(x)
            }
            box.addView(row)
        }
        dlg.pshow()
    }

    private fun addProfileDialog() {
        val et = EditText(act).apply { hint = "Profile name"; setSingleLine() }
        AlertDialog.Builder(act, dlgStyle).setTitle("New profile").setView(et)
            .setMessage("Each profile keeps its own logins, cookies, history and bookmarks.")
            .setPositiveButton("Add") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    val p = Prof("p" + System.currentTimeMillis(), n, palette[profs.size % palette.size])
                    profs.add(p)
                    switchProfile(p.id)
                }
            }.setNegativeButton("Cancel", null).pshow()
    }

    private fun switchProfile(id: String) {
        curProf = id; saveProfiles()
        toast("Profile: " + profOf(id).name)
        val i = tabs.indexOfFirst { it.ntp && !it.incognito && it.profile == id }
        if (i >= 0) select(i) else openNtp(false)
        show()
    }

    private fun confirmDeleteProfile(p: Prof) {
        AlertDialog.Builder(act, dlgStyle).setTitle("Delete \"${p.name}\"?")
            .setMessage("All tabs, logins, cookies, history and bookmarks of this profile will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                for (i in tabs.indices.reversed()) if (tabs[i].profile == p.id && !tabs[i].incognito) closeTab(i)
                deleteWebProfile("prof_" + p.id)
                hp.edit().remove("history_" + p.id).remove("bookmarks_" + p.id).remove("shortcuts_" + p.id).apply()
                profFile(p.id).delete()
                profs.remove(p)
                if (curProf == p.id) curProf = "p1"
                saveProfiles()
                if (tabs.isEmpty()) { openNtp(false) }
            }.setNegativeButton("Cancel", null).pshow()
    }

    // ================= ওয়েব ট্যাব তৈরি =================
    private fun newTab(profile: String, incognito: Boolean): Tab {
        val w = WebView(android.view.ContextThemeWrapper(act, if (light) R.style.WebThemeLight else R.style.WebThemeDark))
        if (android.os.Build.VERSION.SDK_INT >= 26) try { w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false) } catch (e: Throwable) { }
        applyProfile(w, webProfileName(profile, incognito))
        applyWebTheme(w)
        val tab = Tab(w, profile, incognito)
        w.settings.apply {
            javaScriptEnabled = hp.getBoolean("js", true); domStorageEnabled = true
            textZoom = hp.getInt("zoom", 100)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true; allowContentAccess = true
            setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true; loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // কিছু সাইট ("wv" দেখলে) WebView ব্লক করে — তাই সাধারণ মোবাইল ব্রাউজারের মতো পরিচয় দেওয়া হয়
            userAgentString = userAgentString.replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
            if (mobileUa.isEmpty()) mobileUa = userAgentString
        }
        var tx = 0f; var ty = 0f
        // ===== উপর থেকে নিচে বড় স্ক্রল (pull-to-refresh): পেজ একদম উপরে থাকলে টেনে নামালে রিলোড =====
        var pdx = 0f; var py0 = 0f; var pArmed = false; var pPulling = false
        w.setOnTouchListener { _, e ->
            tx = e.x; ty = e.y; tab.touchAt = System.currentTimeMillis()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pdx = e.rawX; py0 = e.rawY; pPulling = false; pArmed = !w.canScrollVertically(-1) }
                MotionEvent.ACTION_MOVE -> if (pArmed && e.pointerCount == 1) {
                    val dy = e.rawY - py0; val dx = Math.abs(e.rawX - pdx)
                    if (w.canScrollVertically(-1) || dy < 0f || dx > dy) {
                        if (pPulling) { pullHide(); pPulling = false }
                        pArmed = false
                    } else if (dy > dp(24)) {
                        pPulling = true
                        if (isCur(tab)) pullShow(dy)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pPulling) {
                        val dy = e.rawY - py0
                        pullHide()
                        if (e.actionMasked == MotionEvent.ACTION_UP && dy > dp(150) && isCur(tab)) reloadCur()
                    }
                    pPulling = false; pArmed = false
                }
            }
            false
        }
        w.setOnLongClickListener {
            val h = w.hitTestResult
            if (h.type == WebView.HitTestResult.IMAGE_TYPE || h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) { h.extra?.let { mediaDialog(tab, listOf(it), true) }; true }
            else if (h.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && h.extra != null) {
                val lk = h.extra!!
                val opts = arrayOf("Open in new tab", "Download file", "Copy link")
                AlertDialog.Builder(act, dlgStyle).setTitle(lk.take(60))
                    .setItems(opts) { _, i ->
                        when (opts[i]) {
                            "Open in new tab" -> openInSameProfile(lk, tab)
                            "Download file" -> enqueue(lk, null, null, null, tab)
                            else -> { (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", lk)); toast("Link copied") }
                        }
                    }.setNegativeButton("Cancel", null).pshow()
                true
            }
            else {
                // ভিডিও ডাউনলোডের অপশন বন্ধ করা হয়েছে
                false
            }
        }
        w.addJavascriptInterface(object {
            @JavascriptInterface fun save(u: String, p: String) { Handler(Looper.getMainLooper()).post { offerSave(tab, u, p) } }
        }, "BPwd")
        // ===== ডাউনলোড (blob/data/প্রিন্ট) — টোকেন ছাড়া অন্য কোনো সাইট এটা ডাকতে পারবে না =====
        val tok = java.util.UUID.randomUUID().toString()
        val bufs = HashMap<String, java.io.ByteArrayOutputStream>()
        w.addJavascriptInterface(object {
            @JavascriptInterface fun chunk(t: String, id: String, b64: String, last: Boolean, name: String, mime: String) {
                if (t != tok) return
                try {
                    val o = bufs.getOrPut(id) { java.io.ByteArrayOutputStream() }
                    if (b64.isNotEmpty()) o.write(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                    if (last) {
                        bufs.remove(id)
                        val nm = fileNameFor(name, mime)
                        val uri = saveToDownloads(nm, mime, o.toByteArray())
                        Handler(Looper.getMainLooper()).post { blobEnd(); DlService.finished(act, nm, uri, mime, null); downloadedDialog(nm, uri, mime) }
                    }
                } catch (e: Throwable) {
                    bufs.remove(id)
                    Handler(Looper.getMainLooper()).post { blobEnd() }
                    Handler(Looper.getMainLooper()).post { toast("Download failed: " + (e.message ?: "")) }
                }
            }
            @JavascriptInterface fun fail(t: String, msg: String) {
                if (t != tok) return
                Handler(Looper.getMainLooper()).post { blobEnd(); toast("Download failed: $msg") }
            }
            @JavascriptInterface fun doPrint(t: String) {
                if (t != tok) return
                Handler(Looper.getMainLooper()).post { printTab(tab) }
            }
        }, "BDl")
        tab.dlJs = DL_JS.replace("__TOK__", tok)
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(w, tab.dlJs, setOf("*"))
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(w, selectJs, setOf("*"))
            }
        } catch (e: Throwable) { }
        cookieMgr(tab).setAcceptCookie(true)
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(w, hp.getBoolean("tpc", true)) } catch (e: Exception) { }
        if (hp.getBoolean("deskdef", false)) { tab.desktop = true; w.settings.userAgentString = desktopUa }
        w.setDownloadListener { url, ua, cd, mime, _ ->
            if (hp.getString("autodl", "allow") == "block" && System.currentTimeMillis() - tab.touchAt > 4000) {
                toast("Automatic download blocked"); return@setDownloadListener
            }
            enqueue(url, ua, mime, cd, tab)
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (adOn && !r.isForMainFrame && AdBlock.isAd(r.url.host)) { AdBlock.blocked++; return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0))) }
                return siteFetch(tab, r)
            }
            private fun real(url: String?) = url != null && !(url.startsWith("file:") && tab.offlineOf != null)
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sch = r.url.scheme ?: ""
                if (sch == "http" || sch == "https") {
                    // Play Store পেজে থাকা অবস্থায় "See in Play Store app / Install" চাপলে Play Store অ্যাপে যাবে
                    val cu = Uri.parse(tab.url)
                    val id = r.url.getQueryParameter("id")
                    if (r.url.host == "play.google.com" && (r.url.path ?: "").startsWith("/store/apps/details") && id != null &&
                        cu.host == "play.google.com" && cu.getQueryParameter("id") == id && System.currentTimeMillis() - tab.touchAt < 6000) {
                        if (ExternalApp.open(act, "market://details?id=$id") { fb -> loadSmart(tab, fb) }) return true
                    }
                    if (!OfflineStore.online(act)) { loadSmart(tab, r.url.toString()); return true }
                    return false
                }
                if (sch == "file" || sch == "content") return false
                handleOther(v, r.url.toString(), tab)
                return true
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame && r.url.scheme?.startsWith("http") == true && OfflineStore.has(act, r.url.toString())) {
                    tab.offlineOf = r.url.toString(); tab.url = tab.offlineOf!!
                    v.loadUrl("file://" + OfflineStore.file(act, tab.offlineOf!!).path)
                }
            }
            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                if (real(url)) {
                    if (Uri.parse(url!!).host != Uri.parse(tab.url).host) tab.icon = null
                    tab.url = url
                    val hh = Uri.parse(url).host ?: ""
                    if (tab.transFrom != null && !(hh.endsWith("translate.goog") || hh == "translate.google.com")) tab.transFrom = null
                }
                if (isCur(tab)) { bar.visibility = View.VISIBLE; showUrl(tab.url) }
            }
            override fun onPageFinished(v: WebView, url: String?) {
                val isFile = !real(url)
                if (!isFile && url != null) tab.url = url
                tab.title = v.title ?: ""
                if (isCur(tab)) { bar.visibility = View.GONE; showUrl(tab.url) }
                if (tab.desktop) v.evaluateJavascript(desktopJs, null)
                if (!isFile) v.evaluateJavascript(tab.dlJs, null)
                if (!isFile) v.evaluateJavascript(selectJs, null)
                if (!isFile && hp.getString("sound", "on") == "mute") v.evaluateJavascript(muteJs, null)
                if (!isFile && !light && !nativeDark) v.evaluateJavascript(darkJs, null)
                if (!tab.incognito && !isFile) addHistory(tab.profile, v.title, url)
                if (adOn && !isFile) v.evaluateJavascript(AdBlock.JS, null)
                if (!tab.incognito && !isFile && url != null && url.startsWith("http")) { v.evaluateJavascript(PWD_JS, null); autofill(tab) }
                // এক্সটেনশন কোড চালানো
                try { ex(tab.profile).scriptFor(tab.url)?.let { v.evaluateJavascript(it, null) } } catch (e: Exception) { }
                // অফলাইনের জন্য পেজ সেভ
                if (!tab.incognito && !isFile && url != null && url.startsWith("http") && OfflineStore.online(act) && cacheOnFor(Uri.parse(url).host)) {
                    v.postDelayed({
                        try { if (v.url == url) { v.saveWebArchive(OfflineStore.file(act, url).path, false, null); OfflineStore.markSite(act, url, siteKey(Uri.parse(url).host)); OfflineStore.prune(act) } } catch (e: Exception) { }
                    }, 1500)
                }
                refreshMini(); persist()
            }
            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                if (real(url)) tab.url = url!!
                if (isCur(tab)) showUrl(tab.url)
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { if (isCur(tab)) bar.progress = p }
            override fun onReceivedIcon(v: WebView, icon: Bitmap?) { tab.icon = icon; refreshBottomBar() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (hp.getString("perm_loc", "ask") == "block") { callback.invoke(origin, false, false); return }
                askSitePerm(Uri.parse(origin).host ?: origin, "your location") { ok ->
                    if (!ok) callback.invoke(origin, false, false)
                    else act.requestPerms(arrayOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"), false) { g -> callback.invoke(origin, g, false) }
                }
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                val res = ArrayList<String>(); val perms = ArrayList<String>(); val what = ArrayList<String>()
                request.resources.forEach { r ->
                    if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && hp.getString("perm_cam", "ask") != "block") { res.add(r); perms.add("android.permission.CAMERA"); what.add("your camera") }
                    if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && hp.getString("perm_mic", "ask") != "block") { res.add(r); perms.add("android.permission.RECORD_AUDIO"); what.add("your microphone") }
                }
                if (res.isEmpty()) { request.deny(); return }
                askSitePerm(request.origin.host ?: "", what.joinToString(" and ")) { ok ->
                    if (!ok) request.deny()
                    else act.requestPerms(perms.toTypedArray(), true) { g -> if (g) request.grant(res.toTypedArray()) else request.deny() }
                }
            }
            override fun onReceivedTitle(v: WebView, t: String?) { tab.title = t ?: ""; if (isCur(tab)) refreshMini() }
            // নতুন উইন্ডো/ট্যাবে খোলার লিংক নতুন ট্যাবে খোলে
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (hp.getString("popups", "allow") == "block" && !isUserGesture) { toast("Pop-up blocked"); return false }
                val tmp = WebView(act)
                var done = false
                fun grab(u: String?) {
                    if (done || u.isNullOrBlank() || u == "about:blank") return
                    done = true
                    if (u.startsWith("http://") || u.startsWith("https://")) openInSameProfile(u, tab)
                    else if (u.startsWith("blob:") || u.startsWith("data:")) enqueue(u, null, null, null, tab)
                    else handleOther(w, u)
                    Handler(Looper.getMainLooper()).post { try { tmp.destroy() } catch (e: Exception) { } }
                }
                tmp.settings.javaScriptEnabled = true
                tmp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(x: WebView, r: WebResourceRequest): Boolean { grab(r.url.toString()); return true }
                    override fun shouldInterceptRequest(x: WebView, r: WebResourceRequest): WebResourceResponse? {
                        if (r.isForMainFrame) { val u = r.url.toString(); Handler(Looper.getMainLooper()).post { grab(u) } }
                        return null
                    }
                    override fun onLoadResource(x: WebView, url: String?) { grab(url) }
                    override fun onPageStarted(x: WebView, url: String?, f: Bitmap?) { grab(url) }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                resultMsg.sendToTarget()
                return true
            }
            override fun onShowFileChooser(x: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean =
                act.pickFile(cb, p.createIntent())
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customCb = cb
                this@BrowserLayer.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            override fun onHideCustomView() { hideCustom() }
        }
        return tab
    }

    // ===== সাইট ক্যাশ: অনলাইনে থাকলে ফাইল নামিয়ে ডিস্কে রাখে, অফলাইনে ডিস্ক থেকে দেয় =====
    private fun cachedResponse(url: String): WebResourceResponse? {
        val h = SiteCache.get(act, SiteCache.resolve(act, url)) ?: return null
        return WebResourceResponse(h.mime, h.enc, 200, "OK", h.headers, java.io.ByteArrayInputStream(h.data))
    }

    private fun siteFetch(tab: Tab, r: WebResourceRequest): WebResourceResponse? {
        if (tab.incognito) return null
        val pageHost = if (r.isForMainFrame) r.url.host else (Uri.parse(tab.url).host ?: r.url.host)
        if (!cacheOnFor(pageHost)) return null          // এই সাইটের অফলাইন ক্যাশ চালু না থাকলে কিছুই জমে না
        val sch = r.url.scheme ?: return null
        if (sch != "http" && sch != "https") return null
        if (!r.method.equals("GET", true)) return null
        val rh = r.requestHeaders ?: emptyMap<String, String>()
        if (rh.keys.any { it.equals("Range", true) }) return null
        val url = r.url.toString()
        val host = (r.url.host ?: "").lowercase()
        if (noCacheHosts.any { host == it || host.endsWith(".$it") }) return null
        if (!OfflineStore.online(act)) return cachedResponse(url)
        var con: java.net.HttpURLConnection? = null
        try {
            val cn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            con = cn
            cn.apply {
                instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 30000; requestMethod = "GET"
                rh.forEach { (k, v) -> if (k.lowercase() !in skipReqHeaders) setRequestProperty(k, v) }
                cookieMgr(tab).getCookie(url)?.let { if (it.isNotEmpty()) setRequestProperty("Cookie", it) }
            }
            val code = cn.responseCode
            val cm = cookieMgr(tab)
            cn.headerFields?.forEach { (k, vals) -> if (k != null && k.equals("Set-Cookie", true)) vals.forEach { cm.setCookie(url, it) } }
            if (code in 300..399) {
                val loc = cn.getHeaderField("Location")
                if (loc != null) try { SiteCache.alias(act, url, java.net.URL(java.net.URL(url), loc).toString()) } catch (e: Exception) { }
                cn.disconnect(); return null          // রিডাইরেক্ট WebView নিজেই করবে
            }
            if (code != 200) { cn.disconnect(); return null }
            val ct = cn.contentType ?: "application/octet-stream"
            val mime = ct.substringBefore(";").trim().lowercase()
            if (mime.startsWith("video/") || mime.startsWith("audio/") || cn.contentLength.toLong() > 12L * 1024 * 1024) { cn.disconnect(); return null }
            val cap = 12 * 1024 * 1024
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16384)
            cn.inputStream.use { ins ->
                while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > cap) { cn.disconnect(); return null } }
            }
            val bytes = out.toByteArray()
            val enc = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)?.trim('"', '\'')
            val hm = HashMap<String, String>()
            cn.headerFields?.forEach { (k, vals) -> if (k != null && k.lowercase() !in skipRespHeaders) hm[k] = vals.joinToString(", ") }
            val cc = (cn.getHeaderField("Cache-Control") ?: "").lowercase()
            if (!cc.contains("no-store")) SiteCache.put(act, url, mime, enc, hm, bytes, siteKey(pageHost))
            return WebResourceResponse(mime, enc, 200, "OK", hm, java.io.ByteArrayInputStream(bytes))
        } catch (e: Throwable) {
            try { con?.disconnect() } catch (x: Throwable) { }
            return cachedResponse(url)                  // নেট ধীর/নষ্ট হলেও সেভ করা কপি দেখায়
        }
    }

    private fun openInSameProfile(url: String, from: Tab) {
        val t = newTab(from.profile, from.incognito)
        tabs.add(t)
        select(tabs.size - 1)
        loadSmart(t, url)
    }

    private fun hideCustom() {
        customView?.let { removeView(it) }
        customView = null
        customCb?.onCustomViewHidden(); customCb = null
    }

    // ================= লিংক বার ও সার্চ =================
    private fun showUrl(u: String?) {
        if (u.isNullOrBlank() || u == "about:blank") return
        if (!urlText.hasFocus()) urlText.setText(u)
    }

    private fun hideKeyboard() {
        try { (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlText.windowToken, 0) } catch (e: Exception) { }
    }

    private val engineNames = arrayOf("Google", "Bing", "DuckDuckGo", "Yahoo", "Brave", "Ecosia", "Startpage", "Yandex", "Qwant", "Wikipedia")
    private fun engineIdx() = hp.getInt("engine", 0).coerceIn(0, engineNames.size - 1)
    private fun searchUrl(q: String): String = when (engineIdx()) {
        1 -> "https://www.bing.com/search?q="
        2 -> "https://duckduckgo.com/?q="
        3 -> "https://search.yahoo.com/search?p="
        4 -> "https://search.brave.com/search?q="
        5 -> "https://www.ecosia.org/search?q="
        6 -> "https://www.startpage.com/do/search?q="
        7 -> "https://yandex.com/search/?text="
        8 -> "https://www.qwant.com/?q="
        9 -> "https://en.wikipedia.org/w/index.php?search="
        else -> "https://www.google.com/search?q="
    } + Uri.encode(q)

    private fun toUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(" ") && t.contains(".") -> "https://$t"
            else -> searchUrl(t)
        }
    }

    private fun goTo(raw: String) {
        if (raw.isBlank()) return
        loadUrlInCurrent(toUrl(raw))
    }

    // হোম পেজ থেকে বা লিংক বার থেকে বর্তমান ট্যাবে লোড
    private fun loadUrlInCurrent(u: String) {
        val t = tabs.getOrNull(cur) ?: return
        if (t.ntp) { t.ntp = false; t.fromNtp = true; select(cur) }
        loadSmart(t, u)
    }

    // নেট থাকলে সাধারণ লোড; না থাকলে আগে দেখা সেভ করা পেজ (অফলাইন)
    private fun loadSmart(t: Tab, u: String) {
        t.offlineOf = null
        if (OfflineStore.online(act) || !u.startsWith("http")) {
            t.web.settings.cacheMode = WebSettings.LOAD_DEFAULT
            t.web.loadUrl(u)
        } else {
            t.web.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            val ru = SiteCache.resolve(act, u)
            if (cacheOnFor(Uri.parse(u).host) && SiteCache.has(act, ru)) t.web.loadUrl(ru)      // আসল লিংকেই খোলে — তাই সেভ/এডিট/PDF সব চলে
            else if (OfflineStore.has(act, u)) { t.offlineOf = u; t.url = u; t.web.loadUrl("file://" + OfflineStore.file(act, u).path) }
            else t.web.loadUrl(u)
        }
    }

    private fun reloadCur() { val t = tabs.getOrNull(cur) ?: return; if (t.ntp) return; loadSmart(t, t.url.ifBlank { t.web.url ?: return }) }

    // intent://, market://, tg://, whatsapp:// ... — ওই অ্যাপে নিয়ে যায়; অ্যাপ না থাকলে fallback/Play Store পেজ এই ব্রাউজারেই
    private fun handleOther(v: WebView, u: String, tab: Tab? = null) {
        val sch = (Uri.parse(u).scheme ?: "").lowercase()
        if (sch in listOf("about", "javascript", "data", "blob", "file", "content", "view-source", "chrome")) return
        // ক্লিক/ছোঁয়া ছাড়া নিজে নিজে অ্যাপ খুলে যাওয়া ঠেকানো
        if (tab != null && System.currentTimeMillis() - tab.touchAt > 6000) return
        ExternalApp.open(act, u) { fb -> v.loadUrl(fb) }
    }

    // ================= ক্রোমের মতো হোম পেজ (নতুন ট্যাব) =================
    private fun topSites(p: String): List<Pair<String, String>> {
        val counts = LinkedHashMap<String, Int>()
        val urls = HashMap<String, String>()
        val arr = histArr(p)
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i).optString("u")
            val host = (Uri.parse(u).host ?: continue).removePrefix("www.")
            counts[host] = (counts[host] ?: 0) + 1
            if (!urls.containsKey(host)) urls[host] = (Uri.parse(u).scheme ?: "https") + "://" + Uri.parse(u).host + "/"
        }
        val list = counts.entries.sortedByDescending { it.value }.take(8).map { Pair(it.key, urls[it.key] ?: "https://${it.key}/") }.toMutableList()
        val defs = listOf("youtube.com" to "https://m.youtube.com/", "google.com" to "https://www.google.com/", "github.com" to "https://github.com/",
            "wikipedia.org" to "https://www.wikipedia.org/", "facebook.com" to "https://m.facebook.com/", "claude.ai" to "https://claude.ai/")
        for (d in defs) { if (list.size >= 8) break; if (list.none { it.first == d.first }) list.add(Pair(d.first, d.second)) }
        return list
    }

    private fun refreshNtp() {
        val t = tabs.getOrNull(cur)
        val inc = t?.incognito == true
        val prof = profOf(t?.profile ?: curProf)
        val fg = ink
        ntpBox.removeAllViews()
        ntpView.setBackgroundColor(pc(if (inc) "#202124" else "#111511"))

        val header = makeDragBar().apply { setPadding(dp(12), 0, dp(8), 0) }
        header.addView(ibtn("home") { minimize() }, LinearLayout.LayoutParams(-2, -1))
        header.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        val av = if (inc) avatar("I", pc("#5F6368"), 34, 15f) else profAvatar(prof, 34, 15f)
        av.setOnClickListener { showProfiles() }
        header.addView(av, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        header.addView(squareCount(ptabs().size) { showTabs() }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(6) })
        header.addView(ibtn("menu") { showChromeMenu(it) }, LinearLayout.LayoutParams(-2, -1))
        ntpBox.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        ntpBox.addView(TextView(act).apply {
            val ct = hp.getString("ntpTitle", "") ?: ""
            val cc = hp.getInt("ntpColor", 0)
            text = if (inc) "Incognito" else if (ct.isNotBlank()) ct else engineNames[engineIdx()]
            textSize = 38f; setTextColor(if (!inc && cc != 0) cc else fg); gravity = Gravity.CENTER
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(36) })

        if (inc) {
            ntpBox.addView(TextView(act).apply {
                text = "History and cookies are not saved in Incognito tabs.\nData is deleted when you close all Incognito tabs."
                textSize = 13f; setTextColor(pc("#BDC1C6")); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); marginStart = dp(24); marginEnd = dp(24) })
        }

        // সার্চ পিল
        val input = EditText(act).apply {
            hint = "Search " + engineNames[engineIdx()] + " or type URL"; textSize = 15f; setSingleLine(true)
            setTextColor(fg); setHintTextColor(pc("#9AA09A")); background = null
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { v, _, _ ->
                val q = v.text.toString()
                if (q.isNotBlank()) { hideKeyboard(); loadUrlInCurrent(toUrl(q)) }
                true
            }
        }
        val pill = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(14), 0)
            background = GradientDrawable().apply { setColor(pc(if (inc) "#35363A" else "#2B302B")); cornerRadius = dp(30).toFloat() }
                        addView(input, LinearLayout.LayoutParams(0, -1, 1f))
            addView(ibtn("search", pc("#F6C85F")) { val q = input.text.toString(); if (q.isNotBlank()) { hideKeyboard(); loadUrlInCurrent(toUrl(q)) } }, LinearLayout.LayoutParams(-2, -1))
            input.setOnKeyListener { _, code, ev ->
                if (code == android.view.KeyEvent.KEYCODE_ENTER && ev.action == android.view.KeyEvent.ACTION_UP) { val q = input.text.toString(); if (q.isNotBlank()) { hideKeyboard(); loadUrlInCurrent(toUrl(q)) }; true } else false
            }
        }
        ntpBox.addView(pill, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(24); marginStart = dp(16); marginEnd = dp(16) })

        fun chip(label: String, onClick: () -> Unit) = TextView(act).apply {
            text = label; textSize = 14f; setTextColor(fg); gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply { setColor(pc(if (inc) "#35363A" else "#1F2A1F")); cornerRadius = dp(24).toFloat() }
            setOnClickListener { onClick() }
        }
        val chips = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        if (!inc) {
            chips.addView(chip("Lens") { showLens() }, LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(8) })
            chips.addView(chip("Bookmarks") { showBookmarks() }, LinearLayout.LayoutParams(-2, dp(46)))
            ntpBox.addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

            // বেশি দেখা সাইট (শর্টকাট)
            val card = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(14), dp(8), dp(10))
                background = GradientDrawable().apply { setColor(pc("#1A1F1A")); cornerRadius = dp(26).toFloat() }
            }
            val cells = ArrayList<Any>(loadShortcuts(prof.id))
            cells.add("ADD")
            cells.chunked(4).forEach { rowItems ->
                val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEach { item ->
                    val tile = LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(6), 0, dp(10))
                    }
                    val label: String
                    if (item is Sc) {
                        label = item.name
                        tile.addView(tileIcon(prof.id, item, 50), LinearLayout.LayoutParams(dp(50), dp(50)))
                        tile.setOnClickListener { loadUrlInCurrent(item.url) }
                        tile.setOnLongClickListener {
                            scDropped = false
                            toast("Drop on another shortcut to move it, or release to edit/delete")
                            tile.alpha = 0.4f
                            tile.startDragAndDrop(ClipData.newPlainText("sc", item.id), View.DragShadowBuilder(tile), item.id, 0)
                            true
                        }
                        tile.setOnDragListener { v, ev ->
                            when (ev.action) {
                                DragEvent.ACTION_DRAG_STARTED -> true
                                DragEvent.ACTION_DROP -> {
                                    val from = ev.localState as? String
                                    if (from != null && from != item.id) { scDropped = true; moveShortcut(prof.id, from, item.id) }
                                    true
                                }
                                DragEvent.ACTION_DRAG_ENDED -> {
                                    v.alpha = 1f
                                    if (ev.localState == item.id && !scDropped) showShortcutMenu(prof.id, item)
                                    true
                                }
                                else -> true
                            }
                        }
                    } else {
                        label = "Add shortcut"
                        tile.addView(avatar("+", pc("#2F362F"), 50, 24f), LinearLayout.LayoutParams(dp(50), dp(50)))
                        tile.setOnClickListener { editShortcut(prof.id, null) }
                        tile.setOnDragListener { _, ev ->
                            when (ev.action) {
                                DragEvent.ACTION_DRAG_STARTED -> true
                                DragEvent.ACTION_DROP -> { val from = ev.localState as? String; if (from != null) { scDropped = true; moveShortcut(prof.id, from, "ADD") }; true }
                                else -> true
                            }
                        }
                    }
                    tile.addView(TextView(act).apply {
                        text = label; textSize = 11f; setTextColor(pc("#C9CEC9"))
                        setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                    row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f))
                }
                for (k in rowItems.size until 4) row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
                card.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            ntpBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(24) })
        }
    }

    // ================= Lens: গুগল অ্যাপ ছাড়াই ছবি দিয়ে সার্চ / লেখা সিলেক্ট / ট্রান্সলেট =================
    private fun showLens() {
        AlertDialog.Builder(act, dlgStyle).setTitle("Lens")
            .setItems(arrayOf("Take photo", "Choose from gallery")) { _, i ->
                if (i == 0) act.takePhoto { uri -> if (uri != null) runLens(uri) }
                else act.pickImage { uri -> if (uri != null) runLens(uri) }
            }.setNegativeButton("Cancel", null).pshow()
    }

    private fun decodeForLens(uri: Uri, max: Int): Bitmap {
        val cr = act.contentResolver
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opt) }
        var ss = 1
        while (opt.outWidth / ss > max * 2 || opt.outHeight / ss > max * 2) ss *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
        var bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o2) }!!
        val big = maxOf(bmp.width, bmp.height)
        if (big > max) {
            val f = max.toFloat() / big
            bmp = Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * f).toInt()), maxOf(1, (bmp.height * f).toInt()), true)
        }
        val rot = try { cr.openInputStream(uri)!!.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } } catch (e: Throwable) { 1 }
        val deg = when (rot) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        if (deg != 0f) {
            val m = android.graphics.Matrix(); m.postRotate(deg)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    // ছবি ছোট করে (সর্বোচ্চ ১৬০০px) Google Lens-এ আপলোড করে ফলাফল পেজ এই ব্রাউজারের ট্যাবেই খোলে
    private fun runLens(uri: Uri) {
        toast("Opening Lens...")
        Thread {
            var b64: String? = null
            try {
                val bmp = decodeForLens(uri, 1600)
                val o = java.io.ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 88, o)
                b64 = android.util.Base64.encodeToString(o.toByteArray(), android.util.Base64.NO_WRAP)
            } catch (e: Throwable) { }
            Handler(Looper.getMainLooper()).post {
                val data = b64
                if (data == null) { toast("Couldn't read the image"); return@post }
                val t = tabs.getOrNull(cur) ?: return@post
                if (t.ntp) { t.ntp = false; t.fromNtp = true; select(cur) }
                t.offlineOf = null
                t.web.loadDataWithBaseURL("https://lens.google.com/", lensHtml(data), "text/html", "utf-8", null)
            }
        }.start()
    }

    private fun lensHtml(b64: String): String =
        "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
        "<body style='background:#202124;color:#fff;font-family:sans-serif;text-align:center;padding-top:30vh'>Opening Google Lens...</body>" +
        "<form id='f' method='POST' action='https://lens.google.com/v3/upload?hl=en' enctype='multipart/form-data'>" +
        "<input type='file' name='encoded_image' id='i' style='display:none'></form>" +
        "<script>try{var s=atob('" + b64 + "'),a=new Uint8Array(s.length);for(var k=0;k<s.length;k++)a[k]=s.charCodeAt(k);" +
        "var d=new DataTransfer();d.items.add(new File([a],'image.jpg',{type:'image/jpeg'}));" +
        "document.getElementById('i').files=d.files;document.getElementById('f').submit()}catch(e){location.href='https://lens.google.com/'}</script></html>"

    // ================= ইতিহাস ও বুকমার্ক (প্রোফাইল অনুযায়ী আলাদা) =================
    private fun hk(p: String) = if (p == "p1") "history" else "history_$p"
    private fun bk(p: String) = if (p == "p1") "bookmarks" else "bookmarks_$p"
    private fun histArr(p: String): JSONArray = try { JSONArray(hp.getString(hk(p), "[]")) } catch (e: Exception) { JSONArray() }
    private fun bmArr(p: String): JSONArray = try { JSONArray(hp.getString(bk(p), "[]")) } catch (e: Exception) { JSONArray() }

    private fun addHistory(p: String, title: String?, url: String?) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return
        unhideSite(p, url)
        try {
            val old = histArr(p)
            if (old.length() > 0 && old.getJSONObject(0).optString("u") == url) return
            val neu = JSONArray()
            neu.put(JSONObject().put("t", if (title.isNullOrBlank()) url else title).put("u", url).put("ts", System.currentTimeMillis()))
            for (k in 0 until minOf(old.length(), 499)) neu.put(old.getJSONObject(k))
            hp.edit().putString(hk(p), neu.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun showHistory() {
        if (isIncog()) { toast("History is not saved in Incognito"); return }
        histSearch.setText("")
        fillHistory("")
        histView.visibility = View.VISIBLE
    }

    private fun closeHistory() {
        histView.visibility = View.GONE
        hideKeyboard()
    }

    private fun removeHistoryAt(p: String, index: Int) {
        val old = histArr(p)
        val neu = JSONArray()
        for (i in 0 until old.length()) if (i != index) neu.put(old.getJSONObject(i))
        hp.edit().putString(hk(p), neu.toString()).apply()
    }

    private fun colorFor(s: String): Int = palette[Math.abs(s.hashCode()) % palette.size]

    // তারিখ অনুযায়ী সাজানো; প্রতিটা লাইন এক লাইনে, আলাদা ✕ দিয়ে মোছা যায়
    private fun fillHistory(filter: String) {
        histList.removeAllViews()
        val p = curProfileId()
        val arr = histArr(p)
        val q = filter.trim().lowercase()
        val dayFmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val headFmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
        val todayKey = dayFmt.format(java.util.Date())
        val yKey = dayFmt.format(java.util.Date(System.currentTimeMillis() - 86400000L))
        var lastKey = ""
        var shown = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("u")
            val host = (Uri.parse(url).host ?: url).removePrefix("www.")
            val title = o.optString("t").ifBlank { host }
            if (q.isNotEmpty() && !title.lowercase().contains(q) && !url.lowercase().contains(q)) continue
            val ts = o.optLong("ts")
            val key = dayFmt.format(java.util.Date(ts))
            if (key != lastKey) {
                lastKey = key
                histList.addView(TextView(act).apply {
                    text = (if (key == todayKey) "Today - " else if (key == yKey) "Yesterday - " else "") + headFmt.format(java.util.Date(ts))
                    textSize = 14f; setTextColor(pc("#9FD0A0")); setTypeface(typeface, Typeface.BOLD)
                    setPadding(dp(18), dp(18), dp(18), dp(6))
                })
            }
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(6), dp(4), dp(6)) }
            row.addView(avatar(host.take(1).uppercase(), colorFor(host), 34, 14f), LinearLayout.LayoutParams(dp(34), dp(34)))
            val full = title + "  •  " + host
            val sp = SpannableString(full)
            sp.setSpan(ForegroundColorSpan(pc("#8C938C")), title.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            row.addView(TextView(act).apply {
                text = sp; textSize = 15f; setTextColor(ink)
                setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), dp(8), dp(8), dp(8))
                setOnClickListener { closeHistory(); loadUrlInCurrent(url) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(act).apply {
                text = "Remove"; textSize = 13f; setTextColor(pc("#C9CEC9")); setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { removeHistoryAt(p, i); fillHistory(histSearch.text.toString()) }
            })
            histList.addView(row, LinearLayout.LayoutParams(-1, -2))
            shown++
        }
        if (shown == 0) histList.addView(TextView(act).apply {
            text = "No history"; textSize = 14f; setTextColor(pc("#8C938C")); gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun isBookmarked(p: String, url: String): Boolean {
        val a = bmArr(p)
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("u") == url) return true
        return false
    }

    private fun toggleBookmark() {
        val t = tabs.getOrNull(cur) ?: return
        if (t.ntp || t.url.isBlank()) { toast("This page can't be bookmarked"); return }
        val p = t.profile
        val old = bmArr(p)
        val neu = JSONArray()
        var removed = false
        for (i in 0 until old.length()) {
            val o = old.getJSONObject(i)
            if (o.optString("u") == t.url) removed = true else neu.put(o)
        }
        if (!removed) neu.put(JSONObject().put("t", if (t.title.isBlank()) t.url else t.title).put("u", t.url))
        hp.edit().putString(bk(p), neu.toString()).apply()
        toast(if (removed) "Bookmark removed" else "Bookmarked")
    }

    private fun showBookmarks() {
        val p = curProfileId()
        val arr = bmArr(p)
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(4)) }
        val sv = ScrollView(act).apply { addView(box) }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle("Bookmarks").setView(sv).setNegativeButton("Close", null).create()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(8)) }
            val label = TextView(act).apply {
                text = o.optString("t").ifBlank { o.optString("u") }; setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; textSize = 14f
                setOnClickListener { dlg.dismiss(); loadUrlInCurrent(o.optString("u")) }
            }
            val x = TextView(act).apply {
                text = "Remove"; textSize = 13f; setPadding(dp(14), dp(6), dp(6), dp(6))
                setOnClickListener {
                    val neu = JSONArray()
                    for (k in 0 until arr.length()) if (k != i) neu.put(arr.getJSONObject(k))
                    hp.edit().putString(bk(p), neu.toString()).apply()
                    dlg.dismiss(); showBookmarks()
                }
            }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(x)
            box.addView(row)
        }
        dlg.pshow()
    }

    // ================= ক্রোমের মতো মেনু (শুধু লেখা, কোনো আইকন নেই) =================
    private fun themeLabel(): String = when (hp.getString("theme", "dark")) { "light" -> "Light"; "system" -> "System default"; else -> "Dark" }

    private fun showChromeMenu(anchor: View) {
        val t = tabs.getOrNull(cur)
        val isNtp = t?.ntp == true
        val w = if (isNtp) null else t?.web
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(10), dp(8), dp(10)) }
        var pop: PopupWindow? = null
        val txt = pc("#F3F4FF")

        fun pill(kind: String, enabled: Boolean, onClick: () -> Unit) = IconView(act, kind).apply {
            col = txt
            alpha = if (enabled) 1f else 0.4f
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(pc("#2A2F7A")); setStroke(dp(1), pc("#66F6C85F")) }
            setOnClickListener { if (enabled) { pop?.dismiss(); onClick() } }
        }
        val marked = t != null && !isNtp && isBookmarked(t.profile, t.url)
        val top4 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(
            pill("back", w?.canGoBack() == true) { w?.goBack() },
            pill("forward", w?.canGoForward() == true) { w?.goForward() },
            pill(if (marked) "bookmarkOn" else "bookmark", !isNtp) { toggleBookmark() },
            pill("reload", !isNtp) { reloadCur() }
        ).forEach { top4.addView(it, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        box.addView(top4, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        fun item(label: String, onClick: () -> Unit) {
            box.addView(TextView(act).apply {
                text = label; textSize = 15f; setTextColor(txt)
                setPadding(dp(16), dp(13), dp(16), dp(13))
                setOnClickListener { pop?.dismiss(); onClick() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        fun divider() {
            box.addView(View(act).apply { setBackgroundColor(pc("#44F6C85F")) },
                LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(12), dp(4), dp(12), dp(4)) })
        }
        item("New tab") { openNtp(false) }
        item("New Incognito tab") { openNtp(true) }
        divider()
        item("History") { showHistory() }
        item("Downloads") { openDownloads() }
        item("Bookmarks") { showBookmarks() }
        divider()
        if (!isNtp) {
            item("Find in page") { openFind() }
            item("Share") { shareCurrent() }
            item("Desktop site" + (if (t?.desktop == true) "  (on)" else "")) { toggleDesktop() }
            item("Open in other browser") { openExternal() }
            divider()
        }
        item("Theme: " + themeLabel()) { showTheme() }
        item("Translate") { showTranslate() }
        item("Ad block: " + onOff(adOn)) { adOn = !adOn; toast(if (adOn) "Ad block on" else "Ad block off"); reloadCur() }
        divider()
        storeId(t?.url)?.let { id -> item("Install this extension") { installFromStore(id) } }
        item("Saved passwords") { showPasswords() }
        item("Settings") { showSettings() }
        item("Close all tabs") { closeAll() }

        val sv = ScrollView(act).apply { addView(box) }
        box.measure(View.MeasureSpec.makeMeasureSpec(dp(290), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val h = minOf(box.measuredHeight, (resources.displayMetrics.heightPixels * 0.82).toInt())
        val popup = PopupWindow(sv, dp(290), h, true)
        pop = popup
        popup.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pc("#1B1F5E"), pc("#0B0F24"))).apply { cornerRadius = dp(26).toFloat(); setStroke(dp(1), pc("#88F6C85F")) })
        popup.elevation = dp(12).toFloat()
        menuPopup = popup
        popup.showAtLocation(this, Gravity.TOP or Gravity.END, dp(8), dp(56))
    }

    // বর্তমান সাইটের অফলাইন ক্যাশ চালু/বন্ধ — সর্বোচ্চ ৩টা সাইট
    private fun toggleSiteCache(t: Tab?) {
        if (t == null || t.ntp) { toast("Open a website first"); return }
        if (t.incognito) { toast("Offline cache is not available in Incognito"); return }
        val host = siteKey(Uri.parse(t.url).host)
        if (host.isEmpty()) { toast("Open a website first"); return }
        val l = cacheHosts()
        if (l.contains(host)) {
            l.remove(host); saveCacheHosts(l)
            Thread { OfflineStore.clearSite(act, host) }.start()
            toast("$host: offline cache off, saved data deleted")
            return
        }
        if (l.size >= maxCacheSites) {
            AlertDialog.Builder(act, dlgStyle).setTitle("Maximum $maxCacheSites sites — remove which one?")
                .setItems(l.toTypedArray()) { _, k ->
                    val old = l[k]; l.removeAt(k); l.add(host); saveCacheHosts(l)
                    Thread { OfflineStore.clearSite(act, old) }.start()
                    toast("$old removed, $host on — open it once online to use it offline"); reloadCur()
                }.setNegativeButton("Cancel", null).pshow()
            return
        }
        l.add(host); saveCacheHosts(l)
        toast("$host: offline cache on — open it once online to use it offline"); reloadCur()
    }

    private fun openDownloads() {
        try { act.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } catch (e: Exception) { toast("Couldn't open Downloads") }
    }

    private fun shareCurrent() {
        val u = tabs.getOrNull(cur)?.url ?: return
        try { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, u), "Share")) } catch (e: Exception) { }
    }

    private fun openFind() {
        findBar.visibility = View.VISIBLE
        findInput.setText("")
        findInput.requestFocus()
        post { (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(findInput, 0) }
    }

    private fun closeFind() {
        if (findBar.visibility != View.VISIBLE) return
        findBar.visibility = View.GONE
        try { currentWeb()?.clearMatches() } catch (e: Exception) { }
        try { (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(findInput.windowToken, 0) } catch (e: Exception) { }
    }

    // ================= Delete browsing data (Chrome-এর মতো পুরো স্ক্রিন) =================
    private fun closeClear() { clearOverlay?.let { removeView(it) }; clearOverlay = null }

    private fun fmtSize(b: Long) = if (b >= 1048576) "${b / 1048576} MB" else "${maxOf(1L, b / 1024)} KB"

    private fun showClearData() {
        menuPopup?.dismiss(); closeClear()
        val bg = pc("#252A25"); val cardC = pc("#343A34")
        val green = pc("#A9DDA0"); val sub = pc("#C9CEC9")
        val p = curProfileId()
        val hosts = LinkedHashSet<String>()
        val ha = histArr(p)
        for (i in 0 until ha.length()) (Uri.parse(ha.getJSONObject(i).optString("u")).host)?.let { hosts.add(it.removePrefix("www.")) }
        val ranges = arrayOf("Last hour", "Last 24 hours", "Last 7 days", "Last 4 weeks", "All time")
        val spans = longArrayOf(3600000L, 86400000L, 7 * 86400000L, 28 * 86400000L, 0L)
        var range = 0

        val root = FrameLayout(act).apply { setBackgroundColor(bg); isClickable = true }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(100)) }
        root.addView(ScrollView(act).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))

        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ibtn("back") { closeClear() }, LinearLayout.LayoutParams(-2, dp(64)))
        head.addView(TextView(act).apply { text = "Delete browsing data"; textSize = 22f; setTextColor(ink); setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(btn("Help", 14f, 12) { toast("Cookies and cache are fully cleared; history is cleared by time range") }, LinearLayout.LayoutParams(-2, -1))
        col.addView(head, LinearLayout.LayoutParams(-1, dp(64)))

        val timeTv = TextView(act).apply { text = ranges[range]; textSize = 17f; setTextColor(ink); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val timeRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(0), dp(14), 0, dp(14)) }
        timeRow.addView(TextView(act).apply { text = "Time range"; textSize = 17f; setTextColor(sub) }, LinearLayout.LayoutParams(0, -2, 1f))
        timeRow.addView(timeTv)
        timeRow.setOnClickListener {
            AlertDialog.Builder(act, dlgStyle).setTitle("Time range").setSingleChoiceItems(ranges, range) { d, w -> range = w; timeTv.text = ranges[w]; d.dismiss() }.pshow()
        }
        col.addView(timeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val first = hosts.firstOrNull()
        val rows = listOf(
            Triple("", "Browsing history" to (if (first == null) "None" else "From $first" + (if (hosts.size > 1) " + ${hosts.size - 1} sites" else "")), true),
            Triple("", "Cookies and site data" to "From ${hosts.size} sites. Signs you out of most sites.", true),
            Triple("", "Cached images and files" to "${fmtSize(OfflineStore.size(act))}. Some sites may load more slowly on your next visit.", true),
            Triple("", "Tabs" to "${tabs.size} tabs on this device", true),
            Triple("", "Saved passwords" to "${pwds.list(p).size} passwords saved in this browser", false),
            Triple("", "Autofill form data" to "Saved form suggestions", true),
            Triple("", "Site settings" to "Permissions and local storage of sites", false)
        )
        val checks = BooleanArray(rows.size) { false }   // কোনো টিক ডিফল্ট না: যেটা মুছতে চাও সেটায় টিক দাও
        rows.forEachIndexed { i, r ->
            val big = dp(22).toFloat(); val sm = dp(4).toFloat()
            val top = if (i == 0) big else sm; val bot = if (i == rows.size - 1) big else sm
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
                background = GradientDrawable().apply { setColor(cardC); cornerRadii = floatArrayOf(top, top, top, top, bot, bot, bot, bot) }
            }
            row.addView(TextView(act).apply { text = r.first; textSize = 20f; gravity = Gravity.CENTER; setTextColor(ink) }, LinearLayout.LayoutParams(dp(32), -2))
            val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(12), 0) }
            tx.addView(TextView(act).apply { text = r.second.first; textSize = 18f; setTextColor(ink) })
            tx.addView(TextView(act).apply { text = r.second.second; textSize = 14.5f; setTextColor(sub) })
            row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
            val cb = TextView(act).apply { gravity = Gravity.CENTER; textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
            fun paint() {
                cb.text = ""
                cb.setTextColor(pc("#1B2E1B"))
                cb.background = GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    if (checks[i]) setColor(green) else { setColor(Color.TRANSPARENT); setStroke(dp(2), pc("#C9CEC9")) }
                }
            }
            paint()
            row.setOnClickListener { checks[i] = !checks[i]; paint() }
            row.addView(cb, LinearLayout.LayoutParams(dp(24), dp(24)))
            col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        }

        val del = TextView(act).apply {
            text = "Delete data"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(pc("#1B2E1B"))
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
            background = GradientDrawable().apply { setColor(green); cornerRadius = dp(28).toFloat() }
            elevation = dp(6).toFloat()
            setOnClickListener {
                val cut = if (spans[range] == 0L) 0L else System.currentTimeMillis() - spans[range]
                val cur0 = tabs.getOrNull(cur)
                if (checks[0]) {
                    val old = histArr(p); val neu = JSONArray()
                    for (k in 0 until old.length()) { val o = old.getJSONObject(k); if (o.optLong("ts") < cut) neu.put(o) }
                    hp.edit().putString(hk(p), neu.toString()).apply()
                }
                if (checks[1]) {
                    val cm = cookieMgr(cur0); cm.removeAllCookies(null); cm.flush()
                    try { CookieManager.getInstance().removeAllCookies(null) } catch (e: Exception) { }
                    try { WebStorage.getInstance().deleteAllData() } catch (e: Exception) { }
                }
                if (checks[2]) {
                    tabs.forEach { try { it.web.clearCache(true) } catch (e: Exception) { } }
                    OfflineStore.clearAll(act)
                    try { act.cacheDir.deleteRecursively(); act.cacheDir.mkdirs() } catch (e: Exception) { }
                }
                if (checks[4]) pwds.clearAll(p)
                if (checks[4]) try { WebViewDatabase.getInstance(act).clearHttpAuthUsernamePassword() } catch (e: Throwable) { }
                if (checks[5]) { try { WebViewDatabase.getInstance(act).clearFormData() } catch (e: Throwable) { }; tabs.forEach { try { it.web.clearFormData() } catch (e: Exception) { } } }
                if (checks[6]) { try { WebStorage.getInstance().deleteAllData(); GeolocationPermissions.getInstance().clearAll() } catch (e: Exception) { } }
                closeClear()
                if (checks[3]) { val keepProf = curProf; closeAll(); toast("Deleted"); curProf = keepProf; openNtp(false); return@setOnClickListener }
                toast("Deleted")
                if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
            }
        }
        root.addView(del, FrameLayout.LayoutParams(dp(170), dp(58)).apply { gravity = Gravity.BOTTOM or Gravity.END; setMargins(0, 0, dp(18), dp(26)) })
        clearOverlay = root
        addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    // ================= এক্সটেনশন =================
    private fun storeId(u: String?): String? =
        u?.let { Regex("(?:chromewebstore\\.google\\.com/detail/|chrome\\.google\\.com/webstore/detail/)(?:[^/]+/)?([a-p]{32})").find(it)?.groupValues?.get(1) }

    // Chrome Web Store-এর এক্সটেনশন নামিয়ে এই ব্রাউজারে যোগ করে (শুধু content-script অংশ চলবে)
    private fun installFromStore(id: String) {
        val prof = curProfileId(); toast("Downloading extension...")
        Thread {
            var msg: String
            try {
                val c = java.net.URL("https://clients2.google.com/service/update2/crx?response=redirect&prodversion=124.0.0.0&acceptformat=crx2,crx3&x=id%3D$id%26uc").openConnection() as java.net.HttpURLConnection
                c.instanceFollowRedirects = true
                val b = c.inputStream.use { it.readBytes() }
                val r = ExtManager(act).also { it.prof = prof }.addBytes(b, "$id.crx")
                msg = if (r != null) "Added: $r — reload the page to run it" else "This extension has no content script to run"
            } catch (e: Throwable) { msg = "Install failed: " + (e.message ?: "") }
            Handler(Looper.getMainLooper()).post { toast(msg); currentWeb()?.reload() }
        }.start()
    }

    private fun showExtensions() {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val sv = ScrollView(act).apply { addView(box) }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle("Extensions").setView(sv)
            .setPositiveButton("Add") { _, _ -> addExtension() }
            .setNegativeButton("Close", null).create()
        val l = ex().list()
        if (l.isEmpty()) box.addView(TextView(act).apply {
            text = "Add .js / .user.js / .css files, or a Chrome extension .zip / .crx. They run automatically when a page loads.\n\n(Only content-script extensions work; background and popup features do not.)"
            textSize = 13f; setTextColor(pc("#C9CEFF")); setPadding(dp(8), dp(8), dp(8), dp(8))
        })
        l.forEach { e ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(10), dp(6), dp(10)) }
            row.addView(TextView(act).apply {
                text = (if (e.enabled) "ON  —  " else "OFF  —  ") + e.name; textSize = 15f; setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
                setOnClickListener { ex().toggle(e.id); dlg.dismiss(); showExtensions(); currentWeb()?.reload() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(act).apply {
                text = "Remove"; textSize = 13f; setPadding(dp(14), dp(6), dp(6), dp(6))
                setOnClickListener { ex().remove(e.id); dlg.dismiss(); showExtensions() }
            })
            box.addView(row)
        }
        dlg.pshow()
    }

    private fun addExtension() {
        act.pickDoc { uri, name ->
            if (uri == null) return@pickDoc
            val r = ex().add(uri, name)
            toast(if (r != null) "Extension added: $r" else "This file can't be added as an extension")
            if (r != null) { currentWeb()?.reload(); showExtensions() }
        }
    }

    // ================= ছবি/ভিডিও ডাউনলোড (লং প্রেস) =================
    private val PWD_JS = "(function(){if(window.__bp)return;window.__bp=1;function grab(r){var p=r.querySelector('input[type=password]');if(!p||!p.value)return;var u='',ins=r.querySelectorAll('input[type=text],input[type=email],input[type=tel],input:not([type])');for(var i=0;i<ins.length;i++){if(ins[i].value)u=ins[i].value}BPwd.save(u,p.value)}document.addEventListener('submit',function(e){grab(e.target)},true);document.addEventListener('click',function(e){var b=e.target.closest&&e.target.closest('button,input[type=submit],[role=button]');if(b)grab(b.form||document)},true)})();"

    // ছবি চেপে ধরলে "Download" বাটন আসে; বাটন চাপলে তবেই আসল কোয়ালিটিতে ডাউনলোড হয়। (ভিডিও ডাউনলোড বন্ধ)
    private fun mediaDialog(tab: Tab, urls: List<String>, image: Boolean) {
        if (!image || urls.isEmpty()) return
        val u = urls[0]
        AlertDialog.Builder(act, dlgStyle).setTitle("Image")
            .setPositiveButton("Download") { _, _ -> downloadImage(tab, u, 0) }
            .setNeutralButton("Open in new tab") { _, _ -> if (u.startsWith("http")) openInSameProfile(u, tab) else toast("Can't open this image in a tab") }
            .setNegativeButton("Cancel", null).pshow()
    }

    // ================= ভিডিও ডাউনলোড: ফরম্যাট লিস্ট (স্ক্রিনশটের মতো) =================
    private class FmtRow(val title: String, val sub: String, val tag: String?, val run: (String) -> Unit)
    private var fmtOverlay: View? = null
    private fun closeFmt() { fmtOverlay?.let { removeView(it) }; fmtOverlay = null }
    private fun mb(b: Long): String = if (b <= 0L) "" else if (b >= 1048576L) String.format("%.1f MB", b / 1048576.0) else "${maxOf(1L, b / 1024)} KB"
    private fun cleanName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").replace(Regex("\\s+"), " ").trim().take(90).ifBlank { "video" }

    private fun askFileName(def: String, cb: (String) -> Unit) {
        val et = EditText(act).apply { setText(def); setSingleLine(true); setSelectAllOnFocus(true) }
        val box = LinearLayout(act).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(act, dlgStyle).setTitle("File name").setMessage("The file extension is added automatically.").setView(box)
            .setPositiveButton("Download") { _, _ -> cb(cleanName(et.text.toString().ifBlank { def })) }
            .setNegativeButton("Cancel", null).pshow()
    }

    private fun showFormatSheet(heading: String, defaultName: String, sections: List<Pair<String, List<FmtRow>>>, pre: FmtRow?) {
        closeFmt(); menuPopup?.dismiss()
        val bg = pc("#181818"); val subC = Color.parseColor("#8E8E8E"); val yellow = Color.parseColor("#FFCA28")
        val root = FrameLayout(act).apply { setBackgroundColor(bg); isClickable = true }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(110)) }
        root.addView(ScrollView(act).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ibtn("back") { closeFmt() }, LinearLayout.LayoutParams(-2, dp(64)))
        head.addView(TextView(act).apply { text = heading; textSize = 20f; setTextColor(ink); setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(head, LinearLayout.LayoutParams(-1, dp(64)))
        var selected: FmtRow? = pre
        val marks = ArrayList<Pair<FmtRow, View>>()
        fun repaint() {
            marks.forEach { (r, v) ->
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (r === selected) { setColor(yellow); setStroke(dp(2), yellow) } else { setColor(Color.TRANSPARENT); setStroke(dp(2), subC) }
                }
            }
        }
        sections.forEach { (secTitle, rows) ->
            if (rows.isEmpty()) return@forEach
            col.addView(TextView(act).apply { text = secTitle; textSize = 15f; setTextColor(subC); setPadding(0, dp(22), 0, dp(6)) })
            rows.forEach { r ->
                val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(14), dp(4), dp(14)) }
                val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                val tl = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                tl.addView(TextView(act).apply { text = r.title; textSize = 17f; setTextColor(ink) })
                if (r.tag != null) tl.addView(TextView(act).apply {
                    text = r.tag; textSize = 11f; setTextColor(ink); setPadding(dp(8), dp(2), dp(8), dp(2))
                    background = GradientDrawable().apply { setColor(pc("#333333")); cornerRadius = dp(6).toFloat() }
                }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
                tx.addView(tl)
                tx.addView(TextView(act).apply { text = r.sub; textSize = 13.5f; setTextColor(subC); setPadding(0, dp(3), 0, 0) })
                row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
                val mk = View(act); marks.add(Pair(r, mk))
                row.addView(mk, LinearLayout.LayoutParams(dp(26), dp(26)))
                row.setOnClickListener { selected = r; repaint() }
                col.addView(row)
            }
        }
        repaint()
        val dl = TextView(act).apply {
            text = "Download"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#1A1A1A"))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply { setColor(yellow); cornerRadius = dp(28).toFloat() }
            setOnClickListener {
                val r = selected
                if (r == null) toast("Select a format first")
                else askFileName(defaultName) { n -> closeFmt(); r.run(n) }
            }
        }
        root.addView(dl, FrameLayout.LayoutParams(-1, dp(56), Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(20)) })
        fmtOverlay = root
        addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    // ---- YouTube ----
    private fun showYtDownload(tab: Tab, id: String) {
        toast("Getting video info...")
        Thread {
            var info: YtDl.Info? = null; var err = ""
            try { info = YtDl.fetch(id) } catch (e: Throwable) { err = e.message ?: "unknown error" }
            val res = info
            Handler(Looper.getMainLooper()).post { if (res == null) toast("Couldn't get video: $err") else showYtSheet(res) }
        }.start()
    }

    private fun showYtSheet(info: YtDl.Info) {
        val fm = info.formats
        val aac = fm.filter { !it.hasVideo && it.isAac }
        val bestAudio = aac.maxByOrNull { it.bitrate }
        val audioRows = aac.sortedBy { it.bitrate }.map { f ->
            FmtRow("M4A audio (${f.bitrate / 1000} kbps)", listOf("Audio only", mb(f.size)).filter { it.isNotEmpty() }.joinToString("  |  "), null) { n -> ytRun(info, null, f, n, "m4a", "audio/mp4") }
        }
        val heights = fm.filter { it.hasVideo && it.h > 0 && it.mime.contains("mp4") && it.isAvc }.map { it.h }.distinct().sorted()
        val videoRows = heights.mapNotNull { h ->
            val prog = fm.filter { it.hasVideo && it.hasAudio && it.h == h && it.mime.contains("mp4") }.maxByOrNull { it.bitrate }
            val ad = fm.filter { it.hasVideo && !it.hasAudio && it.h == h && it.isAvc }.maxWithOrNull(compareBy<YtDl.Fmt>({ it.fps }, { it.bitrate }))
            val v: YtDl.Fmt; val a: YtDl.Fmt?
            if (prog != null) { v = prog; a = null } else if (ad != null && bestAudio != null) { v = ad; a = bestAudio } else return@mapNotNull null
            val size = v.size + (a?.size ?: 0L)
            val tag = if (h <= 144) "Low" else if (h >= 2160) "4K" else if (h >= 1440) "2K" else if (h >= 1080) "Full HD" else if (h >= 720) "HD" else null
            FmtRow("${h}p" + (if (v.fps > 30) " ${v.fps}fps" else ""), listOf("Video + audio", "MP4", mb(size)).filter { it.isNotEmpty() }.joinToString("  |  "), tag) { n -> ytRun(info, v, a, n, "mp4", "video/mp4") }
        }
        if (audioRows.isEmpty() && videoRows.isEmpty()) { toast("No downloadable formats found for this video"); return }
        val pre = videoRows.firstOrNull { it.title.startsWith("720p") } ?: videoRows.lastOrNull() ?: audioRows.lastOrNull()
        showFormatSheet("Download", cleanName(info.title), listOf("Audio" to audioRows, "Video" to videoRows), pre)
    }

    private fun ytRun(info: YtDl.Info, v: YtDl.Fmt?, a: YtDl.Fmt?, name: String, ext: String, mime: String) {
        val hdr = mapOf("User-Agent" to info.ua)
        val base = java.io.File(act.cacheDir, "yt_" + System.currentTimeMillis())
        startJob("$name.$ext", mime) { upd, cancelled ->
            if (v != null && !v.hasAudio && a != null) {
                val vf = java.io.File(base.path + "_v.mp4"); val af = java.io.File(base.path + "_a.m4a"); val out = java.io.File(base.path + ".mp4")
                val tot = maxOf(1L, v.size + a.size)
                try {
                    httpToFile(v.url, vf, hdr, v.size, { p -> upd("Downloading video", (p * 95 / tot).toInt()) }, cancelled)
                    httpToFile(a.url, af, hdr, a.size, { p -> upd("Downloading audio", ((v.size + p) * 95 / tot).toInt()) }, cancelled)
                    upd("Merging video and audio", -1)
                    YtDl.mux(vf, af, out, cancelled)
                } finally { vf.delete(); af.delete() }
                out
            } else {
                val f = (v ?: a)!!
                val out = java.io.File(base.path + "." + ext)
                httpToFile(f.url, out, hdr, f.size, { p -> upd("Downloading", if (f.size > 0L) (p * 100 / f.size).toInt() else -1) }, cancelled)
                out
            }
        }
    }

    // ---- সাধারণ ওয়েবসাইটের ভিডিও (সরাসরি ফাইল লিংক) ----
    private fun extFor(url: String, mime: String): String {
        val e = (Uri.parse(url).lastPathSegment ?: "").substringAfterLast('.', "").lowercase()
        if (e in setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "mp3", "m4a", "ogg", "ogv")) return e
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
    }

    private fun showDirectVideoSheet(tab: Tab, urls: List<String>) {
        toast("Checking video...")
        val ua = tab.web.settings.userAgentString; val ref = tab.web.url
        val cks = urls.associateWith { u -> try { cookieMgr(tab).getCookie(u) } catch (e: Throwable) { null } }
        val defName = cleanName(tab.title.ifBlank { hostOf(tab) })
        Thread {
            class Item(val url: String, val size: Long, val mime: String)
            val items = ArrayList<Item>()
            for (u in urls) {
                if (u.startsWith("data:")) { items.add(Item(u, 0L, u.substringAfter("data:").substringBefore(";").substringBefore(",").ifBlank { "video/mp4" })); continue }
                if (u.contains(".m3u8") || u.contains(".mpd")) continue
                try {
                    val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    c.requestMethod = "HEAD"; c.connectTimeout = 12000; c.readTimeout = 12000
                    c.setRequestProperty("User-Agent", ua); ref?.let { c.setRequestProperty("Referer", it) }
                    cks[u]?.let { c.setRequestProperty("Cookie", it) }
                    val code = c.responseCode
                    val ty = (c.contentType ?: "").substringBefore(";")
                    if (code in 200..299 && ty.contains("mpegurl", true)) continue
                    items.add(Item(u, if (code in 200..299) c.contentLengthLong.coerceAtLeast(0L) else 0L, ty.ifBlank { "video/mp4" }))
                } catch (e: Throwable) { items.add(Item(u, 0L, "video/mp4")) }
            }
            Handler(Looper.getMainLooper()).post {
                if (items.isEmpty()) { toast("This video can't be downloaded (streaming playlist)"); return@post }
                val rows = items.mapIndexed { i, it ->
                    val ext = extFor(it.url, it.mime)
                    val host = if (it.url.startsWith("data:")) "" else (Uri.parse(it.url).host ?: "")
                    FmtRow(if (items.size == 1) "Original quality" else "Source ${i + 1}", listOf(ext.uppercase(), mb(it.size), host).filter { s -> s.isNotEmpty() }.joinToString("  |  "), null) { n ->
                        directRun(it.url, it.size, it.mime, n, ext, ua, ref, cks[it.url])
                    }
                }
                showFormatSheet("Download video", defName, listOf("Video" to rows), rows.firstOrNull())
            }
        }.start()
    }

    private fun directRun(url: String, size: Long, mime: String, name: String, ext: String, ua: String, ref: String?, cookie: String?) {
        val out = java.io.File(act.cacheDir, "yt_dl_" + System.currentTimeMillis() + "." + ext)
        val fixedMime = if (mime.startsWith("video/") || mime.startsWith("audio/")) mime else "video/mp4"
        startJob("$name.$ext", fixedMime) { upd, cancelled ->
            if (url.startsWith("data:")) {
                val head = url.substringBefore(","); val body = url.substringAfter(",")
                out.writeBytes(if (head.contains(";base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT) else Uri.decode(body).toByteArray())
            } else {
                val h = HashMap<String, String>(); h["User-Agent"] = ua; ref?.let { h["Referer"] = it }; cookie?.let { h["Cookie"] = it }
                httpToFile(url, out, h, size, { p -> upd("Downloading", if (size > 0L) (p * 100 / size).toInt() else -1) }, cancelled)
            }
            out
        }
    }

    // ---- ডাউনলোড চালানো (প্রগ্রেস + ক্যান্সেল) ----
    private fun startJob(fileName: String, mime: String, work: ((String, Int) -> Unit, () -> Boolean) -> java.io.File) {
        var cancel = false
        val msg = TextView(act).apply { text = "Starting..."; textSize = 14f }
        val pb = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true }
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(4))
            addView(msg, LinearLayout.LayoutParams(-1, -2)); addView(pb, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle("Downloading").setView(box).setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancel = true }
            .setNeutralButton("Hide") { _, _ -> toast("Download continues in background") }.pshow()
        val ui = Handler(Looper.getMainLooper())
        DlService.begin(act, fileName)
        Thread {
            var outFile: java.io.File? = null; var err: String? = null
            try { act.cacheDir.listFiles()?.filter { it.name.startsWith("yt_") && System.currentTimeMillis() - it.lastModified() > 3600000L }?.forEach { it.delete() } } catch (e: Throwable) { }
            try {
                outFile = work({ m, pct -> DlService.progress(act, m, pct); ui.post { msg.text = m; if (pct >= 0) { pb.isIndeterminate = false; pb.progress = minOf(100, pct) } else pb.isIndeterminate = true } }, { cancel })
            } catch (e: Throwable) { err = e.message ?: e.javaClass.simpleName }
            var uri: Uri? = null
            if (outFile != null && !cancel) try { uri = saveFileToDownloads(fileName, mime, outFile) } catch (e: Throwable) { err = e.message ?: "couldn't save file" }
            try { outFile?.delete() } catch (e: Throwable) { }
            DlService.end(act)
            if (!cancel) DlService.finished(act, fileName, uri, mime, if (err != null || uri == null) (err ?: "unknown error") else null)
            ui.post {
                try { dlg.dismiss() } catch (e: Throwable) { }
                if (cancel) toast("Download cancelled")
                else if (err != null || uri == null) toast("Download failed: " + (err ?: "unknown error"))
                else downloadedDialog(fileName, uri, mime)
            }
        }.start()
    }

    private fun httpToFile(url: String, dest: java.io.File, headers: Map<String, String>, total: Long, upd: (Long) -> Unit, cancelled: () -> Boolean) {
        fun open(range: String?): java.net.HttpURLConnection {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            range?.let { c.setRequestProperty("Range", it) }
            val code = c.responseCode
            if (code !in 200..299) throw RuntimeException("HTTP $code")
            return c
        }
        dest.outputStream().use { out ->
            val buf = ByteArray(65536); var pos = 0L
            if (total <= 0L) {
                open(null).inputStream.use { ins ->
                    while (true) { if (cancelled()) throw RuntimeException("cancelled"); val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); pos += n; upd(pos) }
                }
                return
            }
            val chunk = 9000000L
            while (pos < total) {
                if (cancelled()) throw RuntimeException("cancelled")
                val end = minOf(total - 1, pos + chunk - 1)
                val c = open("bytes=$pos-$end")
                val fullBody = c.responseCode == 200
                var got = 0L
                c.inputStream.use { ins ->
                    while (true) { if (cancelled()) throw RuntimeException("cancelled"); val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); pos += n; got += n; upd(pos) }
                }
                if (got == 0L) throw RuntimeException("Empty response")
                if (fullBody) break
            }
        }
    }

    private fun saveFileToDownloads(name: String, mime: String, src: java.io.File): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val u = act.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            act.contentResolver.openOutputStream(u)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
            return u
        } else {
            val d = act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!; d.mkdirs()
            val f = java.io.File(d, name)
            src.copyTo(f, true)
            return Uri.fromFile(f)
        }
    }

    private fun saveToDownloads(name: String, mime: String, data: ByteArray): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val u = act.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            act.contentResolver.openOutputStream(u)!!.use { it.write(data) }
            return u
        } else {
            val d = act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!; d.mkdirs()
            val f = java.io.File(d, name)
            f.writeBytes(data)
            return Uri.fromFile(f)
        }
    }

    // ফাইলের নামে এক্সটেনশন না থাকলে (বা .bin হলে) মাইম দেখে বসানো
    private fun fileNameFor(raw: String, mime: String): String {
        var n = raw.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
        val ext = n.substringAfterLast('.', "")
        if (ext.isEmpty() || ext.length > 5 || ext.equals("bin", true)) {
            val me = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (me != null) n = (if (ext.equals("bin", true)) n.substringBeforeLast('.') else n) + "." + me
        }
        return n
    }

    private fun downloadImage(tab: Tab, url: String, quality: Int) {
        toast("Downloading...")
        val ua = tab.web.settings.userAgentString; val ref = tab.web.url; val ck = cookieMgr(tab).getCookie(url)
        Thread {
            var msg: String
            try {
                var mime = "image/jpeg"; val bytes: ByteArray
                if (url.startsWith("data:")) {
                    mime = url.substringAfter("data:").substringBefore(";").ifBlank { "image/png" }
                    bytes = android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT)
                } else {
                    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    ck?.let { c.setRequestProperty("Cookie", it) }
                    c.setRequestProperty("User-Agent", ua); ref?.let { c.setRequestProperty("Referer", it) }
                    mime = c.contentType?.substringBefore(";") ?: mime
                    bytes = c.inputStream.use { it.readBytes() }
                }
                var name = URLUtil.guessFileName(url, null, mime)
                if (quality == 0) saveToDownloads(name, mime, bytes)
                else {
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
                    if (quality <= 30) bmp = Bitmap.createScaledBitmap(bmp, maxOf(1, bmp.width / 2), maxOf(1, bmp.height / 2), true)
                    val o = java.io.ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, quality, o)
                    name = name.substringBeforeLast(".") + "_q$quality.jpg"
                    saveToDownloads(name, "image/jpeg", o.toByteArray())
                }
                msg = "Downloaded: $name"
            } catch (e: Throwable) { msg = "Download failed: " + (e.message ?: "") }
            Handler(Looper.getMainLooper()).post { toast(msg) }
        }.start()
    }

    // ================= সেভ করা পাসওয়ার্ড =================
    private fun hostOf(t: Tab) = (Uri.parse(t.url).host ?: "").removePrefix("www.")

    private fun offerSave(tab: Tab, u: String, p: String) {
        val host = hostOf(tab)
        if (host.isEmpty() || tab.incognito || pwdAsking || pwds.never(host) || pwds.has(tab.profile, host, u, p)) return
        pwdAsking = true
        AlertDialog.Builder(act, dlgStyle).setTitle("Save password?")
            .setMessage("$host\n${u.ifBlank { "(no username)" }}")
            .setPositiveButton("Save") { _, _ -> pwds.put(tab.profile, host, u, p); toast("Password saved") }
            .setNeutralButton("Never") { _, _ -> pwds.setNever(host) }
            .setNegativeButton("Not now", null).setOnDismissListener { pwdAsking = false }.pshow()
    }

    private fun autofill(tab: Tab) {
        val c = pwds.find(tab.profile, hostOf(tab)) ?: return
        val js = "(function(u,p){function set(el,v){Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))}var pw=document.querySelector('input[type=password]');if(!pw||pw.value)return;var ins=document.querySelectorAll('input[type=text],input[type=email],input[type=tel],input:not([type])');if(ins.length&&u&&!ins[0].value)set(ins[0],u);set(pw,p)})(" + JSONObject.quote(c.user) + "," + JSONObject.quote(c.pass) + ")"
        tab.web.evaluateJavascript(js, null)
    }

    // পাসওয়ার্ড দেখা/কপি/এডিটের আগে প্রতিবার ফোনের লক (পিন/প্যাটার্ন/ফিঙ্গারপ্রিন্ট) খুলতে হয় — আগের আনলক মনে রাখা হয় না
    private fun requireLock(what: String = "view", cb: () -> Unit) {
        val title = when (what) { "copy" -> "Unlock to copy password"; "export" -> "Unlock to export passwords"; "edit" -> "Unlock to edit password"; else -> "Unlock to view password" }
        act.confirmDeviceLock(title) { ok ->
            if (ok) cb() else toast("Phone lock is required to $what passwords")
        }
    }

    private fun copyText(label: String, v: String) {
        (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, v))
    }

    // সাইটের তালিকা — সাইটে চাপলে সেই সাইটের সব ইউজারনেম/পাসওয়ার্ড
    private fun showPasswords() {
        val p = curProfileId()
        val l = pwds.list(p)
        val groups = l.groupBy { it.host }
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle("Saved passwords (${l.size})").setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("Import CSV") { _, _ -> importPasswords(p) }.setNeutralButton("Export CSV") { _, _ -> requireLock("export") { exportPasswords(p) } }.setNegativeButton("Close", null).create()
        if (l.isEmpty()) box.addView(TextView(act).apply {
            text = "Nothing saved yet. When you log in to a site, you will be asked to save the password.\n\nTo bring in your Google passwords: passwords.google.com > Settings > Export > CSV file, then tap Import CSV here."
            textSize = 13f; setTextColor(pc("#C9CEFF")); setPadding(dp(8), dp(8), dp(8), dp(8))
        })
        groups.forEach { (host, creds) ->
            box.addView(TextView(act).apply {
                text = host + "\n" + creds.size + (if (creds.size == 1) " account" else " accounts"); textSize = 15f; setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { dlg.dismiss(); showPwdSite(host) }
            })
        }
        dlg.pshow()
    }

    private fun showPwdSite(host: String) {
        val p = curProfileId()
        val creds = pwds.list(p).filter { it.host == host }
        if (creds.isEmpty()) { showPasswords(); return }
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(4)) }
        val dlg = AlertDialog.Builder(act, dlgStyle).setTitle(host).setView(ScrollView(act).apply { addView(box) })
            .setNegativeButton("Back") { _, _ -> showPasswords() }.create()
        creds.forEachIndexed { idx, c ->
            val card = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(8)) }
            val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(act).apply { text = c.user.ifBlank { "(no username)" }; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END })
            val masked = "Password: " + "•".repeat(minOf(10, maxOf(6, c.pass.length)))
            var shown = false
            val pw = TextView(act).apply { text = masked; textSize = 14f; setPadding(0, dp(4), 0, 0) }
            fun toggle() {
                if (shown) { shown = false; pw.text = masked; return }
                requireLock("view") { shown = true; pw.text = "Password: " + c.pass }
            }
            pw.setOnClickListener { toggle() }
            info.addView(pw)
            top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            fun action(label: String, click: () -> Unit) = TextView(act).apply {
                text = label; textSize = 14f; setTextColor(pc("#F6C85F")); setPadding(dp(10), dp(8), dp(6), dp(8)); setOnClickListener { click() }
            }
            top.addView(action("Show") { toggle() })
            top.addView(action("Edit") { requireLock("edit") { dlg.dismiss(); editPwd(host, c) } })
            top.addView(action("Copy") { requireLock("copy") { copyText("password", c.pass); toast("Password copied") } })
            card.addView(top, LinearLayout.LayoutParams(-1, -2))
            card.addView(TextView(act).apply {
                text = "Remove"; textSize = 14f; setTextColor(pc("#F28B82")); setPadding(0, dp(8), dp(10), dp(6))
                setOnClickListener {
                    AlertDialog.Builder(act, dlgStyle).setTitle("Remove this password?").setMessage(host + "\n" + c.user.ifBlank { "(no username)" })
                        .setPositiveButton("Remove") { _, _ -> pwds.remove(p, host, c.user); dlg.dismiss(); showPwdSite(host) }
                        .setNegativeButton("Cancel", null).pshow()
                }
            })
            box.addView(card)
            if (idx < creds.size - 1) box.addView(View(act).apply { setBackgroundColor(pc("#44F6C85F")) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
        dlg.pshow()
    }

    private fun editPwd(host: String, c: PwdStore.Cred) {
        val p = curProfileId()
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val u = EditText(act).apply { hint = "Username"; setSingleLine(true); setText(c.user) }
        val pw = EditText(act).apply { hint = "Password"; setSingleLine(true); setText(c.pass); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD }
        fun rowWithCopy(et: EditText, what: String): LinearLayout = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(et, LinearLayout.LayoutParams(0, -2, 1f))
            addView(IconView(act, "copy").apply {
                col = pc("#F6C85F")
                setOnClickListener {
                    val v = et.text.toString()
                    if (v.isEmpty()) toast("Nothing to copy") else { copyText(what, v); toast("$what copied") }
                }
            }, LinearLayout.LayoutParams(dp(44), dp(40)))
        }
        box.addView(rowWithCopy(u, "Username")); box.addView(rowWithCopy(pw, "Password"))
        AlertDialog.Builder(act, dlgStyle).setTitle("Edit — $host").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val nu = u.text.toString().trim(); val np = pw.text.toString()
                if (np.isEmpty()) { toast("Password can't be empty"); showPwdSite(host); return@setPositiveButton }
                pwds.remove(p, host, c.user); pwds.put(p, host, nu, np)
                toast("Saved"); showPwdSite(host)
            }
            .setNegativeButton("Cancel") { _, _ -> showPwdSite(host) }.pshow()
    }

    private fun exportPasswords(p: String) {
        val l = pwds.list(p)
        if (l.isEmpty()) { toast("No saved passwords to export"); return }
        fun q(x: String) = "\"" + x.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("name,url,username,password,note\n")
        l.forEach { sb.append(q(it.host)).append(',').append(q("https://" + it.host + "/")).append(',').append(q(it.user)).append(',').append(q(it.pass)).append(",\"\"\n") }
        val name = "SajidBrowser Passwords.csv"
        val uri = try { saveToDownloads(name, "text/csv", sb.toString().toByteArray(Charsets.UTF_8)) } catch (e: Exception) { null }
        if (uri != null) toast("Exported ${l.size} passwords to Downloads: $name") else toast("Export failed")
    }

    private fun importPasswords(p: String) {
        act.pickDoc { uri, _ ->
            if (uri == null) return@pickDoc
            val n = try { pwds.importCsv(p, act.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }) } catch (e: Exception) { 0 }
            toast(if (n > 0) "$n passwords imported" else "Import failed (CSV needs name,url,username,password)")
            showPasswords()
        }
    }

    // ক্রোম/গুগল থেকে এক্সপোর্ট করা বুকমার্ক HTML ইমপোর্ট
    private fun importBookmarks() {
        act.pickDoc { uri, _ ->
            if (uri == null) return@pickDoc
            try {
                val html = act.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                val p = curProfileId(); val arr = bmArr(p); var n = 0
                val have = HashSet<String>(); for (i in 0 until arr.length()) have.add(arr.getJSONObject(i).optString("u"))
                Regex("<A [^>]*HREF=\"([^\"]+)\"[^>]*>(.*?)</A>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { m ->
                    val u = m.groupValues[1]
                    if (u.startsWith("http") && have.add(u)) { arr.put(JSONObject().put("t", m.groupValues[2].replace(Regex("<[^>]+>"), "").trim().ifBlank { u }).put("u", u)); n++ }
                }
                hp.edit().putString(bk(p), arr.toString()).apply()
                toast("$n bookmarks imported")
            } catch (e: Exception) { toast("Import failed") }
        }
    }

    private fun onOff(b: Boolean) = if (b) "ON" else "OFF"
    private fun askBlock(k: String) = if (hp.getString(k, "ask") == "block") "Blocked" else "Ask first"
    private fun flip(k: String, a: String, b: String) { hp.edit().putString(k, if (hp.getString(k, a) == a) b else a).apply() }

    // ===== Settings =====
    private fun showSettings() {
        menuPopup?.dismiss()
        val t = tabs.getOrNull(cur)
        val zoom = hp.getInt("zoom", 100)
        val cacheHost = if (t == null || t.ntp || t.incognito) "" else siteKey(Uri.parse(t.url).host)
        val cacheList = cacheHosts()
        val labels = arrayOf(
            "Search engine: " + engineNames[engineIdx()],
            "Profile",
            "Extensions",
            "Offline cache (this site): " + (if (cacheHost.isNotEmpty() && cacheList.contains(cacheHost)) "ON" else "OFF") + "  (${cacheList.size}/$maxCacheSites)",
            "Text size: $zoom%",
            "Site settings",
            "Homepage customization",
            "Copy cookies",
            "View source"
        )
        AlertDialog.Builder(act, dlgStyle).setTitle("Settings")
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> AlertDialog.Builder(act, dlgStyle).setTitle("Search engine")
                        .setSingleChoiceItems(engineNames, engineIdx()) { d, which ->
                            hp.edit().putInt("engine", which).apply(); d.dismiss()
                            if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
                        }.pshow()
                    1 -> showProfiles()
                    2 -> showExtensions()
                    3 -> toggleSiteCache(t)
                    4 -> {
                        val sizes = intArrayOf(80, 100, 120, 150)
                        AlertDialog.Builder(act, dlgStyle).setTitle("Text size")
                            .setSingleChoiceItems(arrayOf("80%", "100%", "120%", "150%"), maxOf(0, sizes.indexOf(zoom))) { d, which ->
                                hp.edit().putInt("zoom", sizes[which]).apply()
                                tabs.forEach { it.web.settings.textZoom = sizes[which] }
                                d.dismiss()
                            }.pshow()
                    }
                    5 -> showSiteSettings()
                    6 -> showHomeCustom()
                    7 -> copyCookieHeader()
                    8 -> showSource()
                }
            }.setNegativeButton("Close", null).pshow()
    }

    // ===== Site settings: সবার উপরে "All sites" =====
    private fun showSiteSettings() {
        val labels = arrayOf(
            "All sites",
            "Third-party cookies: " + (if (hp.getBoolean("tpc", true)) "Allowed" else "Blocked"),
            "Location: " + askBlock("perm_loc"),
            "Camera: " + askBlock("perm_cam"),
            "Microphone: " + askBlock("perm_mic"),
            "JavaScript: " + (if (hp.getBoolean("js", true)) "Allowed" else "Blocked"),
            "Pop-ups and redirects: " + (if (hp.getString("popups", "allow") == "block") "Blocked" else "Allowed"),
            "Intrusive ads: " + (if (adOn) "Blocked" else "Allowed"),
            "Automatic downloads: " + (if (hp.getString("autodl", "allow") == "block") "Blocked" else "Allowed"),
            "Sound: " + (if (hp.getString("sound", "on") == "mute") "Muted" else "Allowed"),
            "Desktop site (new tabs): " + onOff(hp.getBoolean("deskdef", false))
        )
        AlertDialog.Builder(act, dlgStyle).setTitle("Site settings")
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> { showAllSites(); return@setItems }
                    1 -> { val v = !hp.getBoolean("tpc", true); hp.edit().putBoolean("tpc", v).apply()
                        tabs.forEach { try { CookieManager.getInstance().setAcceptThirdPartyCookies(it.web, v) } catch (e: Exception) { } } }
                    2 -> flip("perm_loc", "ask", "block")
                    3 -> flip("perm_cam", "ask", "block")
                    4 -> flip("perm_mic", "ask", "block")
                    5 -> { val v = !hp.getBoolean("js", true); hp.edit().putBoolean("js", v).apply(); tabs.forEach { it.web.settings.javaScriptEnabled = v } }
                    6 -> flip("popups", "allow", "block")
                    7 -> { adOn = !adOn; reloadCur() }
                    8 -> flip("autodl", "allow", "block")
                    9 -> { flip("sound", "on", "mute"); reloadCur() }
                    10 -> hp.edit().putBoolean("deskdef", !hp.getBoolean("deskdef", false)).apply()
                }
                showSiteSettings()
            }.setNegativeButton("Back") { _, _ -> showSettings() }.pshow()
    }

    // ===== All sites: প্রতিটা সাইটের কুকিজ / সাইন আউট / ক্যাশ আলাদা আলাদা মোছা যায় =====
    private var sitesOverlay: View? = null
    private fun closeSites() { sitesOverlay?.let { removeView(it) }; sitesOverlay = null; hideKeyboard() }

    private fun profCookies(): CookieManager = cookieMgrFor(curProf, false)

    // এই সাইটের (ও তার সাবডোমেইনের) সব পরিচিত লিংক — কুকি খুঁজতে/মুছতে লাগে
    private fun hostUrls(host: String): List<String> {
        val set = LinkedHashSet<String>()
        set.add("https://$host/"); set.add("http://$host/"); set.add("https://www.$host/"); set.add("https://m.$host/")
        fun add(u: String?) {
            if (u.isNullOrBlank() || !u.startsWith("http")) return
            val h = try { siteKey(Uri.parse(u).host) } catch (e: Exception) { return }
            if (h == host || h.endsWith(".$host") || host.endsWith(".$h")) set.add(u)
        }
        tabs.filter { it.profile == curProf && !it.incognito && !it.ntp }.forEach { add(it.url) }
        try { val ha = histArr(curProf); for (i in 0 until minOf(ha.length(), 400)) add(ha.getJSONObject(i).optString("u")) } catch (e: Exception) { }
        return set.toList().take(40)
    }

    private fun cookieNames(host: String): Set<String> {
        val cm = profCookies(); val names = HashSet<String>()
        for (u in hostUrls(host)) {
            val raw = try { cm.getCookie(u) } catch (e: Throwable) { null }
            if (!raw.isNullOrBlank()) raw.split(";").forEach { val n = it.substringBefore("=").trim(); if (n.isNotEmpty()) names.add(n) }
        }
        return names
    }

    private fun cookieCount(host: String): Int = cookieNames(host).size

    private fun hiddenKey(p: String) = "hiddenSites_$p"
    private fun hiddenHosts(p: String): MutableSet<String> = HashSet(hp.getStringSet(hiddenKey(p), emptySet()) ?: emptySet())
    // সাইটের সব ডাটা মুছলে সাইটের নামও All sites তালিকা থেকে চলে যায় (আবার ঢুকলে ফিরে আসবে)
    private fun forgetSite(host: String) {
        val p = curProf; val h = siteKey(host)
        val hs = hiddenHosts(p); hs.add(h); hp.edit().putStringSet(hiddenKey(p), hs).apply()
        try {
            val ha = histArr(p); val nh = JSONArray()
            for (i in 0 until ha.length()) { val o = ha.getJSONObject(i); if (siteKey(try { Uri.parse(o.optString("u")).host } catch (e: Exception) { null }) != h) nh.put(o) }
            hp.edit().putString(hk(p), nh.toString()).apply()
            val cl = cacheHosts(); if (cl.remove(h)) saveCacheHosts(cl)
        } catch (e: Exception) { }
    }
    private fun unhideSite(p: String, url: String?) {
        try {
            val h = siteKey(Uri.parse(url ?: "").host)
            if (h.isEmpty()) return
            val hs = hiddenHosts(p); if (hs.remove(h)) hp.edit().putStringSet(hiddenKey(p), hs).apply()
        } catch (e: Exception) { }
    }

    private fun knownHosts(): List<String> {
        val p = curProf
        val set = java.util.TreeSet<String>()
        fun add(u: String?) { val h = siteKey(try { Uri.parse(u ?: "").host } catch (e: Exception) { null }); if (h.isNotEmpty() && h.contains('.')) set.add(h) }
        val ha = histArr(p); for (i in 0 until ha.length()) add(ha.getJSONObject(i).optString("u"))
        val ba = bmArr(p); for (i in 0 until ba.length()) add(ba.getJSONObject(i).optString("u"))
        loadShortcuts(p).forEach { add(it.url) }
        pwds.list(p).forEach { if (it.host.contains('.')) set.add(siteKey(it.host)) }
        cacheHosts().forEach { set.add(it) }
        tabs.filter { it.profile == p && !it.incognito && !it.ntp }.forEach { add(it.url) }
        val hid = hiddenHosts(p)
        return set.filter { !hid.contains(it) }
    }

    // শুধু কুকিজ মোছে (সাইন-ইন কুকিও এর মধ্যে) — আসলে কয়টা মুছল সেটা ফেরত দেয়
    private fun clearHostCookies(host: String): Int {
        val cm = profCookies()
        val removed = HashSet<String>()
        val exp = "; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
        for (u in hostUrls(host)) {
            val uri = try { Uri.parse(u) } catch (e: Exception) { continue }
            val uh = uri.host ?: continue
            val raw = (try { cm.getCookie(u) } catch (e: Throwable) { null }) ?: continue
            val names = raw.split(";").map { it.substringBefore("=").trim() }.filter { it.isNotEmpty() }
            if (names.isEmpty()) continue
            val doms = ArrayList<String>(); var h = uh
            while (h.contains('.')) { doms.add(h); h = h.substringAfter('.') }
            val paths = LinkedHashSet<String>(); paths.add("/")
            var acc = ""
            (uri.path ?: "/").split('/').filter { it.isNotEmpty() }.take(3).forEach { acc += "/$it"; paths.add(acc) }
            for (n in names) {
                removed.add(n)
                for (pth in paths) for (sc in listOf("https", "http")) {
                    val base = "$sc://$uh/"
                    val body = "$n=$exp; Path=$pth"
                    val secs = if (sc == "https") listOf("", "; Secure") else listOf("")
                    for (sec in secs) {
                        cm.setCookie(base, body + sec)
                        for (d in doms) { cm.setCookie(base, "$body; Domain=$d$sec"); cm.setCookie(base, "$body; Domain=.$d$sec") }
                    }
                }
            }
        }
        try { cm.flush() } catch (e: Throwable) { }
        return removed.size
    }

    private val WIPE_JS = "(function(){try{localStorage.clear()}catch(e){}try{sessionStorage.clear()}catch(e){}try{if(window.indexedDB&&indexedDB.databases)indexedDB.databases().then(function(l){l.forEach(function(d){indexedDB.deleteDatabase(d.name)})})}catch(e){}try{if(window.caches)caches.keys().then(function(k){k.forEach(function(n){caches.delete(n)})})}catch(e){}try{if(navigator.serviceWorker)navigator.serviceWorker.getRegistrations().then(function(r){r.forEach(function(x){x.unregister()})})}catch(e){}})()"

    private fun tabsOfHost(host: String): List<Tab> = tabs.filter {
        it.profile == curProf && !it.incognito && !it.ntp &&
            siteKey(try { Uri.parse(it.url).host } catch (e: Exception) { null }).let { h -> h == host || h.endsWith(".$host") }
    }

    // এই প্রোফাইলের নিজের WebStorage (আলাদা প্রোফাইল থাকলে সেটার), না থাকলে সাধারণটা
    private fun storageFor(): WebStorage {
        val name = webProfileName(curProf, false)
        if (name != null) {
            try {
                val s = storeObj()
                if (s != null) {
                    val pr = s.javaClass.getMethod("getOrCreateProfile", String::class.java).invoke(s, name)
                    if (pr != null) return pr.javaClass.getMethod("getWebStorage").invoke(pr) as WebStorage
                }
            } catch (e: Throwable) { }
        }
        return WebStorage.getInstance()
    }

    // সাইটের লোকাল স্টোরেজ / IndexedDB / সার্ভিস ওয়ার্কার / সাইটের ক্যাশ-স্টোরেজ মোছে
    private fun clearSiteStorage(host: String) {
        tabsOfHost(host).forEach { try { it.web.evaluateJavascript(WIPE_JS, null) } catch (e: Throwable) { } }
        val ws = storageFor()
        val hs = LinkedHashSet<String>()
        hostUrls(host).forEach { try { Uri.parse(it).host?.let { h -> hs.add(h) } } catch (e: Exception) { } }
        hs.add(host); hs.add("www.$host"); hs.add("m.$host")
        for (d in hs) for (sc in listOf("https", "http")) try { ws.deleteOrigin("$sc://$d") } catch (e: Throwable) { }
    }

    private fun clearSiteCache(host: String) {
        Thread { try { OfflineStore.clearSite(act, host) } catch (e: Throwable) { } }.start()
        tabs.forEach { try { it.web.clearCache(true) } catch (e: Exception) { } }
    }

    private fun reloadHostTabs(host: String) {
        tabs.filter { !it.ntp && siteKey(try { Uri.parse(it.url).host } catch (e: Exception) { null }) == host }
            .forEach { try { it.web.reload() } catch (e: Exception) { } }
    }

    // কাজ আলাদা আলাদা: কুকিজ / স্টোরেজ(সাইন আউট) / ক্যাশ — যেটা বলা হবে শুধু সেটাই হবে। শেষ হলে done(মুছে যাওয়া কুকির সংখ্যা)
    private fun wipeSite(host: String, cookies: Boolean, storage: Boolean, cache: Boolean, done: (Int) -> Unit) {
        if (storage) tabsOfHost(host).forEach { try { it.web.evaluateJavascript(WIPE_JS, null) } catch (e: Throwable) { } }
        Handler(Looper.getMainLooper()).postDelayed({
            var n = 0
            try {
                if (storage) clearSiteStorage(host)
                if (cookies) n = clearHostCookies(host)
                if (cache) clearSiteCache(host)
            } catch (e: Throwable) { }
            if (cookies || storage) reloadHostTabs(host)
            done(n)
        }, if (storage) 400L else 0L)
    }

    private fun clearAllSiteData(host: String) { wipeSite(host, true, true, true) { } }

    private fun showSiteActions(host: String, after: () -> Unit) {
        AlertDialog.Builder(act, dlgStyle).setTitle(host)
            .setItems(arrayOf("Clear cookies (${cookieCount(host)})", "Sign out (cookies + site storage)", "Clear cache", "Clear all data for this site")) { _, i ->
                when (i) {
                    0 -> wipeSite(host, true, false, false) { n -> toast(if (n > 0) "$n cookies cleared" else "No cookies found"); after() }
                    1 -> wipeSite(host, true, true, false) { n -> toast("Signed out of $host"); after() }
                    2 -> wipeSite(host, false, false, true) { _ -> toast("Cache cleared"); after() }
                    else -> wipeSite(host, true, true, true) { _ -> forgetSite(host); toast("All data for $host cleared"); after() }
                }
            }.setNegativeButton("Close", null).pshow()
    }

    private fun showAllSites() {
        closeSites(); menuPopup?.dismiss()
        val bg = pc("#252A25"); val cardC = pc("#343A34"); val sub = pc("#C9CEC9")
        val root = FrameLayout(act).apply { setBackgroundColor(bg); isClickable = true }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(24)) }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ibtn("back") { closeSites() }, LinearLayout.LayoutParams(-2, dp(64)))
        head.addView(TextView(act).apply { text = "All sites"; textSize = 22f; setTextColor(ink); setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(head, LinearLayout.LayoutParams(-1, dp(64)))
        val search = EditText(act).apply {
            hint = "Search sites"; textSize = 14f; setSingleLine(true)
            setTextColor(ink); setHintTextColor(pc("#8C938C")); setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply { setColor(pc("#2B302B")); cornerRadius = dp(22).toFloat() }
        }
        col.addView(search, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(10) })
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ScrollView(act).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun fill(q: String) {
            list.removeAllViews()
            val hosts = knownHosts().filter { q.isBlank() || it.contains(q.trim(), true) }
            if (hosts.isEmpty()) list.addView(TextView(act).apply { text = "No sites yet. Sites you visit will appear here."; textSize = 14f; setTextColor(sub); gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0) })
            hosts.forEach { h ->
                val n = cookieCount(h)
                val row = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(14), dp(10), dp(14))
                    background = GradientDrawable().apply { setColor(cardC); cornerRadius = dp(16).toFloat() }
                }
                val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                tx.addView(TextView(act).apply { text = h; textSize = 17f; setTextColor(ink); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END })
                tx.addView(TextView(act).apply { text = if (n == 0) "No cookies" else if (n == 1) "1 cookie" else "$n cookies"; textSize = 14f; setTextColor(sub) })
                row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(act).apply {
                    text = "Delete"; textSize = 14f; setTextColor(pc("#F28B82")); setPadding(dp(14), dp(10), dp(10), dp(10))
                    setOnClickListener {
                        AlertDialog.Builder(act, dlgStyle).setTitle("Delete all data for $h?")
                            .setMessage("Cookies, sign-in, site storage and cache of this site will be deleted.")
                            .setPositiveButton("Delete") { _, _ -> wipeSite(h, true, true, true) { _ -> forgetSite(h); toast("Deleted"); fill(search.text.toString()) } }
                            .setNegativeButton("Cancel", null).pshow()
                    }
                })
                row.setOnClickListener { showSiteActions(h) { fill(search.text.toString()) } }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            }
        }
        search.doAfterTextChanged { fill(it?.toString() ?: "") }
        fill("")
        sitesOverlay = root
        addView(root, FrameLayout.LayoutParams(-1, -1))
    }

    // সাইট পারমিশন চাইলে Allow/Block জিজ্ঞেস
    private fun askSitePerm(host: String, what: String, cb: (Boolean) -> Unit) {
        var done = false
        fun fin(v: Boolean) { if (!done) { done = true; cb(v) } }
        AlertDialog.Builder(act, dlgStyle).setTitle(host.ifBlank { "This site" })
            .setMessage("Wants to use $what.")
            .setPositiveButton("Allow") { _, _ -> fin(true) }
            .setNegativeButton("Block") { _, _ -> fin(false) }
            .setOnCancelListener { fin(false) }.pshow()
    }

    // ===== Theme: Light / Dark / System default — ব্রাউজার UI ও ওয়েবসাইট দুটোই ফোনের থিমের মতো বদলায় =====
    private fun showTheme() {
        val keys = arrayOf("light", "dark", "system")
        val names = arrayOf("Light", "Dark", "System default")
        val curKey = hp.getString("theme", "dark") ?: "dark"
        AlertDialog.Builder(act, dlgStyle).setTitle("Theme")
            .setSingleChoiceItems(names, maxOf(0, keys.indexOf(curKey))) { d, which -> d.dismiss(); applyTheme(keys[which]) }
            .setNegativeButton("Cancel", null).pshow()
    }

    // WebView-কে অ্যাপের থিম অনুযায়ী ডার্ক/লাইট করে (ওয়েবসাইটও ফোনের ডার্ক মোডের মতো বদলায়)
    private val nativeDark: Boolean by lazy {
        try {
            androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING) ||
                androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)
        } catch (e: Throwable) { false }
    }

    private fun applyWebTheme(w: WebView) {
        val dark = !light
        try { if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, dark) } catch (e: Throwable) { }
        try { if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) androidx.webkit.WebSettingsCompat.setForceDark(w.settings, if (dark) 2 else 0) } catch (e: Throwable) { }
        try { if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY)) androidx.webkit.WebSettingsCompat.setForceDarkStrategy(w.settings, 2) } catch (e: Throwable) { }
    }

    private fun applyTheme(v: String) {
        if (hp.getString("theme", "dark") == v) return
        hp.edit().putString("theme", v).putBoolean("reopen", visibility == View.VISIBLE && !mini).apply()
        persist()
        act.recreate()
    }

    // ===== Homepage customization: হোম পেজের বড় লেখা (ডিফল্ট "Google") ও তার রঙ =====
    private fun showHomeCustom() {
        val cols = listOf("#FFFFFF", "#F6C85F", "#1A73E8", "#E8710A", "#188038", "#A142F4", "#D93025", "#00897B", "#FF4081", "#14183A")
        var chosen = hp.getInt("ntpColor", 0)
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        val preview = TextView(act).apply { textSize = 34f; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(14)) }
        val et = EditText(act).apply { hint = engineNames[engineIdx()]; setSingleLine(true); setText(hp.getString("ntpTitle", "") ?: "") }
        val hex = EditText(act).apply {
            hint = "#RRGGBB"; setSingleLine(true)
            if (chosen != 0) setText(String.format("#%06X", chosen and 0xFFFFFF))
        }
        fun paint() {
            preview.text = et.text.toString().ifBlank { engineNames[engineIdx()] }
            preview.setTextColor(if (chosen != 0) chosen else ink)
        }
        et.doAfterTextChanged { paint() }
        hex.doAfterTextChanged {
            val h = it?.toString()?.trim() ?: ""
            try { chosen = Color.parseColor(if (h.startsWith("#")) h else "#$h") or (0xFF shl 24) } catch (e: Exception) { if (h.isEmpty()) chosen = 0 }
            paint()
        }
        box.addView(preview, LinearLayout.LayoutParams(-1, -2))
        box.addView(TextView(act).apply { text = "Text (anything you like)"; textSize = 13f })
        box.addView(et, LinearLayout.LayoutParams(-1, -2))
        box.addView(TextView(act).apply { text = "Pick a colour"; textSize = 13f; setPadding(0, dp(14), 0, dp(6)) })
        cols.chunked(5).forEach { rowCols ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowCols.forEach { hc ->
                val cv = Color.parseColor(hc)
                row.addView(View(act).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cv); setStroke(dp(1), Color.parseColor("#88888888")) }
                    setOnClickListener { chosen = cv or (0xFF shl 24); hex.setText(String.format("#%06X", cv and 0xFFFFFF)); paint() }
                }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) })
            }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        box.addView(TextView(act).apply { text = "Or hex code"; textSize = 13f; setPadding(0, dp(10), 0, 0) })
        box.addView(hex, LinearLayout.LayoutParams(-1, -2))
        paint()
        AlertDialog.Builder(act, dlgStyle).setTitle("Homepage customization")
            .setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                hp.edit().putString("ntpTitle", et.text.toString().trim()).putInt("ntpColor", chosen).apply()
                if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
                toast("Homepage updated")
            }
            .setNeutralButton("Reset") { _, _ ->
                hp.edit().remove("ntpTitle").remove("ntpColor").apply()
                if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
            }
            .setNegativeButton("Cancel", null).pshow()
    }

    // ===== Translate (Google Translate পেজ দিয়ে) =====
    private fun showTranslate() {
        val t = tabs.getOrNull(cur)
        if (t == null || t.ntp || !t.url.startsWith("http")) { toast("Open a web page first"); return }
        val names = arrayOf("Bengali", "English", "Hindi", "Arabic", "Urdu", "Spanish", "French", "German", "Chinese", "Japanese", "Russian", "Turkish", "Indonesian")
        val codes = arrayOf("bn", "en", "hi", "ar", "ur", "es", "fr", "de", "zh-CN", "ja", "ru", "tr", "id")
        val back = t.transFrom != null
        val items = if (back) arrayOf("Original page") + names else names
        AlertDialog.Builder(act, dlgStyle).setTitle("Translate to")
            .setItems(items) { _, i ->
                if (back && i == 0) { val o = t.transFrom!!; t.transFrom = null; loadSmart(t, o); return@setItems }
                val k = if (back) i - 1 else i
                val orig = t.transFrom ?: t.url
                t.transFrom = orig
                loadSmart(t, "https://translate.google.com/translate?sl=auto&tl=" + codes[k] + "&u=" + Uri.encode(orig))
            }.setNegativeButton("Cancel", null).pshow()
    }

    // ===== View source =====
    private var srcOverlay: View? = null
    private fun closeSource() { srcOverlay?.let { removeView(it) }; srcOverlay = null }

    private fun showSource() {
        val t = tabs.getOrNull(cur)
        if (t == null || t.ntp) { toast("Open a web page first"); return }
        closeSource(); menuPopup?.dismiss(); hideKeyboard()
        val root = FrameLayout(act).apply { setBackgroundColor(pc("#0B0F24")); isClickable = true }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        var full = ""
        val body = TextView(act).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextColor(pc("#DDE3FF")); setTextIsSelectable(true)
            setPadding(dp(10), dp(8), dp(10), dp(80))
        }
        val urlIn = EditText(act).apply {
            setText(t.url); textSize = 13f; setSingleLine(true); setTextColor(ink); setHintTextColor(pc("#8890B0")); hint = "https://..."
            background = GradientDrawable().apply { setColor(pc("#1A2040")); cornerRadius = dp(18).toFloat() }
            setPadding(dp(14), 0, dp(14), 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        fun setSrc(txt: String) {
            full = txt
            body.text = if (txt.length > 200000) txt.take(200000) + "\n\n… (too large, the rest is not shown; tap Copy to copy everything)" else txt
        }
        fun load(u: String) {
            body.text = "Loading..."
            Thread {
                var txt: String? = null
                if (u.startsWith("http")) try {
                    val cn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    cn.instanceFollowRedirects = true; cn.connectTimeout = 15000; cn.readTimeout = 20000
                    cn.setRequestProperty("User-Agent", mobileUa.ifEmpty { desktopUa })
                    cn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
                    cookieMgr(t).getCookie(u)?.let { if (it.isNotEmpty()) cn.setRequestProperty("Cookie", it) }
                    val ct = cn.contentType ?: ""
                    val cs = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)?.trim('"', '\'') ?: "UTF-8"
                    val bytes = cn.inputStream.use { ins ->
                        val o = java.io.ByteArrayOutputStream(); val b = ByteArray(16384); var tot = 0
                        while (true) { val n = ins.read(b); if (n < 0) break; o.write(b, 0, n); tot += n; if (tot > 3 * 1024 * 1024) break }
                        o.toByteArray()
                    }
                    txt = try { String(bytes, charset(cs)) } catch (e: Exception) { String(bytes, Charsets.UTF_8) }
                } catch (e: Throwable) { }
                val result = txt
                post {
                    if (result != null) setSrc(result)
                    else if (u == t.url) {
                        // নেট দিয়ে আনা না গেলে (অফলাইন/ব্লক) পেজে এখন যা আছে সেটাই দেখায়
                        t.web.evaluateJavascript("document.documentElement.outerHTML") { r ->
                            val h = try { org.json.JSONTokener(r).nextValue() as? String } catch (e: Exception) { null }
                            if (h != null) setSrc(h) else body.text = "Couldn't load source"
                        }
                    } else body.text = "Couldn't load source"
                }
            }.start()
        }
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ibtn("back") { closeSource() }, LinearLayout.LayoutParams(-2, dp(52)))
        head.addView(TextView(act).apply { text = "View source"; textSize = 20f; setTextColor(ink) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(btn("Copy", 14f, 14) {
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("source", full)); toast("Source copied")
        }, LinearLayout.LayoutParams(-2, dp(52)))
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(8), dp(6)) }
        row.addView(urlIn, LinearLayout.LayoutParams(0, dp(40), 1f))
        row.addView(ibtn("go", pc("#F6C85F")) { hideKeyboard(); load(toUrl(urlIn.text.toString())) }, LinearLayout.LayoutParams(-2, dp(40)))
        col.addView(head, LinearLayout.LayoutParams(-1, dp(52)))
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
        col.addView(ScrollView(act).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        addView(root, FrameLayout.LayoutParams(-1, -1))
        srcOverlay = root
        load(t.url)
    }

    // সরাসরি কুকি হেডার (name=value; name2=value2) কপি
    private fun copyCookieHeader() {
        val t = tabs.getOrNull(cur)
        val cm = cookieMgr(t)
        cm.flush()
        val ck = cm.getCookie(t?.web?.url ?: "")
        if (ck.isNullOrEmpty()) { toast("No cookies for this site"); return }
        (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("cookies", ck))
        toast("Cookies copied")
    }

    // ডেস্কটপ সাইট চালু হলে সাইট ডেস্কটপ আকারে আসে; পিঞ্চ করে ছোট-বড় (জুম) করা যায়
    private fun toggleDesktop() {
        val t = tabs.getOrNull(cur) ?: return
        t.desktop = !t.desktop
        t.web.settings.apply {
            userAgentString = if (t.desktop) desktopUa else mobileUa
            useWideViewPort = true; loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }
        toast(if (t.desktop) "Desktop site on" else "Mobile site on")
        t.web.reload()
    }

    private fun openExternal() {
        val u = currentWeb()?.url ?: return
        try {
            act.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Open with"))
        } catch (e: Exception) { toast("No browser found") }
    }

    // ================= ছবি সেভ (প্রোফাইল ও শর্টকাট আইকন) =================
    private fun saveSquareImage(uri: Uri, name: String): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        act.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var ss = 1
        while (bounds.outWidth / ss > 600 && bounds.outHeight / ss > 600) ss *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = ss }
        val bmp = act.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) false else {
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
            java.io.File(act.filesDir, name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Throwable) { false }

    private fun profFile(id: String) = java.io.File(act.filesDir, "profile_$id.png")
    private fun iconFile(p: String, id: String) = java.io.File(act.filesDir, "sc_${p}_$id.png")

    private fun circleBitmap(f: java.io.File): android.graphics.drawable.Drawable? {
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return RoundedBitmapDrawableFactory.create(resources, b).also { it.isCircular = true }
    }

    private fun profAvatar(p: Prof, size: Int, tsp: Float): TextView {
        val tv = avatar(initial(p), p.color, size, tsp)
        val d = circleBitmap(profFile(p.id))
        if (d != null) { tv.text = ""; tv.background = d }
        return tv
    }

    private fun tileIcon(p: String, sc: Sc, size: Int): TextView {
        val tv = avatar(sc.name.take(1).uppercase().ifEmpty { "?" }, colorFor(sc.name), size, 20f)
        val d = circleBitmap(iconFile(p, sc.id))
        if (d != null) { tv.text = ""; tv.background = d }
        return tv
    }

    private fun editProfile(p: Prof) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val nameEt = EditText(act).apply { hint = "Profile name"; setSingleLine(true); setText(p.name) }
        var color = p.color
        var pendingUri: Uri? = null
        var removePic = false
        val colorRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        palette.forEach { c ->
            val dot = avatar("", c, 34, 12f)
            dot.setOnClickListener { color = c; removePic = true; pendingUri = null; toast("Colour selected") }
            colorRow.addView(dot, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(dp(4), dp(8), dp(4), dp(8)) })
        }
        val pick = Button(act).apply {
            text = "Choose picture"
            setOnClickListener { act.pickImage { u -> if (u != null) { pendingUri = u; removePic = false; toast("Picture selected — tap Save") } } }
        }
        val noPic = Button(act).apply { text = "Remove picture"; setOnClickListener { removePic = true; pendingUri = null; toast("Picture will be removed") } }
        box.addView(nameEt); box.addView(colorRow); box.addView(pick); box.addView(noPic)
        AlertDialog.Builder(act, dlgStyle).setTitle("Edit profile").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = nameEt.text.toString().trim()
                if (n.isNotEmpty()) p.name = n
                p.color = color
                if (removePic) profFile(p.id).delete()
                pendingUri?.let { saveSquareImage(it, profFile(p.id).name) }
                saveProfiles()
                if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
            }.setNegativeButton("Cancel", null).pshow()
    }

    // ================= হোম পেজের শর্টকাট: যোগ, এডিট, মোছা, আইকন বদল =================
    private fun scKey(p: String) = if (p == "p1") "shortcuts" else "shortcuts_$p"

    private fun saveShortcuts(p: String, l: List<Sc>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url)) }
        hp.edit().putString(scKey(p), a.toString()).apply()
    }

    private fun loadShortcuts(p: String): MutableList<Sc> {
        val list = mutableListOf<Sc>()
        val raw = hp.getString(scKey(p), null)
        if (raw == null) {
            listOf("YouTube" to "https://m.youtube.com/", "Google" to "https://www.google.com/", "GitHub" to "https://github.com/",
                "Wikipedia" to "https://www.wikipedia.org/", "Facebook" to "https://m.facebook.com/", "Claude" to "https://claude.ai/")
                .forEachIndexed { i, d -> list.add(Sc("d$i", d.first, d.second)) }
            saveShortcuts(p, list)
            return list
        }
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); list.add(Sc(o.getString("id"), o.getString("n"), o.getString("u"))) }
        } catch (e: Exception) { }
        return list
    }

    private var scDropped = false
    // শর্টকাট নিজের পছন্দমতো জায়গায় সরানো (ড্র্যাগ করে অন্য শর্টকাটের উপর ছাড়লে সেই জায়গায় বসে)
    private fun moveShortcut(p: String, fromId: String, toId: String) {
        val list = loadShortcuts(p)
        val fi = list.indexOfFirst { it.id == fromId }
        if (fi < 0) return
        val toIdx = if (toId == "ADD") list.size else list.indexOfFirst { it.id == toId }
        if (toIdx < 0) return
        val item = list.removeAt(fi)
        list.add(minOf(toIdx, list.size), item)
        saveShortcuts(p, list)
        post { if (tabs.getOrNull(cur)?.ntp == true) refreshNtp() }
    }

    private fun showShortcutMenu(p: String, sc: Sc) {
        AlertDialog.Builder(act, dlgStyle).setTitle(sc.name)
            .setItems(arrayOf("Edit", "Delete")) { _, i ->
                if (i == 0) editShortcut(p, sc)
                else {
                    val list = loadShortcuts(p)
                    list.removeAll { it.id == sc.id }
                    iconFile(p, sc.id).delete()
                    saveShortcuts(p, list)
                    if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
                }
            }.setNegativeButton("Close", null).pshow()
    }

    private fun editShortcut(p: String, sc: Sc?) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val nameEt = EditText(act).apply { hint = "Name"; setSingleLine(true); setText(sc?.name ?: "") }
        val urlEt = EditText(act).apply {
            hint = "Link (https://...)"; setSingleLine(true); setText(sc?.url ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        var pendingUri: Uri? = null
        var resetIcon = false
        val pick = Button(act).apply {
            text = "Choose icon"
            setOnClickListener { act.pickImage { u -> if (u != null) { pendingUri = u; resetIcon = false; toast("Icon selected — tap Save") } } }
        }
        val reset = Button(act).apply { text = "Default icon"; setOnClickListener { resetIcon = true; pendingUri = null; toast("Default icon will be used") } }
        box.addView(nameEt); box.addView(urlEt); box.addView(pick); box.addView(reset)
        AlertDialog.Builder(act, dlgStyle).setTitle(if (sc == null) "New shortcut" else "Edit shortcut").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val raw = urlEt.text.toString().trim()
                if (raw.isEmpty()) { toast("Enter a link"); return@setPositiveButton }
                val u = toUrl(raw)
                val n = nameEt.text.toString().trim().ifEmpty { (Uri.parse(u).host ?: u).removePrefix("www.") }
                val list = loadShortcuts(p)
                val target = if (sc == null) Sc("s" + System.currentTimeMillis(), n, u).also { list.add(it) } else list.firstOrNull { it.id == sc.id } ?: Sc(sc.id, n, u).also { list.add(it) }
                target.name = n; target.url = u
                if (resetIcon) iconFile(p, target.id).delete()
                pendingUri?.let { saveSquareImage(it, iconFile(p, target.id).name) }
                saveShortcuts(p, list)
                if (tabs.getOrNull(cur)?.ntp == true) refreshNtp()
            }.setNegativeButton("Cancel", null).pshow()
    }

    private var blobJobs = 0
    private fun blobBegin(name: String) {
        blobJobs++; DlService.begin(act, name)
        Handler(Looper.getMainLooper()).postDelayed({ blobEnd() }, 20 * 60 * 1000L)
    }
    private fun blobEnd() { if (blobJobs > 0) { blobJobs--; DlService.end(act) } }

    private fun enqueue(url: String, ua: String?, mime: String?, disp: String?, tab: Tab) {
        if (url.startsWith("blob:")) {
            toast("Downloading...")
            val dflt = tab.title.ifBlank { "download" }
            blobBegin(dflt)
            tab.web.evaluateJavascript(tab.dlJs + ";window.__bdlSend(" + JSONObject.quote(url) + "," + JSONObject.quote(dflt) + ")", null)
            return
        }
        if (url.startsWith("data:")) {
            Thread {
                try {
                    val head = url.substringBefore(",")
                    val body = url.substringAfter(",")
                    val m = head.removePrefix("data:").substringBefore(";").ifBlank { "application/octet-stream" }
                    val bytes = if (head.contains(";base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT) else Uri.decode(body).toByteArray()
                    val nm = fileNameFor(tab.title.ifBlank { "download" }, m)
                    val uri = saveToDownloads(nm, m, bytes)
                    Handler(Looper.getMainLooper()).post { downloadedDialog(nm, uri, m) }
                } catch (e: Throwable) { Handler(Looper.getMainLooper()).post { toast("Download failed: " + (e.message ?: "")) } }
            }.start()
            return
        }
        if (!(url.startsWith("http://") || url.startsWith("https://"))) { toast("This type of link can't be downloaded"); return }
        val cookie = cookieMgr(tab).getCookie(url)
        val uaStr = ua ?: tab.web.settings.userAgentString
        val ref = tab.web.url
        toast("Starting download...")
        // আসল ফাইলের নাম/টাইপ আগে জেনে নিই (Drive ইত্যাদিতে .bin হয়ে যাওয়া ঠেকাতে)
        Thread {
            var name = ""
            var realMime = mime ?: ""
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", uaStr)
                if (cookie != null) c.setRequestProperty("Cookie", cookie)
                if (ref != null) c.setRequestProperty("Referer", ref)
                c.setRequestProperty("Range", "bytes=0-15")
                val code = c.responseCode
                if (code in 200..299) {
                    val cdh = c.getHeaderField("Content-Disposition") ?: disp
                    name = dispositionName(cdh)
                    val ct = (c.contentType ?: "").substringBefore(";").trim()
                    if (ct.isNotEmpty() && !ct.equals("application/octet-stream", true) && !ct.equals("binary/octet-stream", true)) realMime = ct
                    val head = try { c.inputStream.use { ins -> val b = ByteArray(16); val n = ins.read(b); if (n > 0) b.copyOf(n) else ByteArray(0) } } catch (e: Exception) { ByteArray(0) }
                    val sm = sniffMime(head)
                    if (sm != null && (realMime.isBlank() || realMime.contains("octet", true) || (realMime.contains("html", true) && sm == "application/pdf"))) realMime = sm
                }
                c.disconnect()
            } catch (e: Exception) { }
            val finalMime = realMime
            val htmlPage = finalMime.startsWith("text/html", true) && name.isBlank()
            val finalName = fileNameFor(name.ifBlank { URLUtil.guessFileName(url, disp, finalMime.ifBlank { null }) }, finalMime.ifBlank { "application/octet-stream" })
            Handler(Looper.getMainLooper()).post {
                if (htmlPage) {
                    // ওয়েব পেজ কখনো HTML ফাইল হিসেবে নামবে না
                    toast("This link is a web page, not a downloadable file")
                    return@post
                }
                try {
                    val r = DownloadManager.Request(Uri.parse(url))
                    if (cookie != null) r.addRequestHeader("Cookie", cookie)
                    r.addRequestHeader("User-Agent", uaStr)
                    if (ref != null) r.addRequestHeader("Referer", ref)
                    if (finalMime.isNotBlank()) r.setMimeType(finalMime)
                    r.setAllowedOverMetered(true); r.setAllowedOverRoaming(true)
                    r.setTitle(finalName)
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalName)
                    val id = (act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
                    dlIds[id] = finalName
                    toast("Download started: $finalName")
                } catch (e: Exception) { toast("Couldn't start download: " + (e.message ?: "")) }
            }
        }.start()
    }

    // Content-Disposition থেকে ফাইলের নাম (filename*=UTF-8''... সহ)
    private fun dispositionName(cd: String?): String {
        if (cd.isNullOrBlank()) return ""
        try {
            Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let {
                val cs = it.groupValues[1].ifBlank { "UTF-8" }
                return java.net.URLDecoder.decode(it.groupValues[2].trim().trim('"').replace("+", "%2B"), cs)
            }
            Regex("filename\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(cd)?.let { return it.groupValues[1] }
            Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let { return it.groupValues[1].trim() }
        } catch (e: Exception) { }
        return ""
    }

    // ফাইলের প্রথম কয়েক বাইট দেখে টাইপ চেনা
    private fun sniffMime(b: ByteArray): String? {
        fun starts(vararg x: Int) = b.size >= x.size && x.indices.all { (b[it].toInt() and 0xFF) == x[it] }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
            starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
            starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            starts(0x47, 0x49, 0x46, 0x38) -> "image/gif"
            starts(0x50, 0x4B, 0x03, 0x04) -> "application/zip"
            else -> null
        }
    }

    // ================= প্রিন্ট / PDF সেভ (window.print() এও চলে) =================
    private fun printTab(t: Tab) {
        try {
            val pm = act.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            val nm = t.title.ifBlank { "document" }
            pm.print(nm, t.web.createPrintDocumentAdapter(nm), android.print.PrintAttributes.Builder().build())
        } catch (e: Exception) { toast("Couldn't start printing") }
    }

    // ================= ডাউনলোড শেষে প্রিভিউ / খোলা =================
    private fun downloadedDialog(name: String, uri: Uri?, mime: String) {
        if (uri == null) { toast("Downloaded: $name"); return }
        val isPdf = mime.contains("pdf", true) || name.lowercase().endsWith(".pdf")
        try {
            val b = AlertDialog.Builder(act, dlgStyle).setTitle("Downloaded").setMessage(name)
            if (isPdf) b.setPositiveButton("Preview") { _, _ -> showPdf(uri, name) }
            else b.setPositiveButton("Open") { _, _ -> openFileExternal(uri, mime) }
            if (isPdf) b.setNeutralButton("Open in other app") { _, _ -> openFileExternal(uri, "application/pdf") }
            b.setNegativeButton("Close", null).pshow()
        } catch (e: Exception) { toast("Downloaded: $name") }
    }

    private fun openFileExternal(uri: Uri, mime: String) {
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("No app found to open this file — it is in your Downloads folder") }
    }

    private fun closePdf() {
        pdfOverlay?.let { removeView(it) }; pdfOverlay = null
        try { pdfRenderer?.close() } catch (e: Exception) { }
        try { pdfFd?.close() } catch (e: Exception) { }
        pdfRenderer = null; pdfFd = null
        if (tabs.isEmpty() && !mini) visibility = View.GONE
    }

    // অ্যাপের ভেতরেই PDF প্রিভিউ (স্ক্রল করে সব পেজ দেখা যায়)
    private fun showPdf(uri: Uri, name: String) {
        closePdf()
        var fd: android.os.ParcelFileDescriptor? = null
        var rd: android.graphics.pdf.PdfRenderer? = null
        try {
            fd = act.contentResolver.openFileDescriptor(uri, "r")
            if (fd != null) rd = android.graphics.pdf.PdfRenderer(fd)
        } catch (e: Exception) { }
        if (fd == null || rd == null) {
            try { fd?.close() } catch (e: Exception) { }
            openFileExternal(uri, "application/pdf")
            return
        }
        val r: android.graphics.pdf.PdfRenderer = rd
        pdfFd = fd; pdfRenderer = r
        visibility = View.VISIBLE
        val w = resources.displayMetrics.widthPixels
        val cache = android.util.LruCache<Int, Bitmap>(5)
        val lv = ListView(act).apply {
            divider = android.graphics.drawable.ColorDrawable(pc("#1A1D2E")); dividerHeight = dp(8)
            setBackgroundColor(pc("#1A1D2E"))
        }
        lv.adapter = object : BaseAdapter() {
            override fun getCount(): Int = r.pageCount
            override fun getItem(position: Int): Any = position
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val iv = (convertView as? ImageView) ?: ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_XY; setBackgroundColor(Color.WHITE) }
                var bmp: Bitmap? = cache.get(position)
                var h = dp(400)
                if (bmp == null) {
                    try {
                        val pg = r.openPage(position)
                        h = (w.toFloat() * pg.height / pg.width).toInt()
                        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        nb.eraseColor(Color.WHITE)
                        pg.render(nb, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pg.close()
                        cache.put(position, nb)
                        bmp = nb
                    } catch (e: Exception) { }
                } else h = bmp.height
                iv.setImageBitmap(bmp)
                iv.layoutParams = AbsListView.LayoutParams(-1, h)
                return iv
            }
        }
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(pc("#0B0F24")) }
        head.addView(ibtn("back") { closePdf() }, LinearLayout.LayoutParams(-2, -1))
        head.addView(TextView(act).apply {
            text = name; textSize = 15f; setTextColor(ink); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(btn("Open in app", 13f, 14) { openFileExternal(uri, "application/pdf") }, LinearLayout.LayoutParams(-2, -1))
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pc("#1A1D2E")); isClickable = true }
        root.addView(head, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        pdfOverlay = root
        addView(root, FrameLayout.LayoutParams(-1, -1))
    }
}

private const val DL_JS = """(function(){
if(window.__bdl)return;window.__bdl=1;
var T="__TOK__";var blobs={};var nm='';
try{var oc=URL.createObjectURL;URL.createObjectURL=function(b){var u=oc.apply(URL,arguments);try{blobs[u]=b;}catch(e){}return u;};}catch(e){}
try{
document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;if(a)nm=a.getAttribute('download')||'';},true);
var ac=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){if(this.download)nm=this.download;return ac.apply(this,arguments);};
}catch(e){}
function send(b,name){
var mime=b.type||'application/octet-stream';var rd=new FileReader();
rd.onloadend=function(){
var s=String(rd.result||'');var d=s.substring(s.indexOf(',')+1);var id=String(Date.now())+Math.random();var CH=196608;var n=d.length;
if(n==0){BDl.chunk(T,id,'',true,name,mime);return;}
for(var k=0;k<n;k+=CH){BDl.chunk(T,id,d.substring(k,k+CH),k+CH>=n,name,mime);}
};
rd.onerror=function(){BDl.fail(T,'read');};
rd.readAsDataURL(b);
}
window.__bdlSend=function(u,dflt){
var name=nm||dflt;nm='';var b=blobs[u];
if(b){send(b,name);return;}
fetch(u).then(function(r){return r.blob();}).then(function(b){send(b,name);}).catch(function(e){BDl.fail(T,String(e));});
};
var wo=window.open;
window.open=function(u){if(typeof u==='string'&&u.indexOf('blob:')===0){window.__bdlSend(u,'document');return null;}return wo.apply(window,arguments);};
window.print=function(){BDl.doPrint(T);};
})();"""
