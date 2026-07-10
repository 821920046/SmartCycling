-- 智能骑行中控 D1 数据库结构
-- 初始化: wrangler d1 execute smart_cycling --file=./schema.sql --remote

CREATE TABLE IF NOT EXISTS rides (
  id              TEXT PRIMARY KEY,   -- deviceId-startedAt,保证重传不重复
  device_id       TEXT,
  rider           TEXT,
  started_at      INTEGER,
  ended_at        INTEGER,
  duration_sec    INTEGER,
  distance_km     REAL,
  avg_speed_kmh   REAL,
  max_speed_kmh   REAL,
  avg_cadence_rpm REAL,
  created_at      INTEGER
);

CREATE TABLE IF NOT EXISTS track_points (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  ride_id   TEXT,
  lat       REAL,
  lng       REAL,
  speed_kmh REAL,
  ts        INTEGER
);

CREATE INDEX IF NOT EXISTS idx_tp_ride ON track_points(ride_id);
CREATE INDEX IF NOT EXISTS idx_rides_started ON rides(started_at);
