package com.asadamad.i221329

import android.view.View
import android.widget.ImageView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

// Video call: the other person fills the screen, our own camera is the small picture in the corner.
class VideoCallActivity : BaseCallActivity() {

    override val isVideo = true
    override val layoutRes = R.layout.activity_video_call

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private var cameraOn = true

    override fun onViewsReady() {
        localView = findViewById(R.id.localView)
        remoteView = findViewById(R.id.remoteView)

        // The video views draw with the same graphics context WebRTC decodes into
        val egl = call.eglContext()
        remoteView.init(egl, object : RendererCommon.RendererEvents {
            // Keep the avatar up until there really is a picture to show
            override fun onFirstFrameRendered() {
                runOnUiThread { findViewById<View>(R.id.placeholder).visibility = View.GONE }
            }

            override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
        })
        remoteView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        localView.init(egl, null)
        localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        localView.setMirror(true)
        // On top of everything, including the avatar shown while we wait for the other side
        localView.setZOrderOnTop(true)
        localView.visibility = View.INVISIBLE
        call.setRenderers(localView, remoteView)

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

    override fun onDestroy() {
        super.onDestroy()
        // The call is released in super.onDestroy(), so the views can be freed now
        if (::localView.isInitialized) {
            localView.release()
            remoteView.release()
        }
    }
}
