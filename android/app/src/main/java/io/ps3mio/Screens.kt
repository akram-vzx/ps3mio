package io.ps3mio

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

abstract class Screen(val act: MainActivity) {
    abstract val view: View
    open fun onShow() {}
    fun dp(v: Int): Int = act.dp(v)
}

fun typeRank(t: String): Int = when (t) {
    "movie" -> 0
    "series" -> 1
    else -> 2
}

fun typeName(t: String): String = when (t) {
    "movie" -> "Movies"
    "series" -> "Series"
    "channel" -> "Channels"
    "tv" -> "TV"
    else -> t.replaceFirstChar { it.uppercase() }
}

/** Poster card list used by rows and grids. Items are Stremio meta previews (id, type, name, poster). */
class PosterAdapter(
    private val items: MutableList<JSONObject>,
    private val w: Int,
    private val h: Int,
    private val onClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    class VH(val box: LinearLayout, val img: ImageView, val name: android.widget.TextView) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = parent.context
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
        box.isFocusable = true
        box.isClickable = true
        box.background = focusBg(c, 8)
        val img = ImageView(c)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setBackgroundColor(Col.card)
        box.addView(img, LinearLayout.LayoutParams(w, h))
        val name = label(c, "", 12f, Col.dim)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        name.setPadding(0, c.dp(4), 0, 0)
        box.addView(name, LinearLayout.LayoutParams(w, WRAP))
        val lp = RecyclerView.LayoutParams(WRAP, WRAP)
        lp.setMargins(c.dp(3), c.dp(3), c.dp(3), c.dp(3))
        box.layoutParams = lp
        return VH(box, img, name)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.optString("name")
        Img.load(holder.img, item.optString("poster"), w * 2)
        holder.box.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun add(list: List<JSONObject>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun clear() {
        val n = items.size
        items.clear()
        notifyItemRangeRemoved(0, n)
    }
}

/** A titled horizontal row of posters (hidden until it has items). */
class RowView(act: MainActivity, title: String) {
    val box = LinearLayout(act)
    private val adapter = PosterAdapter(ArrayList(), act.dp(120), act.dp(180)) { act.openMeta(it) }

    init {
        box.orientation = LinearLayout.VERTICAL
        box.visibility = View.GONE
        val t = label(act, title, 17f, Col.text, true)
        t.setPadding(act.dp(4), act.dp(14), 0, act.dp(6))
        box.addView(t)
        val rv = RecyclerView(act)
        rv.layoutManager = LinearLayoutManager(act, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = adapter
        rv.clipToPadding = false
        box.addView(rv, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    fun fill(items: List<JSONObject>) {
        if (items.isEmpty()) return
        adapter.add(items)
        box.visibility = View.VISIBLE
    }
}

/** Board: Continue Watching + one row per addon catalog (like Stremio's home). */
class BoardScreen(act: MainActivity) : Screen(act) {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View = scroll
    private var loaded = false

    init {
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(8), dp(20), dp(40))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        scroll.setBackgroundColor(Col.bg)
        scroll.isFillViewport = true
    }

    override fun onShow() {
        if (loaded) return
        loaded = true
        load()
    }

    fun reload() {
        loaded = true
        load()
    }

    private fun load() {
        col.removeAllViews()
        if (Prefs.authKey.isEmpty()) {
            val b = button(act, "Log in to sync your addons and library") { act.open(LoginScreen(act)) }
            col.addView(b, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        Net.bg {
            if (Prefs.authKey.isNotEmpty() &&
                System.currentTimeMillis() - Prefs.syncedAt > 6 * 3600 * 1000L
            ) Stremio.sync()
            val addons = Stremio.addons()
            val lib: List<JSONObject> = if (Prefs.authKey.isNotEmpty()) Stremio.library() else emptyList<JSONObject>()
            Net.ui { build(addons, lib) }
        }
    }

    private fun build(addons: List<Addon>, lib: List<JSONObject>) {
        val cont = lib.filter { it.optLong("timeOffset") > 0 }
        if (cont.isNotEmpty()) {
            val r = RowView(act, "Continue Watching")
            col.addView(r.box)
            r.fill(cont.take(20))
        }
        var rows = 0
        for (a in addons) {
            for (c in a.catalogs()) {
                if (rows >= 12) break
                val ci = catInfo(c)
                if (ci.reqOther || (ci.reqGenre && ci.genres.isEmpty())) continue
                rows++
                val title = c.optString("name", c.optString("id")) + " - " + typeName(c.optString("type"))
                val row = RowView(act, title)
                col.addView(row.box)
                val genre = if (ci.reqGenre) ci.genres[0] else null
                Net.bg {
                    val items = Stremio.catalog(a, c, genre, 0, null).take(24)
                    Net.ui { row.fill(items) }
                }
            }
        }
        if (rows == 0 && cont.isEmpty()) {
            col.addView(label(act, "No catalogs available. Check your connection, then press Refresh (X).", 15f, Col.dim))
        }
    }
}

/** Discover: type / catalog / genre selectors + infinite poster grid. */
class DiscoverScreen(act: MainActivity) : Screen(act) {
    private val root = LinearLayout(act)
    private val grid = RecyclerView(act)
    private val adapter = PosterAdapter(ArrayList(), act.dp(120), act.dp(180)) { act.openMeta(it) }
    private val typeBtn = button(act, "Type") { cycleType() }
    private val catBtn = button(act, "Catalog") { cycleCat() }
    private val genreBtn = button(act, "Genre") { cycleGenre() }
    override val view: View = root

    private var all: List<Pair<Addon, JSONObject>> = emptyList()
    private var types: List<String> = emptyList()
    private var entries: List<Pair<Addon, JSONObject>> = emptyList()
    private var genres: List<String> = listOf("")
    private var ti = 0
    private var ci = 0
    private var gi = 0
    private var generation = 0
    private var loading = false
    private var more = true
    private var started = false

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Col.bg)
        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(16), dp(12), dp(16), dp(8))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.rightMargin = dp(10)
        bar.addView(typeBtn, lp)
        bar.addView(catBtn, lp)
        bar.addView(genreBtn, lp)
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val dm = act.resources.displayMetrics
        val wdp = dm.widthPixels / dm.density
        val span = ((wdp - 190f) / 140f).toInt().coerceAtLeast(3)
        grid.layoutManager = GridLayoutManager(act, span)
        grid.adapter = adapter
        grid.setPadding(dp(12), 0, dp(12), dp(24))
        grid.clipToPadding = false
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (!loading && more && lm.findLastVisibleItemPosition() >= adapter.itemCount - 12) loadMore(false)
            }
        })
        root.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    override fun onShow() {
        if (started) return
        started = true
        Net.bg {
            val addons = Stremio.addons()
            val list = ArrayList<Pair<Addon, JSONObject>>()
            for (a in addons) for (c in a.catalogs()) {
                val inf = catInfo(c)
                if (inf.reqOther || (inf.reqGenre && inf.genres.isEmpty())) continue
                list.add(Pair(a, c))
            }
            Net.ui { setup(list) }
        }
    }

    private fun setup(list: List<Pair<Addon, JSONObject>>) {
        all = list
        val t = ArrayList(list.map { it.second.optString("type") }.distinct())
        t.sortBy { typeRank(it) }
        types = t
        ti = 0
        rebuildCatalogs()
    }

    private fun rebuildCatalogs() {
        if (types.isEmpty()) return
        entries = all.filter { it.second.optString("type") == types[ti] }
        ci = 0
        rebuildGenres()
    }

    private fun rebuildGenres() {
        val e = entries.getOrNull(ci) ?: return
        val inf = catInfo(e.second)
        val g = ArrayList<String>()
        if (!inf.reqGenre) g.add("")
        g.addAll(inf.genres)
        genres = g
        gi = 0
        updateLabels()
        reload()
    }

    private fun updateLabels() {
        typeBtn.text = "Type: " + typeName(types.getOrElse(ti) { "" })
        val e = entries.getOrNull(ci)
        if (e != null) {
            catBtn.text = "Catalog: " + e.second.optString("name", e.second.optString("id")) + "  [" + e.first.name + "]"
        } else {
            catBtn.text = "Catalog"
        }
        val g = genres.getOrElse(gi) { "" }
        genreBtn.text = "Genre: " + (if (g.isEmpty()) "All" else g)
        genreBtn.visibility = if (genres.size > 1) View.VISIBLE else View.GONE
    }

    private fun cycleType() {
        if (types.isEmpty()) return
        ti = (ti + 1) % types.size
        rebuildCatalogs()
    }

    private fun cycleCat() {
        if (entries.isEmpty()) return
        ci = (ci + 1) % entries.size
        rebuildGenres()
    }

    private fun cycleGenre() {
        if (genres.isEmpty()) return
        gi = (gi + 1) % genres.size
        updateLabels()
        reload()
    }

    private fun reload() {
        generation++
        loading = false
        more = true
        loadMore(true)
    }

    private fun loadMore(reset: Boolean) {
        val e = entries.getOrNull(ci) ?: return
        if (loading) return
        loading = true
        val gen = generation
        val skip = if (reset) 0 else adapter.itemCount
        val genre = genres.getOrElse(gi) { "" }
        Net.bg {
            val items = Stremio.catalog(e.first, e.second, genre, skip, null)
            Net.ui {
                if (gen == generation) {
                    if (reset) adapter.clear()
                    adapter.add(items)
                    more = items.isNotEmpty() && catInfo(e.second).supSkip
                    loading = false
                }
            }
        }
    }
}

