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

# ---- 2. navigator.c : start page ----------------------------------------
s = rd("src/navigator.c")
if s is not None and "stremio:start" not in s:
    n1 = len(re.findall(r'gconf\.initial_url\s*!=\s*NULL\)\s*\{', s))
    n2 = len(re.findall(r'\.url\s*=\s*gconf\.initial_url,', s))
    if n1 == 1 and n2 == 1:
        s = re.sub(r'gconf\.initial_url\s*!=\s*NULL\)\s*\{', '1) {', s)
        s = re.sub(r'\.url(\s*)=\s*gconf\.initial_url,', r'.url\1= gconf.initial_url ?: "stremio:start",', s)
        wr("src/navigator.c", s)
        print("ok  navigator.c start page = stremio:start")
    else:
        warn("navigator.c: initial_url pattern not found (app will start on the Movian home page)")
elif s is not None:
    print("ok  navigator.c already patched")

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

static torrent_file_t *
m7io_pick_file(torrent_t *to, const char *url)
{
  torrent_file_t *tf, *best = NULL;
  const char *p;

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
  return find_movie_torrent(to);
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

print("done, %d warning(s)" % len(problems))
