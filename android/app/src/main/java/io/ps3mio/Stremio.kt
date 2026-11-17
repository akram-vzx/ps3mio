package io.ps3mio

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** One installed Stremio addon (its manifest + base URL). */
class Addon(val url: String, val manifest: JSONObject) {
    val base: String = url.replace(Regex("/manifest\\.json.*$"), "").trimEnd('/')
    val name: String = manifest.optString("name", manifest.optString("id", "addon"))

    private fun resInfo(res: String): JSONObject? {
        val rs = manifest.optJSONArray("resources") ?: return null
        for (i in 0 until rs.length()) {
            val r = rs.opt(i)
            if (r is String) {
                if (r == res) {
                    val o = JSONObject()
                    o.put("types", manifest.optJSONArray("types"))
                    o.put("idPrefixes", manifest.optJSONArray("idPrefixes"))
                    return o
                }
            } else if (r is JSONObject && r.optString("name") == res) {
                val o = JSONObject()
                o.put("types", r.optJSONArray("types") ?: manifest.optJSONArray("types"))
                o.put("idPrefixes", r.optJSONArray("idPrefixes") ?: manifest.optJSONArray("idPrefixes"))
                return o
            }
        }
        return null
    }

    fun supports(res: String, type: String, id: String?): Boolean {
        val ri = resInfo(res) ?: return false
        val types = ri.optJSONArray("types")
        if (types != null && types.length() > 0) {
            var ok = false
            for (i in 0 until types.length()) if (types.optString(i) == type) ok = true
            if (!ok) return false
        }
        val pre = ri.optJSONArray("idPrefixes")
        if (pre != null && pre.length() > 0 && id != null) {
            var ok = false
            for (i in 0 until pre.length()) if (id.startsWith(pre.optString(i))) ok = true
            if (!ok) return false
        }
        return true
    }

    fun catalogs(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val cs = manifest.optJSONArray("catalogs") ?: return out
        for (i in 0 until cs.length()) {
            val c = cs.optJSONObject(i)
            if (c != null) out.add(c)
        }
        return out
    }

    fun resourceNames(): String {
        val names = ArrayList<String>()
        val rs = manifest.optJSONArray("resources")
        if (rs != null) {
            for (i in 0 until rs.length()) {
                val r = rs.opt(i)
                if (r is String) names.add(r) else if (r is JSONObject) names.add(r.optString("name"))
            }
        }
        return names.joinToString(", ")
    }
}

class CatInfo(
    val supSearch: Boolean,
    val supSkip: Boolean,
    val reqOther: Boolean,
    val reqGenre: Boolean,
    val genres: List<String>
)

fun catInfo(c: JSONObject): CatInfo {
    var search = false
    var skip = false
    var reqOther = false
    var reqGenre = false
    val genres = ArrayList<String>()
    val extra = c.optJSONArray("extra")
    if (extra != null) {
        for (i in 0 until extra.length()) {
            val e = extra.optJSONObject(i) ?: continue
            val n = e.optString("name")
            if (n == "search") search = true
            if (n == "skip") skip = true
            if (e.optBoolean("isRequired")) {
                if (n == "genre") reqGenre = true else if (n != "skip") reqOther = true
            }
            if (n == "genre") {
                val opts = e.optJSONArray("options")
                if (opts != null) for (k in 0 until opts.length()) genres.add(opts.optString(k))
            }
        }
    }
    val sup = c.optJSONArray("extraSupported")
    if (sup != null) for (i in 0 until sup.length()) {
        val n = sup.optString(i)
        if (n == "search") search = true
        if (n == "skip") skip = true
    }
    val req = c.optJSONArray("extraRequired")
    if (req != null) for (i in 0 until req.length()) {
        val n = req.optString(i)
        if (n == "genre") reqGenre = true else if (n != "skip") reqOther = true
    }
    return CatInfo(search, skip, reqOther, reqGenre, genres)
}

/** A stream row prepared for display: quality, seeders, size, source, file name. */
class StreamRow(
    val quality: String,
    val seeders: Int?,
    val size: String,
    val source: String,
    val fileName: String,
    val raw: String,
    val isTorrent: Boolean
) {
    fun infoLine(): String {
        val p = ArrayList<String>()
        if (isTorrent) p.add("Torrent")
        if (seeders != null) p.add("Seeds $seeders")
        if (size.isNotEmpty()) p.add(size)
        if (source.isNotEmpty()) p.add(source)
        return p.joinToString("  |  ")
    }
}

