package io.ps3mio

import android.graphics.Color

/**
 * Player settings, mirroring Stremio's Settings > Player (webOS / Android TV):
 * Subtitles, Audio, Controls, Auto-play and Advanced.
 */
object PlayerPrefs {
    class Opt(val value: String, val label: String)
    class Setting(val section: String, val key: String, val title: String, val opts: List<Opt>, val def: String)

    private fun onOff() = listOf(Opt("1", "On"), Opt("0", "Off"))

    private val LANGS = listOf(
        Opt("", "None / Auto"), Opt("en", "English"), Opt("ar", "Arabic"), Opt("fr", "French"),
        Opt("es", "Spanish"), Opt("de", "German"), Opt("it", "Italian"), Opt("pt", "Portuguese"),
        Opt("tr", "Turkish"), Opt("ru", "Russian"), Opt("nl", "Dutch"), Opt("pl", "Polish"),
        Opt("ja", "Japanese"), Opt("ko", "Korean"), Opt("zh", "Chinese"), Opt("hi", "Hindi")
    )
    private val SIZES = listOf(75, 100, 125, 150, 175, 200, 250).map { Opt(it.toString(), "$it%") }
    private val TEXT = listOf(
        Opt("#FFFFFF", "White"), Opt("#FFEB3B", "Yellow"), Opt("#4CAF50", "Green"),
        Opt("#00BCD4", "Cyan"), Opt("#FF9800", "Orange")
    )
    private val BG = listOf(Opt("#00000000", "Transparent"), Opt("#99000000", "Black 60%"), Opt("#FF000000", "Black"))
    private val OUTLINE = listOf(Opt("#FF000000", "Black"), Opt("#FFFFFFFF", "White"), Opt("#00000000", "None"))
    private val SEEKS = listOf(3, 5, 10, 15, 20, 30).map { Opt(it.toString(), "$it s") }
    private val POPUP = ArrayList<Opt>().also {
        it.add(Opt("0", "Disabled"))
        for (s in 5..90 step 5) it.add(Opt(s.toString(), "$s s"))
    }
    private val EXT = listOf(
        Opt("", "Disabled (built-in player)"), Opt("ask", "Allow choosing"), Opt("vlc", "VLC"),
        Opt("mxplayer", "MX Player"), Opt("justplayer", "Just Player")
    )

    val ALL: List<Setting> = listOf(
        Setting("Subtitles", "subLang", "Default subtitle language", LANGS, ""),
        Setting("Subtitles", "subSize", "Subtitle size", SIZES, "100"),
        Setting("Subtitles", "subColor", "Subtitle text color", TEXT, "#FFFFFF"),
        Setting("Subtitles", "subBg", "Subtitle background color", BG, "#00000000"),
        Setting("Subtitles", "subOutline", "Subtitle outline color", OUTLINE, "#FF000000"),
        Setting("Audio", "audioLang", "Default audio track", LANGS, ""),
        Setting("Audio", "surround", "Surround sound (keep multi-channel audio)", onOff(), "1"),
        Setting("Controls", "seekArrow", "Seek duration (left / right)", SEEKS, "10"),
        Setting("Controls", "seekShift", "Seek duration (L1 / R1)", SEEKS, "30"),
        Setting("Auto-play", "binge", "Auto-play next episode (binge watching)", onOff(), "1"),
        Setting("Auto-play", "popup", "Next episode popup before the end", POPUP, "30"),
        Setting("Advanced", "ext", "External player", EXT, ""),
        Setting("Advanced", "hw", "Hardware decoding", onOff(), "1")
    )

    private fun def(key: String): String = ALL.first { it.key == key }.def

    fun str(key: String): String = Prefs.getStr("pp_$key", def(key))

    fun put(key: String, v: String) {
        Prefs.setStr("pp_$key", v)
    }

    fun labelOf(s: Setting): String {
        val cur = str(s.key)
        return s.opts.firstOrNull { it.value == cur }?.label ?: cur
    }

    fun cycle(s: Setting) {
        val cur = str(s.key)
        val i = s.opts.indexOfFirst { it.value == cur }
        put(s.key, s.opts[(i + 1) % s.opts.size].value)
    }

    val subLang: String get() = str("subLang")
    val audioLang: String get() = str("audioLang")
    val subSize: Int get() = str("subSize").toIntOrNull() ?: 100
    val subColor: Int get() = Color.parseColor(str("subColor"))
    val subBg: Int get() = Color.parseColor(str("subBg"))
    val subOutline: Int get() = Color.parseColor(str("subOutline"))
    val surround: Boolean get() = str("surround") == "1"
    val binge: Boolean get() = str("binge") == "1"
    val popupSec: Int get() = str("popup").toIntOrNull() ?: 30
    val ext: String get() = str("ext")
    val hw: Boolean get() = str("hw") == "1"
    fun seekArrowMs(): Long = (str("seekArrow").toLongOrNull() ?: 10L) * 1000L
    fun seekShiftMs(): Long = (str("seekShift").toLongOrNull() ?: 30L) * 1000L
}
