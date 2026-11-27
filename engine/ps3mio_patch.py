#!/usr/bin/env python3
"""
ps3mio patcher - turns a Movian source tree into "ps3mio":
  * extra plugin folders that are scanned at start-up (phone: Download/m7, PS3: USB/HDD m7io)
  * "Reload plugins" action in Settings > Plugins  (the Refresh)
  * built-in Stremio plugin (no installation needed) + start page = stremio:start
  * BitTorrent enabled by default
Works on andoma/movian (Android build) and Cruslan/PS3-Movian-Next (PS3 build).
usage: ps3mio_patch.py <movian-tree> [--plugin <dir>]
  --plugin  bundles the Stremio plugin into the binary as a built-in plugin (bundle://res/stremio)
Every step is marker based; a missing marker prints a warning and the build continues.
"""
import re, sys, os

root = sys.argv[1] if len(sys.argv) > 1 else "."
problems = []

def warn(msg):
    problems.append(msg)
    print("::warning::m7io patch: " + msg)

def rd(rel):
    p = os.path.join(root, rel)
    if not os.path.exists(p):
        warn("missing file " + rel)
        return None
    return open(p, encoding="utf-8", errors="surrogateescape").read()

def wr(rel, s):
    open(os.path.join(root, rel), "w", encoding="utf-8", errors="surrogateescape").write(s)

HELPER = r'''
/* ---------------------------------------------------------------------
 * m7io: extra plugin folders + reload ("refresh")
 * Plugin .zip files dropped in these folders are loaded at start-up and
 * whenever Settings > Plugins > "Reload plugins" is pressed.
 * ------------------------------------------------------------------- */
static int
m7io_scan_dir(const char *dir, int flags)
{
  char path[URL_MAX];
  char errbuf[200];
  fa_dir_entry_t *fde;
  int n = 0;

  fa_dir_t *fd = fa_scandir(dir, NULL, 0);
  if(fd == NULL)
    return 0;

  RB_FOREACH(fde, &fd->fd_entries, fde_link) {
    const char *u = rstr_get(fde->fde_url);
    size_t l = u ? strlen(u) : 0;
    if(l < 5 || strcasecmp(u + l - 4, ".zip"))
      continue;
    snprintf(path, sizeof(path), "zip://%s", u);
    if(plugin_load(path, errbuf, sizeof(errbuf), flags)) {
      TRACE(TRACE_ERROR, "plugins", "ps3mio: unable to load %s\n%s", path, errbuf);
    } else {
      TRACE(TRACE_INFO, "plugins", "ps3mio: loaded %s", path);
      n++;
    }
  }
  fa_dir_free(fd);
  return n;
}

static void
m7io_load_builtin(void)
{
  char errbuf[200];
  if(plugin_load("bundle://res/stremio", errbuf, sizeof(errbuf),
                 PLUGIN_LOAD_FORCE))
    TRACE(TRACE_DEBUG, "plugins", "ps3mio: no built-in Stremio plugin: %s", errbuf);
  else
    TRACE(TRACE_INFO, "plugins", "ps3mio: built-in Stremio plugin loaded");
}

static int
m7io_load_dirs(int flags)
{
  int n = 0;
#if defined(__ANDROID__)
  n += m7io_scan_dir("/storage/emulated/0/Download/ps3mio", flags);
  n += m7io_scan_dir("/storage/emulated/0/Download/m7", flags);
  if(!n) {
    n += m7io_scan_dir("/sdcard/Download/ps3mio", flags);
    n += m7io_scan_dir("/sdcard/Download/m7", flags);
  }
#endif
#if defined(PS3)
  n += m7io_scan_dir("/dev_usb000/ps3mio", flags);
  n += m7io_scan_dir("/dev_usb001/ps3mio", flags);
  n += m7io_scan_dir("/dev_hdd0/ps3mio", flags);
#endif
  return n;
}

static void
m7io_reload_action(void *opaque)
{
  char inst[URL_MAX];
  char msg[120];
  int flags = PLUGIN_LOAD_AS_INSTALLED | PLUGIN_LOAD_FORCE;
  int n;

  m7io_load_builtin();
  n = m7io_load_dirs(flags);

  snprintf(inst, sizeof(inst), "%s/installedplugins", gconf.persistent_path);
  n += m7io_scan_dir(inst, flags);

  snprintf(msg, sizeof(msg), "ps3mio: %d plugin(s) reloaded", n);
  notify_add(NULL, NOTIFY_INFO, NULL, 4, rstr_alloc(msg));
}

'''

SETTING = r'''
  setting_create(SETTING_ACTION, dir, SETTINGS_INITIAL_UPDATE,
                 SETTING_TITLE(_p("Reload plugins from folders (refresh)")),
                 SETTING_CALLBACK(m7io_reload_action, NULL),
                 SETTING_MUTEX(&plugin_mutex),
                 NULL);
'''

# ---- 1. plugins.c -------------------------------------------------------
s = rd("src/plugins.c")
if s is not None and "m7io_scan_dir" not in s:
    ok = True
    m = re.search(r'^#define REPO_ERROR_NETWORK', s, re.M)
    if not m:
        warn("plugins.c: REPO_ERROR_NETWORK marker not found (helper not inserted)")
        ok = False
    else:
        s = s[:m.start()] + HELPER + s[m.start():]
    if ok:
        m2 = re.search(r'^\s*plugin_load_installed\(\);', s, re.M)
        if m2:
            s = s[:m2.start()] + "  m7io_load_builtin();\n  m7io_load_dirs(PLUGIN_LOAD_AS_INSTALLED | PLUGIN_LOAD_FORCE);\n" + s[m2.start():]
        else:
            warn("plugins.c: plugin_load_installed() call not found (folders not scanned at start-up)")
        m3 = re.search(r'prop_t \*dir = setting_get_dir\("general:plugins"\);\n', s)
        if m3:
            s = s[:m3.end()] + SETTING + s[m3.end():]
        else:
            warn("plugins.c: general:plugins settings marker not found (no Reload action)")
        wr("src/plugins.c", s)
        print("ok  plugins.c patched")
elif s is not None:
    print("ok  plugins.c already patched")

# ---- 2. start page = Stremio (no Movian home), no update / usage / news checks ---------
s = rd("src/navigator.h")
if s is not None and "stremio:start" not in s:
    n = s.count('#define NAV_HOME "page:home"')
    if n == 1:
        wr("src/navigator.h", s.replace('#define NAV_HOME "page:home"', '#define NAV_HOME "stremio:start"'))
        print("ok  navigator.h NAV_HOME = stremio:start")
    else:
        warn("navigator.h: NAV_HOME definition not found (app will start on the Movian home page)")

s = rd("src/navigator.c")
if s is not None and "ps3mio: wait" not in s:
    m = re.search(r'static navigator_t \*\s*\nnav_create\(void\)\s*\n\{\n', s)
    if m:
        wait = """  /* ps3mio: wait until built-in plugins are loaded so the first page (stremio:start) has a handler */
  hts_mutex_lock(&gconf.state_mutex);
  while(gconf.navigator_can_start == 0)
    hts_cond_wait(&gconf.state_cond, &gconf.state_mutex);
  hts_mutex_unlock(&gconf.state_mutex);

"""
        s = s[:m.end()] + wait + s[m.end():]
        wr("src/navigator.c", s)
        print("ok  navigator.c waits for plugins before the first page")
    else:
        warn("navigator.c: nav_create not found")

s = rd("src/main.c")
if s is not None and "ps3mio: no" not in s:
    changed = 0
    if "if(!gconf.disable_upgrades) {" in s:
        s = s.replace("if(!gconf.disable_upgrades) {", "if(0) { /* ps3mio: no update checks */", 1); changed += 1
    if re.search(r'^\s*usage_start\(\);', s, re.M):
        s = re.sub(r'^(\s*)usage_start\(\);', r'\1if(0) usage_start(); /* ps3mio: no usage reporting */', s, count=1, flags=re.M); changed += 1
    if re.search(r'^\s*load_site_news\(\);', s, re.M):
        s = re.sub(r'^(\s*)load_site_news\(\);', r'\1if(0) load_site_news(); /* ps3mio: no site news */', s, count=1, flags=re.M); changed += 1
    if changed == 3:
        wr("src/main.c", s)
        print("ok  main.c: update / usage / news checks disabled")
    else:
        warn("main.c: expected 3 startup network calls, found %d (Movian update checks may remain)" % changed)