/** Library: the account's saved items with a simple type filter. */
class LibraryScreen(act: MainActivity) : Screen(act) {
    private val root = LinearLayout(act)
    private val grid = RecyclerView(act)
    private val adapter = PosterAdapter(ArrayList(), act.dp(120), act.dp(180)) { act.openMeta(it) }
    private val filterBtn = button(act, "Type: All") { cycle() }
    private val status = label(act, "", 15f, Col.dim)
    override val view: View = root
    private var items: List<JSONObject> = emptyList()
    private val filters = listOf("", "movie", "series")
    private var fi = 0
    private var started = false

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Col.bg)
        val bar = LinearLayout(act)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(16), dp(12), dp(16), dp(8))
        bar.addView(filterBtn, LinearLayout.LayoutParams(WRAP, WRAP))
        status.setPadding(dp(16), 0, 0, 0)
        bar.addView(status, LinearLayout.LayoutParams(WRAP, WRAP))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        val dm = act.resources.displayMetrics
        val span = (((dm.widthPixels / dm.density) - 190f) / 140f).toInt().coerceAtLeast(3)
        grid.layoutManager = GridLayoutManager(act, span)
        grid.adapter = adapter
        grid.setPadding(dp(12), 0, dp(12), dp(24))
        grid.clipToPadding = false
        root.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    override fun onShow() {
        if (started) return
        started = true
        if (Prefs.authKey.isEmpty()) {
            status.text = "Log in from Settings to see your library."
            return
        }
        status.text = "Loading..."
        Net.bg {
            val lib = Stremio.library()
            Net.ui {
                items = lib
                status.text = if (lib.isEmpty()) "Your library is empty." else ""
                apply()
            }
        }
    }

    private fun cycle() {
        fi = (fi + 1) % filters.size
        filterBtn.text = "Type: " + (if (filters[fi].isEmpty()) "All" else typeName(filters[fi]))
        apply()
    }

    private fun apply() {
        adapter.clear()
        val f = filters[fi]
        adapter.add(if (f.isEmpty()) items else items.filter { it.optString("type") == f })
    }
}

