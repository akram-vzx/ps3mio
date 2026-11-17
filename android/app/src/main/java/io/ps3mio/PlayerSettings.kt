package io.ps3mio

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/** Settings > Player: same groups and options as Stremio. Press a row to change its value. */
class PlayerSettingsScreen(act: MainActivity) : Screen(act) {
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
        col.addView(label(act, "Player", 24f, Col.text, true))
        var section = ""
        for (s in PlayerPrefs.ALL) {
            if (s.section != section) {
                section = s.section
                val h = label(act, section, 15f, Col.accent, true)
                h.setPadding(0, dp(18), 0, dp(6))
                col.addView(h)
            }
            val b = button(act, "") { }
            b.text = s.title + ":  " + PlayerPrefs.labelOf(s)
            b.setOnClickListener {
                PlayerPrefs.cycle(s)
                b.text = s.title + ":  " + PlayerPrefs.labelOf(s)
            }
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.bottomMargin = dp(6)
            b.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            col.addView(b, lp)
        }
    }
}
