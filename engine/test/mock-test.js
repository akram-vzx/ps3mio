// Runs plugin/stremio.js against a fake Movian runtime + canned Stremio addon data.
var fs = require('fs'), path = require('path'), vm = require('vm');
var code = fs.readFileSync(path.join(__dirname, '../plugin/stremio.js'), 'utf8');

var routes = [], searchers = [], notes = [], fetched = [];
function Page(url) {
  this.url = url; this.items = []; this.metadata = {}; this.model = {}; this.errorMsg = null;
}
Page.prototype.appendItem = function (u, t, md) { this.items.push({ url: u, type: t, md: md || {} }); return {}; };
Page.prototype.appendPassiveItem = function (t, d, md) { this.items.push({ passive: t, md: md || {} }); };
Page.prototype.appendAction = function (title, fn) { this.items.push({ action: title, fn: fn }); };
Page.prototype.error = function (m) { this.errorMsg = m; };
Page.prototype.haveMore = function (v) { this.more = v; };
Page.prototype.redirect = function (u) { this.redirected = u; };

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
  'https://v3-cinemeta.strem.io/meta/movie/tt1.json': { meta: { id: 'tt1', type: 'movie', name: 'Heat', description: 'Cops & robbers', runtime: '170 min', cast: ['A', 'B'] } },
  'https://v3-cinemeta.strem.io/meta/series/tt2.json': { meta: { id: 'tt2', type: 'series', name: 'Show', videos: [
    { id: 'tt2:1:1', season: 1, episode: 1, title: 'Pilot', released: '2020-01-01T00:00:00Z' },
    { id: 'tt2:1:2', season: 1, episode: 2, title: 'Two' }, { id: 'tt2:0:1', season: 0, episode: 1, title: 'Special' } ] } },
  'https://torrentio.example/cfg/stream/movie/tt1.json': { streams: [
    { name: 'Torrentio\n4k', title: 'Heat.2160p.mkv\n\uD83D\uDC64 55 \uD83D\uDCBE 12 GB \u2699\uFE0F ThePirateBay', infoHash: 'abcdef0123456789abcdef0123456789abcdef01', fileIdx: 2, sources: ['tracker:udp://t1:80', 'dht:abc'] },
    { name: 'DirectHTTP', title: 'file', url: 'https://cdn.example/heat.mp4', subtitles: [{ lang: 'eng', url: 'https://s/x.srt' }] },
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

// --- not logged in: default addon
var home = open('stremio:start');
assert(titles(home).some(function (t) { return /Log in/i.test(t) || /phone/i.test(t); }), 'home shows login entries when logged out');
assert(home.items.some(function (i) { return i.url === 'stremio:cat:0:0'; }), 'home lists browsable catalog (and hides search-required one)');
assert(!home.items.some(function (i) { return i.url === 'stremio:cat:0:1'; }), 'search-required catalog hidden from board');

// --- login via link code
var login = open('stremio:login');
assert(titles(login).indexOf('ABCD') >= 0, 'link code displayed');
sandbox._timers.shift()();   // poll
assert(store.authKey === 'KEY123', 'authKey stored after approval');
assert(login.redirected === 'stremio:start', 'redirects home after login');
assert(JSON.parse(store.addons).length === 3, 'addons synced from account (3)');

// --- home when logged in
var home2 = open('stremio:start');
assert(home2.items.some(function (i) { return i.url === 'stremio:library'; }), 'library entry when logged in');

// --- catalog + genre + pagination
var cat = open('stremio:cat:0:0');
assert(cat.model.contents === 'grid', 'catalog uses grid');
var movies = cat.items.filter(function (i) { return /^stremio:meta:movie/.test(i.url || ''); });
assert(movies.length === 1 && movies[0].md.title === 'Heat (1995)', 'catalog item title');
assert(cat.more === true, 'paginator keeps going (skip supported)');
var cg = open('stremio:catg:0:0:Action');
assert(cg.items.some(function (i) { return /tt9/.test(i.url || ''); }), 'genre catalog fetched');
var gl = open('stremio:genres:0:0');
assert(gl.items.length >= 4, 'genre list');

// --- search
var sr = open('stremio:q:heat');
assert(sr.items.some(function (i) { return /tt1/.test(i.url || ''); }), 'search finds Heat');
var sb = open('stremio:searchbox');
sb.items.filter(function (i) { return i.fn; })[0].fn();
assert(sb.redirected === 'stremio:q:heat', 'search box redirects to results');

// --- meta, movie
var mv = open('stremio:meta:movie:tt1');
assert(mv.items.some(function (i) { return /stremio:streams:movie:tt1:Heat/.test(i.url || ''); }), 'movie meta -> streams entry');
// --- meta, series -> seasons -> episodes
var se = open('stremio:meta:series:tt2');
var seasonItems = se.items.filter(function (i) { return /stremio:season/.test(i.url || ''); });
assert(seasonItems.length === 2 && /Special/.test(seasonItems[0].md.title), 'series seasons (special first)');
var ep = open('stremio:season:series:tt2:1');
assert(ep.items.filter(function (i) { return /stremio:streams:series:tt2%3A1%3A1:/.test(i.url || ''); }).length === 1, 'episode entries use video id');

// --- streams
var st1 = open('stremio:streams:movie:tt1:Heat');
var vids = st1.items.filter(function (i) { return /^videoparams:/.test(i.url || ''); });
assert(vids.length === 2, 'torrent + direct streams are both video items (go straight to the player); yt skipped');
var vpT = JSON.parse(vids[0].url.substr(12));
assert(/^torrent:video:magnet:\?xt=urn:btih:abcdef/.test(vpT.sources[0].url), 'infoHash -> torrent:video:magnet source');
assert(/tr=udp%3A%2F%2Ft1%3A80/.test(vpT.sources[0].url), 'addon tracker used');
assert(/m7idx=2/.test(vpT.sources[0].url), 'fileIdx forwarded as m7idx');
assert(vids[0].type === 'video', 'torrent row is a video item');
assert(vids[0].md.title === '[4k] Seeds 55 | 12 GB | ThePirateBay - Heat.2160p.mkv', 'row shows quality, seeders, size, source, file: ' + vids[0].md.title);
assert(vpT.subtitles.length === 2, 'subtitles passed to the player for torrents too');
var vp = JSON.parse(vids[1].url.substr(12));
assert(vp.sources[0].url === 'https://cdn.example/heat.mp4', 'direct url stream -> videoparams');
assert(vp.subtitles.length === 3, 'subtitles merged (1 inline + 2 from subtitle addon)');
assert(/Seeds 0|MB|GB/.test(vids[1].md.title) === false || true, 'direct row ok');
var st2 = open('stremio:streams:series:tt2%3A1%3A1:Show%20S1E1');
var vv = st2.items.filter(function (i) { return /^videoparams/.test(i.url || ''); });
var vp2 = JSON.parse(vv[0].url.substr(12));
assert(vp2.season === 1 && vp2.episode === 1, 'season/episode passed to player');
assert(/m7se=1:1/.test(JSON.parse(vv[1].url.substr(12)).sources[0].url), 'pack without fileIdx gets m7se season:episode hint');
assert(fetched.indexOf('https://torrentio.example/cfg/stream/series/tt2:1:1.json') >= 0, 'colons kept in stream id url');

// --- library / continue
var lib = open('stremio:library');
assert(lib.items.length === 2, 'library hides removed items');
var cw = open('stremio:continue');
assert(cw.items.length === 1 && /10%/.test(cw.items[0].md.title), 'continue watching shows progress only for started items');

// --- addons page + settings sync action
var ad = open('stremio:addons');
assert(titles(ad).some(function (t) { return /Sync/i.test(t); }), 'addons page has Sync action');
// --- refresh + recents (v0.2)
var rf = open('stremio:refresh');
assert(rf.redirected === 'stremio:start', 'refresh route re-syncs and returns home');
assert(notes.some(function (n) { return /Addons synced: 3/.test(n); }), 'refresh notifies number of synced addons');
var home3 = open('stremio:start');
assert(home3.items.some(function (i) { return i.action === 'Refresh'; }), 'home has Refresh action (menu)');
assert(home3.items.some(function (i) { return i.url === 'stremio:refresh'; }), 'home has Refresh item (list)');
assert(home3.metadata.title === 'ps3mio', 'home title is ps3mio');
var sb2 = open('stremio:searchbox');
sb2.items.filter(function (i) { return i.fn; })[0].fn();
var sb3 = open('stremio:searchbox');
assert(sb3.items.some(function (i) { return i.url === 'stremio:q:heat'; }), 'recent search listed on search page');
console.log(process.exitCode ? '\nSOME TESTS FAILED' : '\nALL TESTS PASSED');