/** Search: asks every catalog that supports search and shows results as rows. */
class SearchScreen(act: MainActivity) : Screen(act) {
    private val root = LinearLayout(act)
    private val input = EditText(act)
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View = root
    private var generation = 0

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Col.bg)
        input.hint = "Search movies, series..."
        input.setHintTextColor(Col.dim)
        input.setTextColor(Col.text)
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        input.background = focusBg(act, 8, Col.card)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(input.text.toString().trim())
                true
            } else false
        }
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.setMargins(dp(20), dp(14), dp(20), dp(6))
        root.addView(input, lp)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), 0, dp(20), dp(30))
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    private fun search(q: String) {
        if (q.isEmpty()) return
        val gen = ++generation
        col.removeAllViews()
        Net.bg {
            for (a in Stremio.addons()) for (c in a.catalogs()) {
                if (!catInfo(c).supSearch) continue
                val title = c.optString("name", c.optString("id")) + " - " + typeName(c.optString("type")) + "  [" + a.name + "]"
                Net.bg {
                    val items = Stremio.catalog(a, c, null, 0, q).take(30)
                    Net.ui {
                        if (gen == generation && items.isNotEmpty()) {
                            val row = RowView(act, title)
                            col.addView(row.box)
                            row.fill(items)
                        }
                    }
                }
            }
        }
    }
}
