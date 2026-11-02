package com.kinnect.i221329_i221069

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

// One voice or video call, built on the Agora SDK.
//
// Two things happen at once:
//   1. Signalling (who is calling whom) goes through the database node calls/{callId}: the caller writes it with
//      status "ringing", the callee's phone sees it (CallWatcher), and the status then changes to accepted / ended / ...
//   2. The sound and the pictures go through Agora. Both phones join the Agora channel named after the call id;
//      Agora connects them. We never touch the audio or video data ourselves.
//
// The Agora App ID comes from local.properties (AGORA_APP_ID=...), see FIREBASE_CHECKLIST.md.
class AgoraCall(
    context: Context,
    val callId: String,
    private val video: Boolean,
    private val isCaller: Boolean,
    private val listener: Listener
) {

    interface Listener {
        /** The other person joined: audio/video is flowing between the phones. */
        fun onConnected()

        /** The first picture from the other person arrived (video calls only). */
        fun onRemoteVideo()

        /** The call is over; [status] is ended, rejected, missed, busy or failed. */
        fun onFinished(status: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val callRef = Backend.ref("calls/$callId")

    private var engine: RtcEngine? = null
    private var statusWatch: ListenerRegistration? = null
    private var localView: SurfaceView? = null
    private var remoteView: SurfaceView? = null

    private var peerUid = ""
    private var peerName = ""
    private var connectedAt = 0L
    private var finished = false
    private var logged = false

    /** The two video pictures (our own camera and the other person). Video calls only. */
    fun setViews(local: SurfaceView, remote: SurfaceView) {
        localView = local
        remoteView = remote
    }

    /** Who is on the other end (used for the call history). */
    fun setPeer(uid: String, name: String) {
        peerUid = uid
        peerName = name
    }

    /** Starts watching the call node: when the other side ends or declines, [Listener.onFinished] is called. */
    fun watch() {
        statusWatch = Backend.listen(callRef) { snap ->
            when (val status = snap.text("status")) {
                "ended", "rejected", "missed", "busy" -> main.post { finish(status) }
            }
        }
    }

    /** The caller: writes the call (the callee's phone starts ringing) and joins the channel. */
    fun startCall(peerUid: String, peerName: String, myName: String) {
        setPeer(peerUid, peerName)
        val uid = Backend.myUid ?: return
        val call = hashMapOf<String, Any?>(
            "callerId" to uid,
            "callerName" to myName,
            "calleeId" to peerUid,
            "calleeName" to peerName,
            "video" to video,
            "status" to "ringing",
            "createdAtMs" to System.currentTimeMillis()
        )
        callRef.setValue(call)
        join()
    }

    /** The callee: answers and joins the channel. */
    fun accept() {
        callRef.child("status").setValue("accepted")
        join()
    }

    /** One of us ends the call; the status tells the other phone how. */
    fun hangUp(status: String) {
        if (finished) {
            return
        }
        finished = true
        callRef.child("status").setValue(status)
        writeLog(status)
        leave()
    }

    /** Stops watching and frees the Agora engine. */
    fun release() {
        statusWatch?.remove()
        statusWatch = null
        leave()
    }

    // ------------------------------------------------------------------ controls

    fun setMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun setSpeaker(on: Boolean) {
        engine?.setEnableSpeakerphone(on)
    }

    fun setCameraEnabled(on: Boolean) {
        engine?.muteLocalVideoStream(!on)
        engine?.enableLocalVideo(on)
    }

    fun switchCamera() {
        engine?.switchCamera()
    }

    // ------------------------------------------------------------------ Agora

    private val events = object : IRtcEngineEventHandler() {
        // The other person is in the channel
        override fun onUserJoined(uid: Int, elapsed: Int) {
            main.post {
                if (video) {
                    showRemote(uid)
                }
                if (connectedAt == 0L) {
                    connectedAt = System.currentTimeMillis()
                    listener.onConnected()
                }
            }
        }

        // The first video picture of the other person arrived
        override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
            main.post {
                showRemote(uid)
                listener.onRemoteVideo()
            }
        }

        // The other person left the channel (hung up or lost the connection)
        override fun onUserOffline(uid: Int, reason: Int) {
            main.post { finish("ended") }
        }

        override fun onConnectionLost() {
            Log.w(TAG, "Agora connection lost")
        }

        override fun onError(err: Int) {
            Log.w(TAG, "Agora error $err")
        }
    }

    /** Creates the Agora engine and joins the channel named after the call. */
    private fun join() {
        if (engine != null) {
            return
        }
        if (BuildConfig.AGORA_APP_ID.isEmpty()) {
            Log.e(TAG, "AGORA_APP_ID is missing in local.properties")
            main.post { finish("failed") }
            return
        }
        try {
            val config = RtcEngineConfig()
            config.mContext = appContext
            config.mAppId = BuildConfig.AGORA_APP_ID
            config.mEventHandler = events
            val rtc = RtcEngine.create(config)
            engine = rtc

            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.setEnableSpeakerphone(video)
            if (video) {
                rtc.enableVideo()
                val local = localView
                if (local != null) {
                    local.setZOrderMediaOverlay(true)
                    rtc.setupLocalVideo(VideoCanvas(local, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                }
                rtc.startPreview()
            }

            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.publishMicrophoneTrack = true
            options.publishCameraTrack = video
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = video
            // No token: the Agora project must be in "App ID" (testing) mode, see FIREBASE_CHECKLIST.md
            rtc.joinChannel(null, callId, 0, options)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start Agora: " + e.message)
            main.post { finish("failed") }
        }
    }

    private fun showRemote(uid: Int) {
        val remote = remoteView ?: return
        engine?.setupRemoteVideo(VideoCanvas(remote, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    private fun leave() {
        val rtc = engine ?: return
        engine = null
        try {
            rtc.leaveChannel()
        } catch (e: Exception) {
            Log.w(TAG, "leaveChannel: " + e.message)
        }
        // Destroying the engine takes a moment, so it is done off the main thread
        Thread { RtcEngine.destroy() }.start()
    }

    private fun finish(status: String) {
        if (finished) {
            return
        }
        finished = true
        writeLog(status)
        listener.onFinished(status)
    }

    /** Remembers this call in my call history (once). */
    private fun writeLog(status: String) {
        if (logged || peerUid.isEmpty()) {
            return
        }
        logged = true
        val seconds = if (connectedAt > 0) (System.currentTimeMillis() - connectedAt) / 1000 else 0L
        val shown = when {
            connectedAt > 0 -> "connected"
            status == "rejected" -> "declined"
            status == "busy" -> "busy"
            isCaller -> "no answer"
            else -> "missed"
        }
        Backend.writeCallLog(callId, peerUid, peerName, video, isCaller, shown, seconds)
        // The caller leaves a line in the chat, like a normal messenger
        if (isCaller) {
            val kind = if (video) "Video call" else "Voice call"
            val line = if (connectedAt > 0) String.format("%s · %d:%02d", kind, seconds / 60, seconds % 60) else "$kind · $shown"
            Backend.sendSystemMessage(peerUid, line)
        }
    }

    companion object {
        private const val TAG = "AgoraCall"
    }
}
