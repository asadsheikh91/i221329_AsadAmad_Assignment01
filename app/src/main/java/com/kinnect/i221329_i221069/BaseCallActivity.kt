package com.kinnect.i221329_i221069

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Everything the voice and the video call screens have in common: ringing, accepting,
// the timer, mute and hang up. The two subclasses only add what is special about their layout.
abstract class BaseCallActivity : AppCompatActivity(), WebRtcCall.Listener {

    protected abstract val isVideo: Boolean
    protected abstract val layoutRes: Int

    protected lateinit var call: WebRtcCall
    protected lateinit var peerUid: String
    protected lateinit var peerName: String
    protected lateinit var statusView: TextView
    protected lateinit var acceptButton: View

    protected val handler = Handler(Looper.getMainLooper())

    private var incoming = false
    private var started = false     // we sent the offer or the answer
    private var connected = false
    private var finishing = false
    private var muted = false
    private var connectedAt = 0L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            begin()
        } else {
            Toast.makeText(this, "Microphone and camera permission are needed for calls", Toast.LENGTH_LONG).show()
            endCall(if (incoming) "rejected" else "ended")
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val seconds = (System.currentTimeMillis() - connectedAt) / 1000
            statusView.text = String.format("%02d:%02d", seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }

    // The person did not pick up
    private val giveUp = Runnable {
        if (!connected) {
            endCall("missed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutRes)

        peerUid = intent.getStringExtra(EXTRA_PEER_UID) ?: run {
            finish()
            return
        }
        peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Kinnect user"
        incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: Backend.newCallId()

        Backend.activeCallId = callId
        call = WebRtcCall(this, callId, isVideo, !incoming, this)

        findViewById<TextView>(R.id.tvCallName).text = peerName
        findViewById<TextView>(R.id.tvCallInitials).apply {
            text = initialsOf(peerName)
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(avatarColorOf(peerUid)))
        }
        statusView = findViewById(R.id.tvCallStatus)
        acceptButton = findViewById(R.id.btnAccept)

        findViewById<View>(R.id.btnEndCall).setOnClickListener {
            // Hanging up before answering an incoming call is a decline
            endCall(if (incoming && !started) "rejected" else "ended")
        }
        findViewById<ImageView>(R.id.btnMute).setOnClickListener { view ->
            muted = !muted
            call.setMuted(muted)
            view.alpha = if (muted) 0.5f else 1f
        }

        onViewsReady()
        call.watch()

        if (incoming) {
            statusView.text = if (isVideo) "Incoming video call" else "Incoming voice call"
            acceptButton.visibility = View.VISIBLE
            acceptButton.setOnClickListener {
                acceptButton.visibility = hiddenVisibility()
                statusView.text = "Connecting..."
                requestPermissionsThenBegin()
            }
        } else {
            statusView.text = "Calling..."
            requestPermissionsThenBegin()
        }
    }

    /** Called once the views exist and before the call starts. */
    protected open fun onViewsReady() {}

    /** Called when the camera and microphone are about to be switched on (we dialled, or we accepted). */
    protected open fun onCallStarted() {}

    /** What the Accept button does to the layout once it has been used. */
    protected open fun hiddenVisibility(): Int = View.INVISIBLE

    private fun requestPermissionsThenBegin() {
        val permissions = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionLauncher.launch(permissions)
    }

    private fun begin() {
        if (started || finishing) {
            return
        }
        started = true
        onCallStarted()
        if (incoming) {
            call.accept()
        } else {
            call.startCall(peerUid, peerName, Backend.me?.name ?: "Kinnect user")
            handler.postDelayed(giveUp, RING_TIMEOUT_MS)
        }
    }

    // ------------------------------------------------------------------ WebRtcCall.Listener

    override fun onConnected() {
        if (connected) {
            return
        }
        connected = true
        connectedAt = System.currentTimeMillis()
        handler.removeCallbacks(giveUp)
        handler.post(tick)
    }

    override fun onRemoteVideo() {}

    // The other person hung up, declined, or the connection broke
    override fun onFinished(status: String) {
        if (finishing) {
            return
        }
        finishing = true
        handler.removeCallbacks(tick)
        handler.removeCallbacks(giveUp)
        statusView.text = when (status) {
            "rejected" -> "Call declined"
            "missed" -> "No answer"
            "busy" -> "$peerName is busy"
            "failed" -> "Connection lost"
            else -> "Call ended"
        }
        call.release()
        handler.postDelayed({ finish() }, 1200)
    }

    private fun endCall(status: String) {
        if (finishing) {
            return
        }
        finishing = true
        call.hangUp(status)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Leaving the screen (back button, swipe away) ends the call
        if (::call.isInitialized) {
            if (!finishing) {
                call.hangUp(if (incoming && !started) "rejected" else "ended")
            }
            call.release()
        }
        Backend.activeCallId = null
    }

    companion object {
        private const val RING_TIMEOUT_MS = 45_000L
    }
}
