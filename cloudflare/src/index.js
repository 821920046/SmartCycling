// 智慧骑行 中控 Worker
// 路由:
//   GET  /                → 中控网页(表格查看骑行记录)
//   POST /api/rides       → App 上传一次骑行(含轨迹点),需 Bearer 令牌
//   GET  /api/rides       → 骑行列表 JSON
//   GET  /api/rides/:id   → 单次骑行详情 + 轨迹点

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    const path = url.pathname
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type,Authorization",
    }
    if (request.method === "OPTIONS") return new Response(null, { headers: cors })

    try {
      if (path === "/" || path === "/index.html") {
        return new Response(DASHBOARD_HTML, {
          headers: { "Content-Type": "text/html; charset=utf-8" },
        })
      }

      await ensureSchema(env)

      if (path === "/api/rides" && request.method === "POST") {
        if (!checkToken(request, url, env)) return json({ error: "unauthorized" }, 401, cors)
        const b = await request.json()
        if (!b || !b.id) return json({ error: "missing id" }, 400, cors)
        await env.DB.prepare(
          "INSERT OR REPLACE INTO rides (id, device_id, rider, started_at, ended_at, duration_sec, distance_km, avg_speed_kmh, max_speed_kmh, avg_cadence_rpm, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
        ).bind(
          b.id, b.deviceId || "", b.rider || "",
          b.startedAt || 0, b.endedAt || 0, b.durationSec || 0,
          b.distanceKm || 0, b.avgSpeedKmh || 0, b.maxSpeedKmh || 0,
          b.avgCadenceRpm || 0, Date.now()
        ).run()

        await env.DB.prepare("DELETE FROM track_points WHERE ride_id = ?").bind(b.id).run()
        const pts = Array.isArray(b.points) ? b.points : []
        if (pts.length) {
          const stmt = env.DB.prepare(
            "INSERT INTO track_points (ride_id, lat, lng, speed_kmh, ts) VALUES (?,?,?,?,?)"
          )
          const batch = pts.map(function (p) {
            return stmt.bind(b.id, p.lat, p.lng, p.speedKmh || 0, p.ts || 0)
          })
          await env.DB.batch(batch)
        }
        return json({ ok: true, id: b.id, points: pts.length }, 200, cors)
      }

      if (path === "/api/rides" && request.method === "GET") {
        if (!checkToken(request, url, env)) return json({ error: "unauthorized" }, 401, cors)
        const res = await env.DB.prepare(
          "SELECT * FROM rides ORDER BY started_at DESC LIMIT 500"
        ).all()
        return json({ rides: res.results || [] }, 200, cors)
      }

      if (path.startsWith("/api/rides/") && request.method === "GET") {
        if (!checkToken(request, url, env)) return json({ error: "unauthorized" }, 401, cors)
        const id = decodeURIComponent(path.substring("/api/rides/".length))
        const ride = await env.DB.prepare("SELECT * FROM rides WHERE id = ?").bind(id).first()
        const tp = await env.DB.prepare(
          "SELECT lat, lng, speed_kmh, ts FROM track_points WHERE ride_id = ? ORDER BY ts ASC"
        ).bind(id).all()
        return json({ ride: ride, points: tp.results || [] }, 200, cors)
      }

      return new Response("Not found", { status: 404, headers: cors })
    } catch (e) {
      return json({ error: String(e && e.message ? e.message : e) }, 500, cors)
    }
  },
}

function json(obj, status, extra) {
  return new Response(JSON.stringify(obj), {
    status: status || 200,
    headers: Object.assign({ "Content-Type": "application/json; charset=utf-8" }, extra || {}),
  })
}

function tokenOf(request, url) {
  const h = request.headers.get("Authorization") || ""
  if (h.indexOf("Bearer ") === 0) return h.slice(7).trim()
  return (url.searchParams.get("token") || "").trim()
}

function checkToken(request, url, env) {
  const expected = ((env && env.SYNC_TOKEN) || "").trim()
  if (!expected) return true // 未设令牌则不校验(方便快速试用)
  return tokenOf(request, url) === expected
}

