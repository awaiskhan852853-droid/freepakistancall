'use strict';
require('dotenv').config();
const http = require('http');
const path = require('path');
const crypto = require('crypto');
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const jwt = require('jsonwebtoken');
const Database = require('better-sqlite3');
const { WebSocketServer } = require('ws');
const twilioLib = require('twilio');

const env = process.env;
const PROD = env.NODE_ENV === 'production';
if (!env.JWT_SECRET || env.JWT_SECRET.length < 32) {
  console.error('JWT_SECRET (>= 32 chars) is required'); process.exit(1);
}

const tw = env.TWILIO_ACCOUNT_SID && env.TWILIO_AUTH_TOKEN
  ? twilioLib(env.TWILIO_ACCOUNT_SID, env.TWILIO_AUTH_TOKEN) : null;
let fcm = null;
if (env.FIREBASE_SERVICE_ACCOUNT_PATH) {
  const admin = require('firebase-admin');
  admin.initializeApp({ credential: admin.credential.cert(require(path.resolve(env.FIREBASE_SERVICE_ACCOUNT_PATH))) });
  fcm = admin.messaging();
}
const PSTN_ON = env.PSTN_ENABLED === 'true' && tw && env.TWILIO_API_KEY_SID && env.TWILIO_API_KEY_SECRET &&
  env.TWILIO_TWIML_APP_SID && env.TWILIO_CALLER_ID && env.PSTN_RATE_PER_MIN;

// ---------- database ----------
const db = new Database(env.DB_PATH || 'data.db');
db.pragma('journal_mode = WAL');
db.exec(`
CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT UNIQUE NOT NULL, name TEXT NOT NULL, fcm_token TEXT, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS favorites(user_id INTEGER NOT NULL, fav_id INTEGER NOT NULL, PRIMARY KEY(user_id, fav_id));
CREATE TABLE IF NOT EXISTS calls(id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, peer_name TEXT, peer_phone TEXT, direction TEXT, type TEXT, status TEXT, duration INTEGER, at INTEGER);
CREATE INDEX IF NOT EXISTS idx_calls_user ON calls(user_id, at DESC);
`);

// ---------- helpers ----------
function normalizePk(input) {
  let d = String(input || '').replace(/\D/g, '');
  if (d.startsWith('0092')) d = d.slice(2);
  if (d.startsWith('92') && d.length === 12) d = d.slice(2);
  else if (d.startsWith('0') && d.length === 11) d = d.slice(1);
  return /^3\d{9}$/.test(d) ? '+92' + d : null;
}
const wrap = fn => (req, res) => fn(req, res).catch(e => {
  console.error(e.message);
  res.status(e.status || 500).json({ error: e.status ? e.message : 'server_error' });
});
function auth(req, res, next) {
  try { req.uid = jwt.verify((req.headers.authorization || '').replace(/^Bearer /, ''), env.JWT_SECRET).uid; next(); }
  catch { res.status(401).json({ error: 'unauthorized' }); }
}
const pub = u => ({ id: u.id, name: u.name, phone: u.phone, fav: !!u.fav });

// ---------- OTP ----------
const devOtps = new Map(); const lastSent = new Map();
async function sendOtp(phone) {
  if (tw && env.TWILIO_VERIFY_SID) {
    await tw.verify.v2.services(env.TWILIO_VERIFY_SID).verifications.create({ to: phone, channel: 'sms' }); return;
  }
  if (PROD) throw Object.assign(new Error('otp_unavailable'), { status: 503 });
  const code = String(crypto.randomInt(100000, 1000000));
  devOtps.set(phone, { code, exp: Date.now() + 300000, tries: 0 });
  console.log(`[DEV OTP] ${phone}: ${code}`);
}
async function checkOtp(phone, code) {
  if (tw && env.TWILIO_VERIFY_SID) {
    const r = await tw.verify.v2.services(env.TWILIO_VERIFY_SID).verificationChecks.create({ to: phone, code });
    return r.status === 'approved';
  }
  if (PROD) return false;
  const o = devOtps.get(phone);
  if (!o || o.exp < Date.now() || ++o.tries > 5 || o.code !== code) return false;
  devOtps.delete(phone); return true;
}

// ---------- HTTP ----------
const app = express();
app.set('trust proxy', 1);
app.use(helmet());
app.use(express.json({ limit: '20kb' }));
app.get('/health', (_q, r) => r.json({ ok: true, pstn: !!PSTN_ON, push: !!fcm, otp: !!(tw && env.TWILIO_VERIFY_SID) }));

const authLimiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: 20, standardHeaders: true, legacyHeaders: false });
const apiLimiter = rateLimit({ windowMs: 60 * 1000, limit: 120 });

app.post('/auth/request-otp', authLimiter, wrap(async (req, res) => {
  const phone = normalizePk(req.body.phone);
  if (!phone) return res.status(400).json({ error: 'invalid_phone' });
  if (Date.now() - (lastSent.get(phone) || 0) < 45000) return res.status(429).json({ error: 'wait' });
  lastSent.set(phone, Date.now());
  await sendOtp(phone);
  res.json({ ok: true, phone });
}));

