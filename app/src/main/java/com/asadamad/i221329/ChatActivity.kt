package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 21: one conversation. The phone icon starts a voice call.
class ChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_chat_conversation)

        linkBack(this, R.id.btnBack)

        // Messages from Aisha
        bindMessage(findViewById(R.id.msgHey), "Hey! Are you still coming Saturday?")
        bindMessage(findViewById(R.id.msgTable), "Lina booked the table for 8")
        bindMessage(findViewById(R.id.msgBring), "Bring the camera")

        // Our replies
        bindMessage(findViewById(R.id.msgYes), "Yes, obviously")
        bindMessage(findViewById(R.id.msgBatteries), "Already charged both batteries")
        bindMessage(findViewById(R.id.msgPerfect), "Perfect, I'll be there by 7:45")
        linkTo(this, R.id.btnCall, VoiceCallActivity::class.java)
    }
}
