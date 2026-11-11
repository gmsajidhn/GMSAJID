package com.sajid.studytracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

// ===== অফলাইন পেজ: একবার দেখা পেজ সেভ থাকে, নেট না থাকলে সেটাই খোলে =====
object OfflineStore {
    fun online(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetworkInfo; n != null && n.isConnected
    } catch (e: Exception) { true }

    private fun dir(c: Context) = File(c.filesDir, "offline").also { it.mkdirs() }
    fun file(c: Context, url: String): File {
        val h = MessageDigest.getInstance("MD5").digest(url.substringBefore("#").toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(c), "$h.mht")
    }
    fun has(c: Context, url: String) = file(c, url).let { it.exists() && it.length() > 0 }
    fun prune(c: Context) {
        val fs = dir(c).listFiles()?.filter { it.name.endsWith(".mht") }?.sortedByDescending { it.lastModified() } ?: return
        fs.drop(250).forEach { it.delete(); File(it.path.removeSuffix(".mht") + ".host").delete() }
    }
    // কোন সাইটের পেজ সেভ হয়েছে সেটা পাশে লিখে রাখা হয়, যাতে সাইটের ক্যাশ বন্ধ করলে শুধু ওই সাইটের ডেটা মোছা যায়
    fun markSite(c: Context, url: String, site: String) {
        try { File(file(c, url).path.removeSuffix(".mht") + ".host").writeText(site) } catch (e: Exception) { }
    }
    fun clearSite(c: Context, site: String) {
        try {
            dir(c).listFiles()?.filter { it.name.endsWith(".host") }?.forEach { hf ->
                if ((try { hf.readText() } catch (e: Exception) { "" }) == site) {
                    File(hf.path.removeSuffix(".host") + ".mht").delete(); hf.delete()
                }
            }
        } catch (e: Exception) { }
        SiteCache.clearSite(c, site)
    }
    fun clearAll(c: Context) { dir(c).listFiles()?.forEach { it.delete() }; SiteCache.clearAll(c) }
    fun size(c: Context): Long {
        fun sz(f: File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { sz(it) } ?: 0L) else f.length()
        return sz(c.cacheDir) + sz(dir(c)) + SiteCache.size(c)
    }
}

// ===== সাইট ক্যাশ: অনলাইনে থাকতে পেজ + তার সব ফাইল (JS/CSS/ফন্ট/ছবি) ডিস্কে জমে; অফলাইনে আসল লিংকেই (https://...) খোলে,
// তাই localStorage ঠিকমতো চলে, এডিট করা যায়, ফন্ট ঠিক দেখায়, PDF ডাউনলোডও চলে =====
object SiteCache {
    class Hit(val mime: String, val enc: String?, val headers: Map<String, String>, val data: ByteArray)
    const val MAX_BYTES = 450L * 1024 * 1024
    private var puts = 0

    private fun dir(c: Context) = File(c.filesDir, "sitecache").also { it.mkdirs() }
    private fun key(url: String): String =
        MessageDigest.getInstance("MD5").digest(url.substringBefore("#").toByteArray()).joinToString("") { "%02x".format(it) }

    fun has(c: Context, url: String) = File(dir(c), key(url) + ".meta").exists()

    @Synchronized fun put(c: Context, url: String, mime: String, enc: String?, headers: Map<String, String>, data: ByteArray, site: String = "") {
        try {
            val k = key(url); val d = dir(c)
            File(d, "$k.body").writeBytes(data)
            File(d, "$k.meta").writeText(JSONObject().put("m", mime).put("e", enc ?: "").put("s", site).put("h", JSONObject(headers)).toString())
            if (++puts % 40 == 0) prune(c)
        } catch (e: Exception) { }
    }

    @Synchronized fun get(c: Context, url: String): Hit? = try {
        val k = key(url); val d = dir(c)
        val mf = File(d, "$k.meta"); val bf = File(d, "$k.body")
        if (!mf.exists() || !bf.exists()) null else {
            val o = JSONObject(mf.readText())
            val ho = o.optJSONObject("h") ?: JSONObject()
            val hm = HashMap<String, String>()
            ho.keys().forEach { hm[it] = ho.optString(it) }
            Hit(o.optString("m", "application/octet-stream"), o.optString("e", "").ifEmpty { null }, hm, bf.readBytes())
        }
    } catch (e: Exception) { null }

