package io.ps3mio

import android.app.AlertDialog
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.TreeMap

/** Optional torrent engine hook (filled by a separate module; not part of the first build). */
interface TorrentResolver {
    fun resolve(stream: JSONObject, videoId: String, done: (String?) -> Unit)
}

object TorrentHook {
    var resolver: TorrentResolver? = null
}

private fun nonEmpty(s: String?): String? = if (s.isNullOrEmpty()) null else s

/** Title page: poster, logo, description; seasons + episodes for series; Watch for movies. */
class DetailScreen(
    act: MainActivity,
    private val type: String,
    private val id: String,
    private val fallbackName: String,
    private val poster: String?
) : Screen(act) {
    private val frame = FrameLayout(act)
    private val bg = ImageView(act)
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    private val episodes = LinearLayout(act)
    override val view: View = frame
    private var started = false
    private var meta: JSONObject? = null
    private var seasons: TreeMap<Int, MutableList<JSONObject>> = TreeMap()
    private var seasonKeys: List<Int> = emptyList()
    private var si = 0
    private var seasonBtn: TextView? = null
    private var shownName = ""

    init {
        frame.setBackgroundColor(Col.bg)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        bg.alpha = 0.22f
        frame.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(28), dp(20), dp(28), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        episodes.orientation = LinearLayout.VERTICAL
    }

    override fun onShow() {
        if (started) return
        started = true
        col.addView(label(act, fallbackName, 22f, Col.text, true))
        col.addView(label(act, "Loading...", 14f, Col.dim))
        Net.bg {
            val m = Stremio.meta(type, id)
            Net.ui {
                meta = m
                render(m)
            }
        }
    }

    private fun render(m: JSONObject?) {
        col.removeAllViews()
        val name = nonEmpty(m?.optString("name")) ?: fallbackName
        shownName = name
        Img.load(bg, nonEmpty(m?.optString("background")), 1280)

        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        val posterV = ImageView(act)
        posterV.scaleType = ImageView.ScaleType.CENTER_CROP
        posterV.setBackgroundColor(Col.card)
        top.addView(posterV, LinearLayout.LayoutParams(dp(180), dp(270)))
        Img.load(posterV, nonEmpty(m?.optString("poster")) ?: poster, 400)

        val info = LinearLayout(act)
        info.orientation = LinearLayout.VERTICAL
        info.setPadding(dp(22), 0, 0, 0)
        val logoUrl = nonEmpty(m?.optString("logo"))
        if (logoUrl != null) {
            val logo = ImageView(act)
            logo.adjustViewBounds = true
            logo.scaleType = ImageView.ScaleType.FIT_START
            info.addView(logo, LinearLayout.LayoutParams(dp(300), dp(90)))
            Img.load(logo, logoUrl, 600)
        } else {
            info.addView(label(act, name, 28f, Col.text, true))
        }

        if (m != null) {
            val parts = ArrayList<String>()
            val rel = nonEmpty(m.optString("releaseInfo")) ?: nonEmpty(m.optString("year"))
            if (rel != null) parts.add(rel)
            nonEmpty(m.optString("runtime"))?.let { parts.add(it) }
            nonEmpty(m.optString("imdbRating"))?.let { parts.add("IMDb $it") }
            if (parts.isNotEmpty()) info.addView(label(act, parts.joinToString("   "), 14f, Col.dim))
            val g = m.optJSONArray("genres")
            if (g != null && g.length() > 0) {
                val gs = ArrayList<String>()
                for (i in 0 until g.length()) gs.add(g.optString(i))
                info.addView(label(act, gs.joinToString(", "), 14f, Col.accent))
            }
            val d = nonEmpty(m.optString("description"))
            if (d != null) {
                val t = label(act, d, 14f, Col.text)
                t.maxLines = 7
                t.ellipsize = TextUtils.TruncateAt.END
                t.setPadding(0, dp(10), 0, dp(6))
                info.addView(t)
            }
            val cast = m.optJSONArray("cast")
            if (cast != null && cast.length() > 0) {
                val cs = ArrayList<String>()
                for (i in 0 until minOf(cast.length(), 6)) cs.add(cast.optString(i))
                info.addView(label(act, "Cast: " + cs.joinToString(", "), 13f, Col.dim))
            }
        }
        top.addView(info, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        val vids = m?.optJSONArray("videos")
        if (m != null && type != "movie" && vids != null && vids.length() > 0) {
            val map = TreeMap<Int, MutableList<JSONObject>>()
            for (i in 0 until vids.length()) {
                val v = vids.optJSONObject(i) ?: continue
                val s = if (v.has("season")) v.optInt("season") else 0
                map.getOrPut(s) { ArrayList() }.add(v)
            }
            for (list in map.values) list.sortBy { it.optInt("episode", it.optInt("number")) }
            seasons = map
            seasonKeys = ArrayList(map.keys)
            si = if (seasonKeys.size > 1 && seasonKeys[0] == 0) 1 else 0
            val sb = button(act, "") { cycleSeason() }
            seasonBtn = sb
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.topMargin = dp(18)
            col.addView(sb, lp)
            col.addView(episodes, LinearLayout.LayoutParams(MATCH, WRAP))
            fillEpisodes()
            sb.requestFocus()
        } else {
            val watch = button(act, "Watch") { act.open(StreamsScreen(act, type, id, name, m, poster)) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.topMargin = dp(18)
            col.addView(watch, lp)
            watch.requestFocus()
        }
    }

    private fun cycleSeason() {
        if (seasonKeys.isEmpty()) return
        si = (si + 1) % seasonKeys.size
        fillEpisodes()
    }

    private fun fillEpisodes() {
        episodes.removeAllViews()
        val key = seasonKeys.getOrNull(si) ?: return
        seasonBtn?.text = if (key == 0) "Specials  (tap to change)" else "Season $key  (tap to change)"
        val list = seasons[key] ?: return
        for (v in list) {
            val ep = if (v.has("episode")) v.optInt("episode") else v.optInt("number")
            val title = (if (ep > 0) "$ep. " else "") + nonEmpty(v.optString("title")).orEmpty().ifEmpty { v.optString("name", "Episode") }
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(6), dp(8), dp(6))
            row.isFocusable = true
            row.isClickable = true
            row.background = focusBg(act, 8)
            val thumb = ImageView(act)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.setBackgroundColor(Col.card)
            row.addView(thumb, LinearLayout.LayoutParams(dp(120), dp(68)))
            Img.load(thumb, nonEmpty(v.optString("thumbnail")), 300)
            val txt = LinearLayout(act)
            txt.orientation = LinearLayout.VERTICAL
            txt.setPadding(dp(14), 0, 0, 0)
            txt.addView(label(act, title, 15f, Col.text, true))
            val sub = ArrayList<String>()
            nonEmpty(v.optString("released"))?.let { sub.add(it.take(10)) }
            val ov = nonEmpty(v.optString("overview")) ?: nonEmpty(v.optString("description"))
            if (ov != null) sub.add(ov)
            if (sub.isNotEmpty()) {
                val t = label(act, sub.joinToString("  -  "), 12f, Col.dim)
                t.maxLines = 2
                t.ellipsize = TextUtils.TruncateAt.END
                txt.addView(t)
            }
            row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
            val vid = v.optString("id")
            val shown = shownName + " S" + key + "E" + (if (ep > 0) ep.toString() else "?")
            row.setOnClickListener { act.open(StreamsScreen(act, type, vid, shown, meta, poster)) }
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.topMargin = dp(6)
            episodes.addView(row, lp)
        }
    }
}

/** Stream list: one row per stream (quality, seeders, size, source, file) grouped by addon. */
class StreamsScreen(
    act: MainActivity,
    private val type: String,
    private val vid: String,
    private val title: String,
    private val meta: JSONObject?,
    private val poster: String?
) : Screen(act) {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    private val status = label(act, "Loading streams...", 14f, Col.dim)
    override val view: View = scroll
    private var started = false
    private var total = 0
    @Volatile
    private var subs: List<JSONObject> = emptyList()

    init {
        scroll.setBackgroundColor(Col.bg)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(16), dp(24), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    override fun onShow() {
        if (started) return
        started = true
        col.addView(label(act, title, 22f, Col.text, true))
        col.addView(status)
        Net.bg { subs = Stremio.subtitles(type, vid) }
        Stremio.streams(type, vid, { a, arr ->
            col.addView(label(act, a.name, 14f, Col.accent, true).also { it.setPadding(0, dp(14), 0, dp(4)) })
            for (i in 0 until minOf(arr.length(), 60)) {
                val s = arr.optJSONObject(i) ?: continue
                addRow(a, s)
            }
        }, {
            status.text = if (total == 0) "No streams found." else ""
        })
    }

    private fun addRow(a: Addon, s: JSONObject) {
        val url = s.optString("url")
        val isHttp = url.startsWith("http://") || url.startsWith("https://")
        val isTorrent = s.has("infoHash") || url.startsWith("magnet:")
        if (!isHttp && !isTorrent) return
        val r = Stremio.parseStream(a, s)
        total++
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.isFocusable = true
        row.isClickable = true
        row.background = focusBg(act, 8, Col.panel)
        val badge = label(act, if (r.quality.isNotEmpty()) r.quality else if (isTorrent) "TOR" else "HTTP", 13f, Col.accent, true)
        badge.gravity = Gravity.CENTER
        row.addView(badge, LinearLayout.LayoutParams(dp(78), WRAP))
        val txt = LinearLayout(act)
        txt.orientation = LinearLayout.VERTICAL
        val name = if (r.fileName.isNotEmpty()) r.fileName else r.raw.split(Regex("[\\r\\n]+")).firstOrNull().orEmpty().ifEmpty { a.name }
        val l1 = label(act, name, 15f, Col.text)
        l1.maxLines = 2
        l1.ellipsize = TextUtils.TruncateAt.END
        txt.addView(l1)
        txt.addView(label(act, r.infoLine(), 13f, Col.dim))
        row.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.setOnClickListener { play(s, isHttp, url) }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(5)
        col.addView(row, lp)
    }

    private fun play(s: JSONObject, isHttp: Boolean, url: String) {
        val headers = s.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")
        if (isHttp) {
            act.play(url, title, meta, poster, vid, subs, headers)
            return
        }
        val res = TorrentHook.resolver
        if (res == null) {
            act.toast("Torrent engine is not part of this build yet")
            return
        }
        act.toast("Starting torrent...")
        res.resolve(s, vid) { playUrl ->
            Net.ui {
                if (playUrl == null) act.toast("Torrent failed to start")
                else act.play(playUrl, title, meta, poster, vid, subs, null)
            }
        }
    }
}

/** Addons: read-only list of what is installed on the account. Edit them from your phone or PC. */
class AddonsScreen(act: MainActivity) : Screen(act) {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View = scroll
    private var started = false

    init {
        scroll.setBackgroundColor(Col.bg)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(16), dp(24), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    override fun onShow() {
        if (started) return
        started = true
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(label(act, "Addons", 24f, Col.text, true))
        val help = label(act, "Addons are managed from your phone or PC (Stremio app > Addons). Press Sync to update this list.", 13f, Col.dim)
        help.setPadding(0, dp(4), 0, dp(10))
        col.addView(help)
        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.rightMargin = dp(10)
        bar.addView(button(act, "Sync addons") { sync() }, lp)
        if (Prefs.authKey.isEmpty()) bar.addView(button(act, "Log in") { act.open(LoginScreen(act)) }, lp)
        col.addView(bar)
        val status = label(act, "Loading...", 13f, Col.dim)
        status.setPadding(0, dp(8), 0, dp(4))
        col.addView(status)
        Net.bg {
            val list = Stremio.addons()
            Net.ui {
                status.text = if (Prefs.authKey.isEmpty()) "Not logged in - showing the default addon." else list.size.toString() + " addon(s) on your account"
                for (a in list) col.addView(addonRow(a))
            }
        }
    }

    private fun addonRow(a: Addon): View {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(14), dp(10), dp(14), dp(10))
        row.isFocusable = true
        row.background = focusBg(act, 8, Col.panel)
        val ver = a.manifest.optString("version")
        row.addView(label(act, a.name + (if (ver.isNotEmpty()) "   v$ver" else ""), 16f, Col.text, true))
        val d = a.manifest.optString("description")
        if (d.isNotEmpty()) {
            val t = label(act, d, 13f, Col.dim)
            t.maxLines = 2
            t.ellipsize = TextUtils.TruncateAt.END
            row.addView(t)
        }
        row.addView(label(act, a.resourceNames(), 12f, Col.accent))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(6)
        row.layoutParams = lp
        return row
    }

    private fun sync() {
        act.toast("Syncing...")
        Net.bg {
            val n = Stremio.sync()
            Net.ui {
                if (n >= 0) {
                    act.toast("Synced $n addon(s)")
                    render()
                } else if (n == -1) act.toast("Log in first") else act.toast("Network error")
            }
        }
    }
}

/** Log in with a link code (approve on phone / PC) - the same flow Stremio uses on TVs. */
class LoginScreen(act: MainActivity) : Screen(act) {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    private val codeView = label(act, "...", 46f, Col.accent, true)
    private val status = label(act, "Contacting Stremio...", 15f, Col.dim)
    override val view: View = scroll
    private var started = false

    init {
        scroll.setBackgroundColor(Col.bg)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(28), dp(24), dp(28), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        col.addView(label(act, "Log in", 24f, Col.text, true))
        codeView.setPadding(0, dp(20), 0, dp(10))
        col.addView(codeView)
        col.addView(status)
        val b = button(act, "Use email and password instead") { emailLogin(act) }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = dp(24)
        col.addView(b, lp)
    }

    override fun onShow() {
        if (started) return
        started = true
        Net.bg {
            val r = Stremio.linkCreate()
            val code = r?.optString("code") ?: ""
            val link = r?.optString("link") ?: ""
            if (code.isEmpty()) {
                Net.ui { status.text = "Cannot reach Stremio. Check your internet connection." }
                return@bg
            }
            Net.ui {
                codeView.text = code
                status.text = "On your phone or PC open:\n$link\nand enter the code above."
            }
            for (i in 0 until 100) {
                Thread.sleep(3000)
                if (!view.isAttachedToWindow) return@bg
                val key = Stremio.linkRead(code)
                if (key != null) {
                    Prefs.authKey = key
                    val n = Stremio.sync()
                    Net.ui {
                        act.toast("Logged in" + (if (n >= 0) " - $n addon(s) synced" else ""))
                        act.goHome()
                    }
                    return@bg
                }
            }
            Net.ui { status.text = "The code expired. Go back and try again." }
        }
    }
}

fun emailLogin(act: MainActivity) {
    val box = LinearLayout(act)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(act.dp(20), act.dp(10), act.dp(20), 0)
    val email = EditText(act)
    email.hint = "Email"
    email.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    val pw = EditText(act)
    pw.hint = "Password"
    pw.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    box.addView(email)
    box.addView(pw)
    AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
        .setTitle("Log in")
        .setView(box)
        .setPositiveButton("Log in") { _, _ ->
            val e = email.text.toString().trim()
            val p = pw.text.toString()
            Net.bg {
                val key = Stremio.login(e, p)
                if (key == null) {
                    Net.ui { act.toast("Login failed") }
                } else {
                    Prefs.authKey = key
                    val n = Stremio.sync()
                    Net.ui {
                        act.toast("Logged in" + (if (n >= 0) " - $n addon(s) synced" else ""))
                        act.goHome()
                    }
                }
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

class SettingsScreen(act: MainActivity) : Screen(act) {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View = scroll

    init {
        scroll.setBackgroundColor(Col.bg)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(24), dp(16), dp(24), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
    }

    override fun onShow() {
        col.removeAllViews()
        col.addView(label(act, "Settings", 24f, Col.text, true))
        val st = label(act, if (Prefs.authKey.isEmpty()) "Not logged in" else "Logged in to your Stremio account", 14f, Col.dim)
        st.setPadding(0, dp(6), 0, dp(12))
        col.addView(st)
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.bottomMargin = dp(10)
        if (Prefs.authKey.isEmpty()) {
            col.addView(button(act, "Log in with phone / PC code") { act.open(LoginScreen(act)) }, lp)
            col.addView(button(act, "Log in with email") { emailLogin(act) }, lp)
        } else {
            col.addView(button(act, "Log out") {
                Prefs.authKey = ""
                Prefs.addonsJson = "[]"
                Stremio.invalidate()
                act.toast("Logged out")
                act.goHome()
            }, lp)
        }
        col.addView(button(act, "Refresh (sync addons)") { act.refresh() }, lp)
        val about = label(act, "ps3mio 0.1  -  Stremio client\nGamepad: A select, B back, Y search, X refresh.\nPlayer: A pause, Left/Right seek, L1/R1 +-30s, Y subtitles, X audio.", 13f, Col.dim)
        about.setPadding(0, dp(20), 0, 0)
        col.addView(about)
    }
}
