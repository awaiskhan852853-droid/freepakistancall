package com.freepakistancall.app.call

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import com.freepakistancall.app.data.Api
import com.freepakistancall.app.data.ApiException
import com.freepakistancall.app.data.Prefs
import com.freepakistancall.app.service.CallService
import com.freepakistancall.app.service.Notifs
import com.twilio.voice.Call as TwilioCall
import com.twilio.voice.CallException
import com.twilio.voice.ConnectOptions
import com.twilio.voice.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

enum class CallPhase { Idle, Outgoing, Incoming, Connecting, Active, Ended }

data class CallUi(
    val phase: CallPhase = CallPhase.Idle,
    val callId: String = "",
    val peerId: Long = 0,
    val peerName: String = "",
    val peerPhone: String = "",
    val outgoing: Boolean = false,
    val isPstn: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val reconnecting: Boolean = false,
    val durationSec: Int = 0,
    val endReason: String? = null,
)

/** Owns signaling, the WebRTC session, PSTN session and call state. Single call at a time. */
object CallManager {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = MutableStateFlow(CallUi())

    private var signaling: Signaling? = null
    private var rtc: RtcClient? = null
    private var pstn: TwilioCall? = null
    private var pendingOffer: String? = null
    private val bufferedIce = mutableListOf<IceCandidate>()
    private var ticker: Job? = null
    private var timeout: Job? = null
    private var reconnectJob: Job? = null
    private var ringtone: Ringtone? = null
    private var startedAt = 0L
    private var inited = false

    fun init(ctx: Context) { if (!inited) { inited = true; app = ctx.applicationContext } }

    // ---------------- connection ----------------
    fun connect() {
        val token = Prefs.token ?: return
        if (signaling?.isOpen == true) return
        signaling?.close()
        signaling = Signaling({ m -> scope.launch { onSignal(m) } }, { up -> if (!up) scheduleReconnect() })
            .also { it.connect(token) }
    }
    private fun scheduleReconnect() {
        if (Prefs.token == null) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch { delay(3000); connect() }
    }
    fun disconnect() { reconnectJob?.cancel(); signaling?.close(); signaling = null }

    // ---------------- app-to-app: outgoing ----------------
    fun startCall(peerId: Long, name: String, phone: String) {
        if (state.value.phase != CallPhase.Idle) return
        if (signaling?.isOpen != true) { connect(); failFast("network"); return }
        val cid = UUID.randomUUID().toString()
        state.value = CallUi(CallPhase.Outgoing, cid, peerId, name, phone, outgoing = true)
        audioStart()
        scope.launch {
            try {
                val client = newRtc(cid, peerId); rtc = client
                val sdp = client.createOffer()
                signaling?.send(JSONObject().put("type", "offer").put("callId", cid).put("to", peerId).put("sdp", sdp))
                timeout = launch { delay(45_000); endCall("no_answer") }
            } catch (e: Exception) { endCall(if (e is ApiException && e.code == 0) "network" else "error") }
        }
    }

    // ---------------- app-to-app: incoming ----------------
    private fun onOffer(m: JSONObject) {
        val cid = m.getString("callId")
        val s = state.value
        if (cid == s.callId) return
        if (s.phase != CallPhase.Idle) {
            signaling?.send(JSONObject().put("type", "reject").put("callId", cid).put("to", m.getLong("from"))); return
        }
        pendingOffer = m.getString("sdp")
        state.value = CallUi(CallPhase.Incoming, cid, m.getLong("from"), m.optString("fromName"), m.optString("fromPhone"))
        startRing()
        Notifs.incoming(app, m.optString("fromName").ifEmpty { m.optString("fromPhone") })
        timeout = scope.launch { delay(45_000); endCall("missed") }
    }