    // রিডাইরেক্ট মনে রাখা: A -> B
    @Synchronized fun alias(c: Context, from: String, to: String) {
        try { if (key(from) != key(to)) File(dir(c), key(from) + ".alias").writeText(to) } catch (e: Exception) { }
    }

    fun resolve(c: Context, url: String): String {
        var u = url
        try {
            for (i in 0 until 5) {
                if (has(c, u)) return u
                val f = File(dir(c), key(u) + ".alias")
                if (!f.exists()) return u
                u = f.readText()
            }
        } catch (e: Exception) { }
        return u
    }

    @Synchronized fun prune(c: Context) {
        try {
            val fs = dir(c).listFiles()?.filter { it.name.endsWith(".meta") } ?: return
            var total = dir(c).listFiles()?.sumOf { it.length() } ?: 0L
            if (total <= MAX_BYTES) return
            for (m in fs.sortedBy { it.lastModified() }) {
                val base = m.path.removeSuffix(".meta")
                total -= m.length() + File("$base.body").length()
                m.delete(); File("$base.body").delete()
                if (total <= MAX_BYTES * 8 / 10) break
            }
        } catch (e: Exception) { }
    }

    // একটা সাইটের জমানো সব ফাইল মোছা
    @Synchronized fun clearSite(c: Context, site: String) {
        try {
            dir(c).listFiles()?.filter { it.name.endsWith(".meta") }?.forEach { m ->
                val s = try { JSONObject(m.readText()).optString("s", "") } catch (e: Exception) { "" }
                if (s == site) { m.delete(); File(m.path.removeSuffix(".meta") + ".body").delete() }
            }
        } catch (e: Exception) { }
    }
    fun clearAll(c: Context) { dir(c).listFiles()?.forEach { it.delete() } }
    fun size(c: Context): Long = dir(c).listFiles()?.sumOf { it.length() } ?: 0L
}

// ===== এক্সটেনশন: ইউজারস্ক্রিপ্ট (.js/.user.js), .css, এবং আনপ্যাক/zip/crx এক্সটেনশনের content_scripts =====
class ExtManager(private val c: Context) {
    class Rule(val matches: List<String>, val js: List<String>, val css: List<String>)
    class Ext(val id: String, var name: String, var enabled: Boolean, val rules: List<Rule>)

    var prof = "p1"
    private val sp = c.getSharedPreferences("browser", Context.MODE_PRIVATE)
    private fun key() = if (prof == "p1") "exts" else "exts_$prof"
    private fun root() = File(c.filesDir, if (prof == "p1") "ext" else "ext_$prof").also { it.mkdirs() }

