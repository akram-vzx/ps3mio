package io.ps3mio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Player: ExoPlayer (the same engine Stremio Android uses) with a Stremio-style loading screen
 * (title logo on the backdrop), an on-screen display, audio / subtitle menus inside the player,
 * resume position and gamepad shortcuts.  No animations except the loading spinner.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView
    private lateinit var splash: FrameLayout
    private lateinit var splashText: TextView
    private lateinit var osd: FrameLayout
    private lateinit var timeLbl: TextView
    private lateinit var durLbl: TextView
    private lateinit var seek: SeekBar
    private lateinit var playBtn: TextView
    private lateinit var speedBtn: TextView
    private lateinit var buffering: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var vid = ""
    private var osdHideAt = 0L
    private var firstFrame = false
    private var dragging = false
    private var lastSave = 0L
    private var speedIdx = 1
    private val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var nextVid: String? = null
    private var bingeGroup = ""
    private lateinit var nextBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val url = intent.getStringExtra("url") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val logo = intent.getStringExtra("logo") ?: ""
        val bg = intent.getStringExtra("bg") ?: ""
        vid = intent.getStringExtra("vid") ?: url
        val nv = intent.getStringExtra("nextVid") ?: ""
        nextVid = if (nv.isEmpty()) null else nv
        bingeGroup = intent.getStringExtra("group") ?: ""
        if (url.isEmpty()) {
            finish()
            return
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        pv = PlayerView(this)
        pv.useController = false
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        root.addView(pv, FrameLayout.LayoutParams(MATCH, MATCH))

        buffering = ProgressBar(this)
        buffering.indeterminateTintList = ColorStateList.valueOf(Col.accent)
        buffering.visibility = View.GONE
        root.addView(buffering, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))

        buildOsd(root, title)
        buildNext(root)
        buildSplash(root, title, logo, bg)
        root.setOnClickListener { toggleOsd() }
        setContentView(root)

        startPlayback(url)
        handler.post(ticker)
    }

    // ---- UI -------------------------------------------------------------------------------
    private fun buildSplash(root: FrameLayout, title: String, logo: String, bg: String) {
        splash = FrameLayout(this)
        splash.setBackgroundColor(Color.BLACK)
        val bgImg = ImageView(this)
        bgImg.scaleType = ImageView.ScaleType.CENTER_CROP
        bgImg.alpha = 0.35f
        splash.addView(bgImg, FrameLayout.LayoutParams(MATCH, MATCH))
        Img.load(bgImg, if (bg.isEmpty()) null else bg, 1280)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        if (logo.isNotEmpty()) {
            val l = ImageView(this)
            l.adjustViewBounds = true
            l.scaleType = ImageView.ScaleType.FIT_CENTER
            box.addView(l, LinearLayout.LayoutParams(dp(420), dp(170)))
            Img.load(l, logo, 800)
        } else {
            val t = label(this, title, 34f, Col.text, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(40), 0, dp(40), 0)
            box.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        val spin = ProgressBar(this)
        spin.indeterminateTintList = ColorStateList.valueOf(Col.accent)
        val slp = LinearLayout.LayoutParams(dp(48), dp(48))
        slp.topMargin = dp(26)
        box.addView(spin, slp)
        splashText = label(this, "Loading...", 14f, Col.dim)
        splashText.gravity = Gravity.CENTER
        val tlp = LinearLayout.LayoutParams(WRAP, WRAP)
        tlp.topMargin = dp(14)
        box.addView(splashText, tlp)
        splash.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(splash, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun touchBtn(text: String, onClick: () -> Unit): TextView {
        val b = label(this, text, 14f)
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.gravity = Gravity.CENTER
        b.background = focusBg(this, 8, Color.parseColor("#44000000"))
        b.isClickable = true
        b.setOnClickListener {
            onClick()
            showOsd()
        }
        return b
    }

    private fun buildOsd(root: FrameLayout, title: String) {
        osd = FrameLayout(this)
        osd.visibility = View.GONE

        val titleLbl = label(this, title, 18f, Col.text, true)
        titleLbl.setPadding(dp(24), dp(14), dp(24), dp(36))
        titleLbl.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT)
        )
        osd.addView(titleLbl, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setPadding(dp(24), dp(40), dp(24), dp(14))
        bottom.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#DD000000"))
        )

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = Gravity.CENTER_VERTICAL
        timeLbl = label(this, "0:00", 13f)
        durLbl = label(this, "0:00", 13f)
        seek = SeekBar(this)
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val p = player ?: return
                val d = p.duration
                if (d > 0) p.seekTo(d * progress / 1000L)
                showOsd()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                dragging = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                dragging = false
            }
        })
        row1.addView(timeLbl, LinearLayout.LayoutParams(WRAP, WRAP))
        row1.addView(seek, LinearLayout.LayoutParams(0, WRAP, 1f))
        row1.addView(durLbl, LinearLayout.LayoutParams(WRAP, WRAP))
        bottom.addView(row1, LinearLayout.LayoutParams(MATCH, WRAP))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.gravity = Gravity.CENTER
        val blp = LinearLayout.LayoutParams(WRAP, WRAP)
        blp.setMargins(dp(5), dp(6), dp(5), dp(6))
        playBtn = touchBtn("Pause") { togglePlay() }
        speedBtn = touchBtn("1.0x") { cycleSpeed() }
        row2.addView(touchBtn("-10s") { seekBy(-10_000L) }, blp)
        row2.addView(playBtn, blp)
        row2.addView(touchBtn("+10s") { seekBy(10_000L) }, blp)
        row2.addView(touchBtn("Audio") { showTracks(C.TRACK_TYPE_AUDIO, "Audio") }, blp)
        row2.addView(touchBtn("Subtitles") { showTracks(C.TRACK_TYPE_TEXT, "Subtitles") }, blp)
        row2.addView(speedBtn, blp)
        bottom.addView(row2, LinearLayout.LayoutParams(MATCH, WRAP))

        val hint = label(this, "A pause   Left/Right seek   L1/R1 seek (long)   R2 next episode   Y subtitles   X audio   B back", 11f, Col.dim)
        hint.gravity = Gravity.CENTER
        bottom.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
        osd.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        root.addView(osd, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ---- playback -------------------------------------------------------------------------
    private fun startPlayback(url: String) {
        val dsf = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) ps3mio/0.1")
        val hdr = HashMap<String, String>()
        try {
            val h = JSONObject(intent.getStringExtra("headers") ?: "{}")
            val keys = h.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                hdr[k] = h.optString(k)
            }
        } catch (e: Exception) {
            // ignore malformed headers
        }
        if (hdr.isNotEmpty()) dsf.setDefaultRequestProperties(hdr)

        val msf = DefaultMediaSourceFactory(this).setDataSourceFactory(dsf)
        val hw = PlayerPrefs.hw
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val all = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
            if (hw) all else {
                val sw = all.filter { !it.hardwareAccelerated }
                if (sw.isEmpty()) all else sw
            }
        }
        val rf = DefaultRenderersFactory(this).setMediaCodecSelector(selector).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, rf).setMediaSourceFactory(msf).build()
        player = p
        pv.player = p
        applyPrefs(p)

        val subCfg = ArrayList<MediaItem.SubtitleConfiguration>()
        try {
            val arr = JSONArray(intent.getStringExtra("subs") ?: "[]")
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val u = s.optString("url")
                if (u.isEmpty()) continue
                val lang = s.optString("lang")
                val mime = when {
                    u.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                    u.contains(".ass", true) || u.contains(".ssa", true) -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                val name = (if (lang.isNotEmpty()) langName(lang) else "Subtitle") + " - " + s.optString("addon")
                subCfg.add(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(u))
                        .setMimeType(mime)
                        .setLanguage(if (lang.isEmpty()) null else lang)
                        .setLabel(name)
                        .build()
                )
            }
        } catch (e: Exception) {
            // ignore malformed subtitle list
        }

        val item = MediaItem.Builder().setUri(url).setSubtitleConfigurations(subCfg).build()
        val resume = Prefs.getPos(vid)
        if (resume > 30_000L) p.setMediaItem(item, resume) else p.setMediaItem(item)

        p.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
                splash.visibility = View.GONE
                showOsd()
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering.visibility =
                    if (state == Player.STATE_BUFFERING && firstFrame) View.VISIBLE else View.GONE
                if (!firstFrame) {
                    splashText.text = when (state) {
                        Player.STATE_BUFFERING -> "Buffering..."
                        Player.STATE_READY -> "Starting..."
                        else -> "Loading..."
                    }
                }
                if (state == Player.STATE_ENDED) {
                    Prefs.setPos(vid, 0L)
                    if (PlayerPrefs.binge && nextVid != null) playNext() else finish()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.text = if (isPlaying) "Pause" else "Play"
            }

            override fun onPlayerError(error: PlaybackException) {
                val msg = "Playback error: " + error.errorCodeName
                if (!firstFrame) splashText.text = msg + "   (press B to go back)"
                else {
                    android.widget.Toast.makeText(this@PlayerActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
        p.prepare()
        p.playWhenReady = true
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val d = p.duration
                val pos = p.currentPosition
                timeLbl.text = fmt(pos)
                if (d > 0) {
                    durLbl.text = fmt(d)
                    if (!dragging) {
                        seek.progress = (pos * 1000L / d).toInt()
                        seek.secondaryProgress = (p.bufferedPosition * 1000L / d).toInt()
                    }
                }
                val now = System.currentTimeMillis()
                val pop = PlayerPrefs.popupSec
                nextBtn.visibility =
                    if (nextVid != null && pop > 0 && d > 0 && pos > 0 && d - pos <= pop * 1000L) View.VISIBLE else View.GONE
                if (osd.visibility == View.VISIBLE && now > osdHideAt && p.isPlaying) osd.visibility = View.GONE
                if (firstFrame && now - lastSave > 10_000L) {
                    savePos()
                    lastSave = now
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun fmt(ms: Long): String {
        val t = (ms / 1000L).coerceAtLeast(0L)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    private fun savePos() {
        val p = player ?: return
        val d = p.duration
        val pos = p.currentPosition
        Prefs.setPos(vid, if (d > 0 && pos > d * 95 / 100) 0L else pos)
    }

    private fun showOsd() {
        osd.visibility = View.VISIBLE
        osdHideAt = System.currentTimeMillis() + 4000L
    }

    private fun toggleOsd() {
        if (osd.visibility == View.VISIBLE) osd.visibility = View.GONE else showOsd()
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val d = p.duration
        var target = p.currentPosition + deltaMs
        if (target < 0) target = 0
        if (d > 0 && target > d) target = d
        p.seekTo(target)
    }

    private fun cycleSpeed() {
        val p = player ?: return
        speedIdx = (speedIdx + 1) % speeds.size
        p.setPlaybackSpeed(speeds[speedIdx])
        speedBtn.text = speeds[speedIdx].toString() + "x"
    }

    // ---- settings (Stremio Settings > Player) + next episode ---------------------------------
    private fun buildNext(root: FrameLayout) {
        nextBtn = label(this, "Next episode   (R2)", 15f, Col.text, true)
        nextBtn.setPadding(dp(18), dp(12), dp(18), dp(12))
        nextBtn.background = focusBg(this, 8, Col.accent)
        nextBtn.isClickable = true
        nextBtn.visibility = View.GONE
        nextBtn.setOnClickListener { playNext() }
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, dp(28), dp(120))
        root.addView(nextBtn, lp)
    }

    private fun playNext() {
        val nv = nextVid ?: return
        Prefs.setPos(vid, 0L)
        val r = Intent()
        r.putExtra("nextVid", nv)
        r.putExtra("group", bingeGroup)
        setResult(RESULT_OK, r)
        finish()
    }

    private fun applyPrefs(p: ExoPlayer) {
        val sv: SubtitleView? = pv.subtitleView
        if (sv != null) {
            val outline = PlayerPrefs.subOutline
            sv.setApplyEmbeddedStyles(false)
            sv.setStyle(
                CaptionStyleCompat(
                    PlayerPrefs.subColor, PlayerPrefs.subBg, Color.TRANSPARENT,
                    if (Color.alpha(outline) == 0) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    outline, null
                )
            )
            sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * PlayerPrefs.subSize / 100f)
        }
        val b = p.trackSelectionParameters.buildUpon()
        if (PlayerPrefs.audioLang.isNotEmpty()) b.setPreferredAudioLanguage(PlayerPrefs.audioLang)
        if (PlayerPrefs.subLang.isNotEmpty()) b.setPreferredTextLanguage(PlayerPrefs.subLang)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        if (!PlayerPrefs.surround) b.setMaxAudioChannelCount(2)
        p.trackSelectionParameters = b.build()
    }

    // ---- audio / subtitle menus (inside the player, like Stremio) -----------------------------
    private val lang3: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (c in Locale.getISOLanguages()) {
            try {
                val l = Locale(c)
                m[l.isO3Language] = l.displayLanguage
            } catch (e: Exception) {
                // some codes have no 3-letter mapping
            }
        }
        m
    }

    private fun langName(code: String): String {
        val c = code.lowercase()
        return lang3[c] ?: (if (c.length == 2) Locale(c).displayLanguage else code)
    }

    private fun trackLabel(f: Format): String {
        val parts = ArrayList<String>()
        val lang = f.language
        if (lang != null && lang != "und") parts.add(langName(lang))
        val lb = f.label
        if (lb != null && lb.isNotEmpty()) parts.add(lb)
        if (f.channelCount > 0) parts.add(f.channelCount.toString() + "ch")
        return if (parts.isEmpty()) "Track" else parts.joinToString(" - ")
    }

    private fun showTracks(type: Int, title: String) {
        val p = player ?: return
        val labels = ArrayList<String>()
        val refs = ArrayList<Pair<Tracks.Group, Int>>()
        if (type == C.TRACK_TYPE_TEXT) labels.add("Off")
        var checked = 0
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                if (g.isTrackSelected(i)) checked = labels.size
                labels.add(trackLabel(g.getTrackFormat(i)))
                refs.add(Pair(g, i))
            }
        }
        if (labels.isEmpty() || (type == C.TRACK_TYPE_TEXT && refs.isEmpty())) {
            android.widget.Toast.makeText(this, "No $title tracks", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val offset = if (type == C.TRACK_TYPE_TEXT) 1 else 0
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val params = p.trackSelectionParameters.buildUpon()
                if (type == C.TRACK_TYPE_TEXT && which == 0) {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    val ref = refs[which - offset]
                    params.setTrackTypeDisabled(type, false)
                    params.setOverrideForType(TrackSelectionOverride(ref.first.mediaTrackGroup, ref.second))
                }
                p.trackSelectionParameters = params.build()
                dialog.dismiss()
            }
            .show()
    }

    // ---- keys: gamepad / remote / keyboard ---------------------------------------------------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        val p = player ?: return super.dispatchKeyEvent(e)
        val step = PlayerPrefs.seekArrowMs() * (1 + minOf(e.repeatCount / 4, 5))
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (e.repeatCount == 0) togglePlay()
                showOsd()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBy(-step)
                showOsd()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy(step)
                showOsd()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                seekBy(-PlayerPrefs.seekShiftMs())
                showOsd()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                seekBy(PlayerPrefs.seekShiftMs())
                showOsd()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val dir = if (e.keyCode == KeyEvent.KEYCODE_DPAD_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                showTracks(C.TRACK_TYPE_TEXT, "Subtitles")
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                showTracks(C.TRACK_TYPE_AUDIO, "Audio")
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_N -> {
                if (nextVid != null) playNext()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                toggleOsd()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                finish()
                return true
            }
        }
        if (p.isPlaying) {
            // unknown key: just show the controls
            showOsd()
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        if (firstFrame) savePos()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release()
        player = null
        super.onDestroy()
    }
}
