// Builds dist/stremio.zip (the Movian plugin) and dist/plugins-v1.json (a custom
// plugin repository that Movian can read).  Needs only node + the `zip` command.
//   node tools/build.js [BASE_URL_WHERE_ZIP_WILL_BE_HOSTED]
var fs = require('fs'), path = require('path'), cp = require('child_process');
var root = path.join(__dirname, '..');
var src = fs.readFileSync(path.join(root, 'plugin/stremio.js.src'), 'utf8');
var i18n = fs.readFileSync(path.join(root, 'plugin/i18n.json'), 'utf8');
var js = src.replace('/*I18N*/{}', i18n);
if (js === src) throw new Error('I18N placeholder not found');
fs.writeFileSync(path.join(root, 'plugin/stremio.js'), js);

var meta = JSON.parse(fs.readFileSync(path.join(root, 'plugin/plugin.json'), 'utf8'));
var dist = path.join(root, 'dist');
fs.mkdirSync(dist, { recursive: true });
var zip = path.join(dist, 'stremio.zip');
if (fs.existsSync(zip)) fs.unlinkSync(zip);
cp.execSync('zip -q -j "' + zip + '" plugin.json stremio.js logo.png', { cwd: path.join(root, 'plugin') });

var base = (process.argv[2] || '.').replace(/\/+$/, '');
var repo = { version: 1, plugins: [{
  id: meta.id, type: 'ecmascript', version: meta.version, title: meta.title,
  author: meta.author, synopsis: meta.synopsis, category: meta.category,
  icon: base + '/logo.png', downloadURL: base + '/stremio.zip',
  showtimeVersion: '4.0.0'
}] };
fs.writeFileSync(path.join(dist, 'plugins-v1.json'), JSON.stringify(repo, null, 2));
fs.copyFileSync(path.join(root, 'plugin/logo.png'), path.join(dist, 'logo.png'));
console.log('built', zip, fs.statSync(zip).size + ' bytes');
