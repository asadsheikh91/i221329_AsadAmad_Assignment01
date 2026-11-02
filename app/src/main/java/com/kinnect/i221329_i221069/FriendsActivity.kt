package com.kinnect.i221329_i221069

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 14: friends tab. Friend requests, people you may know and your friends, all live from Firestore.
// Tapping a person opens their profile.
class FriendsActivity : AppCompatActivity() {

    private var people: Map<String, UserInfo>? = null
    private var friendships: List<Friendship>? = null
    private var listener: ListenerRegistration? = null

    // People the user dismissed from the suggestions during this visit
    private val dismissed = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_friends)

        setupTabs(this, TAB_FRIENDS)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople {
            people = it
            render()
        }
        listener = Backend.listenFriendships {
            friendships = it
            render()
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun render() {
        val everyone = people ?: return
        val all = friendships ?: return

        val requests = findViewById<LinearLayout>(R.id.requestsContainer)
        val suggestions = findViewById<LinearLayout>(R.id.suggestionsContainer)
        val friends = findViewById<LinearLayout>(R.id.friendsContainer)
        requests.removeAllViews()
        suggestions.removeAllViews()
        friends.removeAllViews()

        val byUid = all.associateBy { it.otherUid }
        var requestCount = 0
        var friendCount = 0

        // Requests sent to us
        for (f in all) {
            val user = everyone[f.otherUid] ?: continue
            if (!f.accepted && !f.sentByMe) {
                requestCount++
                addRow(requests, user, "Sent you a request", "Confirm", "Delete",
                    { Backend.acceptFriendRequest(user.uid) },
                    { Backend.removeFriendship(user.uid) })
            } else if (f.accepted) {
                friendCount++
                addRow(friends, user, "Friends on Kinnect", "Message", "Unfriend",
                    { openChat(this, user.uid) },
                    { Backend.removeFriendship(user.uid) })
            }
        }

        // Everyone else is a suggestion (requests we already sent can be cancelled)
        for (user in everyone.values) {
            val f = byUid[user.uid]
            if (f == null && user.uid !in dismissed) {
                addRow(suggestions, user, "On Kinnect", "Add friend", "Remove",
                    { Backend.sendFriendRequest(user.uid) },
                    { dismissed.add(user.uid); render() })
            } else if (f != null && !f.accepted && f.sentByMe) {
                addRow(suggestions, user, "Request sent", "Requested", "Cancel",
                    { },
                    { Backend.removeFriendship(user.uid) })
            }
        }

        findViewById<TextView>(R.id.tvRequestCount).text = requestCount.toString()
        findViewById<View>(R.id.tvNoRequests).visibility = if (requestCount == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvNoFriends).visibility = if (friendCount == 0) View.VISIBLE else View.GONE
    }

    private fun addRow(
        into: LinearLayout, user: UserInfo, detail: String, primary: String, secondary: String,
        onPrimary: () -> Unit, onSecondary: () -> Unit
    ) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_friend, into, false)
        bindFriend(row, user.initials, user.color, user.name, "", detail, primary, secondary)
        row.findViewById<Button>(R.id.btnPrimary).setOnClickListener { onPrimary() }
        row.findViewById<Button>(R.id.btnSecondary).setOnClickListener { onSecondary() }
        row.setOnClickListener { openUserProfile(this, user.uid) }
        into.addView(row)
    }
}