    fun list(): MutableList<Ext> {
        val out = mutableListOf<Ext>()
        try {
            val a = JSONArray(sp.getString(key(), "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val rs = mutableListOf<Rule>()
                val ra = o.getJSONArray("rules")
                for (k in 0 until ra.length()) {
                    val r = ra.getJSONObject(k)
                    fun l(n: String) = r.getJSONArray(n).let { x -> (0 until x.length()).map { x.getString(it) } }
                    rs.add(Rule(l("m"), l("js"), l("css")))
                }
                out.add(Ext(o.getString("id"), o.getString("name"), o.optBoolean("on", true), rs))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun save(l: List<Ext>) {
        val a = JSONArray()
        l.forEach { e ->
            val ra = JSONArray()
            e.rules.forEach { ra.put(JSONObject().put("m", JSONArray(it.matches)).put("js", JSONArray(it.js)).put("css", JSONArray(it.css))) }
            a.put(JSONObject().put("id", e.id).put("name", e.name).put("on", e.enabled).put("rules", ra))
        }
        sp.edit().putString(key(), a.toString()).apply()
    }

    fun toggle(id: String) { val l = list(); l.firstOrNull { it.id == id }?.let { it.enabled = !it.enabled }; save(l) }
    fun remove(id: String) { val l = list(); l.removeAll { it.id == id }; File(root(), id).deleteRecursively(); save(l) }

    // নতুন এক্সটেনশন যোগ; সফল হলে নাম, নইলে null
    fun add(uri: Uri, fileName: String): String? = try { addBytes(c.contentResolver.openInputStream(uri)!!.use { it.readBytes() }, fileName) } catch (e: Throwable) { null }

    fun addBytes(bytes: ByteArray, fileName: String): String? {
        return try {
        val id = "e" + System.currentTimeMillis()
        val d = File(root(), id).also { it.mkdirs() }
        val low = fileName.lowercase()
        val ext: Ext
        if (low.endsWith(".zip") || low.endsWith(".crx") || low.endsWith(".xpi")) {
            var off = 0
            if (bytes.size > 12 && bytes[0] == 'C'.code.toByte() && bytes[1] == 'r'.code.toByte()) {
                val ver = (bytes[4].toInt() and 255) or ((bytes[5].toInt() and 255) shl 8)
                off = if (ver >= 3) 12 + ((bytes[8].toInt() and 255) or ((bytes[9].toInt() and 255) shl 8) or ((bytes[10].toInt() and 255) shl 16)) else 16 + ((bytes[8].toInt() and 255) or ((bytes[9].toInt() and 255) shl 8)) + ((bytes[12].toInt() and 255) or ((bytes[13].toInt() and 255) shl 8))
            }
            java.util.zip.ZipInputStream(bytes.inputStream(off, bytes.size - off)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val f = File(d, e.name)
                    if (f.canonicalPath.startsWith(d.canonicalPath)) {
                        if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                    }
                    e = z.nextEntry
                }
            }
            val mf = JSONObject(File(d, "manifest.json").readText())
            val rules = mutableListOf<Rule>()
            val cs = mf.optJSONArray("content_scripts") ?: JSONArray()
            for (i in 0 until cs.length()) {
                val o = cs.getJSONObject(i)
                fun l(n: String) = o.optJSONArray(n)?.let { x -> (0 until x.length()).map { x.getString(it) } } ?: emptyList()
                rules.add(Rule(l("matches").ifEmpty { listOf("<all_urls>") }, l("js"), l("css")))
            }
            if (rules.isEmpty()) return null
            ext = Ext(id, mf.optString("name", fileName).let { if (it.startsWith("__MSG")) fileName else it }, true, rules)
        } else {
            val txt = String(bytes)
            val isCss = low.endsWith(".css")
            File(d, "main" + if (isCss) ".css" else ".js").writeText(txt)
            val ms = Regex("//\\s*@(?:match|include)\\s+(\\S+)").findAll(txt).map { it.groupValues[1] }.toList().ifEmpty { listOf("<all_urls>") }
            val nm = Regex("//\\s*@name\\s+(.+)").find(txt)?.groupValues?.get(1)?.trim() ?: fileName
            ext = Ext(id, nm, true, listOf(Rule(ms, if (isCss) emptyList() else listOf("main.js"), if (isCss) listOf("main.css") else emptyList())))
        }
        val l = list(); l.add(ext); save(l)
        ext.name
        } catch (e: Throwable) { null }
    }

    private fun matches(p: String, url: String): Boolean {
        if (p == "<all_urls>" || p == "*") return true
        val q = p.replaceFirst("*://", "\u0001")
        val full = q.map { ch -> when (ch) { '*' -> ".*"; '\u0001' -> "https?://"; else -> Regex.escape(ch.toString()) } }.joinToString("")
        return try { Regex(full).matches(url) } catch (e: Exception) { false }
    }

    // এই ইউআরএলের জন্য যে সব এক্সটেনশনের কোড চলবে সেগুলো একটা JS স্ক্রিপ্টে
    fun scriptFor(url: String): String? {
        val sb = StringBuilder()
        for (e in list()) {
            if (!e.enabled) continue
            for (r in e.rules) {
                if (r.matches.none { matches(it, url) }) continue
                val d = File(root(), e.id)
                r.css.forEach { f -> try {
                    sb.append("(function(){var s=document.createElement('style');s.textContent=").append(JSONObject.quote(File(d, f).readText())).append(";(document.head||document.documentElement).appendChild(s);})();\n")
                } catch (x: Exception) { } }
                r.js.forEach { f -> try { sb.append("try{\n").append(File(d, f).readText()).append("\n}catch(e){console.log('ext error',e)}\n") } catch (x: Exception) { } }
            }
        }
        if (sb.isEmpty()) return null
        val stub = "if(!window.chrome)window.chrome={};chrome.runtime=chrome.runtime||{getURL:function(p){return p},sendMessage:function(){},onMessage:{addListener:function(){}}};chrome.storage=chrome.storage||{local:{get:function(k,cb){cb&&cb({})},set:function(){}}};window.GM_addStyle=window.GM_addStyle||function(t){var s=document.createElement('style');s.textContent=t;document.head.appendChild(s)};\n"
        return "(function(){" + stub + sb + "})();"
    }
}