object Stremio {
    const val API = "https://api.strem.io"
    const val LINK = "https://link.stremio.com"
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"

    @Volatile
    private var cached: List<Addon>? = null

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    fun encId(s: String): String = enc(s).replace("%3A", ":")

    // ---- account ---------------------------------------------------------------------------
    fun linkCreate(): JSONObject? =
        Net.getJson("$LINK/api/v2/create?type=Create")?.optJSONObject("result")

    fun linkRead(code: String): String? {
        val r = Net.getJson("$LINK/api/v2/read?type=Read&code=" + enc(code))?.optJSONObject("result")
        val k = r?.optString("authKey") ?: ""
        return if (k.isNotEmpty()) k else null
    }

    fun login(email: String, password: String): String? {
        val body = JSONObject()
        body.put("type", "Auth")
        body.put("email", email)
        body.put("password", password)
        body.put("facebook", false)
        val r = Net.postJson("$API/api/login", body) ?: return null
        val k = r.optJSONObject("result")?.optString("authKey") ?: ""
        return if (k.isNotEmpty()) k else null
    }

    // ---- addons ----------------------------------------------------------------------------
    /** Returns the synced addon count, -1 when logged out, -2 on network/API error. */
    fun sync(): Int {
        val key = Prefs.authKey
        if (key.isEmpty()) return -1
        val body = JSONObject()
        body.put("type", "AddonCollectionGet")
        body.put("authKey", key)
        body.put("update", true)
        val r = Net.postJson("$API/api/addonCollectionGet", body) ?: return -2
        val arr = r.optJSONObject("result")?.optJSONArray("addons") ?: return -2
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val m = a.optJSONObject("manifest") ?: continue
            val u = a.optString("transportUrl")
            if (u.isEmpty()) continue
            val o = JSONObject()
            o.put("url", u)
            o.put("manifest", m)
            out.put(o)
        }
        Prefs.addonsJson = out.toString()
        Prefs.syncedAt = System.currentTimeMillis()
        cached = null
        return out.length()
    }

    /** Must be called from a background thread (may fetch the default addon). */
    @Synchronized
    fun addons(): List<Addon> {
        val c = cached
        if (c != null) return c
        val list = ArrayList<Addon>()
        try {
            val arr = JSONArray(Prefs.addonsJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Addon(o.getString("url"), o.getJSONObject("manifest")))
            }
        } catch (e: Exception) {
            // ignore, fall back below
        }
        if (list.isEmpty()) {
            val m = Net.getJson(CINEMETA)
            if (m != null && m.has("id")) list.add(Addon(CINEMETA, m))
        }
        cached = list
        return list
    }

    fun invalidate() {
        cached = null
    }

    // ---- catalogs --------------------------------------------------------------------------
    fun catalogUrl(a: Addon, c: JSONObject, genre: String?, skip: Int, search: String?): String {
        val p = ArrayList<String>()
        if (search != null) p.add("search=" + enc(search))
        if (!genre.isNullOrEmpty()) p.add("genre=" + enc(genre))
        if (skip > 0) p.add("skip=$skip")
        return a.base + "/catalog/" + c.optString("type") + "/" + enc(c.optString("id")) +
            (if (p.isEmpty()) "" else "/" + p.joinToString("&")) + ".json"
    }

    fun catalog(a: Addon, c: JSONObject, genre: String?, skip: Int, search: String?): List<JSONObject> {
        val r = Net.getJson(catalogUrl(a, c, genre, skip, search)) ?: return emptyList()
        val metas = r.optJSONArray("metas") ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (i in 0 until metas.length()) {
            val m = metas.optJSONObject(i)
            if (m != null && m.optString("id").isNotEmpty()) out.add(m)
        }
        return out
    }

    // ---- library ---------------------------------------------------------------------------
    fun library(): List<JSONObject> {
        val key = Prefs.authKey
        if (key.isEmpty()) return emptyList()
        val body = JSONObject()
        body.put("authKey", key)
        body.put("collection", "libraryItem")
        body.put("ids", JSONArray())
        body.put("all", true)
        val r = Net.postJson("$API/api/datastoreGet", body) ?: return emptyList()
        val arr = r.optJSONArray("result") ?: return emptyList()
        val items = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val it0 = arr.optJSONObject(i) ?: continue
            if (it0.optBoolean("removed")) continue
            items.add(it0)
        }
        items.sortByDescending { it.optString("_mtime") }
        val out = ArrayList<JSONObject>()
        for (it0 in items) {
            val o = JSONObject()
            o.put("id", it0.optString("_id"))
            o.put("type", it0.optString("type"))
            o.put("name", it0.optString("name"))
            o.put("poster", it0.optString("poster"))
            val st = it0.optJSONObject("state")
            if (st != null) {
                o.put("timeOffset", st.optLong("timeOffset"))
                o.put("duration", st.optLong("duration"))
                o.put("video_id", st.optString("video_id"))
            }
            out.add(o)
        }
        return out
    }

    // ---- meta / streams / subtitles --------------------------------------------------------
    fun meta(type: String, id: String): JSONObject? {
        for (a in addons()) {
            if (!a.supports("meta", type, id)) continue
            val r = Net.getJson(a.base + "/meta/" + type + "/" + encId(id) + ".json") ?: continue
            val m = r.optJSONObject("meta")
            if (m != null) return m
        }
        return null
    }

    /** Asks every stream addon in parallel; callbacks run on the UI thread. */
    fun streams(type: String, id: String, onAddon: (Addon, JSONArray) -> Unit, onDone: () -> Unit) {
        Net.bg {
            val list = addons().filter { it.supports("stream", type, id) }
            if (list.isEmpty()) {
                Net.ui(onDone)
            } else {
                val left = AtomicInteger(list.size)
                for (a in list) {
                    Net.bg {
                        val r = Net.getJson(a.base + "/stream/" + type + "/" + encId(id) + ".json")
                        val arr = r?.optJSONArray("streams")
                        Net.ui {
                            if (arr != null && arr.length() > 0) onAddon(a, arr)
                            if (left.decrementAndGet() == 0) onDone()
                        }
                    }
                }
            }
        }
    }

    /** Blocking; call from a background thread. */
    fun subtitles(type: String, id: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (a in addons()) {
            if (!a.supports("subtitles", type, id)) continue
            val r = Net.getJson(a.base + "/subtitles/" + type + "/" + encId(id) + ".json") ?: continue
            val arr = r.optJSONArray("subtitles") ?: continue
            for (i in 0 until minOf(arr.length(), 15)) {
                val s = arr.optJSONObject(i) ?: continue
                if (s.optString("url").isEmpty()) continue
                s.put("addon", a.name)
                out.add(s)
            }
        }
        return out
    }

    // ---- stream row parsing (Torrentio & co. put seeders / size in the title with emoji) ----
    private fun mapEmoji(t: String): String =
        t.replace("\uD83D\uDC64", "Seeds ")
            .replace("\uD83D\uDC65", "Peers ")
            .replace("\uD83D\uDCBE", "Size ")
            .replace("\u2699\uFE0F", "Src ")
            .replace("\u2699", "Src ")

    private fun fmtSize(b: Long): String = when {
        b >= 1073741824L -> String.format("%.2f GB", b / 1073741824.0)
        b >= 1048576L -> (b / 1048576L).toString() + " MB"
        b > 0 -> (b / 1024L).toString() + " KB"
        else -> ""
    }

    fun parseStream(a: Addon, s: JSONObject): StreamRow {
        val nameLines = mapEmoji(s.optString("name", a.name)).trim().split(Regex("[\\r\\n]+"))
        val quality = nameLines.drop(1).joinToString(" ").trim()
        val text = mapEmoji(if (s.has("title")) s.optString("title") else s.optString("description")).trim()
        val bh = s.optJSONObject("behaviorHints")
        val seedM = Regex("Seeds\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
        val sizeM = Regex("Size\\s*([\\d.,]+\\s*[KMGT]i?B)", RegexOption.IGNORE_CASE).find(text)
        val srcM = Regex("Src\\s*([^\\r\\n]+)").find(text)
        val seeders: Int? = when {
            seedM != null -> seedM.groupValues[1].toIntOrNull()
            s.has("seeders") -> s.optInt("seeders")
            else -> null
        }
        val size = sizeM?.groupValues?.get(1)?.trim() ?: fmtSize(bh?.optLong("videoSize") ?: 0L)
        val src = srcM?.groupValues?.get(1)?.trim() ?: ""
        var fname = bh?.optString("filename") ?: ""
        if (fname.isEmpty()) fname = text.split(Regex("[\\r\\n]+")).firstOrNull() ?: ""
        if (Regex("^(Seeds|Size|Src)\\b", RegexOption.IGNORE_CASE).containsMatchIn(fname)) fname = ""
        val torrent = s.has("infoHash") || s.optString("url").startsWith("magnet:")
        return StreamRow(quality, seeders, size, src, fname.trim(), text, torrent)
    }
}