app.post('/auth/verify', authLimiter, wrap(async (req, res) => {
  const phone = normalizePk(req.body.phone); const code = String(req.body.code || '');
  if (!phone || !/^\d{4,8}$/.test(code)) return res.status(400).json({ error: 'invalid_request' });
  if (!(await checkOtp(phone, code))) return res.status(401).json({ error: 'invalid_code' });
  const name = String(req.body.name || '').trim().slice(0, 40);
  let u = db.prepare('SELECT * FROM users WHERE phone=?').get(phone);
  if (!u) {
    const id = db.prepare('INSERT INTO users(phone,name,created_at) VALUES(?,?,?)').run(phone, name || 'User', Date.now()).lastInsertRowid;
    u = db.prepare('SELECT * FROM users WHERE id=?').get(id);
  } else if (name) { db.prepare('UPDATE users SET name=? WHERE id=?').run(name, u.id); u.name = name; }
  const token = jwt.sign({ uid: u.id }, env.JWT_SECRET, { expiresIn: '30d' });
  res.json({ token, user: pub(u) });
}));

app.use(apiLimiter);
app.get('/me', auth, wrap(async (req, res) => res.json({ user: pub(db.prepare('SELECT * FROM users WHERE id=?').get(req.uid)) })));
app.put('/me', auth, wrap(async (req, res) => {
  const { name, fcmToken } = req.body;
  if (typeof name === 'string' && name.trim()) db.prepare('UPDATE users SET name=? WHERE id=?').run(name.trim().slice(0, 40), req.uid);
  if (typeof fcmToken === 'string') db.prepare('UPDATE users SET fcm_token=? WHERE id=?').run(fcmToken.slice(0, 400), req.uid);
  res.json({ ok: true });
}));

app.get('/users/search', auth, wrap(async (req, res) => {
  const q = String(req.query.q || '').replace(/\D/g, '');
  if (q.length < 7) return res.json({ items: [] });
  const rows = db.prepare(`SELECT u.*, EXISTS(SELECT 1 FROM favorites f WHERE f.user_id=? AND f.fav_id=u.id) AS fav
    FROM users u WHERE u.id<>? AND u.phone LIKE ? LIMIT 20`).all(req.uid, req.uid, '%' + q + '%');
  res.json({ items: rows.map(pub) });
}));
app.get('/users/lookup', auth, wrap(async (req, res) => {
  const phone = normalizePk(req.query.phone);
  const u = phone && db.prepare('SELECT * FROM users WHERE phone=? AND id<>?').get(phone, req.uid);
  res.json({ user: u ? pub(u) : null });
}));

app.get('/favorites', auth, wrap(async (req, res) => {
  const rows = db.prepare('SELECT u.*, 1 AS fav FROM favorites f JOIN users u ON u.id=f.fav_id WHERE f.user_id=? ORDER BY u.name').all(req.uid);
  res.json({ items: rows.map(pub) });
}));
app.post('/favorites', auth, wrap(async (req, res) => {
  if (!Number.isInteger(req.body.id)) return res.status(400).json({ error: 'invalid_request' });
  db.prepare('INSERT OR IGNORE INTO favorites(user_id,fav_id) SELECT ?, id FROM users WHERE id=?').run(req.uid, req.body.id);
  res.json({ ok: true });
}));
app.delete('/favorites/:id', auth, wrap(async (req, res) => {
  db.prepare('DELETE FROM favorites WHERE user_id=? AND fav_id=?').run(req.uid, Number(req.params.id)); res.json({ ok: true });
}));

app.get('/calls', auth, wrap(async (req, res) => {
  res.json({ items: db.prepare('SELECT peer_name AS peerName, peer_phone AS peerPhone, direction, type, status, duration, at FROM calls WHERE user_id=? ORDER BY at DESC LIMIT 100').all(req.uid) });
}));
app.post('/calls', auth, wrap(async (req, res) => {
  const b = req.body;
  db.prepare('INSERT INTO calls(user_id,peer_name,peer_phone,direction,type,status,duration,at) VALUES(?,?,?,?,?,?,?,?)').run(
    req.uid, String(b.peerName || '').slice(0, 40), String(b.peerPhone || '').slice(0, 20),
    b.direction === 'incoming' ? 'incoming' : 'outgoing', b.type === 'pstn' ? 'pstn' : 'app',
    String(b.status || '').slice(0, 20), Math.max(0, Math.min(86400, parseInt(b.duration) || 0)), Date.now());
  res.json({ ok: true });
}));

// WebRTC ICE servers (STUN + time-limited TURN credentials, coturn "use-auth-secret")
app.get('/rtc/config', auth, (req, res) => {
  const ice = [{ urls: (env.STUN_URLS || 'stun:stun.l.google.com:19302').split(',') }];
  if (env.TURN_URLS && env.TURN_SECRET) {
    const username = `${Math.floor(Date.now() / 1000) + 3600}:${req.uid}`;
    const credential = crypto.createHmac('sha1', env.TURN_SECRET).update(username).digest('base64');
    ice.push({ urls: env.TURN_URLS.split(','), username, credential });
  }
  res.json({ iceServers: ice });
});

