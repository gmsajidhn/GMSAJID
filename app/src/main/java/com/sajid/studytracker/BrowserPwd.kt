package com.sajid.studytracker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// ===== সেভ করা পাসওয়ার্ড (Android Keystore দিয়ে এনক্রিপ্ট করা) =====
class PwdStore(c: Context) {
    class Cred(val host: String, val user: String, val pass: String)
    private val sp = c.getSharedPreferences("browser", Context.MODE_PRIVATE)
    private fun k(p: String) = "pwds_$p"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("bpwd", null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("bpwd", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }
    private fun enc(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(s.toByteArray()), Base64.NO_WRAP)
    }
    private fun dec(s: String): String = try {
        val (iv, ct) = s.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)))
    } catch (e: Throwable) { "" }

    fun list(p: String): MutableList<Cred> {
        val out = mutableListOf<Cred>()
        try {
            val a = JSONArray(sp.getString(k(p), "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.add(Cred(o.getString("h"), o.getString("u"), dec(o.getString("p")))) }
        } catch (e: Exception) { }
        return out
    }
    private fun write(p: String, l: List<Cred>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("h", it.host).put("u", it.user).put("p", enc(it.pass))) }
        sp.edit().putString(k(p), a.toString()).apply()
    }
    fun find(p: String, host: String) = list(p).firstOrNull { it.host == host }
    fun has(p: String, host: String, u: String, pw: String) = list(p).any { it.host == host && it.user == u && it.pass == pw }
    fun put(p: String, host: String, u: String, pw: String) {
        val l = list(p); l.removeAll { it.host == host && it.user == u }; l.add(0, Cred(host, u, pw)); write(p, l)
    }
    fun remove(p: String, host: String, u: String) { val l = list(p); l.removeAll { it.host == host && it.user == u }; write(p, l) }
    fun clearAll(p: String) { sp.edit().remove(k(p)).apply() }
    fun never(host: String) = sp.getStringSet("pwd_never", emptySet())!!.contains(host)
    fun setNever(host: String) { sp.edit().putStringSet("pwd_never", HashSet(sp.getStringSet("pwd_never", emptySet())!!).apply { add(host) }).apply() }

    // গুগল পাসওয়ার্ড ম্যানেজার / ক্রোম থেকে এক্সপোর্ট করা CSV (name,url,username,password)
    fun importCsv(p: String, text: String): Int {
        val rows = text.lines().filter { it.isNotBlank() }
        if (rows.isEmpty()) return 0
        fun split(line: String): List<String> {
            val r = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (ch == '"') { if (q && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else q = !q }
                else if (ch == ',' && !q) { r.add(sb.toString()); sb.clear() } else sb.append(ch)
                i++
            }
            r.add(sb.toString()); return r
        }
        val head = split(rows[0]).map { it.lowercase().trim() }
        val iu = head.indexOf("url"); val iun = head.indexOf("username"); val ip = head.indexOf("password"); val inm = head.indexOf("name")
        if (iun < 0 || ip < 0) return 0
        var n = 0
        for (r in rows.drop(1)) {
            val f = split(r)
            val raw = (if (iu >= 0) f.getOrNull(iu) else null).orEmpty().ifBlank { f.getOrNull(inm).orEmpty() }
            val host = (android.net.Uri.parse(if (raw.contains("://")) raw else "https://$raw").host ?: continue).removePrefix("www.")
            val u = f.getOrNull(iun).orEmpty(); val pw = f.getOrNull(ip).orEmpty()
            if (pw.isEmpty()) continue
            put(p, host, u, pw); n++
        }
        return n
    }
}

// ===== বিজ্ঞাপন ব্লক =====
object AdBlock {
    private val hosts = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "googletagservices.com", "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com",
        "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "advertising.com", "adform.net",
        "amazon-adsystem.com", "scorecardresearch.com", "quantserve.com", "moatads.com", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "ads.mopub.com", "inmobi.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com", "trafficjunky.net",
        "adsterra.com", "mgid.com", "revcontent.com", "smartadserver.com", "yieldmo.com", "teads.tv", "media.net", "ad.gt", "adskeeper.com",
        "hotjar.com", "mc.yandex.ru", "an.yandex.ru", "ads.yahoo.com", "analytics.yahoo.com", "facebook.net.ads", "ads.twitter.com",
        "static.ads-twitter.com", "ads.linkedin.com", "bat.bing.com", "serving-sys.com", "2mdn.net", "adition.com", "zedo.com", "onetag-sys.com")
    @Volatile var blocked = 0
    fun isAd(host: String?): Boolean {
        var h = host ?: return false
        while (true) { if (hosts.contains(h)) return true; val i = h.indexOf('.'); if (i < 0) return false; h = h.substring(i + 1) }
    }
    const val CSS = "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],iframe[src*='doubleclick'],iframe[src*='googlesyndication'],iframe[id^='google_ads'],.ad-banner,.ad-container,.ad-slot,.advert,.advertisement,[class*='sponsored-ad'],[id*='taboola'],[class*='taboola'],[id*='outbrain'],[class*='outbrain']{display:none!important}"
    val JS = "(function(){var s=document.createElement('style');s.textContent=\"" + CSS.replace("\"", "\\\"") + "\";(document.head||document.documentElement).appendChild(s);})();"
}