# ---- 3. bittorrent on by default -----------------------------------------
s = rd("src/backend/bittorrent/torrent_settings.c")
if s is not None:
    i = s.find('"Enable bittorrent"')
    if i < 0:
        warn("torrent_settings.c: 'Enable bittorrent' not found")
    else:
        m = re.compile(r'SETTING_VALUE\(\s*\d+\s*\)').search(s, i, i + 400)
        if m:
            s = s[:m.start()] + "SETTING_VALUE(1)" + s[m.end():]
            wr("src/backend/bittorrent/torrent_settings.c", s)
            print("ok  bittorrent enabled by default")
        else:
            warn("torrent_settings.c: no SETTING_VALUE near 'Enable bittorrent'")

BT_HELPER = r'''
/* ---------------------------------------------------------------------
 * m7io: choose the right file inside a torrent (season packs).
 *   &m7idx=N     file index (Stremio "fileIdx")
 *   &m7se=S:E    season:episode, matched against file names (S01E02 / 1x02)
 * Falls back to the biggest file (stock behaviour).
 * ------------------------------------------------------------------- */
static int
m7io_ifind(const char *hay, const char *needle)
{
  size_t n = strlen(needle);
  for(; *hay; hay++) {
    if(!strncasecmp(hay, needle, n))
      return 1;
  }
  return 0;
}

/* Magnet links have no file list until the metadata arrives from peers.  The stock player path
 * looked for a file immediately and gave up ("No files in torrent"), which closed the player.
 * Wait (mutex released) until the files are known, up to `seconds`. */
static void
m7io_wait_files(torrent_t *to, int seconds)
{
  int i;
  if(!TAILQ_EMPTY(&to->to_files))
    return;
  TRACE(TRACE_INFO, "ps3mio", "waiting for torrent metadata (up to %d s)", seconds);
  torrent_retain(to);
  for(i = 0; i < seconds * 5 && TAILQ_EMPTY(&to->to_files); i++) {
    hts_mutex_unlock(&bittorrent_mutex);
    usleep(200000);
    hts_mutex_lock(&bittorrent_mutex);
  }
  torrent_release(to);
  if(TAILQ_EMPTY(&to->to_files))
    TRACE(TRACE_ERROR, "ps3mio", "torrent metadata timeout - no peers answered");
  else
    TRACE(TRACE_INFO, "ps3mio", "torrent metadata ready after about %d s", i / 5);
}

static torrent_file_t *
m7io_pick_file(torrent_t *to, const char *url)
{
  torrent_file_t *tf, *best = NULL;
  const char *p;

  m7io_wait_files(to, 120);

  if(url != NULL && (p = strstr(url, "m7idx=")) != NULL) {
    int want = atoi(p + 6);
    int i = 0;
    TAILQ_FOREACH(tf, &to->to_files, tf_torrent_link) {
      if(i++ == want)
        return tf;
    }
  }

  if(url != NULL && (p = strstr(url, "m7se=")) != NULL) {
    int sn = 0, ep = 0;
    if(sscanf(p + 5, "%d:%d", &sn, &ep) == 2 && sn >= 0 && ep > 0) {
      char a[24], b[24];
      snprintf(a, sizeof(a), "s%02de%02d", sn, ep);
      snprintf(b, sizeof(b), "%dx%02d", sn, ep);
      TAILQ_FOREACH(tf, &to->to_files, tf_torrent_link) {
        if(tf->tf_fullpath == NULL)
          continue;
        if(!m7io_ifind(tf->tf_fullpath, a) && !m7io_ifind(tf->tf_fullpath, b))
          continue;
        if(best == NULL || best->tf_size < tf->tf_size)
          best = tf;
      }
      if(best != NULL)
        return best;
    }
  }
  best = find_movie_torrent(to);
  TRACE(TRACE_INFO, "ps3mio", "torrent file chosen: %s", best ? best->tf_fullpath : "(none)");
  return best;
}

'''

# ---- 4. bt_backend.c : file selection ---------------------------------------
s = rd("src/backend/bittorrent/bt_backend.c")
if s is not None and "m7io_pick_file" not in s:
    mo = re.search(r'static int\s*\ntorrent_movie_open\(', s)
    mp = re.search(r'\nbt_playvideo\(', s)
    if not mo or not mp or "find_movie_torrent(to)" not in s:
        warn("bt_backend.c: torrent_movie_open/bt_playvideo/find_movie_torrent not found (no season-pack file selection)")
    else:
        # replace calls first (positions are after each function's definition), then insert helper
        i = s.find("find_movie_torrent(to)", mp.start())
        s = s[:i] + "m7io_pick_file(to, url)" + s[i + len("find_movie_torrent(to)"):]
        mo = re.search(r'static int\s*\ntorrent_movie_open\(', s)
        i = s.find("find_movie_torrent(to)", mo.start())
        s = s[:i] + "m7io_pick_file(to, url0)" + s[i + len("find_movie_torrent(to)"):]
        mo = re.search(r'/\*\*\s*\n\s*\*\s*\n\s*\*/\s*\nstatic int\s*\ntorrent_movie_open\(', s)
        pos = mo.start() if mo else re.search(r'static int\s*\ntorrent_movie_open\(', s).start()
        s = s[:pos] + BT_HELPER + s[pos:]
        wr("src/backend/bittorrent/bt_backend.c", s)
        print("ok  bt_backend.c file selection (m7idx / m7se)")
elif s is not None:
    print("ok  bt_backend.c already patched")

# ---- 5. built-in Stremio plugin ---------------------------------------------
plugin_dir = None
if "--plugin" in sys.argv:
    plugin_dir = sys.argv[sys.argv.index("--plugin") + 1]
if plugin_dir:
    import shutil
    dst = os.path.join(root, "res", "stremio")
    os.makedirs(dst, exist_ok=True)
    for f in ("plugin.json", "stremio.js", "logo.png"):
        src = os.path.join(plugin_dir, f)
        if os.path.exists(src):
            shutil.copy(src, os.path.join(dst, f))
        else:
            warn("plugin file missing: " + f)
    mk = rd("Makefile")
    if mk is not None and "res/stremio" not in mk:
        m = re.search(r'^BUNDLES \+= \$\(sort \$\(BUNDLES-yes\)\)', mk, re.M)
        if m:
            mk = mk[:m.start()] + "BUNDLES += res/stremio\n" + mk[m.start():]
            wr("Makefile", mk)
            print("ok  Makefile: res/stremio bundled")
        else:
            warn("Makefile: BUNDLES marker not found (plugin not bundled - use the zip in the ps3mio folder instead)")

# ---- 6. player: logo / backdrop for the loading screen + Stremio player settings ------
s = rd("src/video/video_playback.c")
if s is not None and "ps3mio" not in s:
    m = re.search(r'if\(\(str = htsmsg_get_str\(m, "icon"\)\) != NULL\) \{\n[^}]*\}\n', s)
    if m:
        add = """
    /* ps3mio: title logo + backdrop for the Stremio-style loading screen */
    if((str = htsmsg_get_str(m, "logo")) != NULL)
      prop_set(mp->mp_prop_metadata, "logo", PROP_SET_STRING, str);
    if((str = htsmsg_get_str(m, "backdrop")) != NULL)
      prop_set(mp->mp_prop_metadata, "backdrop", PROP_SET_STRING, str);

    /* ps3mio: Stremio player settings sent by the app */
    htsmsg_t *ps3mio_cfg = htsmsg_get_map(m, "ps3mio");
    if(ps3mio_cfg != NULL) {
      int32_t sv;
      if(!htsmsg_get_s32(ps3mio_cfg, "seekBack", &sv) && sv > 0)
        video_settings.seek_back_step = sv;
      if(!htsmsg_get_s32(ps3mio_cfg, "seekFwd", &sv) && sv > 0)
        video_settings.seek_fwd_step = sv;
      if(!htsmsg_get_s32(ps3mio_cfg, "resume", &sv) && sv >= 0 && sv <= 2)
        video_settings.resume_mode = sv;
    }
"""
        s = s[:m.end()] + add + s[m.end():]
        wr("src/video/video_playback.c", s)
        print("ok  video_playback.c: logo/backdrop + player settings")
    else:
        warn("video_playback.c: icon marker not found (no logo on the loading screen, settings not applied)")

