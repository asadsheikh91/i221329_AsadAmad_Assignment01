package com.kinnect.i221329_i221069

import android.widget.ImageView

// Screen 22: voice call. Green button answers, red button hangs up, mute and speaker work during the call.
class VoiceCallActivity : BaseCallActivity() {

    override val isVideo = false
    override val layoutRes = R.layout.fragment_call

    private var speakerOn = false

    override fun onViewsReady() {
        findViewById<ImageView>(R.id.btnSpeaker).setOnClickListener { view ->
            speakerOn = !speakerOn
            call.setSpeaker(speakerOn)
            view.alpha = if (speakerOn) 0.5f else 1f
        }
    }
}
