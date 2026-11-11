package com.sajid.studytracker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// ওয়েবপেজের "Open app / See in Play Store app" বাটন (intent://, market://, tg://, whatsapp:// ...) চাপলে সেই অ্যাপে নিয়ে যায়
object ExternalApp {
    // অ্যাপ না থাকলে onWeb দিয়ে ব্রাউজারে খোলার লিংক দেওয়া হয়
    fun open(c: Context, raw: String, onWeb: (String) -> Unit): Boolean {
        val u = raw.trim()
        try {
            val i: Intent
            var fallback: String? = null
            var pkg: String? = null
            if (u.startsWith("intent:")) {
                i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                fallback = i.getStringExtra("browser_fallback_url")
                pkg = i.`package`
                i.component = null
                i.selector = null
            } else {
                i = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                if (u.startsWith("market:")) pkg = Uri.parse(u).getQueryParameter("id")
            }
            if (i.`package` == c.packageName) return false
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                c.startActivity(i)
                return true
            } catch (e: ActivityNotFoundException) { }

            // অ্যাপ ফোনে নেই
            val fb = fallback
            if (fb != null && fb.startsWith("http")) { onWeb(fb); return true }
            if (pkg != null) {
                try {
                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (e: ActivityNotFoundException) { }
                onWeb("https://play.google.com/store/apps/details?id=$pkg")
                return true
            }
            val d = i.dataString
            if (d != null && d.startsWith("http")) { onWeb(d); return true }
            Toast.makeText(c, "This app is not installed on the phone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try { Toast.makeText(c, "Couldn't open the app", Toast.LENGTH_SHORT).show() } catch (x: Exception) { }
        }
        return false
    }
}
