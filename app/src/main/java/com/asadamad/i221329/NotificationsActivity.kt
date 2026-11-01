package com.asadamad.i221329

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 18: notifications tab, live from Firestore (see Notifications.kt).
// New ones (not seen before this visit) sit on the tinted band, the rest are under "Earlier".
// The rows of the design are templates: the sample rows are removed and one real row is added per notification.
class NotificationsActivity : AppCompatActivity() {

    // How many older notifications are shown before "See previous notifications" is tapped
    private val earlierLimit = 12

    private var noticeListener: ListenerRegistration? = null
    private var friendListener: ListenerRegistration? = null
    private var notices: List<Notice>? = null
    private var waitingRequests: Set<String> = emptySet()   // people whose friend request to me is still open
    private val newIds = HashSet<String>()                  // shown as new during this visit
    private val markedIds = HashSet<String>()               // already marked as read in Firestore
    private var showAllEarlier = false

    private lateinit var newContainer: LinearLayout
    private lateinit var earlierContainer: LinearLayout
    private lateinit var newTitle: TextView
    private lateinit var earlierTitle: TextView
    private lateinit var moreButton: Button
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_notifications)

        setupTabs(this, TAB_NOTIFICATIONS)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        newContainer = findViewById<View>(R.id.notifReaction).parent as LinearLayout
        earlierContainer = findViewById<View>(R.id.notifReply).parent as LinearLayout
        newTitle = findTextExact(earlierContainer, "New")!!
        earlierTitle = findTextExact(earlierContainer, "Earlier")!!
        moreButton = findTextExact(earlierContainer, "See previous notifications") as Button

        // The sample rows (and the sample Confirm / Delete buttons) of the design are replaced by real ones
        newContainer.removeAllViews()
        for (id in intArrayOf(R.id.notifReply, R.id.notifGroup, R.id.notifTagged, R.id.notifLiked)) {
            earlierContainer.removeView(findViewById(id))
        }

        emptyText = styledText(this, "You are all caught up. Likes, comments and friend requests show up here.", R.style.Kin_Text_Secondary)
        val margin = dp(this, 16)
        emptyText.setPadding(margin, margin, margin, margin)
        earlierContainer.addView(emptyText, earlierContainer.indexOfChild(moreButton))

        moreButton.setOnClickListener {
            showAllEarlier = true
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        noticeListener = Backend.listenNotices { list ->
            // Everything unread when it arrives is "new" for as long as this screen is open
            val unread = list.filter { !it.read }
            for (notice in unread) {
                newIds.add(notice.id)
            }
            notices = list
            // They have now been seen, so the number on the bell goes down
            val toMark = unread.map { it.id }.filter { markedIds.add(it) }
            Backend.markNoticesRead(toMark)
            render()
        }
        friendListener = Backend.listenFriendships { list ->
            waitingRequests = list.filter { !it.accepted && !it.sentByMe }.map { it.otherUid }.toSet()
            render()
        }
    }

    override fun onStop() {
        super.onStop()
        noticeListener?.remove()
        noticeListener = null
        friendListener?.remove()
        friendListener = null
    }

    private fun render() {
        val all = notices ?: return
        val inflater = LayoutInflater.from(this)

        // Throw away the rows of the last render
        newContainer.removeAllViews()
        var index = 0
        while (index < earlierContainer.childCount) {
            if (earlierContainer.getChildAt(index).tag == "notice") {
                earlierContainer.removeViewAt(index)
            } else {
                index++
            }
        }

        val fresh = all.filter { it.id in newIds }
        val older = all.filter { it.id !in newIds }
        val shownOlder = if (showAllEarlier) older else older.take(earlierLimit)

        for (notice in fresh) {
            addRow(inflater, newContainer, notice, true, null)
        }
        for (notice in shownOlder) {
            addRow(inflater, earlierContainer, notice, false, moreButton)
        }

        setVisible(newTitle, fresh.isNotEmpty())
        setVisible(newContainer, fresh.isNotEmpty())
        setVisible(earlierTitle, older.isNotEmpty())
        setVisible(moreButton, older.size > shownOlder.size)
        setVisible(emptyText, all.isEmpty())
    }

    /**
     * Adds one notification (and the Confirm / Delete buttons of an open friend request) to [into],
     * in front of [before] (at the end when [before] is null).
     */
    private fun addRow(inflater: LayoutInflater, into: LinearLayout, notice: Notice, isNew: Boolean, before: View?) {
        val row = inflater.inflate(R.layout.item_notification, into, false)
        bindRow(row, notice, isNew)
        row.tag = "notice"
        val position = if (before == null) into.childCount else into.indexOfChild(before)
        into.addView(row, position)

        if (notice.type == "request" && notice.fromUid in waitingRequests) {
            val buttons = LinearLayout(this)
            buttons.orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 36))
            params.marginStart = dp(this, 92)
            params.marginEnd = dp(this, 78)
            params.topMargin = 0
            params.bottomMargin = dp(this, 10)

            val confirm = styledButton(this, "Confirm", false)
            confirm.setOnClickListener {
                Backend.acceptFriendRequest(notice.fromUid)
                Toast.makeText(this, "You and ${notice.fromName} are now friends", Toast.LENGTH_SHORT).show()
            }
            val delete = styledButton(this, "Delete", true)
            delete.setOnClickListener { Backend.removeFriendship(notice.fromUid) }

            buttons.addView(confirm, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            val deleteParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            deleteParams.marginStart = dp(this, 8)
            buttons.addView(delete, deleteParams)
            buttons.tag = "notice"
            into.addView(buttons, position + 1, params)
        }
    }

    private fun bindRow(row: View, notice: Notice, isNew: Boolean) {
        row.findViewById<TextView>(R.id.tvInitials).apply {
            text = initialsOf(notice.fromName)
            backgroundTintList = ColorStateList.valueOf(getColor(avatarColorOf(notice.fromUid)))
        }
        row.findViewById<TextView>(R.id.tvText).text = message(notice)

        val time = row.findViewById<TextView>(R.id.tvTime)
        time.text = noticeTime(notice.time)
        if (isNew) {
            // New notifications show the time in bold teal
            time.setTextColor(getColor(R.color.teal))
            time.setTypeface(null, Typeface.BOLD)
        }

        val badge = row.findViewById<ImageView>(R.id.notifBadge)
        when (notice.type) {
            "like" -> showBadge(badge, R.color.love, R.drawable.ic_heart_filled)
            "comment", "story_reply" -> showBadge(badge, R.color.avatar_green, R.drawable.ic_comment)
            "request" -> showBadge(badge, R.color.teal, R.drawable.ic_user)
            "accepted" -> showBadge(badge, R.color.avatar_indigo, R.drawable.ic_friends)
            "story_like" -> when (notice.text) {
                "Love" -> showBadge(badge, R.color.love, R.drawable.ic_heart_filled)
                "Haha" -> showBadge(badge, R.color.gold, R.drawable.ic_face_haha)
                else -> showBadge(badge, R.color.teal, R.drawable.ic_thumb_filled)
            }
            else -> showBadge(badge, R.color.teal, R.drawable.ic_notification_filled)
        }

        row.setOnClickListener { open(notice) }

        // The three dots at the end of the row
        (row as LinearLayout).getChildAt(2).setOnClickListener {
            AlertDialog.Builder(this)
                .setItems(arrayOf("Remove this notification")) { _, _ -> Backend.removeNotice(notice.id) }
                .show()
        }
    }

    private fun showBadge(badge: ImageView, color: Int, icon: Int) {
        badge.setImageResource(icon)
        badge.backgroundTintList = ColorStateList.valueOf(getColor(color))
    }

    /** "Aisha Khan liked your post." with the name in bold. */
    private fun message(notice: Notice): CharSequence {
        val builder = SpannableStringBuilder(notice.fromName)
        builder.setSpan(StyleSpan(Typeface.BOLD), 0, notice.fromName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val quote = if (notice.text.isBlank()) "" else ": “${notice.text}”"
        builder.append(
            when (notice.type) {
                "like" -> if (quote.isEmpty()) " liked your post." else " liked your post$quote"
                "comment" -> " commented on your post$quote"
                "request" -> " sent you a friend request."
                "accepted" -> " accepted your friend request."
                "story_like" -> when (notice.text) {
                    "Love" -> " loved your story."
                    "Haha" -> " laughed at your story."
                    else -> " liked your story."
                }
                "story_reply" -> " replied to your story$quote"
                else -> " sent you a notification."
            }
        )
        return builder
    }

    /** "Just now", "5m", "2h", "Yesterday", "3d". */
    private fun noticeTime(millis: Long): String {
        if (millis <= 0) {
            return "Just now"
        }
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            minutes < 60 * 48 -> "Yesterday"
            else -> "${minutes / (60 * 24)}d"
        }
    }

    /** Tapping a notification opens what it is about. */
    private fun open(notice: Notice) {
        when (notice.type) {
            "like", "comment" -> {
                val intent = Intent(this, CommentsActivity::class.java)
                intent.putExtra(EXTRA_POST_ID, notice.refId)
                startActivity(intent)
            }
            "request", "accepted" -> openUserProfile(this, notice.fromUid)
            "story_like", "story_reply" -> openScreen(this, MyStoryActivity::class.java)
        }
    }
}
