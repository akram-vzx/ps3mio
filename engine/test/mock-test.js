// Runs plugin/stremio.js against a fake Movian runtime + canned Stremio addon data.
var fs = require('fs'), path = require('path'), vm = require('vm');
var code = fs.readFileSync(path.join(__dirname, '../plugin/stremio.js'), 'utf8');

var routes = [], searchers = [], notes = [], fetched = [];
function deep() {
  var store = {};
  return new Proxy(store, {
    get: function (t, k) {
      if (k === '__raw') return t;
      if (typeof k === 'symbol') return undefined;
      if (!(k in t)) t[k] = deep();
      return t[k];
    },
    set: function (t, k, v) { t[k] = v; return true; }
  });
}
function raw(x) { return x && x.__raw ? x.__raw : x; }
function Page(url) {
  this.url = url; this.items = []; this.metadata = deep(); this.model = deep(); this.errorMsg = null;
  this.type = null; this.loading = false;
}
Page.prototype.appendItem = function (u, t, md) { this.items.push({ url: u, type: t, md: md || {} }); return {}; };
Page.prototype.appendPassiveItem = function (t, d, md) { this.items.push({ passive: t, md: md || {} }); };
Page.prototype.appendAction = function (title, fn) { this.items.push({ action: title, fn: fn }); };
Page.prototype.error = function (m) { this.errorMsg = m; };
Page.prototype.haveMore = function (v) { this.more = v; };
Page.prototype.redirect = function (u) { this.redirected = u; };
function rowsOf(pg) {
  var r = raw(pg.model.rows), out = [];
  Object.keys(r).forEach(function (k) {
    var row = raw(r[k]); var items = raw(row.items); var list = [];
    Object.keys(items).forEach(function (i) { var it = raw(items[i]); list.push({ url: it.url, title: raw(it.metadata).title, icon: raw(it.metadata).icon }); });
    out.push({ title: row.title, items: list });
  });
  return out;
}
function filtersOf(pg) {
  var f = raw(pg.model.filters), out = [];
  Object.keys(f).forEach(function (k) { var x = raw(f[k]); out.push({ title: x.title, url: x.url }); });
  return out;
}

var CINE = { id: 'com.linvo.cinemeta', name: 'Cinemeta', version: '3.0.0', types: ['movie', 'series'],
  idPrefixes: ['tt'], resources: ['catalog', 'meta', { name: 'stream', types: ['movie'], idPrefixes: ['zz'] }],
  catalogs: [
    { type: 'movie', id: 'top', name: 'Popular', extra: [{ name: 'genre', options: ['Action', 'Drama'] }, { name: 'skip' }] },
    { type: 'series', id: 'top', name: 'Popular', extra: [{ name: 'search', isRequired: true }] },
    { type: 'movie', id: 'top', name: 'Search', extra: [{ name: 'search' }] }
  ] };
var TORR = { id: 'torr', name: 'Torrentio', version: '1', types: ['movie', 'series'], idPrefixes: ['tt'],
  resources: ['stream'], catalogs: [] };
var SUBS = { id: 'subs', name: 'OpenSubs', version: '1', types: ['movie', 'series'], idPrefixes: ['tt'],
  resources: ['subtitles'], catalogs: [] };