async function ensureSchema(env) {
  await env.DB.batch([
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS rides (id TEXT PRIMARY KEY, device_id TEXT, rider TEXT, started_at INTEGER, ended_at INTEGER, duration_sec INTEGER, distance_km REAL, avg_speed_kmh REAL, max_speed_kmh REAL, avg_cadence_rpm REAL, created_at INTEGER)"
    ),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, ride_id TEXT, lat REAL, lng REAL, speed_kmh REAL, ts INTEGER)"
    ),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS idx_tp_ride ON track_points(ride_id)"),
  ])
}

const DASHBOARD_HTML = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>智慧骑行 · 中控</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#0b1220; color:#e6edf6; }
  header { padding:20px 24px; background:#0f1a2e; border-bottom:1px solid #1e2a44; display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
  header h1 { font-size:18px; margin:0; font-weight:600; }
  .sp { flex:1; }
  input { background:#0b1220; border:1px solid #26324d; color:#e6edf6; border-radius:8px; padding:8px 10px; font-size:13px; width:220px; }
  button { background:#2563eb; border:0; color:#fff; border-radius:8px; padding:8px 14px; font-size:13px; cursor:pointer; }
  button:hover { background:#1d4ed8; }
  .wrap { padding:20px 24px; }
  #tip { color:#8aa0c0; font-size:13px; margin-bottom:12px; }
  table { width:100%; border-collapse:collapse; font-size:14px; }
  th,td { text-align:left; padding:10px 12px; border-bottom:1px solid #1a2540; }
  th { color:#8aa0c0; font-weight:500; font-size:12px; }
  tr:hover td { background:#101a30; }
  .num { font-variant-numeric:tabular-nums; }
</style>
</head>
<body>
<header>
  <h1>🚴 智慧骑行 · 中控</h1>
  <div class="sp"></div>
  <input id="tok" placeholder="访问令牌 SYNC_TOKEN">
  <button id="save">保存并刷新</button>
</header>
<div class="wrap">
  <div id="tip">加载中…</div>
  <table>
    <thead><tr>
      <th>开始时间</th><th>骑行者</th><th>里程(km)</th><th>时长</th>
      <th>均速(km/h)</th><th>最高速(km/h)</th><th>踏频(rpm)</th>
    </tr></thead>
    <tbody id="body"></tbody>
  </table>
</div>
<script>
  var $ = function(s){ return document.querySelector(s); };
  function getToken(){ return localStorage.getItem('sc_token') || ''; }
  function setToken(t){ localStorage.setItem('sc_token', t); }
  function pad(n){ return (n<10?'0':'')+n; }
  function fmtDur(s){ s=Math.floor(s||0); var h=Math.floor(s/3600), m=Math.floor((s%3600)/60), x=s%60; return pad(h)+':'+pad(m)+':'+pad(x); }
  function fmtDate(ms){ var d=new Date(ms||0); return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes()); }
  function num(v,f){ return (typeof v==='number'?v:0).toFixed(f); }
  async function load(){
    var t=getToken(); $('#tok').value=t;
    try {
      var r=await fetch('/api/rides',{headers:{'Authorization':'Bearer '+t}});
      if(r.status===401){ $('#tip').textContent='✘ 令牌无效,请在右上方填写正确的 SYNC_TOKEN'; $('#body').innerHTML=''; return; }
      var data=await r.json();
      var list=data.rides||[];
      var rows=list.map(function(x){
        return '<tr>'+
          '<td>'+fmtDate(x.started_at)+'</td>'+
          '<td>'+(x.rider||'-')+'</td>'+
          '<td class=num>'+num(x.distance_km,2)+'</td>'+
          '<td class=num>'+fmtDur(x.duration_sec)+'</td>'+
          '<td class=num>'+num(x.avg_speed_kmh,1)+'</td>'+
          '<td class=num>'+num(x.max_speed_kmh,1)+'</td>'+
          '<td class=num>'+num(x.avg_cadence_rpm,0)+'</td>'+
        '</tr>';
      }).join('');
      $('#body').innerHTML = rows || '<tr><td colspan=7 style="text-align:center;color:#8aa0c0">暂无记录</td></tr>';
      $('#tip').textContent='共 '+list.length+' 条骑行记录';
    } catch(e){ $('#tip').textContent='加载失败: '+e; }
  }
  $('#save').onclick=function(){ setToken($('#tok').value.trim()); load(); };
  load();
</script>
</body>
</html>`
