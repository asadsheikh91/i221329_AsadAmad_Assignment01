package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 22: voice call. The red button hangs up and returns to the chat.
class VoiceCallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_call)

        linkBack(this, R.id.btnEndCall)
    }
}