var ADDONS = [
  { transportUrl: 'https://v3-cinemeta.strem.io/manifest.json', manifest: CINE },
  { transportUrl: 'https://torrentio.example/cfg/manifest.json', manifest: TORR },
  { transportUrl: 'https://subs.example/manifest.json', manifest: SUBS }
];
var db = {
  'https://v3-cinemeta.strem.io/manifest.json': CINE,
  'https://api.strem.io/api/addonCollectionGet': { result: { addons: ADDONS } },
  'https://api.strem.io/api/datastoreGet': { result: [
    { _id: 'tt1', type: 'movie', name: 'Heat 📀', poster: 'https://p/1.jpg', removed: false, _mtime: '2026-01-02', state: { timeOffset: 600, duration: 6000 } },
    { _id: 'tt2', type: 'series', name: 'Show', removed: false, _mtime: '2026-01-01', state: { timeOffset: 0 } },
    { _id: 'tt3', type: 'movie', name: 'Gone', removed: true, _mtime: '2026-01-03', state: {} } ] },
  'https://link.stremio.com/api/v2/create?type=Create': { result: { code: 'ABCD', link: 'https://link.stremio.com', qrcode: 'x' } },
  'https://link.stremio.com/api/v2/read?type=Read&code=ABCD': { result: { authKey: 'KEY123' } },
  'https://v3-cinemeta.strem.io/catalog/movie/top.json': { metas: [{ id: 'tt1', type: 'movie', name: 'Heat', releaseInfo: '1995', poster: 'https://p/1.jpg', imdbRating: '8.3', genres: ['Crime'] }] },
  'https://v3-cinemeta.strem.io/catalog/movie/top/genre=Action.json': { metas: [{ id: 'tt9', type: 'movie', name: 'Act' }] },
  'https://v3-cinemeta.strem.io/catalog/movie/top/search=heat.json': { metas: [{ id: 'tt1', type: 'movie', name: 'Heat' }] },
  'https://v3-cinemeta.strem.io/meta/movie/tt1.json': { meta: { id: 'tt1', type: 'movie', name: 'Heat', description: 'Cops & robbers', runtime: '170 min', cast: ['A', 'B'], logo: 'https://img/logo.png', background: 'https://img/bg.jpg', poster: 'https://img/p.jpg' } },
  'https://v3-cinemeta.strem.io/meta/series/tt2.json': { meta: { id: 'tt2', type: 'series', name: 'Show', videos: [
    { id: 'tt2:1:1', season: 1, episode: 1, title: 'Pilot', released: '2020-01-01T00:00:00Z' },
    { id: 'tt2:1:2', season: 1, episode: 2, title: 'Two' }, { id: 'tt2:0:1', season: 0, episode: 1, title: 'Special' } ] } },
  'https://torrentio.example/cfg/stream/movie/tt1.json': { streams: [
    { name: 'Torrentio\n4k', title: 'Heat.2160p.mkv\n\uD83D\uDC64 55 \uD83D\uDCBE 12 GB \u2699\uFE0F ThePirateBay', infoHash: 'abcdef0123456789abcdef0123456789abcdef01', fileIdx: 2, sources: ['tracker:udp://t1:80', 'dht:abc'] },
    { name: 'DirectHTTP', title: 'file', url: 'https://cdn.example/heat.mp4', subtitles: [{ lang: 'eng', url: 'https://s/x.srt' }, { lang: 'ara', url: 'https://s/a.srt' }] },
    { name: 'YT', ytId: 'abc' } ] },
  'https://torrentio.example/cfg/stream/series/tt2:1:1.json': { streams: [{ name: 'T', url: 'https://cdn/e1.mkv' }, { name: 'Pack\n1080p', title: 'Show.Season.1.Pack\n\uD83D\uDC64 9 \uD83D\uDCBE 30 GB', infoHash: '1111111111111111111111111111111111111111' }] },
  'https://subs.example/subtitles/movie/tt1.json': { subtitles: [{ id: 1, lang: 'ara', url: 'https://s/a.srt' }, { id: 2, lang: 'eng', url: 'https://s/e.srt' }] },
  'https://subs.example/subtitles/series/tt2:1:1.json': { subtitles: [] }
};

var store = {};
var sandbox = {
  console: console, JSON: JSON, Date: Date, parseInt: parseInt, parseFloat: parseFloat, isNaN: isNaN,
  encodeURIComponent: encodeURIComponent, decodeURIComponent: decodeURIComponent, String: String,
  Math: Math, setTimeout: function (fn) { sandbox._timers.push(fn); }, _timers: [],
  Plugin: { path: '/p/' },
  require: function (n) {
    switch (n) {
      case 'movian/page': return { Route: function (re, cb) { routes.push({ re: new RegExp(re), cb: cb }); },
        Searcher: function (t, i, cb) { searchers.push(cb); } };
      case 'movian/http': return { request: function (url) {
        fetched.push(url);
        if (!(url in db)) return { toString: function () { return ''; }, statuscode: 404 };
        return { toString: function () { return JSON.stringify(db[url]); }, statuscode: 200 };
      } };
      case 'movian/settings': return { globalSettings: function () {
        var self = this;
        ['createMultiOpt', 'createBool', 'createInt', 'createDivider', 'createString', 'createAction'].forEach(function (m) {
          self[m] = function () { var cb = arguments[arguments.length - 1]; var args = [].slice.call(arguments);
            if (m === 'createString') cb(args[2]); if (m === 'createBool') cb(args[2]); if (m === 'createInt') cb(args[2]); };
        });
      } };
      case 'movian/store': return { create: function () { return store; } };
      case 'movian/service': return { create: function () {} };
      case 'native/popup': return { notify: function (t) { notes.push(t); }, textDialog: function () { return { input: 'heat' }; } };
      case 'movian/videoscrobbler': return { VideoScrobbler: function () {} };
    }
    throw new Error('unexpected require ' + n);
  }
};
vm.createContext(sandbox);
vm.runInContext(code, sandbox);