// ---------- PSTN (Twilio Voice) ----------
app.get('/pstn/rate', auth, (_req, res) => res.json({
  enabled: !!PSTN_ON, perMinute: PSTN_ON ? Number(env.PSTN_RATE_PER_MIN) : 0, currency: env.PSTN_CURRENCY || 'PKR',
}));
app.get('/pstn/token', auth, wrap(async (req, res) => {
  if (!PSTN_ON) return res.status(503).json({ error: 'pstn_unavailable' });
  const cap = Number(env.PSTN_DAILY_SECONDS_PER_USER || 600);
  const since = Date.now() - 86400000;
  const used = db.prepare("SELECT COALESCE(SUM(duration),0) s FROM calls WHERE user_id=? AND type='pstn' AND at>?").get(req.uid, since).s;
  if (used >= cap) return res.status(429).json({ error: 'daily_limit' });
  const { AccessToken } = twilioLib.jwt; const { VoiceGrant } = AccessToken;
  const t = new AccessToken(env.TWILIO_ACCOUNT_SID, env.TWILIO_API_KEY_SID, env.TWILIO_API_KEY_SECRET, { identity: `user_${req.uid}`, ttl: 900 });
  t.addGrant(new VoiceGrant({ outgoingApplicationSid: env.TWILIO_TWIML_APP_SID }));
  res.json({ token: t.toJwt() });
}));
// Twilio calls this webhook when the app starts a PSTN call. Signature-validated in production.
app.post('/pstn/twiml', express.urlencoded({ extended: false }),
  PROD ? twilioLib.webhook({ protocol: 'https' }) : (_q, _r, n) => n(),
  (req, res) => {
    const to = normalizePk(req.body.To);
    const vr = new twilioLib.twiml.VoiceResponse();
    if (!PSTN_ON || !to) vr.reject();
    else vr.dial({ callerId: env.TWILIO_CALLER_ID, timeLimit: Number(env.PSTN_MAX_SECONDS || 3600) }).number(to);
    res.type('text/xml').send(vr.toString());
  });

// ---------- WebSocket signaling ----------
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 65536 });
const online = new Map(); const queue = new Map();
const SIG = new Set(['offer', 'answer', 'ice', 'hangup', 'reject']);
const sendTo = (uid, msg) => { const w = online.get(uid); if (w && w.readyState === 1) { w.send(JSON.stringify(msg)); return true; } return false; };
const enqueue = (uid, msg) => { const q = queue.get(uid) || []; q.push({ msg, ts: Date.now() }); queue.set(uid, q.slice(-100)); };

wss.on('connection', (ws, req) => {
  let uid;
  try { uid = jwt.verify((req.headers.authorization || '').replace(/^Bearer /, ''), env.JWT_SECRET).uid; }
  catch { return ws.close(4401); }
  const old = online.get(uid); if (old && old !== ws) old.close(4000);
  online.set(uid, ws); ws.isAlive = true; ws.on('pong', () => { ws.isAlive = true; });
  const pending = (queue.get(uid) || []).filter(e => Date.now() - e.ts < 60000); queue.delete(uid);
  pending.forEach(e => ws.send(JSON.stringify(e.msg)));

  ws.on('message', raw => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    if (!SIG.has(m.type) || typeof m.callId !== 'string' || m.callId.length > 64 || !Number.isInteger(m.to)) return;
    const me = db.prepare('SELECT name, phone FROM users WHERE id=?').get(uid); if (!me) return;
    const out = { type: m.type, callId: m.callId, from: uid, fromName: me.name, fromPhone: me.phone };
    if (m.sdp) out.sdp = String(m.sdp).slice(0, 20000);
    if (m.candidate && typeof m.candidate === 'object') out.candidate = {
      sdpMid: m.candidate.sdpMid, sdpMLineIndex: m.candidate.sdpMLineIndex, candidate: String(m.candidate.candidate).slice(0, 1000) };
    const delivered = sendTo(m.to, out);
    if (!delivered) enqueue(m.to, out);
    if (m.type === 'offer') {
      const peer = db.prepare('SELECT fcm_token FROM users WHERE id=?').get(m.to);
      let pushed = false;
      if (peer && peer.fcm_token && fcm) {   // always push: wakes killed/dozing apps
        fcm.send({ token: peer.fcm_token, data: { type: 'incoming_call', callId: m.callId, fromName: me.name, fromPhone: me.phone },
          android: { priority: 'high', ttl: 45000 } }).catch(e => console.error('FCM', e.code));
        pushed = true;
      }
      if (!delivered && !pushed) sendTo(uid, { type: 'unavailable', callId: m.callId, from: m.to });
    }
  });
  ws.on('close', () => { if (online.get(uid) === ws) online.delete(uid); });
});
setInterval(() => wss.clients.forEach(w => { if (!w.isAlive) return w.terminate(); w.isAlive = false; w.ping(); }), 30000);

const port = Number(env.PORT || 3000);
server.listen(port, () => console.log(`Free Pakistan Call backend on :${port} (prod=${PROD}, pstn=${!!PSTN_ON}, push=${!!fcm})`));