# ---- 7. ps3mio skin (overlay on the flat skin) --------------------------------------
skin_dir = None
if "--skin" in sys.argv:
    skin_dir = sys.argv[sys.argv.index("--skin") + 1]
if skin_dir:
    import shutil
    flat = os.path.join(root, "glwskins", "flat")
    if not os.path.isdir(flat):
        warn("glwskins/flat not found - skin not installed")
    else:
        for dp, dn, fn in os.walk(skin_dir):
            rel = os.path.relpath(dp, skin_dir)
            os.makedirs(os.path.join(flat, rel), exist_ok=True)
            for f in fn:
                shutil.copy(os.path.join(dp, f), os.path.join(flat, rel, f))
        print("ok  skin files copied over glwskins/flat")
        u = rd("glwskins/flat/universe.view")
        if u is not None and "#7b5bf5" not in u:
            u = u.replace('$ui.color1 = "#4192ff";', '$ui.color1 = "#7b5bf5";').replace('$ui.color2 = "#306cbe";', '$ui.color2 = "#5a40c0";')
            wr("glwskins/flat/universe.view", u)
        v = rd("glwskins/flat/pages/video.view")
        if v is not None and "splashTitle" not in v:
            old = "$self.model.loading = $self.media.loading;"
            if old in v:
                v = v.replace(old, old + "\n$self.model.splashTitle = $self.media.metadata.title;\n$self.model.logo = $self.media.metadata.logo;\n$self.model.backdrop = $self.media.metadata.backdrop;\n$self.model.wipe = select($self.media.buffer.limit > 0, select($self.media.buffer.current > $self.media.buffer.limit, 1, $self.media.buffer.current / $self.media.buffer.limit), 0);", 1)
                wr("glwskins/flat/pages/video.view", v)
            else:
                warn("video.view: loading assignment not found (no splash)")
        bg = rd("glwskins/flat/background.view")
        if bg is not None:
            bg = bg.replace("pixmap:gradient:30,30,30:10,10,10", "pixmap:gradient:16,14,24:8,7,12")
            wr("glwskins/flat/background.view", bg)

# ---- 8. mirror the log to a folder the user can reach (Download/ps3mio/log.txt) -------
s = rd("src/trace.c")
if s is not None and "ps3mio_fd" not in s:
    ok = True
    if "static int log_fd;" not in s:
        ok = False
    m_write = re.search(r'write\(log_fd, "\\n", 1\) != 1\) \{\n\s*close\(log_fd\);\n\s*log_fd = -1;\n\s*\}\n', s)
    m_init = re.search(r'log_start_ts = arch_get_ts\(\);\n', s)
    if ok and m_write and m_init:
        mirror = """      if(ps3mio_fd != -1 && m7io_log_on) {
        if(write(ps3mio_fd, buf3, strlen(buf3)) != (ssize_t)strlen(buf3) ||
           write(ps3mio_fd, buf2, strlen(buf2)) != (ssize_t)strlen(buf2) ||
           write(ps3mio_fd, s, strlen(s)) != (ssize_t)strlen(s) ||
           write(ps3mio_fd, "\\n", 1) != 1) {
          close(ps3mio_fd);
          ps3mio_fd = -1;
        }
      }
"""
        s = s[:m_write.end()] + mirror + s[m_write.end():]
        m_init = re.search(r'log_start_ts = arch_get_ts\(\);\n', s)
        init = """#if defined(__ANDROID__) || defined(PS3)
  {
#if defined(__ANDROID__)
    static const char *dirs[] = {"/storage/emulated/0/Download/ps3mio", "/sdcard/Download/ps3mio", NULL};
#else
    static const char *dirs[] = {"/dev_hdd0/game/HTSS00004/USRDIR/ps3mio", "/dev_usb000/ps3mio", "/dev_hdd0/ps3mio", NULL};
#endif
    int di;
    for(di = 0; dirs[di] != NULL && ps3mio_fd == -1; di++) {
      char lp[PATH_MAX];
      mkdir(dirs[di], 0777);
      snprintf(lp, sizeof(lp), "%s/log.txt", dirs[di]);
      ps3mio_fd = open(lp, O_CREAT | O_TRUNC | O_WRONLY, 0644);
    }
  }
#endif
"""
        s = s[:m_init.end()] + init + s[m_init.end():]
        s = s.replace("static int log_fd;", "static int log_fd;\nstatic int ps3mio_fd = -1;\nint m7io_log_on = 1;   /* Settings > Enable log (global.ps3mio.log) */", 1)
        wr("src/trace.c", s)
        print("ok  trace.c: log mirrored to Download/ps3mio/log.txt (PS3: usb/hdd ps3mio/log.txt)")
    else:
        warn("trace.c: log markers not found (no log.txt mirror)")

