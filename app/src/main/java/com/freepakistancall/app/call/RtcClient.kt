package com.freepakistancall.app.call

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private open class Sdp : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}

/** One WebRTC audio peer connection. */
class RtcClient(
    ctx: Context,
    iceServers: List<PeerConnection.IceServer>,
    private val onIce: (IceCandidate) -> Unit,
    private val onIceState: (PeerConnection.IceConnectionState) -> Unit,
) {
    private val factory: PeerConnectionFactory
    private val source: AudioSource
    private val track: AudioTrack
    private val pc: PeerConnection
    private var remoteSet = false
    private val queued = mutableListOf<IceCandidate>()

    init {
        initOnce(ctx.applicationContext)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(ctx.applicationContext).createAudioDeviceModule())
            .createPeerConnectionFactory()
        source = factory.createAudioSource(MediaConstraints())
        track = factory.createAudioTrack("audio0", source)
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        pc = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) = onIce(c)
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) = onIceState(s)
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
            override fun onAddStream(s: MediaStream) {}
            override fun onRemoveStream(s: MediaStream) {}
            override fun onDataChannel(d: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
        })!!
        pc.addTrack(track, listOf("stream0"))
    }

    suspend fun createOffer(): String = suspendCoroutine { k ->
        pc.createOffer(object : Sdp() {
            override fun onCreateSuccess(d: SessionDescription) {
                pc.setLocalDescription(object : Sdp() {
                    override fun onSetSuccess() = k.resume(d.description)
                    override fun onSetFailure(e: String?) = k.resumeWithException(Exception(e))
                }, d)
            }
            override fun onCreateFailure(e: String?) = k.resumeWithException(Exception(e))
        }, MediaConstraints())
    }

    suspend fun createAnswer(): String = suspendCoroutine { k ->
        pc.createAnswer(object : Sdp() {
            override fun onCreateSuccess(d: SessionDescription) {
                pc.setLocalDescription(object : Sdp() {
                    override fun onSetSuccess() = k.resume(d.description)
                    override fun onSetFailure(e: String?) = k.resumeWithException(Exception(e))
                }, d)
            }
            override fun onCreateFailure(e: String?) = k.resumeWithException(Exception(e))
        }, MediaConstraints())
    }

    suspend fun setRemote(type: SessionDescription.Type, sdp: String) = suspendCoroutine<Unit> { k ->
        pc.setRemoteDescription(object : Sdp() {
            override fun onSetSuccess() { remoteSet = true; queued.forEach { pc.addIceCandidate(it) }; queued.clear(); k.resume(Unit) }
            override fun onSetFailure(e: String?) = k.resumeWithException(Exception(e))
        }, SessionDescription(type, sdp))
    }

    fun addIce(c: IceCandidate) { if (remoteSet) pc.addIceCandidate(c) else queued.add(c) }
    fun setMute(m: Boolean) { track.setEnabled(!m) }
    fun close() {
        try { pc.close(); pc.dispose(); source.dispose(); factory.dispose() } catch (_: Exception) {}
    }

    companion object {
        private var inited = false
        private fun initOnce(c: Context) {
            if (inited) return
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c).createInitializationOptions())
            inited = true
        }
    }
}