    fun accept() {
        val s = state.value
        if (s.phase != CallPhase.Incoming) return
        stopRing(); Notifs.cancelIncoming(app); timeout?.cancel()
        setPhase(CallPhase.Connecting); audioStart()
        scope.launch {
            try {
                val c = newRtc(s.callId, s.peerId); rtc = c
                c.setRemote(SessionDescription.Type.OFFER, pendingOffer!!)
                bufferedIce.forEach { c.addIce(it) }; bufferedIce.clear()
                val sdp = c.createAnswer()
                signaling?.send(JSONObject().put("type", "answer").put("callId", s.callId).put("to", s.peerId).put("sdp", sdp))
                CallService.start(app)
            } catch (e: Exception) { endCall(if (e is ApiException && e.code == 0) "network" else "error") }
        }
    }
    fun reject() { if (state.value.phase == CallPhase.Incoming) endCall("rejected") }
    fun hangup() { endCall("hangup") }

    // ---------------- PSTN (regular Pakistani numbers) ----------------
    fun startPstn(e164: String, name: String) {
        if (state.value.phase != CallPhase.Idle) return
        state.value = CallUi(CallPhase.Outgoing, UUID.randomUUID().toString(), 0, name, e164, outgoing = true, isPstn = true)
        audioStart()
        scope.launch {
            try {
                val tok = Api.req("GET", "/pstn/token").getString("token")
                val opts = ConnectOptions.Builder(tok).params(hashMapOf("To" to e164)).build()
                pstn = Voice.connect(app, opts, pstnListener)
            } catch (e: Exception) {
                endCall(when { e is ApiException && e.code == 429 -> "limit"; e is ApiException && e.code == 0 -> "network"; else -> "error" })
            }
        }
    }
    private val pstnListener = object : TwilioCall.Listener {
        override fun onRinging(call: TwilioCall) {}
        override fun onConnectFailure(call: TwilioCall, e: CallException) { scope.launch { endCall("error") } }
        override fun onConnected(call: TwilioCall) { scope.launch { markActive() } }
        override fun onReconnecting(call: TwilioCall, e: CallException) { scope.launch { state.value = state.value.copy(reconnecting = true) } }
        override fun onReconnected(call: TwilioCall) { scope.launch { state.value = state.value.copy(reconnecting = false) } }
        override fun onDisconnected(call: TwilioCall, e: CallException?) { scope.launch { endCall(if (e != null) "network" else "hangup", false) } }
    }

    // ---------------- in-call controls ----------------
    fun toggleMute() {
        val m = !state.value.muted
        rtc?.setMute(m); pstn?.mute(m)
        state.value = state.value.copy(muted = m)
    }
    fun toggleSpeaker() {
        val v = !state.value.speaker
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = v
        state.value = state.value.copy(speaker = v)
    }