# ---- 9. WebVTT subtitles (Stremio addons serve .vtt) -> converted to SRT on load -------
VTT_CONVERTER = r'''
/* WebVTT -> SRT converter used by ps3mio (Stremio addons serve subtitles as WebVTT). */
static int
ps3mio_vtt_num(const char **pp, int *v, int *nd)
{
  const char *p = *pp;
  int x = 0, d = 0;
  while(*p >= '0' && *p <= '9') {
    x = x * 10 + (*p - '0');
    d++;
    p++;
  }
  if(d == 0)
    return -1;
  *v = x;
  *nd = d;
  *pp = p;
  return 0;
}

static int
ps3mio_vtt_ts(const char **pp, int *h, int *m, int *s, int *ms)
{
  const char *p = *pp;
  int a, b, c = 0, f = 0, da, db, dc, df;

  while(*p == ' ' || *p == '\t')
    p++;
  if(ps3mio_vtt_num(&p, &a, &da) || *p != ':')
    return -1;
  p++;
  if(ps3mio_vtt_num(&p, &b, &db))
    return -1;
  if(*p == ':') {
    p++;
    if(ps3mio_vtt_num(&p, &c, &dc))
      return -1;
    *h = a; *m = b; *s = c;
  } else {
    *h = 0; *m = a; *s = b;
  }
  if(*p != '.' && *p != ',')
    return -1;
  p++;
  if(ps3mio_vtt_num(&p, &f, &df))
    return -1;
  while(df > 3) { f /= 10; df--; }
  while(df < 3) { f *= 10; df++; }
  *ms = f;
  *pp = p;
  return 0;
}

static size_t
ps3mio_vtt_text(const char *in, size_t n, char *out)
{
  size_t i = 0, o = 0;
  while(i < n) {
    if(in[i] == '&') {
      if(!strncmp(in + i, "&gt;", 4))   { out[o++] = '>'; i += 4; continue; }
      if(!strncmp(in + i, "&lt;", 4))   { out[o++] = '<'; i += 4; continue; }
      if(!strncmp(in + i, "&amp;", 5))  { out[o++] = '&'; i += 5; continue; }
      if(!strncmp(in + i, "&quot;", 6)) { out[o++] = '"'; i += 6; continue; }
      if(!strncmp(in + i, "&nbsp;", 6)) { out[o++] = ' '; i += 6; continue; }
      if(!strncmp(in + i, "&lrm;", 5) || !strncmp(in + i, "&rlm;", 5)) { i += 5; continue; }
    }
    out[o++] = in[i++];
  }
  return o;
}

/* out must hold at least 2 * len + 64 bytes. Returns the number of bytes written. */
static size_t
ps3mio_vtt_convert(const char *in, size_t len, char *out)
{
  size_t p = 0, o = 0;
  int idx = 0;

  if(len >= 3 && !memcmp(in, "\xef\xbb\xbf", 3))
    p = 3;
  if(len - p < 6 || memcmp(in + p, "WEBVTT", 6))
    return 0;
  while(p < len && in[p] != '\n')           /* rest of the WEBVTT line */
    p++;

  while(p < len) {
    /* collect one block: lines up to the next blank line */
    size_t bstart, bend;
    while(p < len && (in[p] == '\n' || in[p] == '\r'))
      p++;
    bstart = p;
    for(;;) {
      size_t ls = p, le;
      while(p < len && in[p] != '\n')
        p++;
      le = p;
      if(p < len)
        p++;
      while(le > ls && (in[le - 1] == '\r' || in[le - 1] == ' ' || in[le - 1] == '\t'))
        le--;
      if(le == ls || p >= len) {            /* blank line or end of data */
        bend = (le == ls) ? ls : le;
        break;
      }
    }
    if(bend <= bstart)
      continue;

    {
      const char *blk = in + bstart;
      size_t bl = bend - bstart, q = 0;
      int th = 0, tm = 0, ts = 0, tms = 0, eh = 0, em = 0, es = 0, ems = 0;
      int found = 0;
      size_t textstart = 0;

      if(!strncmp(blk, "NOTE", 4) || !strncmp(blk, "STYLE", 5) || !strncmp(blk, "REGION", 6))
        continue;

      /* the timing line is the first or second line of the block */
      while(q < bl && !found) {
        size_t le = q;
        const char *l = blk + q, *arrow;
        while(le < bl && blk[le] != '\n')
          le++;
        arrow = NULL;
        {
          size_t k;
          for(k = 0; k + 3 <= le - q; k++)
            if(!memcmp(l + k, "-->", 3)) { arrow = l + k; break; }
        }
        if(arrow != NULL) {
          const char *a = l, *b = arrow + 3;
          if(!ps3mio_vtt_ts(&a, &th, &tm, &ts, &tms) &&
             !ps3mio_vtt_ts(&b, &eh, &em, &es, &ems)) {
            found = 1;
            textstart = (le < bl) ? le + 1 : bl;
          }
        }
        if(!found) {
          if(q != 0)
            break;                            /* only one identifier line allowed */
          q = (le < bl) ? le + 1 : bl;
        }
      }
      if(!found)
        continue;

      idx++;
      o += sprintf(out + o, "%d\n%02d:%02d:%02d,%03d --> %02d:%02d:%02d,%03d\n",
                   idx, th, tm, ts, tms, eh, em, es, ems);
      {
        size_t tp = textstart;
        while(tp < bl) {
          size_t le = tp;
          while(le < bl && blk[le] != '\n')
            le++;
          {
            size_t e2 = le;
            while(e2 > tp && (blk[e2 - 1] == '\r' || blk[e2 - 1] == ' '))
              e2--;
            if(e2 > tp) {
              o += ps3mio_vtt_text(blk + tp, e2 - tp, out + o);
              out[o++] = '\n';
            }
          }
          tp = le + 1;
        }
      }
      out[o++] = '\n';
    }
  }
  out[o] = 0;
  return o;
}

static buf_t *
ps3mio_vtt_to_srt(buf_t *b)
{
  buf_t *c = buf_create(b->b_size * 2 + 64);
  size_t n;
  if(c == NULL)
    return NULL;
  n = ps3mio_vtt_convert(buf_cstr(b), b->b_size, (char *)c->b_ptr);
  if(n == 0) {
    buf_release(c);
    return NULL;
  }
  c->b_size = n;
  ((char *)c->b_ptr)[n] = 0;
  return c;
}

'''

VTT_HOOK = """  /* ps3mio: Stremio addons serve WebVTT; convert it so the SRT parser accepts it */
  if(b->b_size > 9 && (!memcmp(buf_cstr(b), "WEBVTT", 6) ||
                       !memcmp(buf_cstr(b), "\\xef\\xbb\\xbfWEBVTT", 9))) {
    buf_t *c = ps3mio_vtt_to_srt(b);
    if(c != NULL) {
      buf_release(b);
      b = c;
    }
  }

"""

s = rd("src/subtitles/ext_subtitles.c")
if s is not None and "ps3mio_vtt_convert" not in s:
    m1 = re.search(r'ext_subtitles_t \*\s*\nsubtitles_load\(media_pipe_t \*mp, const char \*url\)', s)
    m2 = re.search(r'  uint8_t header\[64\];\n  memcpy\(header, b->b_ptr, MIN\(b->b_size, 64\)\);', s)
    if m1 and m2:
        # insert hook first (later position), then the converter (earlier position)
        s = s[:m2.start()] + VTT_HOOK + s[m2.start():]
        start = s.rfind("/**", 0, m1.start())
        if start < 0 or s[start:m1.start()].count("*/") != 1:
            start = m1.start()
        s = s[:start] + VTT_CONVERTER + "\n" + s[start:]
        wr("src/subtitles/ext_subtitles.c", s)
        print("ok  ext_subtitles.c: WebVTT -> SRT")
    else:
        warn("ext_subtitles.c: markers not found (WebVTT subtitles will be rejected)")

# ---- 10. Android: MediaFormat.getInteger crashed when a key is missing (NullPointerException) ---
jf = os.path.join(root, "src/arch/android/android_video_codec.c")
if os.path.exists(jf):
    s = rd("src/arch/android/android_video_codec.c")
    if s is not None and "containsKey" not in s:
        m = re.search(r'static int\s*\ngetInteger\(JNIEnv \*env, jobject obj, const char \*name\)\s*\n\{.*?\n\}\n', s, re.S)
        if m:
            fix = """static int
getInteger(JNIEnv *env, jobject obj, const char *name)
{
  /* ps3mio: some devices omit keys such as "stride" -> MediaFormat.getInteger() threw a
   * NullPointerException that killed the whole app. Check the key first, default to 0. */
  jclass class = (*env)->GetObjectClass(env, obj);
  jstring key = (*env)->NewStringUTF(env, name);
  jmethodID has = (*env)->GetMethodID(env, class, "containsKey", "(Ljava/lang/String;)Z");
  jmethodID get = (*env)->GetMethodID(env, class, "getInteger", "(Ljava/lang/String;)I");
  int v = 0;
  if(has != NULL && get != NULL &&
     (*env)->CallBooleanMethod(env, obj, has, key))
    v = (*env)->CallIntMethod(env, obj, get, key);
  if((*env)->ExceptionCheck(env))
    (*env)->ExceptionClear(env);
  (*env)->DeleteLocalRef(env, key);
  (*env)->DeleteLocalRef(env, class);
  return v;
}
"""
            s = s[:m.start()] + fix + s[m.end():]
            wr("src/arch/android/android_video_codec.c", s)
            print("ok  android_video_codec.c: getInteger no longer crashes on missing keys")
        else:
            warn("android_video_codec.c: getInteger not found (video decoder may crash on some devices)")

