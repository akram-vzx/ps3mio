// Builds the I18N table embedded in plugin/stremio.js from the official
// stremio-translations repo (en-US.json, ar-AR.json, ...) + a few custom strings.
// usage: node tools/gen-i18n.js <path-to-stremio-translations> > i18n.json
var fs = require('fs'), path = require('path');
var dir = process.argv[2] || '../stremio-translations-master';
var langs = { en: 'en-US', ar: 'ar-AR', fr: 'fr-FR', es: 'es-ES', de: 'de-DE',
              it: 'it-IT', pt: 'pt-BR', tr: 'tr-TR' };
// internal key -> official translation key
var map = {
  board: 'Board', discover: 'Discover', library: 'Library', addons: 'ADDONS',
  settings: 'SETTINGS', search: 'STREMIO_TV_NAV_SEARCH', cw: 'CONTINUE_WATCHING',
  watch: 'WATCH_NOW', season: 'SEASON', episode: 'EPISODE', streams: 'STREAMS',
  logout: 'LOGOUT', login: 'LOG_IN', all: 'ALL', genre: 'GENRE',
  subtitles: 'SUBTITLES', special: 'SPECIAL', cast: 'CAST', director: 'DIRECTOR',
  movie: 'TYPE_movie_PL', series: 'TYPE_series_PL', channel: 'TYPE_channel_PL',
  email: 'EMAIL', password: 'PASSWORD'
};
var custom = {
  en: {
    hint: 'X Select   O Back   Triangle Menu   Square Options   START Play/Pause   L1/R1 Skip',
    player_hint: 'X Pause   L1/R1 Skip   Left/Right Seek   Triangle Menu   Square Info',
    phone_login: 'Log in with phone / PC (link code)',
    email_login: 'Log in with email + password',
    sync: 'Sync addons from my Stremio account',
    synced: 'Addons synced',
    need_login: 'Log in first (Stremio > Log in)',
    link_1: 'On your phone or PC open:',
    link_2: 'and enter this code:',
    link_wait: 'Waiting for approval...',
    link_fail: 'Cannot reach the Stremio link service (check network / HTTPS relay)',
    no_results: 'No results',
    no_streams: 'No streams found',
    addons_help: 'Addons are managed from your phone or PC (Stremio app > Addons). Press Sync here to update the list.',
    net_error: 'Network error',
    not_logged: 'Not logged in - using default addon (Cinemeta)',
    logged_ok: 'Logged in',
    logged_out: 'Logged out',
    torrent_note: 'Torrent (opens file list)',
    search_btn: 'Search...',
    refresh: 'Refresh',
    recent: 'Recent searches'
  },
  ar: {
    hint: 'X اختيار   O رجوع   Triangle القائمة   Square خيارات   START تشغيل/ايقاف   L1/R1 تخطي',
    player_hint: 'X ايقاف مؤقت   L1/R1 تخطي   يمين/يسار تقديم   Triangle القائمة   Square معلومات',
    phone_login: 'تسجيل الدخول بالهاتف / الكمبيوتر (رمز ربط)',
    email_login: 'تسجيل الدخول بالبريد وكلمة المرور',
    sync: 'مزامنة الإضافات من حسابي',
    synced: 'تمت مزامنة الإضافات',
    need_login: 'سجّل الدخول أولاً',
    link_1: 'افتح هذا الرابط على هاتفك أو الكمبيوتر:',
    link_2: 'وأدخل هذا الرمز:',
    link_wait: 'بانتظار الموافقة...',
    link_fail: 'تعذر الوصول لخدمة الربط (تحقق من الشبكة / وسيط HTTPS)',
    no_results: 'لا نتائج',
    no_streams: 'لا توجد روابط تشغيل',
    addons_help: 'تتم إدارة الإضافات من الهاتف أو الكمبيوتر (تطبيق Stremio > الإضافات). اضغط مزامنة هنا لتحديث القائمة.',
    net_error: 'خطأ في الشبكة',
    not_logged: 'غير مسجل - يتم استخدام إضافة Cinemeta الافتراضية',
    logged_ok: 'تم تسجيل الدخول',
    logged_out: 'تم تسجيل الخروج',
    torrent_note: 'تورنت (يفتح قائمة الملفات)',
    search_btn: 'بحث...',
    refresh: 'تحديث',
    recent: 'عمليات البحث الأخيرة'
  }
};
var out = {};
Object.keys(langs).forEach(function (l) {
  var tr = JSON.parse(fs.readFileSync(path.join(dir, langs[l] + '.json'), 'utf8'));
  out[l] = {};
  Object.keys(map).forEach(function (k) {
    var v = tr[map[k]];
    if (typeof v === 'string' && v.length) out[l][k] = v;
  });
  var c = custom[l] || {};
  Object.keys(c).forEach(function (k) { out[l][k] = c[k]; });
});
Object.keys(custom.en).forEach(function (k) { out.en[k] = custom.en[k]; });
process.stdout.write(JSON.stringify(out));