function open(url) {
  for (var i = 0; i < routes.length; i++) {
    var m = routes[i].re.exec(url);
    if (m) { var pg = new Page(url); routes[i].cb.apply(null, [pg].concat(m.slice(1))); return pg; }
  }
  throw new Error('no route for ' + url);
}
function assert(c, msg) { if (!c) { console.error('FAIL:', msg); process.exitCode = 1; } else console.log('ok  -', msg); }
function titles(pg) { return pg.items.map(function (i) { return (i.md && i.md.title) || i.action || ''; }); }

// --- not logged in: default addon, board rows
var home = open('stremio:start');
assert(home.type === 'sboard' && home.metadata.nav === 'board', 'start is a board page');
var rows = rowsOf(home);
assert(rows.length === 2 && /Popular - Movies/.test(rows[0].title), 'board has catalog rows (search-required catalog hidden)');
assert(rows[0].items[0].url === 'stremio:meta:movie:tt1' && rows[0].items[0].title === 'Heat (1995)', 'row item url/title');
assert(filtersOf(home).length === 1 && /phone/i.test(filtersOf(home)[0].title), 'login button when logged out');

// --- login via link code
var login = open('stremio:login');
assert(login.items.some(function (i) { return i.md && i.md.title === 'ABCD'; }), 'link code displayed');
sandbox._timers.shift()();
assert(store.authKey === 'KEY123', 'authKey stored after approval');
assert(login.redirected === 'stremio:start', 'redirects to board after login');
assert(JSON.parse(store.addons).length === 3, 'addons synced (3)');

// --- board when logged in: continue watching row first
var home2 = open('stremio:start');
var rows2 = rowsOf(home2);
assert(/Continue Watching/.test(rows2[0].title) && rows2[0].items.length === 1, 'continue watching row');
assert(filtersOf(home2).length === 0, 'no login button when logged in');

// --- discover: filters cycle, grid items, paging
var ds = open('stremio:discover:0:0:0');
assert(ds.type === 'sgrid' && ds.metadata.nav === 'discover', 'discover page type');
var f = filtersOf(ds);
assert(f.length === 3 && f[0].url === 'stremio:discover:0:0:0' && /Movies/.test(f[0].title), 'type filter (single type wraps)');
assert(/genre/i.test(f[2].title) && f[2].url === 'stremio:discover:0:0:1', 'genre filter cycles');
assert(ds.items.filter(function (i) { return /stremio:meta:movie/.test(i.url || ''); }).length === 1, 'discover posters');
assert(ds.more === true, 'paginator continues (skip supported)');
var dg = open('stremio:discover:0:0:1');
assert(dg.items.some(function (i) { return /tt9/.test(i.url || ''); }), 'genre results fetched');
assert(open('stremio:discover').redirected === 'stremio:discover:0:0:0', 'discover redirect');

// --- search
var sb = open('stremio:searchbox');
assert(sb.type === 'slist' && sb.items[0].url === 'stremio:dosearch', 'search box page');
var ds2 = open('stremio:dosearch');
assert(ds2.redirected === 'stremio:q:heat', 'search dialog -> results');
var sr = open('stremio:q:heat');
assert(sr.type === 'sboard' && rowsOf(sr).length === 1, 'search results as board rows');
var sb2 = open('stremio:searchbox');
assert(sb2.items.some(function (i) { return i.url === 'stremio:q:heat'; }), 'recent search listed');

// --- detail
var mv = open('stremio:meta:movie:tt1');
assert(mv.type === 'sdetail' && mv.metadata.title === 'Heat' && /Cops/.test(mv.metadata.description), 'movie detail fields');
assert(mv.items.some(function (i) { return /stremio:streams:movie:tt1:Heat/.test(i.url || ''); }), 'movie -> streams');
var se = open('stremio:meta:series:tt2');
var seasonItems = se.items.filter(function (i) { return /stremio:season/.test(i.url || ''); });
assert(seasonItems.length === 2 && /Special/.test(seasonItems[0].md.title), 'seasons (special first)');
var ep = open('stremio:season:series:tt2:1');
assert(ep.items.filter(function (i) { return /stremio:streams:series:tt2%3A1%3A1:/.test(i.url || ''); }).length === 1, 'episode entries use video id');

