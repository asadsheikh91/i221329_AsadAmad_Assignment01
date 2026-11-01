package com.asadamad.i221329

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

// One voice or video call, peer to peer with WebRTC.
//
// WebRTC sends the audio and video straight between the two phones, but the phones first have to
// swap a few messages to find each other. That "signaling" goes through one Firestore document:
//
//   calls/{callId}                   the caller writes `offer`, the callee answers with `answer`,
//                                    `status` goes ringing -> accepted -> ended (or rejected / missed / busy)
//   calls/{callId}/callerCandidates  network addresses (ICE candidates) found by the caller
//   calls/{callId}/calleeCandidates  network addresses found by the callee
class WebRtcCall(
    context: Context,
    val callId: String,
    private val video: Boolean,
    private val isCaller: Boolean,
    private val listener: Listener
) {

    interface Listener {
        /** Audio/video started flowing between the phones. */
        fun onConnected()

        /** The first picture from the other person arrived (video calls only). */
        fun onRemoteVideo()

        /** The call is over; [status] is ended, rejected, missed, busy or failed. */
        fun onFinished(status: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val callRef = Backend.callDoc(callId)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var egl: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var captureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var statusListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null

    private val pendingRemote = ArrayList<IceCandidate>()
    private val pendingLocal = ArrayList<IceCandidate>()
    private var remoteDescriptionSet = false
    private var signalingReady = false
    private var finished = false
    private var released = false

    /** Needed to initialise the video views before the call starts. */
    fun eglContext(): EglBase.Context {
        val e = egl ?: EglBase.create().also { egl = it }
        return e.eglBaseContext
    }

    fun setRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
    }

    // ------------------------------------------------------------------ start

    /**
     * Follow the call document so we notice when the other side hangs up.
     * The caller starts following only once the document exists (see publishOffer).
     */
    fun watch() {
        if (!isCaller) {
            startWatching()
        }
    }

    private fun startWatching() {
        statusListener = callRef.addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists() || released) {
                return@addSnapshotListener
            }
            val status = snap.getString("status") ?: ""
            if (status in FINISHED_STATES) {
                finish(status)
                return@addSnapshotListener
            }
            // The caller picks the answer up from the same document
            if (isCaller && !remoteDescriptionSet) {
                val answer = snap.get("answer") as? Map<*, *>
                val sdp = answer?.get("sdp") as? String
                if (sdp != null) {
                    applyRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
            }
        }
    }

    /** Caller: build the offer and publish the call so the other phone starts ringing. */
    fun startCall(peerUid: String, peerName: String, myName: String) {
        val pc = prepare() ?: return
        pc.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(offer: SessionDescription) {
                pc.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        main.post { publishOffer(offer, peerUid, peerName, myName) }
                    }

                    override fun onSetFailure(error: String?) {
                        main.post { finish("failed") }
                    }
                }, offer)
            }

            override fun onCreateFailure(error: String?) {
                main.post { finish("failed") }
            }
        }, MediaConstraints())
    }

    private fun publishOffer(offer: SessionDescription, peerUid: String, peerName: String, myName: String) {
        if (released) {
            return
        }
        val doc = hashMapOf(
            "callerId" to Backend.myUid,
            "callerName" to myName,
            "calleeId" to peerUid,
            "calleeName" to peerName,
            "video" to video,
            "status" to "ringing",
            "offer" to mapOf("type" to "offer", "sdp" to offer.description),
            "createdAtMs" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        callRef.set(doc)
            .addOnSuccessListener {
                if (released) {
                    // Hung up while the call was still being published: make sure it does not ring
                    callRef.update("status", "ended")
                    return@addOnSuccessListener
                }
                signalingReady = true
                flushLocalCandidates()
                startWatching()
                listenForCandidates("calleeCandidates")
            }
            .addOnFailureListener { finish("failed") }
    }

    /** Callee: read the offer, answer it and flip the call to "accepted". */
    fun accept() {
        val pc = prepare() ?: return
        // The call document already exists, so our candidates can be uploaded straight away
        signalingReady = true
        callRef.get()
            .addOnSuccessListener { snap ->
                val sdp = (snap.get("offer") as? Map<*, *>)?.get("sdp") as? String
                if (sdp == null || released) {
                    finish("failed")
                    return@addOnSuccessListener
                }
                applyRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) {
                    listenForCandidates("callerCandidates")
                    answer(pc)
                }
            }
            .addOnFailureListener { finish("failed") }
    }

    private fun answer(pc: PeerConnection) {
        pc.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() {
                        main.post {
                            callRef.update(
                                mapOf(
                                    "answer" to mapOf("type" to "answer", "sdp" to answer.description),
                                    "status" to "accepted"
                                )
                            )
                        }
                    }
                }, answer)
            }

            override fun onCreateFailure(error: String?) {
                main.post { finish("failed") }
            }
        }, MediaConstraints())
    }

    // ------------------------------------------------------------------ signaling helpers

    private fun applyRemote(description: SessionDescription, then: (() -> Unit)? = null) {
        val pc = peer ?: return
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                main.post {
                    if (released) {
                        return@post
                    }
                    remoteDescriptionSet = true
                    for (candidate in pendingRemote) {
                        pc.addIceCandidate(candidate)
                    }
                    pendingRemote.clear()
                    then?.invoke()
                }
            }

            override fun onSetFailure(error: String?) {
                main.post { finish("failed") }
            }
        }, description)
    }

    private fun listenForCandidates(collection: String) {
        candidateListener = callRef.collection(collection).addSnapshotListener { snap, _ ->
            if (snap == null || released) {
                return@addSnapshotListener
            }
            for (change in snap.documentChanges) {
                if (change.type != DocumentChange.Type.ADDED) {
                    continue
                }
                val data = change.document.data
                val candidate = IceCandidate(
                    data["sdpMid"] as? String,
                    (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                    data["sdp"] as? String ?: continue
                )
                // A candidate is only usable once the other side's description is set
                if (remoteDescriptionSet) {
                    peer?.addIceCandidate(candidate)
                } else {
                    pendingRemote.add(candidate)
                }
            }
        }
    }

    private fun uploadCandidate(candidate: IceCandidate) {
        val collection = if (isCaller) "callerCandidates" else "calleeCandidates"
        callRef.collection(collection).add(
            mapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "sdp" to candidate.sdp
            )
        )
    }

    private fun flushLocalCandidates() {
        for (candidate in pendingLocal) {
            uploadCandidate(candidate)
        }
        pendingLocal.clear()
    }

    // ------------------------------------------------------------------ WebRTC set-up

    private fun prepare(): PeerConnection? {
        if (peer != null) {
            return peer
        }
        if (!initialised) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
            )
            initialised = true
        }

        val eglContext = eglContext()
        val audioModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
        audioModule.release()
        factory = f

        val config = PeerConnection.RTCConfiguration(ICE_SERVERS)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        val pc = f.createPeerConnection(config, PeerObserver()) ?: run {
            finish("failed")
            return null
        }
        peer = pc

        // Microphone
        val audioSource = f.createAudioSource(MediaConstraints())
        val audio = f.createAudioTrack("audio0", audioSource)
        audioTrack = audio
        pc.addTrack(audio, listOf(STREAM_ID))

        // Camera
        if (video) {
            startCamera(f, pc, eglContext)
        }

        // Route the sound through the earpiece for voice calls and the speaker for video calls
        @Suppress("DEPRECATION")
        run {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = video
        }
        return pc
    }

    private fun startCamera(f: PeerConnectionFactory, pc: PeerConnection, eglContext: EglBase.Context) {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull() ?: return
        val cam = enumerator.createCapturer(name, null) ?: return
        capturer = cam

        val helper = SurfaceTextureHelper.create("CaptureThread", eglContext)
        captureHelper = helper
        val source = f.createVideoSource(false)
        videoSource = source
        cam.initialize(helper, appContext, source.capturerObserver)
        cam.startCapture(640, 480, 30)

        val track = f.createVideoTrack("video0", source)
        videoTrack = track
        localRenderer?.let { track.addSink(it) }
        pc.addTrack(track, listOf(STREAM_ID))
    }

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            main.post {
                if (released) {
                    return@post
                }
                if (signalingReady) {
                    uploadCandidate(candidate)
                } else {
                    pendingLocal.add(candidate)
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                main.post {
                    if (released) {
                        return@post
                    }
                    remoteVideoTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                    listener.onRemoteVideo()
                }
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            main.post {
                if (released) {
                    return@post
                }
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> listener.onConnected()
                    PeerConnection.IceConnectionState.FAILED -> {
                        // Tell the other phone too, then close our side
                        callRef.update("status", "ended")
                        finish("failed")
                    }
                    else -> {}
                }
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }

    // ------------------------------------------------------------------ controls

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        capturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                main.post { localRenderer?.setMirror(isFrontCamera) }
            }

            override fun onCameraSwitchError(error: String?) {}
        })
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    /** Our side ends the call: tell the other phone and free everything. */
    fun hangUp(status: String) {
        if (!finished) {
            finished = true
            // update() fails harmlessly if the call document was never created (caller cancelled early)
            callRef.update("status", status)
        }
        release()
    }

    private fun finish(status: String) {
        if (finished) {
            return
        }
        finished = true
        main.post { listener.onFinished(status) }
    }

    /** Stops the camera and microphone and closes the connection. Safe to call twice. */
    fun release() {
        if (released) {
            return
        }
        released = true
        statusListener?.remove()
        candidateListener?.remove()

        localRenderer?.let { videoTrack?.removeSink(it) }
        remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        try {
            capturer?.stopCapture()
        } catch (_: InterruptedException) {
        }
        capturer?.dispose()
        peer?.close()
        peer?.dispose()
        videoSource?.dispose()
        captureHelper?.dispose()
        factory?.dispose()
        egl?.release()
        peer = null
        factory = null
        egl = null

        @Suppress("DEPRECATION")
        run {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
    }

    /** The WebRTC callbacks we do not care about have empty bodies. */
    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    companion object {
        private const val STREAM_ID = "kinnect_stream"
        private val FINISHED_STATES = setOf("ended", "rejected", "missed", "busy")
        private var initialised = false

        // Google's free STUN servers let the phones find their public addresses.
        // Calls between phones on strict mobile networks need a TURN relay as well: add one here
        // (PeerConnection.IceServer.builder("turn:host:3478").setUsername(..).setPassword(..)).
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }
}