# ---- 11. Arabic: joining forms + right-to-left ordering (text_render has neither) -------------
M7_BIDI = r'''
/* ---------------------------------------------------------------------
 * ps3mio: Arabic shaping (joining forms) + bidirectional reordering.
 * text_render() lays glyphs out strictly left to right and never joins
 * letters, so Arabic came out as separate letters in the wrong order.
 * Lines without Arabic letters are never touched.
 * ------------------------------------------------------------------- */
#define M7_ISCODE(c) ((c) > 0x10ffffu)

typedef struct { uint16_t base; uint16_t iso; uint8_t dual; } m7_ar_t;

static const m7_ar_t m7_ar[] = {
  {0x0621,0xFE80,0},
  {0x0622,0xFE81,1},{0x0623,0xFE83,1},{0x0624,0xFE85,1},{0x0625,0xFE87,1},
  {0x0626,0xFE89,2},{0x0627,0xFE8D,1},{0x0628,0xFE8F,2},{0x0629,0xFE93,1},
  {0x062A,0xFE95,2},{0x062B,0xFE99,2},{0x062C,0xFE9D,2},{0x062D,0xFEA1,2},
  {0x062E,0xFEA5,2},{0x062F,0xFEA9,1},{0x0630,0xFEAB,1},{0x0631,0xFEAD,1},
  {0x0632,0xFEAF,1},{0x0633,0xFEB1,2},{0x0634,0xFEB5,2},{0x0635,0xFEB9,2},
  {0x0636,0xFEBD,2},{0x0637,0xFEC1,2},{0x0638,0xFEC5,2},{0x0639,0xFEC9,2},
  {0x063A,0xFECD,2},{0x0641,0xFED1,2},{0x0642,0xFED5,2},{0x0643,0xFED9,2},
  {0x0644,0xFEDD,2},{0x0645,0xFEE1,2},{0x0646,0xFEE5,2},{0x0647,0xFEE9,2},
  {0x0648,0xFEED,1},{0x0649,0xFEEF,1},{0x064A,0xFEF1,2},
};
#define M7_AR_COUNT ((int)(sizeof(m7_ar) / sizeof(m7_ar[0])))

/* dual: 0 = not joining (hamza), 1 = joins to the previous letter only,
 * 2 = joins on both sides. tatweel (0640) is a pure connector. */
static const m7_ar_t *
m7_ar_find(uint32_t c)
{
  int i;
  if(c < 0x0621 || c > 0x064A)
    return NULL;
  for(i = 0; i < M7_AR_COUNT; i++)
    if(m7_ar[i].base == c)
      return &m7_ar[i];
  return NULL;
}

static int
m7_is_mark(uint32_t c)
{
  return (c >= 0x064B && c <= 0x065F) || c == 0x0670 ||
    (c >= 0x06D6 && c <= 0x06DC) || (c >= 0x06DF && c <= 0x06E4) ||
    (c >= 0x06E7 && c <= 0x06E8) || (c >= 0x06EA && c <= 0x06ED);
}

static int
m7_is_ar_char(uint32_t c)
{
  return (c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) ||
    (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF);
}

/* 1 = accepts a connection from the previous letter */
static int
m7_joins_prev(uint32_t c)
{
  const m7_ar_t *a;
  if(c == 0x0640)
    return 1;
  a = m7_ar_find(c);
  return a != NULL && a->dual >= 1;
}

static int
m7_is_alef(uint32_t c)
{
  return c == 0x0622 || c == 0x0623 || c == 0x0625 || c == 0x0627;
}

static int
m7_alef_lig(uint32_t c)  /* isolated lam-alef ligature (final = +1) */
{
  switch(c) {
  case 0x0622: return 0xFEF5;
  case 0x0623: return 0xFEF7;
  case 0x0625: return 0xFEF9;
  default:     return 0xFEFB;
  }
}

/* Replace Arabic letters by their joining forms. Returns the new length. */
static int
m7_shape(uint32_t *s, int n)
{
  int i, j, o = 0;
  int carry = 0;   /* the previous letter connects to the next one */

  for(i = 0; i < n; i++) {
    uint32_t c = s[i];
    const m7_ar_t *a = m7_ar_find(c);
    int next_join = 0;

    if(m7_is_mark(c))                        /* the font has no mark positioning: drop */
      continue;

    if(M7_ISCODE(c)) {                       /* transparent */
      s[o++] = c;
      continue;
    }

    if(a == NULL) {
      carry = (c == 0x0640);                 /* tatweel connects both ways */
      s[o++] = c;
      continue;
    }

    for(j = i + 1; j < n; j++) {
      if(M7_ISCODE(s[j]) || m7_is_mark(s[j]))
        continue;
      next_join = m7_joins_prev(s[j]);
      break;
    }

    /* lam + alef ligature */
    if(c == 0x0644) {
      for(j = i + 1; j < n; j++) {
        if(M7_ISCODE(s[j]) || m7_is_mark(s[j]))
          continue;
        break;
      }
      if(j < n && m7_is_alef(s[j])) {
        int k;
        s[o++] = m7_alef_lig(s[j]) + (carry ? 1 : 0);
        for(k = i + 1; k < j; k++)           /* style codes in between */
          if(M7_ISCODE(s[k]))
            s[o++] = s[k];
        i = j;
        carry = 0;
        continue;
      }
    }

    if(a->dual == 0) {
      s[o++] = a->iso;
      carry = 0;
    } else if(a->dual == 1) {
      s[o++] = a->iso + (carry ? 1 : 0);
      carry = 0;
    } else {
      if(carry && next_join)  s[o++] = a->iso + 3;
      else if(carry)          s[o++] = a->iso + 1;
      else if(next_join)      s[o++] = a->iso + 2;
      else                    s[o++] = a->iso;
      carry = next_join;
    }
  }
  return o;
}

enum { M7T_N = 0, M7T_L, M7T_R, M7T_EN };

static int
m7_type(uint32_t c)
{
  if(M7_ISCODE(c))
    return M7T_N;
  if(c >= '0' && c <= '9')
    return M7T_EN;
  if(c >= 0x0660 && c <= 0x0669)
    return M7T_EN;
  if(m7_is_ar_char(c) || (c >= 0x0590 && c <= 0x05FF))
    return m7_is_mark(c) ? M7T_N : M7T_R;
  if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
     (c >= 0x00C0 && c <= 0x024F && c != 0xD7 && c != 0xF7) ||
     (c >= 0x0370 && c <= 0x058F) || (c >= 0x1E00 && c <= 0x1FFF) ||
     (c >= 0x3040 && c <= 0xD7FF))
    return M7T_L;
  return M7T_N;
}

static uint32_t
m7_mirror(uint32_t c)
{
  switch(c) {
  case '(': return ')';  case ')': return '(';
  case '[': return ']';  case ']': return '[';
  case '{': return '}';  case '}': return '{';
  case '<': return '>';  case '>': return '<';
  case 0xAB: return 0xBB; case 0xBB: return 0xAB;
  default: return c;
  }
}

static int
m7_is_linebreak(uint32_t c)
{
  return c == '\n' || c == TR_CODE_NEWLINE || c == TR_CODE_START ||
    c == TR_CODE_HR || c == TR_CODE_CENTER_ON || c == TR_CODE_CENTER_OFF;
}

/* Reorder one logical line into visual (left to right) order. */
static void
m7_reorder(uint32_t *s, int n)
{
  uint8_t *type, *lvl;
  int base = -1, i, j, maxl = 0, minodd = 255;
  int last = M7T_N;   /* last strong type seen: L or R */

  if(n < 2)
    return;

  type = alloca(n);
  lvl  = alloca(n);

  for(i = 0; i < n; i++) {
    type[i] = m7_type(s[i]);
    if(base < 0 && (type[i] == M7T_L || type[i] == M7T_R))
      base = type[i] == M7T_R;
  }
  if(base < 0)
    base = 1;

  /* weak types: digits after Latin text in a left to right line are Latin */
  last = base ? M7T_R : M7T_L;
  for(i = 0; i < n; i++) {
    if(type[i] == M7T_L || type[i] == M7T_R)
      last = type[i];
    else if(type[i] == M7T_EN && !base && last == M7T_L)
      type[i] = M7T_L;
  }

  /* levels for strong / digit characters */
  for(i = 0; i < n; i++) {
    switch(type[i]) {
    case M7T_R:  lvl[i] = 1; break;
    case M7T_L:  lvl[i] = base ? 2 : 0; break;
    case M7T_EN: lvl[i] = 2; break;
    default:     lvl[i] = 255; break;
    }
  }

  /* neutrals take the direction of their neighbours, else the base one */
  for(i = 0; i < n; i++) {
    int left, right;
    if(lvl[i] != 255)
      continue;
    j = i;
    while(j < n && lvl[j] == 255)
      j++;
    left  = (i > 0) ? type[i - 1] : (base ? M7T_R : M7T_L);
    right = (j < n) ? type[j]     : (base ? M7T_R : M7T_L);
    if(left == M7T_EN)  left = M7T_R;
    if(right == M7T_EN) right = M7T_R;
    {
      uint8_t v;
      if(left == right && left == M7T_R)       v = 1;
      else if(left == right && left == M7T_L)  v = base ? 2 : 0;
      else                                     v = base ? 1 : 0;
      for(; i < j; i++)
        lvl[i] = v;
      i--;
    }
  }

  for(i = 0; i < n; i++) {
    if(lvl[i] > maxl) maxl = lvl[i];
    if((lvl[i] & 1) && lvl[i] < minodd) minodd = lvl[i];
    if(lvl[i] & 1) {
      s[i] = m7_mirror(s[i]);
      /* a reversed ON/OFF pair must keep its meaning */
      if(s[i] == TR_CODE_BOLD_ON)        s[i] = TR_CODE_BOLD_OFF;
      else if(s[i] == TR_CODE_BOLD_OFF)  s[i] = TR_CODE_BOLD_ON;
      else if(s[i] == TR_CODE_ITALIC_ON) s[i] = TR_CODE_ITALIC_OFF;
      else if(s[i] == TR_CODE_ITALIC_OFF)s[i] = TR_CODE_ITALIC_ON;
    }
  }
  if(minodd == 255)
    return;

  for(j = maxl; j >= minodd; j--) {
    for(i = 0; i < n; i++) {
      if(lvl[i] >= j) {
        int a = i, b;
        uint32_t t;
        uint8_t tl;
        while(i < n && lvl[i] >= j)
          i++;
        b = i - 1;
        while(a < b) {
          t = s[a]; s[a] = s[b]; s[b] = t;
          tl = lvl[a]; lvl[a] = lvl[b]; lvl[b] = tl;
          a++; b--;
        }
      }
    }
  }
}

/* Returns a new malloc()ed string when the text contains Arabic, else NULL */
static uint32_t *
m7_arabic_prepare(const uint32_t *uc, int len, int *outlen)
{
  int i, has = 0, s, e;
  uint32_t *buf;

  for(i = 0; i < len; i++) {
    if(m7_is_ar_char(uc[i])) {
      has = 1;
      break;
    }
  }
  if(!has)
    return NULL;

  buf = malloc(sizeof(uint32_t) * (len + 1));
  if(buf == NULL)
    return NULL;
  memcpy(buf, uc, sizeof(uint32_t) * len);

  s = 0;
  while(s < len) {
    int n;
    if(m7_is_linebreak(buf[s])) {
      s++;
      continue;
    }
    e = s;
    while(e < len && !m7_is_linebreak(buf[e]))
      e++;
    n = m7_shape(buf + s, e - s);
    /* close the gap left by ligatures */
    if(n < e - s) {
      memmove(buf + s + n, buf + e, sizeof(uint32_t) * (len - e));
      len -= (e - s) - n;
      e = s + n;
    }
    /* Style codes at the very start / end of a line (shadow, outline, colour ...) must stay
     * where they are, otherwise reversing the line moves them to the other end. */
    {
      int a = s, b = e;
      while(a < b && M7_ISCODE(buf[a]))
        a++;
      while(b > a && M7_ISCODE(buf[b - 1]))
        b--;
      m7_reorder(buf + a, b - a);
    }
    s = e;
  }
  *outlen = len;
  return buf;
}

'''

