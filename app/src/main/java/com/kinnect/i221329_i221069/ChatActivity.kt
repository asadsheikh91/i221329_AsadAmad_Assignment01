package com.kinnect.i221329_i221069

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 21: one conversation, live from Firestore. The phone icon starts a voice call, the camera icon a video call.
class ChatActivity : AppCompatActivity() {

    private var peer: UserInfo? = null
    private var listener: ListenerRegistration? = null
    private lateinit var peerUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_chat_conversation)

        peerUid = intent.getStringExtra(EXTRA_UID) ?: run {
            finish()
            return
        }

        linkBack(this, R.id.btnBack)

        Backend.loadUser(peerUid) { user ->
            if (user != null) {
                peer = user
                showPeer(user)
            }
        }

        val input = findViewById<EditText>(R.id.etMessage)
        findViewById<android.view.View>(R.id.btnSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                Backend.sendMessage(peerUid, text)
                input.setText("")
            }
        }

        findViewById<android.view.View>(R.id.btnCall).setOnClickListener { call(video = false) }
        findViewById<android.view.View>(R.id.btnVideoCall).setOnClickListener { call(video = true) }
    }

    override fun onStart() {
        super.onStart()
        if (::peerUid.isInitialized) {
            listener = Backend.listenMessages(peerUid) { showMessages(it) }
            Backend.markChatRead(peerUid)
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun call(video: Boolean) {
        val user = peer
        if (user == null) {
            Toast.makeText(this, "Still loading this contact", Toast.LENGTH_SHORT).show()
        } else {
            placeCall(this, user, video)
        }
    }

    private fun showPeer(user: UserInfo) {
        findViewById<TextView>(R.id.tvName).text = user.name
        findViewById<TextView>(R.id.tvIntroName).text = user.name
        for (id in intArrayOf(R.id.tvInitials, R.id.tvIntroInitials)) {
            findViewById<TextView>(id).apply {
                text = user.initials
                backgroundTintList = ColorStateList.valueOf(getColor(user.color))
            }
        }
    }

    // Shortcut: every update rebuilds the whole list of bubbles, which is fine for a chat of this size
    private fun showMessages(messages: List<Message>) {
        val container = findViewById<LinearLayout>(R.id.messagesContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val margin = (12 * resources.displayMetrics.density).toInt()
        val gap = (2 * resources.displayMetrics.density).toInt()
        val maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()

        for (message in messages) {
            val layout = if (message.fromMe) R.layout.item_message_sent else R.layout.item_message_received
            val bubble = inflater.inflate(layout, container, false)
            bindMessage(bubble, message.text)
            (bubble as TextView).maxWidth = maxWidth

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = gap
            if (message.fromMe) {
                params.gravity = Gravity.END
                params.marginEnd = margin
            } else {
                params.gravity = Gravity.START
                params.marginStart = margin
            }
            container.addView(bubble, params)
        }

        // A message from the other person arrived while the chat is open: it counts as read
        if (messages.isNotEmpty() && !messages.last().fromMe) {
            Backend.markChatRead(peerUid)
        }

        val scroll = findViewById<ScrollView>(R.id.messagesScroll)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
