package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 20: list of conversations.
class ChatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_chats)

        linkBack(this, R.id.btnBack)

        bindChat(findViewById(R.id.chatLina), "LM", R.color.avatar_purple, "Lina Marsh", "You: See you at the pier · 1h")
        bindChat(findViewById(R.id.chatBilal), "BA", R.color.avatar_purple, "Bilal Ahmed", "Thanks, bro! · Mon")
        bindChat(findViewById(R.id.chatNoor), "NF", R.color.avatar_green, "Noor Fatima", "You: Happy birthday! · Sun")
        linkTo(this, R.id.chatAisha, ChatActivity::class.java)
    }
}
