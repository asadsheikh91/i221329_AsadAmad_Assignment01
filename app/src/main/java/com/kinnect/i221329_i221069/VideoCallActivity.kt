package com.kinnect.i221329_i221069

import android.view.SurfaceView
import android.view.View
import android.widget.ImageView

// Video call: the other person fills the screen, our own camera is the small picture in the corner.
// Agora draws both pictures into ordinary SurfaceViews.
class VideoCallActivity : BaseCallActivity() {

    override val isVideo = true
    override val layoutRes = R.layout.activity_video_call

    private lateinit var localView: SurfaceView
    private lateinit var remoteView: SurfaceView
    private var cameraOn = true

    override fun onViewsReady() {
        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)
        // The small picture sits on top of everything, including the avatar shown while we wait
        localView.setZOrderMediaOverlay(true)
        localView.visibility = View.INVISIBLE
        call.setViews(localView, remoteView)

        findViewById<View>(R.id.btnSwitchCamera).setOnClickListener { call.switchCamera() }
        findViewById<ImageView>(R.id.btnCamera).setOnClickListener { view ->
            cameraOn = !cameraOn
            call.setCameraEnabled(cameraOn)
            view.alpha = if (cameraOn) 1f else 0.5f
            localView.visibility = if (cameraOn) View.VISIBLE else View.INVISIBLE
        }
    }

    // On the video screen the Accept button takes no space once it has been used
    override fun hiddenVisibility(): Int = View.GONE

    // Our own picture appears as soon as the camera starts
    override fun onCallStarted() {
        localView.visibility = if (cameraOn) View.VISIBLE else View.INVISIBLE
    }

    // Keep the avatar up until there really is a picture to show
    override fun onRemoteVideo() {
        findViewById<View>(R.id.placeholder).visibility = View.GONE
    }
}