s = rd("src/text/freetype.c")
if s is not None and "m7_arabic_prepare" not in s:
    pat = re.compile(r'(/\*\*\n \*\n \*/\n)?struct image \*\ntext_render\(const uint32_t \*uc')
    m = pat.search(s)
    ok = False
    if m:
        s2 = s[:m.start()] + M7_BIDI + "\n" + s[m.start():]
        i = s2.index("struct image *\ntext_render(const uint32_t *uc")
        j = s2.index("im = text_render0(uc, len, flags,", i)
        k = s2.index("hts_mutex_unlock(&text_mutex);", j)
        h = s2.rindex("struct image *im;", i, j)
        if h > 0 and j > 0 and k > 0:
            s2 = (s2[:h] + "struct image *im;\n  uint32_t *m7buf = NULL;\n  int m7len = len;\n\n"
                  "  /* ps3mio: Arabic shaping + RTL order (lines without Arabic are untouched) */\n"
                  "  if(!(flags & TR_RENDER_CHARACTER_POS))\n"
                  "    m7buf = m7_arabic_prepare(uc, len, &m7len);\n"
                  + s2[h + len("struct image *im;"):j]
                  + "im = text_render0(m7buf ? m7buf : uc, m7len, flags,"
                  + s2[j + len("im = text_render0(uc, len, flags,"):k]
                  + "hts_mutex_unlock(&text_mutex);\n  free(m7buf);"
                  + s2[k + len("hts_mutex_unlock(&text_mutex);"):])
            wr("src/text/freetype.c", s2)
            print("ok  freetype.c: Arabic shaping + RTL ordering")
            ok = True
    if not ok:
        warn("freetype.c: text_render not found (Arabic text will not be joined / reordered)")

# ---- 12. fonts: bundled Arabic+English text font (also the fallback for glyphs Liberation lacks) ----
fonts_dir = None
if "--fonts" in sys.argv:
    fonts_dir = sys.argv[sys.argv.index("--fonts") + 1]
elif plugin_dir:
    fonts_dir = os.path.join(os.path.dirname(os.path.abspath(plugin_dir)), "fonts")
if fonts_dir and os.path.isdir(fonts_dir):
    import shutil
    dst = os.path.join(root, "res", "fonts", "ps3mio")
    os.makedirs(dst, exist_ok=True)
    for f in os.listdir(fonts_dir):
        if f.lower().endswith((".ttf", ".otf")):
            shutil.copy(os.path.join(fonts_dir, f), os.path.join(dst, f))
    mk = rd("Makefile")
    if mk is not None and "res/fonts/ps3mio" not in mk:
        m = re.search(r'^BUNDLES \+= \$\(sort \$\(BUNDLES-yes\)\)', mk, re.M)
        if m:
            mk = mk[:m.start()] + "BUNDLES += res/fonts/ps3mio\n" + mk[m.start():]
            wr("Makefile", mk)
            print("ok  Makefile: res/fonts/ps3mio bundled")
        else:
            warn("Makefile: BUNDLES marker not found (ps3mio fonts not bundled)")
    s = rd("src/text/freetype.c")
    if s is not None and "ps3mio/text.ttf" not in s:
        m = re.search(r'  freetype_load_default_font\(url, 0\);\n', s)
        if m:
            add = """
  /* ps3mio: Arabic + English text font. Loaded last with prio 0 so it is searched first;
   * glyphs it lacks still fall back to Liberation. */
  snprintf(url, sizeof(url), "%s/res/fonts/ps3mio/text.ttf", app_dataroot());
  freetype_load_default_font(url, 0);
"""
            s = s[:m.end()] + add + s[m.end():]
            wr("src/text/freetype.c", s)
            print("ok  freetype.c: ps3mio text font registered")
        else:
            warn("freetype.c: freetype_load_default_font marker not found (ps3mio font not used)")
elif plugin_dir:
    warn("fonts folder not found - fonts not bundled")

