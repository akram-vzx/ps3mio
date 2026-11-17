// Optional HTTPS->HTTP relay. Only needed if the PS3 cannot do TLS with modern servers.
// Run on a phone (Termux: `pkg install nodejs`) or PC on the same network:
//     node relay.js            (listens on :8787)
// Then in Movian > Settings > Stremio > "HTTP relay prefix" set:
//     http://PHONE_IP:8787/?u=
var http = require('http'), https = require('https'), url = require('url');
http.createServer(function (req, res) {
  var q = url.parse(req.url, true).query;
  if (!q.u) { res.writeHead(400); return res.end('missing ?u='); }
  var target = url.parse(q.u);
  var lib = target.protocol === 'http:' ? http : https;
  var headers = { 'User-Agent': 'Stremio-PS3-relay' };
  if (req.headers.range) headers.Range = req.headers.range;
  var chunks = [];
  req.on('data', function (c) { chunks.push(c); });
  req.on('end', function () {
    var body = Buffer.concat(chunks);
    if (body.length) { headers['Content-Type'] = req.headers['content-type'] || 'application/json'; headers['Content-Length'] = body.length; }
    var up = lib.request({ hostname: target.hostname, port: target.port, path: target.path,
      method: req.method, headers: headers }, function (r) {
      if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location) {
        res.writeHead(302, { Location: '/?u=' + encodeURIComponent(url.resolve(q.u, r.headers.location)) });
        return res.end();
      }
      var h = {};
      ['content-type', 'content-length', 'content-range', 'accept-ranges'].forEach(function (k) { if (r.headers[k]) h[k] = r.headers[k]; });
      res.writeHead(r.statusCode, h);
      r.pipe(res);
    });
    up.on('error', function (e) { res.writeHead(502); res.end(String(e)); });
    up.end(body);
  });
}).listen(8787, function () { console.log('relay on :8787'); });
