# Free Pakistan Call (com.freepakistancall.app)

Kotlin + Jetpack Compose Android app, Node.js backend (REST + WebSocket signaling), WebRTC app-to-app voice,
Twilio Voice for calls to ordinary Pakistani mobiles. Urdu (default) / English, green-and-white UI.

## Honest status
| Area | State |
|---|---|
| Android source (12 screens, WebRTC, signaling, FCM, PSTN, encrypted token storage) | Written. **Not compiled or run** by the author's tooling — expect to fix small compile errors on first build. |
| Backend (auth/OTP, users, favorites, call history, signaling, push, TURN creds, PSTN token + TwiML) | Written; `node --check` passes. **Not run end-to-end.** |
| Debug / Release APK | **Not included.** Build with the commands below (needs Android SDK + internet). |
| Real-device testing | **Not done.** Follow the checklist below. |

### Needs YOUR credentials/config (nothing is hardcoded)
| Feature | Required |
|---|---|
| OTP SMS (production) | Twilio Verify service (`TWILIO_ACCOUNT_SID/AUTH_TOKEN/VERIFY_SID`). Without it, OTP prints to server console in dev mode only. |
| Incoming-call push | Firebase project → `app/google-services.json` + service-account JSON on server |
| Reliable calls on mobile data | A TURN server (coturn) → `TURN_URLS`, `TURN_SECRET` |
| Calls to normal Pakistani numbers | Twilio Voice: API key, TwiML App, caller ID, and **your real per-minute price** (`PSTN_RATE_PER_MIN`) |
| Release APK | Your own signing keystore + `RELEASE_SERVER_URL` (HTTPS) |

### Not implemented (be aware before launching)
- **No wallet / top-up / billing.** PSTN calls are billed to *your* Twilio account. A per-user daily seconds cap
  (`PSTN_DAILY_SECONDS_PER_USER`) limits exposure, and the rate shown is whatever you configure. Add payments before opening PSTN to the public.
- PSTN is off unless `PSTN_ENABLED=true` and all Twilio vars are set; the app then says it is unavailable (it never shows a fake "free" claim for these calls).
- **Legal:** terminating VoIP traffic to Pakistani mobile networks is regulated by the PTA. Confirm your provider/route is licensed and caller-ID rules are met.
  Also confirm Twilio supports and prices the Pakistan mobile destinations you intend (Jazz, Zong, Ufone, Telenor are routed by number, not by separate integrations).
- No call-waiting/multiple calls, no ringback tone, no Bluetooth routing UI, Urdu font is the system font.
- Phone registration accepts Pakistani mobile numbers only (+92 3XX).

## 1. Backend
```bash
cd backend
cp .env.example .env        # fill in; JWT_SECRET: openssl rand -hex 32
npm install
npm start                   # http://localhost:3000   check: curl localhost:3000/health
```
Dev mode (no Twilio): the OTP code is printed in the server console. In `NODE_ENV=production` OTP requires Twilio Verify.

**Deploy:** any Linux VPS (Node 18+). Put it behind HTTPS/WSS (Caddy or nginx + Let's Encrypt), run with systemd or pm2,
set `NODE_ENV=production`, back up `data.db`. Example Caddy: `api.example.com { reverse_proxy localhost:3000 }` (WebSocket works automatically).
Use `/health` for monitoring. For multiple instances, replace the in-memory presence/queue with Redis.

**TURN (coturn):** `use-auth-secret`, `static-auth-secret=<TURN_SECRET>`, `realm=turn.example.com`, open UDP/TCP 3478 and 5349 (TLS) + relay port range.

**Twilio PSTN:** create an API Key, a TwiML App whose *Voice Request URL* is `https://api.example.com/pstn/twiml` (POST), buy/verify a caller ID, fill `.env`.

## 2. Android
1. Open the folder in **Android Studio (Koala+ / JDK 17)**; let it sync (it creates the Gradle wrapper).
2. Firebase: create project → add Android app `com.freepakistancall.app` → put `google-services.json` in `app/`.
3. `cp local.properties.example local.properties`; set `DEBUG_SERVER_URL` (emulator: `http://10.0.2.2:3000`; real phone on same Wi-Fi: `http://<PC-LAN-IP>:3000`).
4. Debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
5. Release APK:
   ```bash
   keytool -genkeypair -v -keystore release.keystore -alias fpc -keyalg RSA -keysize 2048 -validity 10000
   # set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, RELEASE_SERVER_URL (https://...) in local.properties
   ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
   ```
   Keep the keystore backed up and out of git. For Play Store use `./gradlew bundleRelease`.
6. Check the Twilio Voice SDK version in `app/build.gradle.kts` against Twilio's current docs.

## 3. Test checklist (use 2 real phones)
1. Register phone A and B (OTP from console in dev). Set names.
2. A: Contacts → type B's number (7+ digits) → call. B rings; accept. Verify two-way audio, mute, speaker, timer, end from either side.
3. B app swiped away / screen locked: call from A → push → full-screen incoming screen (needs FCM + notification permission).
4. Switch A from Wi-Fi to mobile data mid-call → "reconnecting"; ends cleanly if ICE fails. Test across different networks (requires TURN).
5. Airplane mode → friendly network error. Decline/no-answer (45 s) → correct status. Busy: call B while B is in a call.
6. Call history appears on both sides; favorites toggle persists.
7. Dial an unregistered number → paid-call dialog with your configured rate; with PSTN off → "not available".
8. Switch language Urdu↔English (layout flips RTL/LTR). Deny mic permission → clear message.
9. Release build: confirm HTTPS/WSS only (cleartext is enabled in debug only).

## Security notes
JWT (30 days) stored with AES-256 EncryptedSharedPreferences; OTP via Twilio Verify with rate limits; WebSocket auth via header; signaling payloads size-limited;
Twilio webhook signature validated in production; helmet + rate limiting on API; user search needs 7+ digits; call audio is peer-to-peer (SRTP/DTLS), never recorded by the server.
Publish a privacy policy before release (phone numbers, call metadata, mic, notifications).
