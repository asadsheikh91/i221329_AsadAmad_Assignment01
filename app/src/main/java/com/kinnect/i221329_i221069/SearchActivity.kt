package com.kinnect.i221329_i221069

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Screen 13: search. Type a username or a name and the matching people appear while you type: every change of the
// text asks the database (Backend.searchUsers, three small queries on indexed fields). Tap a result to open the
// profile, or the + button to send a friend request.
class SearchActivity : AppCompatActivity() {

    private var results: List<UserInfo> = emptyList()
    private var friendships: Map<String, Friendship> = emptyMap()
    private var listener: ListenerRegistration? = null
    private var lastQuery = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_search)

        linkBack(this, R.id.btnBack)

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Wait a moment after the last key so the database is not asked for every letter at once
                handler.removeCallbacksAndMessages(null)
                val text = s?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    lastQuery = ""
                    results = emptyList()
                    showResults()
                } else {
                    handler.postDelayed({ search(text) }, 250)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onStart() {
        super.onStart()
        listener = Backend.listenFriendships { list ->
            friendships = list.associateBy { it.otherUid }
            showResults()
        }
        showResults()
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun search(text: String) {
        lastQuery = text
        Backend.searchUsers(text) { found ->
            // An answer to an older text is ignored
            if (text == lastQuery) {
                results = found
                showResults()
            }
        }
    }

    private fun showResults() {
        val query = findViewById<EditText>(R.id.etSearch).text.toString().trim()
        val container = findViewById<LinearLayout>(R.id.peopleContainer)
        val message = findViewById<TextView>(R.id.tvNoResults)
        container.removeAllViews()

        if (query.isEmpty()) {
            message.text = "Type a name or a username to find people."
            message.visibility = View.VISIBLE
            return
        }
        message.text = "No one on Kinnect matches \"$query\"."
        message.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE

        for (user in results) {
            val friendship = friendships[user.uid]
            val row = LayoutInflater.from(this).inflate(R.layout.item_person, container, false)
            bindAvatar(row.findViewById(R.id.tvInitials), user.uid, user.name)
            row.findViewById<TextView>(R.id.tvName).text = user.name
            val status = when {
                friendship == null -> "On Kinnect"
                friendship.accepted -> "Friend"
                friendship.sentByMe -> "Request sent"
                else -> "Wants to be your friend"
            }
            row.findViewById<TextView>(R.id.tvDetail).text =
                if (user.username.isEmpty()) status else "@${user.username} · $status"

            // The + button only makes sense while there is no request yet
            val add = row.findViewById<View>(R.id.btnAdd)
            if (friendship == null) {
                add.setOnClickListener { Backend.sendFriendRequest(user.uid) }
            } else {
                add.visibility = View.GONE
            }
            row.setOnClickListener { openUserProfile(this, user.uid) }
            container.addView(row)
        }
    }
}
