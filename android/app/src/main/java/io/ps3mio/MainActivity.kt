package io.ps3mio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shell: left navigation (Board / Discover / Library / Search / Addons / Settings) + content,
 * the same structure as Stremio on webOS. No transitions or animations.
 */
class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private val stack = ArrayList<Screen>()
    private var current: Screen? = null
    private var board: BoardScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        window.statusBarColor = Col.bg
        window.navigationBarColor = Col.bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.setBackgroundColor(Col.bg)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.VERTICAL
        nav.setBackgroundColor(Col.panel)
        nav.setPadding(dp(10), dp(16), dp(10), dp(16))

        val logo = ImageView(this)
        logo.setImageResource(R.mipmap.logo)
        val llp = LinearLayout.LayoutParams(dp(48), dp(48))
        llp.gravity = Gravity.CENTER_HORIZONTAL
        llp.bottomMargin = dp(14)
        nav.addView(logo, llp)

        val b = BoardScreen(this)
        board = b
        addNav(nav, "Board") { b }
        addNav(nav, "Discover") { DiscoverScreen(this) }
        addNav(nav, "Library") { LibraryScreen(this) }
        addNav(nav, "Search") { SearchScreen(this) }
        addNav(nav, "Addons") { AddonsScreen(this) }
        addNav(nav, "Settings") { SettingsScreen(this) }

        root.addView(nav, LinearLayout.LayoutParams(dp(150), MATCH))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))
        setContentView(root)

        top(b)
    }

    private fun addNav(nav: LinearLayout, text: String, make: () -> Screen) {
        val btn = button(this, text) { top(make()) }
        btn.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.bottomMargin = dp(6)
        nav.addView(btn, lp)
    }

    // ---- navigation -----------------------------------------------------------------------
    fun top(s: Screen) {
        stack.clear()
        show(s)
    }

    fun open(s: Screen) {
        val c = current
        if (c != null) stack.add(c)
        show(s)
    }

    fun openMeta(m: JSONObject) {
        open(DetailScreen(this, m.optString("type"), m.optString("id"), m.optString("name"), m.optString("poster")))
    }

    fun goHome() {
        val b = board
        if (b != null) {
            b.reload()
            top(b)
        }
    }

    private fun show(s: Screen) {
        current = s
        content.removeAllViews()
        val old = s.view.parent as? ViewGroup
        old?.removeView(s.view)
        content.addView(s.view, FrameLayout.LayoutParams(MATCH, MATCH))
        s.onShow()
        s.view.post { s.view.requestFocus() }
    }

    private fun back() {
        if (stack.isNotEmpty()) {
            show(stack.removeAt(stack.size - 1))
            return
        }
        val b = board
        if (b != null && current !== b) top(b) else finish()
    }

    fun refresh() {
        toast("Refreshing...")
        Net.bg {
            Stremio.sync()
            Stremio.invalidate()
            Net.ui {
                val b = board
                if (b != null && current === b) b.reload()
                toast("Refreshed")
            }
        }
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---- player ---------------------------------------------------------------------------
    fun play(
        url: String,
        title: String,
        meta: JSONObject?,
        poster: String?,
        vid: String,
        subs: List<JSONObject>,
        headers: JSONObject?
    ) {
        val i = Intent(this, PlayerActivity::class.java)
        i.putExtra("url", url)
        i.putExtra("title", title)
        i.putExtra("vid", vid)
        i.putExtra("logo", meta?.optString("logo") ?: "")
        val bgUrl = meta?.optString("background") ?: ""
        i.putExtra("bg", if (bgUrl.isNotEmpty()) bgUrl else (poster ?: ""))
        val arr = JSONArray()
        for (s in subs) arr.put(s)
        i.putExtra("subs", arr.toString())
        if (headers != null) i.putExtra("headers", headers.toString())
        startActivity(i)
    }

    // ---- gamepad ----------------------------------------------------------------------------
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B -> {
                back()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                top(SearchScreen(this))
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                refresh()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        back()
    }
}