// --- streams (direct-to-player + logo/backdrop/settings for the player)
function codecCheck(str) {
  var r = [['x265', /(x\.?265|h\.?265|hevc)/i], ['x264', /(x\.?264|h\.?264|avc)/i]];
  for (var i = 0; i < r.length; i++) if (r[i][1].test(str)) return r[i][0];
  return '';
}
var st1 = open('stremio:streams:movie:tt1:Heat');
var vids = st1.items.filter(function (i) { return /^videoparams:/.test(i.url || ''); });
assert(vids.length === 2 && vids[0].type === 'video', 'torrent + direct streams are video items');
var vpT = JSON.parse(vids[0].url.substr(12));
assert(/^torrent:video:magnet:\?xt=urn:btih:abcdef/.test(vpT.sources[0].url) && /m7idx=2/.test(vpT.sources[0].url), 'torrent source with fileIdx');
assert(vids[0].md.title === '[4k] Heat.2160p.mkv', 'row title is quality + file name only');
assert(vids[0].md.description === 'Seeds 55   |   12 GB', 'line under the title: seeders | size (no codec in this name)');
assert(codecCheck('Movie.2020.1080p.BluRay.x265-GRP.mkv') === 'x265' && codecCheck('Movie.H.264.mp4') === 'x264' && codecCheck('Movie.HEVC.mkv') === 'x265' && codecCheck('plain.mkv') === '', 'codec detection');
assert(vids[0].md.title.indexOf('Seeds') < 0, 'title no longer repeats the seeders line');
var subUrls = JSON.parse(vids[1].url.substr(12)).subtitles.map(function (x) { return x.url; });
assert(subUrls.indexOf('https://s/a.srt') >= 0 && subUrls.length === subUrls.filter(function (u, i) { return subUrls.indexOf(u) === i; }).length, 'subtitle URLs are unique');
var setPg = open('stremio:settings');
assert(!setPg.items.some(function (i) { return i.md && /Language/.test(i.md.title || ''); }), 'no interface language option');
assert(setPg.items.some(function (i) { return i.passive && i.md.title === 'Buttons info'; }), 'buttons info section in settings');
assert(vpT.subtitles.length === 2, 'subtitles passed to the player');
assert(vpT.logo === 'https://img/logo.png' && vpT.backdrop === 'https://img/bg.jpg', 'logo + backdrop passed for the loading screen');
assert(vpT.ps3mio.seekBack === 15 && vpT.ps3mio.seekFwd === 30 && vpT.ps3mio.resume === 1, 'player settings passed');
var st2 = open('stremio:streams:series:tt2%3A1%3A1:Show%20S1E1');
var vv = st2.items.filter(function (i) { return /^videoparams/.test(i.url || ''); });
var vp2 = JSON.parse(vv[0].url.substr(12));
assert(vp2.season === 1 && vp2.episode === 1, 'season/episode passed');
assert(/m7se=1:1/.test(JSON.parse(vv[1].url.substr(12)).sources[0].url), 'pack without fileIdx gets season:episode hint');

// --- library / addons / settings
var lib = open('stremio:library');
assert(lib.type === 'sgrid' && lib.items.length === 2, 'library grid hides removed items');
var ad = open('stremio:addons');
assert(ad.metadata.nav === 'addons' && ad.items.some(function (i) { return i.url === 'stremio:refresh'; }), 'addons page has sync');
var set = open('stremio:settings');
assert(set.metadata.nav === 'settings' && set.items.some(function (i) { return /Seek back step:\s+15 s/.test((i.md || {}).title || ''); }), 'settings page lists Stremio settings');
var c1 = open('stremio:cfg:seekBack');
assert(c1.redirected === 'stremio:settings' && store.cfg_seekBack === '20', 'setting cycles (15 -> 20)');
var set2 = open('stremio:settings');
assert(set2.items.some(function (i) { return /Seek back step:\s+20 s/.test((i.md || {}).title || ''); }), 'new value shown');
var c2 = open('stremio:cfg:relay');
assert(c2.redirected === 'stremio:settings' && store.cfg_relay === 'heat', 'text setting via dialog');
store.cfg_relay = '';
var rf = open('stremio:refresh');
assert(rf.redirected === 'stremio:start', 'refresh returns to board');
var lo = open('stremio:logout');
assert(lo.redirected === 'stremio:start' && store.authKey === '', 'logout clears account');
console.log(process.exitCode ? '\nSOME TESTS FAILED' : '\nALL TESTS PASSED');
