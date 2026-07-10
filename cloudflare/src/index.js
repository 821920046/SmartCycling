// 智能骑行 中控 Worker
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
<title>🚴 智能骑行 · 中控大屏</title>
<!-- Tailwind CSS & FontAwesome -->
<script src="https://cdn.tailwindcss.com"></script>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
<!-- Leaflet CSS & JS -->
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body {
    background-color: #030712;
    color: #f3f4f6;
    font-family: ui-sans-serif, system-ui, -apple-system, sans-serif;
  }
  /* 自定义滚动条 */
  ::-webkit-scrollbar { width: 6px; }
  ::-webkit-scrollbar-track { background: #0f172a; }
  ::-webkit-scrollbar-thumb { background: #1e293b; border-radius: 3px; }
  ::-webkit-scrollbar-thumb:hover { background: #334155; }
  .glass-card {
    background: rgba(15, 23, 42, 0.65);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(0, 240, 255, 0.15);
  }
  .neon-border-active {
    border-color: rgba(0, 240, 255, 0.6);
    box-shadow: 0 0 10px rgba(0, 240, 255, 0.25);
  }
</style>
</head>
<body class="min-h-screen flex flex-col overflow-hidden">

  <!-- 顶栏 -->
  <header class="bg-slate-900/80 backdrop-blur border-b border-cyan-500/20 px-6 py-4 flex flex-wrap items-center justify-between gap-4 z-10">
    <div class="flex items-center gap-3">
      <div class="w-10 h-10 rounded-xl bg-cyan-500/10 flex items-center justify-center border border-cyan-500/30">
        <i class="fa-solid fa-bicycle text-cyan-400 text-xl"></i>
      </div>
      <div>
        <h1 class="text-lg font-black tracking-wider text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-emerald-400">SMART CYCLING</h1>
        <p class="text-xs text-slate-400 font-medium">数据云中控管理平台</p>
      </div>
    </div>
    
    <div class="flex items-center gap-3">
      <div class="relative">
        <i class="fa-solid fa-key absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 text-sm"></i>
        <input id="tok" type="password" placeholder="访问令牌 SYNC_TOKEN" class="bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-3 py-2 text-sm text-slate-300 w-56 focus:outline-none focus:border-cyan-500/50 transition">
      </div>
      <button id="save" class="bg-cyan-500 hover:bg-cyan-600 text-slate-950 font-bold px-4 py-2 rounded-lg text-sm transition flex items-center gap-2">
        <i class="fa-solid fa-rotate"></i> 保存并刷新
      </button>
    </div>
  </header>

  <!-- 内容区 -->
  <div class="flex-1 flex flex-col lg:flex-row overflow-hidden">
    
    <!-- 左边栏：数据面板与历史列表 -->
    <div class="w-full lg:w-[480px] bg-slate-950/40 border-r border-slate-900 flex flex-col overflow-hidden">
      <!-- 汇总看板 -->
      <div class="p-5 grid grid-cols-3 gap-3 border-b border-slate-900 bg-slate-950/20">
        <div class="glass-card rounded-xl p-3 text-center">
          <p class="text-[10px] text-slate-400 font-bold mb-1">累计里程</p>
          <p class="text-lg font-black text-cyan-400" id="stat-dist">0.0 <span class="text-[10px] text-slate-400">km</span></p>
        </div>
        <div class="glass-card rounded-xl p-3 text-center">
          <p class="text-[10px] text-slate-400 font-bold mb-1">骑行次数</p>
          <p class="text-lg font-black text-cyan-400" id="stat-count">0 <span class="text-[10px] text-slate-400">次</span></p>
        </div>
        <div class="glass-card rounded-xl p-3 text-center">
          <p class="text-[10px] text-slate-400 font-bold mb-1">累计时长</p>
          <p class="text-lg font-black text-cyan-400" id="stat-time">0.0 <span class="text-[10px] text-slate-400">h</span></p>
        </div>
      </div>

      <!-- 列表状态提示 -->
      <div class="px-5 py-3 border-b border-slate-900 bg-slate-900/10 flex justify-between items-center">
        <span class="text-xs text-slate-400 font-semibold" id="tip">加载中…</span>
      </div>

      <!-- 历史条目滚动列表 -->
      <div class="flex-1 overflow-y-auto p-4 space-y-3" id="list-container">
        <!-- 动态装载 -->
      </div>
    </div>

    <!-- 右边栏：Leaflet 轨迹大地图 -->
    <div class="flex-1 relative bg-slate-950 flex flex-col">
      <div id="map" class="w-full h-full z-0"></div>
      
      <!-- 地图浮层：未选择数据提示 -->
      <div id="map-overlay" class="absolute inset-0 bg-slate-950/80 backdrop-blur-sm z-10 flex flex-col items-center justify-center pointer-events-none transition-all duration-300">
        <div class="w-16 h-16 rounded-full bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center mb-4 animate-bounce">
          <i class="fa-solid fa-map-location-dot text-cyan-400 text-2xl"></i>
        </div>
        <p class="text-slate-300 font-bold text-base">请在左侧选择一次骑行记录</p>
        <p class="text-slate-500 text-xs mt-1">云端将自动载入高精度 GCJ-02 定位轨迹点并平滑绘制</p>
      </div>
    </div>

  </div>

<script>
  var map;
  var currentPolyline = null;
  var startMarker = null;
  var endMarker = null;
  var activeRideId = null;

  // 初始化地图 (Leaflet)
  function initMap() {
    map = L.map('map', {
      zoomControl: false,
      attributionControl: false
    }).setView([23.1291, 113.2644], 13); // 默认定位广州

    // 使用高德地图瓦片(无偏差地融合 GCJ-02 轨迹)
    L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}', {
      subdomains: '1234',
      maxZoom: 18,
      minZoom: 3
    }).addTo(map);

    L.control.zoom({ position: 'topright' }).addTo(map);
  }

  function getToken(){ return localStorage.getItem('sc_token') || ''; }
  function setToken(t){ localStorage.setItem('sc_token', t); }
  function pad(n){ return (n<10?'0':'')+n; }
  function fmtDur(s){ s=Math.floor(s||0); var h=Math.floor(s/3600), m=Math.floor((s%3600)/60), x=s%60; return pad(h)+':'+pad(m)+':'+pad(x); }
  function fmtDate(ms){ var d=new Date(ms||0); return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes()); }
  
  async function load(){
    var t=getToken(); $('#tok').value=t;
    $('#list-container').innerHTML = '';
    $('#tip').textContent = '拉取云端记录中…';
    
    try {
      var r=await fetch('/api/rides',{headers:{'Authorization':'Bearer '+t}});
      if(r.status===401){
        $('#tip').textContent='✘ 令牌校验失败';
        $('#list-container').innerHTML='<div class="text-center py-8 text-rose-500 font-bold"><i class="fa-solid fa-triangle-exclamation mb-2 text-xl"></i><br>安全令牌无效，请在右上方重新输入</div>';
        return;
      }
      var data=await r.json();
      var list=data.rides||[];
      
      // 更新大看板
      var totalDist = list.reduce((a,b)=>a+(b.distance_km||0), 0);
      var totalTimeSec = list.reduce((a,b)=>a+(b.duration_sec||0), 0);
      $('#stat-dist').innerHTML = totalDist.toFixed(1) + ' <span class="text-[10px] text-slate-400">km</span>';
      $('#stat-count').innerHTML = list.length + ' <span class="text-[10px] text-slate-400">次</span>';
      $('#stat-time').innerHTML = (totalTimeSec/3600.0).toFixed(1) + ' <span class="text-[10px] text-slate-400">h</span>';
      
      $('#tip').textContent='共发现 '+list.length+' 条骑行封盘记录';

      if (list.length === 0) {
        $('#list-container').innerHTML = '<div class="text-center py-12 text-slate-500 text-sm">暂无云端数据同步</div>';
        return;
      }

      list.forEach(function(x){
        var card = document.createElement('div');
        card.id = 'ride-' + x.id;
        card.className = 'glass-card rounded-xl p-4 cursor-pointer transition-all duration-200 hover:bg-slate-900/50 hover:border-cyan-500/40';
        card.innerHTML = 
          '<div class="flex items-center justify-between mb-2">' +
            '<span class="text-xs text-slate-400 font-bold"><i class="fa-regular fa-clock mr-1"></i>' + fmtDate(x.started_at) + '</span>' +
            '<span class="text-[10px] bg-cyan-500/10 text-cyan-400 px-2 py-0.5 rounded font-black border border-cyan-500/20"><i class="fa-solid fa-user mr-1"></i>' + (x.rider||'未知骑手') + '</span>' +
          '</div>' +
          '<div class="grid grid-cols-3 gap-2 mt-3">' +
            '<div><p class="text-[10px] text-slate-500 font-bold">里程</p><p class="text-sm font-black text-slate-200">' + x.distance_km.toFixed(2) + ' km</p></div>' +
            '<div><p class="text-[10px] text-slate-500 font-bold">时长</p><p class="text-sm font-black text-slate-200">' + fmtDur(x.duration_sec) + '</p></div>' +
            '<div><p class="text-[10px] text-slate-500 font-bold">均速</p><p class="text-sm font-black text-slate-200">' + x.avg_speed_kmh.toFixed(1) + ' km/h</p></div>' +
          '</div>';
        
        card.onclick = function() { selectRide(x) };
        $('#list-container').appendChild(card);
      });

    } catch(e){
      $('#tip').textContent='加载失败: '+e;
    }
  }

  async function selectRide(ride) {
    if (activeRideId) {
      var prevCard = $('#ride-' + activeRideId);
      if (prevCard) prevCard.classList.remove('neon-border-active');
    }
    
    activeRideId = ride.id;
    var activeCard = $('#ride-' + activeRideId);
    if (activeCard) activeCard.classList.add('neon-border-active');

    // 隐藏遮罩层
    $('#map-overlay').classList.add('opacity-0');
    $('#map-overlay').classList.add('pointer-events-none');

    // 清空上次轨迹
    if (currentPolyline) map.removeLayer(currentPolyline);
    if (startMarker) map.removeLayer(startMarker);
    if (endMarker) map.removeLayer(endMarker);

    try {
      var t = getToken();
      var r = await fetch('/api/rides/' + encodeURIComponent(ride.id), {headers:{'Authorization':'Bearer '+t}});
      var data = await r.json();
      var points = data.points || [];
      
      if (points.length === 0) {
        alert("该次骑行记录没有采集到有效的 GPS 轨迹点");
        return;
      }

      var latlngs = points.map(function(p) {
        return [p.lat, p.lng];
      });

      // 绘制轨迹折线
      currentPolyline = L.polyline(latlngs, {
        color: '#00ff88',
        weight: 6,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round'
      }).addTo(map);

      // 起点标记
      startMarker = L.circleMarker(latlngs[0], {
        radius: 7,
        fillColor: '#00f0ff',
        color: '#fff',
        weight: 2,
        fillOpacity: 1
      }).addTo(map).bindPopup("<b>骑行起点</b>");

      // 终点标记
      endMarker = L.circleMarker(latlngs[latlngs.length - 1], {
        radius: 7,
        fillColor: '#ff3b30',
        color: '#fff',
        weight: 2,
        fillOpacity: 1
      }).addTo(map).bindPopup("<b>骑行终点</b>");

      // 地图自适应视角
      map.fitBounds(currentPolyline.getBounds(), { padding: [40, 40] });

    } catch (e) {
      alert("加载轨迹失败: " + e);
    }
  }

  function $(s){ return document.querySelector(s); }
  
  $('#save').onclick=function(){
    setToken($('#tok').value.trim());
    load();
  };

  window.onload = function() {
    initMap();
    load();
  };
</script>
</body>
</html>`