    // ---------------- internals ----------------
    private fun onSignal(m: JSONObject) {
        val cid = m.optString("callId")
        when (val type = m.optString("type")) {
            "offer" -> onOffer(m)
            "answer" -> if (cid == state.value.callId && !state.value.isPstn) scope.launch {
                try { timeout?.cancel(); rtc?.setRemote(SessionDescription.Type.ANSWER, m.getString("sdp")); setPhase(CallPhase.Connecting) }
                catch (_: Exception) { endCall("error") }
            }
            "ice" -> if (cid == state.value.callId) {
                val c = m.getJSONObject("candidate")
                val ice = IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.getString("candidate"))
                rtc?.addIce(ice) ?: bufferedIce.add(ice)
            }
            "hangup", "reject", "unavailable" -> if (cid == state.value.callId)
                endCall(when (type) { "reject" -> if (state.value.outgoing) "busy" else "rejected"; "unavailable" -> "unavailable"; else -> "hangup" }, false)
        }
    }

    private suspend fun newRtc(cid: String, peerId: Long): RtcClient {
        val cfg = Api.req("GET", "/rtc/config").getJSONArray("iceServers")
        val servers = (0 until cfg.length()).map {
            val o = cfg.getJSONObject(it)
            val urls = o.getJSONArray("urls").let { a -> (0 until a.length()).map { i -> a.getString(i) } }
            PeerConnection.IceServer.builder(urls).apply {
                if (o.has("username")) setUsername(o.getString("username")).setPassword(o.getString("credential"))
            }.createIceServer()
        }
        return RtcClient(app, servers,
            onIce = { c ->
                signaling?.send(JSONObject().put("type", "ice").put("callId", cid).put("to", peerId)
                    .put("candidate", JSONObject().put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex).put("candidate", c.sdp)))
            },
            onIceState = { st -> scope.launch { onIceState(st) } })
    }

    private fun onIceState(st: PeerConnection.IceConnectionState) {
        when (st) {
            PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> markActive()
            PeerConnection.IceConnectionState.DISCONNECTED -> if (state.value.phase == CallPhase.Active) state.value = state.value.copy(reconnecting = true)
            PeerConnection.IceConnectionState.FAILED -> endCall("network")
            else -> {}
        }
    }

    private fun markActive() {
        val s = state.value
        if (s.phase == CallPhase.Ended || s.phase == CallPhase.Idle) return
        if (s.phase != CallPhase.Active) {
            startedAt = System.currentTimeMillis(); timeout?.cancel()
            state.value = s.copy(phase = CallPhase.Active, reconnecting = false)
            if (!s.isPstn) CallService.start(app) else CallService.start(app)
            ticker?.cancel()
            ticker = scope.launch { while (true) { delay(1000); state.value = state.value.copy(durationSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()) } }
        } else state.value = s.copy(reconnecting = false)
    }

    private fun endCall(reason: String, sendSignal: Boolean = true) {
        val s = state.value
        if (s.phase == CallPhase.Idle || s.phase == CallPhase.Ended) return
        if (sendSignal && !s.isPstn && s.peerId != 0L)
            signaling?.send(JSONObject().put("type", if (s.phase == CallPhase.Incoming) "reject" else "hangup").put("callId", s.callId).put("to", s.peerId))
        ticker?.cancel(); timeout?.cancel(); stopRing(); Notifs.cancelIncoming(app)
        val dur = if (startedAt > 0) ((System.currentTimeMillis() - startedAt) / 1000).toInt() else 0
        val p = pstn; pstn = null; rtc?.close(); rtc = null
        pendingOffer = null; bufferedIce.clear(); startedAt = 0
        state.value = s.copy(phase = CallPhase.Ended, endReason = if (reason == "hangup") null else reason, durationSec = dur)
        p?.disconnect()
        audioStop(); CallService.stop(app)
        logCall(s, reason, dur)
        scope.launch { delay(1800); if (state.value.phase == CallPhase.Ended) state.value = CallUi() }
    }

    private fun failFast(reason: String) {
        state.value = CallUi(CallPhase.Ended, endReason = reason)
        scope.launch { delay(1800); state.value = CallUi() }
    }

    private fun logCall(s: CallUi, reason: String, dur: Int) {
        if (Prefs.token == null) return
        val status = when { dur > 0 -> "completed"; reason == "missed" -> "missed"; else -> reason }
        scope.launch {
            runCatching {
                Api.req("POST", "/calls", JSONObject().put("peerName", s.peerName).put("peerPhone", s.peerPhone)
                    .put("direction", if (s.outgoing) "outgoing" else "incoming").put("type", if (s.isPstn) "pstn" else "app")
                    .put("status", status).put("duration", dur))
            }
        }
    }

    private fun setPhase(p: CallPhase) { state.value = state.value.copy(phase = p) }
    private fun audioStart() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION; am.isSpeakerphoneOn = false
    }
    private fun audioStop() {
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = false; am.mode = AudioManager.MODE_NORMAL
    }
    private fun startRing() {
        try { ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)).also { it.play() } } catch (_: Exception) {}
    }
    private fun stopRing() { try { ringtone?.stop() } catch (_: Exception) {}; ringtone = null }
}
