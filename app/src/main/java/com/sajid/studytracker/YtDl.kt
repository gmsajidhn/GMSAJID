package com.sajid.studytracker

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

// YouTube ভিডিওর ফরম্যাট তালিকা আনে (YouTube-এর অ্যাপ-ক্লায়েন্ট API দিয়ে) এবং ভিডিও+অডিও জোড়া লাগায় (MediaMuxer)।
// YouTube প্রায়ই এই API বদলায় — কাজ বন্ধ হলে নিচের clients তালিকার ভার্সন/কনফিগ আপডেট করতে হবে।
object YtDl {
    class Fmt(val itag: Int, val url: String, val mime: String, val h: Int, val fps: Int, val bitrate: Int, val size: Long, val hasVideo: Boolean, val hasAudio: Boolean) {
        val isAvc: Boolean get() = mime.contains("avc1")
        val isAac: Boolean get() = mime.contains("mp4a")
    }
    class Info(val id: String, val title: String, val ua: String, val formats: List<Fmt>)

    private val idRe = Regex("^[A-Za-z0-9_-]{11}$")

    fun idOf(u: String?): String? {
        if (u.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(u)
            val host = (uri.host ?: return null).lowercase()
            val segs = uri.pathSegments
            val c: String? = when {
                host == "youtu.be" -> segs.firstOrNull()
                host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> when (segs.firstOrNull()) {
                    "watch" -> uri.getQueryParameter("v")
                    "shorts", "embed", "live", "v" -> segs.getOrNull(1)
                    else -> null
                }
                else -> null
            }
            if (c != null && idRe.matches(c)) c else null
        } catch (e: Exception) { null }
    }

    private class Client(val ua: String, val num: String, val ver: String, val ctx: String)
    private val clients = listOf(
        Client("com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip", "28", "1.60.19",
            """{"clientName":"ANDROID_VR","clientVersion":"1.60.19","deviceMake":"Oculus","deviceModel":"Quest 3","androidSdkVersion":32,"osName":"Android","osVersion":"12L","hl":"en","gl":"US"}"""),
        Client("com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)", "5", "20.10.4",
            """{"clientName":"IOS","clientVersion":"20.10.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.3.2.22D82","hl":"en","gl":"US"}""")
    )

    fun fetch(id: String): Info {
        var reason = "No downloadable formats"
        for (c in clients) {
            try {
                val body = "{\"videoId\":\"$id\",\"contentCheckOk\":true,\"racyCheckOk\":true,\"context\":{\"client\":${c.ctx}}}"
                val cn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection
                cn.requestMethod = "POST"; cn.connectTimeout = 20000; cn.readTimeout = 20000; cn.doOutput = true
                cn.setRequestProperty("Content-Type", "application/json")
                cn.setRequestProperty("User-Agent", c.ua)
                cn.setRequestProperty("X-YouTube-Client-Name", c.num)
                cn.setRequestProperty("X-YouTube-Client-Version", c.ver)
                cn.setRequestProperty("Origin", "https://www.youtube.com")
                cn.outputStream.use { it.write(body.toByteArray()) }
                val ins = if (cn.responseCode in 200..299) cn.inputStream else cn.errorStream
                val txt = if (ins == null) "" else ins.use { String(it.readBytes()) }
                if (txt.isBlank()) continue
                val j = JSONObject(txt)
                val st = j.optJSONObject("streamingData")
                if (st == null) {
                    val r = j.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                    if (r.isNotBlank()) reason = r
                    continue
                }
                val out = ArrayList<Fmt>()
                fun add(arr: JSONArray?, progressive: Boolean) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val u = o.optString("url")
                        if (u.isBlank()) continue
                        val m = o.optString("mimeType")
                        out.add(Fmt(o.optInt("itag"), u, m, o.optInt("height"), o.optInt("fps"), o.optInt("bitrate"),
                            o.optString("contentLength").toLongOrNull() ?: 0L, m.startsWith("video/"), progressive || m.startsWith("audio/")))
                    }
                }
                add(st.optJSONArray("formats"), true)
                add(st.optJSONArray("adaptiveFormats"), false)
                if (out.isEmpty()) continue
                val title = j.optJSONObject("videoDetails")?.optString("title").orEmpty().ifBlank { "video" }
                return Info(id, title, c.ua, out)
            } catch (e: Exception) { reason = e.message ?: reason }
        }
        throw RuntimeException(reason)
    }

    // ভিডিও-অনলি mp4 (H.264) + অডিও m4a (AAC) জোড়া লাগিয়ে একটা mp4 বানায়
    fun mux(video: File, audio: File, out: File, cancelled: () -> Boolean) {
        val ve = MediaExtractor(); val ae = MediaExtractor()
        var mm: MediaMuxer? = null
        try {
            ve.setDataSource(video.path); ae.setDataSource(audio.path)
            fun pick(e: MediaExtractor, p: String): Int {
                for (i in 0 until e.trackCount) if ((e.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith(p)) return i
                return -1
            }
            val vt = pick(ve, "video/"); val at = pick(ae, "audio/")
            if (vt < 0 || at < 0) throw RuntimeException("Can't merge: video or audio track not found")
            ve.selectTrack(vt); ae.selectTrack(at)
            val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mm = m
            val vi = m.addTrack(ve.getTrackFormat(vt)); val ai = m.addTrack(ae.getTrackFormat(at))
            m.start()
            val buf = ByteBuffer.allocate(4 * 1024 * 1024); val info = MediaCodec.BufferInfo()
            while (true) {
                if (cancelled()) throw RuntimeException("cancelled")
                val vtm = ve.sampleTime; val atm = ae.sampleTime
                if (vtm < 0 && atm < 0) break
                val useV = vtm >= 0 && (atm < 0 || vtm <= atm)
                val e = if (useV) ve else ae
                buf.clear()
                val n = e.readSampleData(buf, 0)
                if (n < 0) { e.advance(); continue }
                info.set(0, n, e.sampleTime, if ((e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                m.writeSampleData(if (useV) vi else ai, buf, info)
                e.advance()
            }
            m.stop()
        } finally {
            try { mm?.release() } catch (e: Exception) { }
            try { ve.release() } catch (e: Exception) { }
            try { ae.release() } catch (e: Exception) { }
        }
    }
}