# ---- 13. buffering: pre-buffer 10 s before playing and after every stall -------------------
PREBUF = "10 * 1000000"   # microseconds of media that must be queued before playback (re)starts
s = rd("src/media/media.c")
if s is not None and "ps3mio" not in s:
    ok = True
    # a) enable pre-buffering for video
    a = "  mp->mp_max_realtime_delay = INT32_MAX;\n"
    if a in s:
        s = s.replace(a, a + """
  /* ps3mio: hold playback until 10 s of media is queued (start, seek and every
   * underrun) instead of starting on a few packets and stuttering. */
  if((mp->mp_flags & MP_VIDEO) || (type != NULL && !strcmp(type, "video"))) {
    flags |= MP_PRE_BUFFERING;   /* mp_set_clr_flags_locked() below applies it */
    mp->mp_pre_buffer_delay = """ + PREBUF + """;
  }
""", 1)
    else:
        ok = False
    # b) underrun: not at the very end of the stream, and say why we are paused
    m = re.search(r'void\nmp_underrun\(media_pipe_t \*mp\)\n\{\n  mp->mp_hold_flags \|= MP_HOLD_PRE_BUFFERING;\n  mp_set_playstatus_by_hold_locked\(mp, NULL\);\n\}', s)
    if m:
        s = s[:m.start()] + """void
mp_underrun(media_pipe_t *mp)
{
  media_buf_t *mb;

  /* ps3mio: an EXIT already queued = end of the stream, nothing more will arrive */
  TAILQ_FOREACH(mb, &mp->mp_video.mq_q_ctrl, mb_link)
    if(mb->mb_data_type == MB_CTRL_EXIT)
      return;
  TAILQ_FOREACH(mb, &mp->mp_audio.mq_q_ctrl, mb_link)
    if(mb->mb_data_type == MB_CTRL_EXIT)
      return;

  mp->mp_hold_flags |= MP_HOLD_PRE_BUFFERING;
  mp_set_playstatus_by_hold_locked(mp, "Buffering");
}""" + s[m.end():]
    else:
        ok = False
    if ok:
        wr("src/media/media.c", s)
        print("ok  media.c: pre-buffering enabled for video (10 s)")
    else:
        warn("media.c: mp_configure / mp_underrun markers not found (no pre-buffering)")

s = rd("src/media/media_queue.c")
if s is not None and "ps3mio" not in s:
    a = "    if(mp->mp_buffer_delay > mp->mp_pre_buffer_delay) {\n"
    if a in s:
        s = s.replace(a, """    /* ps3mio: also release when the buffer is nearly full (very high bitrates), otherwise
     * the demuxer waits for room, nothing plays and the wait never ends */
    if(mp->mp_buffer_delay > mp->mp_pre_buffer_delay ||
       mp->mp_buffer_current * 4 > mp->mp_buffer_limit * 3) {
""", 1)
        wr("src/media/media_queue.c", s)
        print("ok  media_queue.c: pre-buffer also released when the buffer is 75% full")
    else:
        warn("media_queue.c: pre-buffering marker not found")

s = rd("src/media/media_event.c")
if s is not None and "ps3mio" not in s:
    old = """    if(action_update_hold_by_event(mp->mp_hold_flags & MP_HOLD_PAUSE, e)) {
      mp->mp_hold_flags |= MP_HOLD_PAUSE;
    } else {
      mp->mp_hold_flags &= ~MP_HOLD_PAUSE;
    }
"""
    if old in s:
        s = s.replace(old, """    if(!(mp->mp_hold_flags & MP_HOLD_PAUSE) &&
       (mp->mp_hold_flags & MP_HOLD_PRE_BUFFERING) &&
       !event_is_action(e, ACTION_PAUSE)) {
      /* ps3mio: pressing play while buffering starts the movie now */
      mp->mp_hold_flags &= ~MP_HOLD_PRE_BUFFERING;
    } else if(action_update_hold_by_event(mp->mp_hold_flags & MP_HOLD_PAUSE, e)) {
      mp->mp_hold_flags |= MP_HOLD_PAUSE;
    } else {
      /* ps3mio: pause then play also skips the buffering wait */
      mp->mp_hold_flags &= ~(MP_HOLD_PAUSE | MP_HOLD_PRE_BUFFERING);
    }
""", 1)
        wr("src/media/media_event.c", s)
        print("ok  media_event.c: play/pause interrupts buffering")
    else:
        warn("media_event.c: play/pause marker not found (cannot interrupt buffering)")

# ---- 14. diagnostics for log.txt (rate limited): buffering state and A/V clock ---------------
s = rd("src/media/media.c")
if s is not None and "M7BUF-trace" not in s:
    old = """  mp->mp_hold_flags |= MP_HOLD_PRE_BUFFERING;
  mp_set_playstatus_by_hold_locked(mp, "Buffering");
}"""
    if old in s:
        s = s.replace(old, """  /* M7BUF-trace */
  TRACE(TRACE_DEBUG, "BUFFER", "Pre-buffering: waiting for data (queued %u bytes, %d ms)",
        mp->mp_buffer_current, (int)(mp->mp_buffer_delay / 1000));
  mp->mp_hold_flags |= MP_HOLD_PRE_BUFFERING;
  mp_set_playstatus_by_hold_locked(mp, "Buffering");
}""", 1)
        wr("src/media/media.c", s)
        print("ok  media.c: buffering trace")
    else:
        warn("media.c: mp_underrun body not found (no buffering trace)")

s = rd("src/media/media_queue.c")
if s is not None and "M7BUF-trace" not in s:
    if '#include "media.h"\n' in s and "mp->mp_buffer_current * 4 > mp->mp_buffer_limit * 3) {\n" in s:
        s = s.replace('#include "media.h"\n', '#include "media.h"\n#include "main.h"\n', 1)
        s = s.replace("mp->mp_buffer_current * 4 > mp->mp_buffer_limit * 3) {\n",
                      "mp->mp_buffer_current * 4 > mp->mp_buffer_limit * 3) {\n"
                      "      /* M7BUF-trace */\n"
                      "      TRACE(TRACE_DEBUG, \"BUFFER\", \"Pre-buffering done: %d ms queued, %u of %u bytes\",\n"
                      "            (int)(mp->mp_buffer_delay / 1000), mp->mp_buffer_current, mp->mp_buffer_limit);\n", 1)
        wr("src/media/media_queue.c", s)
        print("ok  media_queue.c: buffering-done trace")
    else:
        warn("media_queue.c: trace marker not found")

s = rd("src/arch/android/android_video_codec.c")
if s is not None and "M7SYNC" not in s:
    old = "    int64_t wt =  fi.fi_pts + rtd;\n    static int64_t ptsdelta;\n"
    if old in s:
        s = s.replace(old, old + """
    {
      /* M7SYNC: one line per 16 frames - pts step vs wall-clock step shows a fast-forward */
      static int m7n, m7skipped;
      static int64_t m7pts, m7now;
      if(skip || epoch != fi.fi_epoch || (wt - now) <= 10000LL)
        m7skipped++;
      if((++m7n & 15) == 1) {
        TRACE(TRACE_DEBUG, "M7SYNC",
              "n=%d dropped=%d pts=%lld dpts=%lld dnow=%lld lead=%lld rtd=%lld epoch=%d/%d",
              m7n, m7skipped, (long long)fi.fi_pts, (long long)(fi.fi_pts - m7pts),
              (long long)(now - m7now), (long long)(wt - now), (long long)rtd,
              epoch, fi.fi_epoch);
        m7pts = fi.fi_pts;
        m7now = now;
      }
    }
""", 1)
        wr("src/arch/android/android_video_codec.c", s)
        print("ok  android_video_codec.c: M7SYNC trace")
    else:
        warn("android_video_codec.c: schedule marker not found (no M7SYNC trace)")

s = rd("src/arch/android/android_audio.c")
if s is not None and "M7CLOCK" not in s:
    old = "  d->ad.ad_delay = audio_delay_samples * 1000000LL / d->ad.ad_out_sample_rate;\n"
    if old in s:
        s = s.replace(old, old + """  {
    /* M7CLOCK: audio position / delay every 20 callbacks */
    static int m7c;
    if((++m7c % 20) == 1)
      TRACE(TRACE_DEBUG, "M7CLOCK", "pos=%ums sent=%lld delay=%lldus rtd=%lld",
            (unsigned)ms, (long long)d->d_samples_sent, (long long)d->ad.ad_delay,
            (long long)mp->mp_realtime_delta);
  }
""", 1)
        wr("src/arch/android/android_audio.c", s)
        print("ok  android_audio.c: M7CLOCK trace")
    else:
        warn("android_audio.c: delay marker not found (no M7CLOCK trace)")

