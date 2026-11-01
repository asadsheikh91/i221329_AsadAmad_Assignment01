package com.asadamad.i221329

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

// Screen 13: search. Type a name to find people on Kinnect; tap a result to open the profile,
// or the + button to send a friend request.
class SearchActivity : AppCompatActivity() {

    private var people: Map<String, UserInfo> = emptyMap()
    private var friendships: Map<String, Friendship> = emptyMap()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_search)

        linkBack(this, R.id.btnBack)

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                showResults()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople {
            people = it
            showResults()
        }
        listener = Backend.listenFriendships { list ->
            friendships = list.associateBy { it.otherUid }
            showResults()
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun showResults() {
        val query = findViewById<EditText>(R.id.etSearch).text.toString().trim().lowercase()
        val container = findViewById<LinearLayout>(R.id.peopleContainer)
        val message = findViewById<TextView>(R.id.tvNoResults)
        container.removeAllViews()

        if (query.isEmpty()) {
            message.text = "Type a name to find people."
            message.visibility = View.VISIBLE
            return
        }

        val matches = people.values.filter { it.name.lowercase().contains(query) }
        message.text = "No one on Kinnect matches \"$query\"."
        message.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE

        for (user in matches) {
            val friendship = friendships[user.uid]
            val row = LayoutInflater.from(this).inflate(R.layout.item_person, container, false)
            row.findViewById<TextView>(R.id.tvInitials).apply {
                text = user.initials
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(user.color))
            }
            row.findViewById<TextView>(R.id.tvName).text = user.name
            row.findViewById<TextView>(R.id.tvDetail).text = when {
                friendship == null -> "On Kinnect"
                friendship.accepted -> "Friend"
                friendship.sentByMe -> "Request sent"
                else -> "Wants to be your friend"
            }

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
