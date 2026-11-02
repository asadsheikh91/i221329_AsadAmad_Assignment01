package com.kinnect.i221329_i221069

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 20: list of conversations (newest first) with your friends along the top.
class ChatsActivity : AppCompatActivity() {

    private var people: Map<String, UserInfo> = emptyMap()
    private var chats: List<ChatSummary> = emptyList()
    private var friendUids: List<String> = emptyList()
    private val listeners = ArrayList<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_chats)

        linkBack(this, R.id.btnBack)

        findViewById<EditText>(R.id.etSearchChats).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople {
            people = it
            render()
        }
        listeners.add(Backend.listenChats {
            chats = it
            render()
        })
        listeners.add(Backend.listenFriendships { list ->
            friendUids = list.filter { it.accepted }.map { it.otherUid }
            render()
        })
    }

    override fun onStop() {
        super.onStop()
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun render() {
        val inflater = LayoutInflater.from(this)

        // Friends strip
        val strip = findViewById<LinearLayout>(R.id.friendsStrip)
        strip.removeAllViews()
        for (uid in friendUids) {
            val user = people[uid] ?: continue
            val item = inflater.inflate(R.layout.item_strip_friend, strip, false)
            item.findViewById<TextView>(R.id.tvInitials).apply {
                text = user.initials
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(user.color))
            }
            item.findViewById<TextView>(R.id.tvName).text = user.name.substringBefore(' ')
            item.setOnClickListener { openChat(this, uid) }
            strip.addView(item)
        }

        // Conversations, filtered by the search box
        val query = findViewById<EditText>(R.id.etSearchChats).text.toString().trim().lowercase()
        val container = findViewById<LinearLayout>(R.id.chatsContainer)
        container.removeAllViews()
        var shown = 0
        for (chat in chats) {
            val user = people[chat.otherUid] ?: continue
            if (query.isNotEmpty() && !user.name.lowercase().contains(query)) {
                continue
            }
            val row = inflater.inflate(R.layout.item_chat, container, false)
            val sender = if (chat.lastFromMe) "You: " else ""
            val age = timeAgo(chat.lastTime)
            val preview = sender + chat.lastMessage + if (age.isEmpty()) "" else " · $age"
            bindChat(row, user.initials, user.color, user.name, preview)
            if (chat.unread) {
                // Conversations with something new are written in bold
                row.findViewById<TextView>(R.id.tvName).setTypeface(null, Typeface.BOLD)
                row.findViewById<TextView>(R.id.tvPreview).setTypeface(null, Typeface.BOLD)
            }
            row.setOnClickListener { openChat(this, user.uid) }
            container.addView(row)
            shown++
        }
        findViewById<View>(R.id.tvNoChats).visibility = if (shown == 0) View.VISIBLE else View.GONE
    }
}