# ---- 15. UI font: the Arabic + English text font everywhere in the flat skin ----------------
FONT_URL = "dataroot://res/fonts/ps3mio/text.ttf"
flatdir = os.path.join(root, "glwskins", "flat")
if fonts_dir and os.path.isdir(fonts_dir) and os.path.isdir(flatdir):
    pat = re.compile(r'skin://fonts/RobotoCondensed-(Regular|Light|Bold)\.ttf')
    changed = 0
    for dp, dn, fn in os.walk(flatdir):
        for f in fn:
            if f.endswith(".view"):
                fp = os.path.join(dp, f)
                with open(fp, encoding="utf-8") as fh:
                    t = fh.read()
                t2 = pat.sub(FONT_URL, t)
                if t2 != t:
                    with open(fp, "w", encoding="utf-8") as fh:
                        fh.write(t2)
                    changed += 1
    print("ok  skin: text font set in %d view files" % changed)
else:
    warn("skin fonts: fonts folder or glwskins/flat missing (UI keeps its default font)")

# ---- 16. Android: do not let the decoder run ahead of the audio clock -------------------------
# SurfaceFlinger presents a frame immediately when its release time is more than ~1 s in the
# future. A decoder that got ahead (frames dropped while the audio clock starts) then ran away at
# 3-4x speed, because every later frame was "too far ahead" and was shown at once.
s = rd("src/arch/android/android_video_codec.c")
if s is not None and "M7LEAD" not in s:
    old = "    int64_t wt =  fi.fi_pts + rtd;\n    static int64_t ptsdelta;\n"
    if old in s:
        s = s.replace(old, old + """
    /* M7LEAD: hold a frame back until it is due within 0.6 s of now */
    if(epoch == fi.fi_epoch && !skip && (wt - now) > 600000LL) {
      int m7w = 0;
      while((wt - now) > 600000LL && m7w++ < 400 &&
            TAILQ_FIRST(&mp->mp_video.mq_q_ctrl) == NULL && mp->mp_hold_flags == 0) {
        usleep(10000);
        now = arch_get_avtime();
        hts_mutex_lock(&mp->mp_clock_mutex);
        rtd = mp->mp_realtime_delta + mp->mp_avdelta;
        epoch = mp->mp_audio_clock_epoch;
        hts_mutex_unlock(&mp->mp_clock_mutex);
        wt = fi.fi_pts + rtd;
        if(epoch != fi.fi_epoch)
          break;
      }
    }
""", 1)
        wr("src/arch/android/android_video_codec.c", s)
        print("ok  android_video_codec.c: frames held back when more than 0.6 s early (x2 speed fix)")
    else:
        warn("android_video_codec.c: schedule marker not found (x2 speed fix not applied)")

# ---- 17. Settings switch "Enable log" (plugin writes global.ps3mio.log, plugins.c listens) --------
s = rd("src/plugins.c")
if s is not None and "m7io_log_watch" not in s:
    old = "static void\nm7io_load_builtin(void)\n{\n  char errbuf[200];\n"
    if old in s:
        s = s.replace(old, """extern int m7io_log_on;

static void
m7io_log_cb(void *opaque, int v)
{
  m7io_log_on = v;
}

static void
m7io_log_watch(void)
{
  static int done;
  if(done)
    return;
  done = 1;
  prop_subscribe(0,
                 PROP_TAG_NAME("global", "ps3mio", "log"),
                 PROP_TAG_CALLBACK_INT, m7io_log_cb, NULL,
                 NULL);
}

static void
m7io_load_builtin(void)
{
  char errbuf[200];
  m7io_log_watch();
""", 1)
        wr("src/plugins.c", s)
        print("ok  plugins.c: Enable log switch (global.ps3mio.log)")
    else:
        warn("plugins.c: m7io_load_builtin marker not found (Enable log switch inactive, log stays on)")

# ---- 18. subtitle options inside the player: Offset, Size, Color (white / yellow), Outline, Shadow ----
s = rd("src/media/media_settings.c")
if s is not None and "m7io_sub_color" not in s:
    ok = True
    s = s.replace('#include "misc/minmax.h"\n', '#include "misc/minmax.h"\n\nint m7io_sub_color;   /* 0 = white, 1 = yellow (subtitle colour chosen in the player) */\n', 1)
    old_delay = 'SETTING_TITLE(_p("Delay")),\n                 SETTING_RANGE(-600000, 600000),\n                 SETTING_STEP(500),\n                 SETTING_UNIT_CSTR("ms"),\n                 SETTING_CALLBACK(update_sv_delta, mp),'
    if old_delay in s:
        s = s.replace(old_delay, old_delay.replace('_p("Delay")', '_p("Offset")'), 1)
    else:
        ok = False
    old_scale = 'SETTING_TITLE(_p("Scaling")),\n                 SETTING_RANGE(30, 500),'
    if old_scale in s:
        s = s.replace(old_scale, old_scale.replace('_p("Scaling")', '_p("Size")'), 1)
    else:
        ok = False
    marker = '  p = make_dir_setting(SETTING_BOOL, "subalign", &mp->mp_settings_subtitle_dir,'
    if marker in s:
        add = """  /* ps3mio: colour / outline / shadow. The outline and shadow colour stay black. */
  setting_create(SETTING_MULTIOPT, mp->mp_setting_subtitle_root,
                 SETTINGS_INITIAL_UPDATE,
                 SETTING_TITLE(_p("Color")),
                 SETTING_STORE("ps3mio", "subcolor"),
                 SETTING_WRITE_INT(&m7io_sub_color),
                 SETTING_OPTION("0", _p("White")),
                 SETTING_OPTION("1", _p("Yellow")),
                 SETTING_GROUP(&mp->mp_settings_subtitle),
                 NULL);

  setting_create(SETTING_INT, mp->mp_setting_subtitle_root,
                 SETTINGS_INITIAL_UPDATE,
                 SETTING_TITLE(_p("Outline")),
                 SETTING_VALUE(1),
                 SETTING_RANGE(0, 4),
                 SETTING_STEP(1),
                 SETTING_UNIT_CSTR("px"),
                 SETTING_STORE("subtitles", "shadowoutlinesize"),
                 SETTING_WRITE_INT(&subtitle_settings.outline_size),
                 SETTING_GROUP(&mp->mp_settings_subtitle),
                 NULL);

  setting_create(SETTING_INT, mp->mp_setting_subtitle_root,
                 SETTINGS_INITIAL_UPDATE,
                 SETTING_TITLE(_p("Shadow")),
                 SETTING_VALUE(2),
                 SETTING_RANGE(0, 10),
                 SETTING_STEP(1),
                 SETTING_UNIT_CSTR("px"),
                 SETTING_STORE("subtitles", "shadowcolorsize"),
                 SETTING_WRITE_INT(&subtitle_settings.shadow_displacement),
                 SETTING_GROUP(&mp->mp_settings_subtitle),
                 NULL);

"""
        s = s.replace(marker, add + marker, 1)
    else:
        ok = False
    if ok:
        wr("src/media/media_settings.c", s)
        print("ok  media_settings.c: player subtitle options (Offset, Size, Color, Outline, Shadow)")
    else:
        warn("media_settings.c: subtitle settings markers not found (subtitle options unchanged)")

s = rd("src/subtitles/video_overlay.c")
if s is not None and "m7io_sub_color" not in s:
    old = "    uint32_t pfx[6];\n\n    pfx[0] = TR_CODE_COLOR | subtitle_settings.color;\n"
    if old in s:
        s = s.replace(old, "    uint32_t pfx[6];\n    extern int m7io_sub_color;\n\n    /* ps3mio: yellow chosen in the player (TR_CODE_COLOR is BGR) */\n    pfx[0] = TR_CODE_COLOR | (m7io_sub_color ? 0x00FFFF : subtitle_settings.color);\n", 1)
        wr("src/subtitles/video_overlay.c", s)
        print("ok  video_overlay.c: yellow subtitle colour")
    else:
        warn("video_overlay.c: colour marker not found (colour choice has no effect)")

print("done, %d warning(s)" % len(problems))
