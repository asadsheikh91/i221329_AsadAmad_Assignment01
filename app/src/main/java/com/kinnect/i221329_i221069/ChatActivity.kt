package com.kinnect.i221329_i221069

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 21: one conversation, live from the database.
//  - the green dot and "Active now" follow the other person's presence; the phone / camera icons start calls
//  - send text, photos (Photos / Camera icons) and posts (the + icon); every message shows live
//  - long-press a message: Reply, Heart, Copy and, for your own messages within 5 minutes, Edit and Delete
//    (after 5 minutes these two are not offered; an edited message shows "Edited")
//  - double-tap a message to put a heart on it; replies and hearts are stored with the message
//  - a screenshot of this chat is reported: "Aisha took a screenshot" appears in the conversation and the other
//    person gets a notification
class ChatActivity : AppCompatActivity() {

    companion object {
        /** The person whose chat is open right now (a push notification from them is not shown on top of it). */
        var openPeer: String? = null
    }

    private lateinit var peerUid: String
    private var peer: UserInfo? = null
    private val listeners = ArrayList<ListenerRegistration>()
    private var messages: List<Message> = emptyList()
    private var replyTo: Message? = null
    private var cameraUri: Uri? = null

    private lateinit var replyBar: LinearLayout
    private lateinit var replyLabel: TextView
    private lateinit var onlineDot: View
    private lateinit var statusView: TextView
    private lateinit var screenshots: ScreenshotWatcher

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            sendPhoto(uri)
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) {
            sendPhoto(uri)
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_chat_conversation)

        peerUid = intent.getStringExtra(EXTRA_UID) ?: run {
            finish()
            return
        }

        linkBack(this, R.id.btnBack)
        val root = findViewById<View>(R.id.root)

        // The green dot on the avatar is the second child next to it (it has no id); hidden until the person is online
        onlineDot = (findViewById<View>(R.id.tvInitials).parent as ViewGroup).getChildAt(1)
        onlineDot.visibility = View.GONE
        statusView = findViewById(R.id.tvStatus)
        statusView.text = ""

        Backend.loadUser(peerUid) { user ->
            if (user != null) {
                peer = user
                showPeer(user)
            }
        }

        val input = findViewById<EditText>(R.id.etMessage)
        findViewById<View>(R.id.btnSend).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                Backend.sendMessage(peerUid, text, replyTo)
                input.setText("")
                setReply(null)
            }
        }

        findViewById<View>(R.id.btnCall).setOnClickListener { call(video = false) }
        findViewById<View>(R.id.btnVideoCall).setOnClickListener { call(video = true) }

        // The icons of the message bar and the header have no ids: found by their descriptions
        fun icon(description: String): View? = allViews(root).firstOrNull { it.contentDescription == description }
        icon("Photos")?.setOnClickListener { pickPhoto.launch("image/*") }
        icon("Camera")?.setOnClickListener { openCamera() }
        icon("More options")?.setOnClickListener { showMoreOptions() }
        icon("Voice message")?.setOnClickListener {
            Toast.makeText(this, "Voice messages are not available yet", Toast.LENGTH_SHORT).show()
        }
        icon("Conversation info")?.setOnClickListener { showInfo() }

        buildReplyBar()
        screenshots = ScreenshotWatcher(this) { Backend.reportScreenshot(peerUid) }
    }

    override fun onStart() {
        super.onStart()
        if (!::peerUid.isInitialized) {
            return
        }
        openPeer = peerUid
        listeners.add(Backend.listenMessages(peerUid) { showMessages(it) })
        listeners.add(Backend.listenPresence(peerUid) { online, lastSeen -> showPresence(online, lastSeen) })
        Backend.markChatRead(peerUid)
        screenshots.start()
    }

    override fun onStop() {
        super.onStop()
        openPeer = null
        listeners.forEach { it.remove() }
        listeners.clear()
        if (::screenshots.isInitialized) {
            screenshots.stop()
        }
    }

    // ------------------------------------------------------------------ header

    private fun showPeer(user: UserInfo) {
        findViewById<TextView>(R.id.tvName).text = user.name
        findViewById<TextView>(R.id.tvIntroName).text = user.name
        bindAvatar(findViewById(R.id.tvInitials), user.uid, user.name)
        bindAvatar(findViewById(R.id.tvIntroInitials), user.uid, user.name)
        findTextExact(findViewById(R.id.root), "Say hi on Kinnect")?.text =
            if (user.username.isEmpty()) "Kinnect friend" else "@${user.username} · Kinnect friend"
    }

    /** Green dot and "Active now" while the other person is online; otherwise when they were last seen. */
    private fun showPresence(online: Boolean, lastSeen: Long) {
        onlineDot.visibility = if (online) View.VISIBLE else View.GONE
        statusView.text = when {
            online -> "Active now"
            lastSeen > 0 -> "Active " + timeAgo(lastSeen).let { if (it == "now") "just now" else "$it ago" }
            else -> "Offline"
        }
    }

    private fun call(video: Boolean) {
        val user = peer
        if (user == null) {
            Toast.makeText(this, "Still loading this contact", Toast.LENGTH_SHORT).show()
        } else {
            placeCall(this, user, video)
        }
    }

    private fun showInfo() {
        val user = peer ?: return
        val lines = ArrayList<String>()
        if (user.username.isNotEmpty()) lines.add("@" + user.username)
        if (user.bio.isNotEmpty()) lines.add(user.bio)
        lines.add(joinedText(user.joined).ifEmpty { "Kinnect member" })
        AlertDialog.Builder(this)
            .setTitle(user.name)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Close", null)
            .setNeutralButton("View profile") { _, _ -> openUserProfile(this, user.uid) }
            .show()
    }

    // ------------------------------------------------------------------ sending

    private fun openCamera() {
        // The camera app refuses to start for an app that declares the permission but was not given it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val uri = newCameraPhotoUri(this)
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "No camera app found. Use Photos instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendPhoto(uri: Uri) {
        ImageTools.encode(this, uri, 800, 150_000, ImageTools.FILTER_NONE) { encoded ->
            if (encoded == null) {
                Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
            } else {
                Backend.sendImageMessage(peerUid, encoded, replyTo)
                setReply(null)
            }
        }
    }

    /** The + icon: share one of the latest posts, or look at the call history with this person. */
    private fun showMoreOptions() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Share a post", "Call history")) { _, which ->
                if (which == 0) pickPostToShare() else showCallHistory()
            }
            .show()
    }

    private fun pickPostToShare() {
        Backend.loadRecentPosts { posts ->
            if (posts.isEmpty()) {
                Toast.makeText(this, "There are no posts to share yet", Toast.LENGTH_SHORT).show()
                return@loadRecentPosts
            }
            val shown = posts.take(15)
            val names = shown.map { it.authorName + ": " + it.text.ifEmpty { "Photo post" }.take(40) }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Share a post")
                .setItems(names) { _, which ->
                    Backend.sendPostMessage(peerUid, shown[which])
                    Toast.makeText(this, "Post shared", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun showCallHistory() {
        var registration: ListenerRegistration? = null
        registration = Backend.listenCallLogs { logs ->
            registration?.remove()
            val mine = logs.filter { it.peerUid == peerUid }
            val lines = mine.map {
                val kind = if (it.video) "Video" else "Voice"
                val way = if (it.outgoing) "outgoing" else "incoming"
                val length = if (it.status == "connected") String.format(" · %d:%02d", it.seconds / 60, it.seconds % 60) else " · ${it.status}"
                "$kind, $way$length · ${timeAgo(it.time)}"
            }
            AlertDialog.Builder(this)
                .setTitle("Call history")
                .setMessage(if (lines.isEmpty()) "No calls with ${peer?.name ?: "this person"} yet." else lines.joinToString("\n"))
                .setPositiveButton("Close", null)
                .show()
        }
    }

    // ------------------------------------------------------------------ replying

    /** The bar above the message box that says who is being answered. */
    private fun buildReplyBar() {
        val scroll = findViewById<View>(R.id.messagesScroll)
        val column = scroll.parent as LinearLayout
        replyBar = LinearLayout(this)
        replyBar.orientation = LinearLayout.HORIZONTAL
        replyBar.gravity = Gravity.CENTER_VERTICAL
        replyBar.setBackgroundColor(getColor(R.color.cream))
        replyBar.setPadding(dp(this, 16), dp(this, 8), dp(this, 12), dp(this, 8))
        replyBar.visibility = View.GONE

        replyLabel = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        replyLabel.maxLines = 1
        replyLabel.ellipsize = android.text.TextUtils.TruncateAt.END
        replyBar.addView(replyLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val close = ImageView(this)
        close.setImageResource(R.drawable.ic_close)
        tintImage(close, getColor(R.color.text_secondary))
        close.setPadding(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 4))
        close.setOnClickListener { setReply(null) }
        replyBar.addView(close, LinearLayout.LayoutParams(dp(this, 32), dp(this, 32)))

        column.addView(replyBar, column.indexOfChild(scroll) + 1)
    }

    private fun setReply(message: Message?) {
        replyTo = message
        if (message == null) {
            replyBar.visibility = View.GONE
            return
        }
        val who = if (message.fromMe) "yourself" else (peer?.name ?: "them")
        replyLabel.text = boldThen(this, "", "Replying to ").append(boldThen(this, who, ": " + snippet(message)))
        replyBar.visibility = View.VISIBLE
        val input = findViewById<EditText>(R.id.etMessage)
        input.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
    }

    private fun snippet(message: Message): String = when {
        message.deleted -> "Message deleted"
        message.type == MSG_IMAGE -> "Photo"
        message.type == MSG_POST -> "Shared post"
        else -> message.text.take(60)
    }

    // ------------------------------------------------------------------ showing the conversation

    private fun showMessages(list: List<Message>) {
        messages = list
        val container = findViewById<LinearLayout>(R.id.messagesContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (message in list) {
            addMessage(container, inflater, message)
        }

        // A message from the other person arrived while the chat is open: it counts as read
        if (list.isNotEmpty() && !list.last().fromMe) {
            Backend.markChatRead(peerUid)
        }

        val scroll = findViewById<ScrollView>(R.id.messagesScroll)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun addMessage(container: LinearLayout, inflater: LayoutInflater, message: Message) {
        val margin = dp(this, 12)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(this, 2)

        // "Aisha took a screenshot", "Voice call 2:13": a small grey line in the middle
        if (message.type == MSG_SYSTEM) {
            val line = TextView(this, null, 0, R.style.Kin_Text_Secondary)
            line.text = message.text
            line.textSize = 13f
            line.gravity = Gravity.CENTER
            line.setPadding(0, dp(this, 6), 0, dp(this, 6))
            container.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        val mine = message.fromMe
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        if (mine) {
            params.gravity = Gravity.END
            params.marginEnd = margin
        } else {
            params.gravity = Gravity.START
            params.marginStart = margin
        }
        val side = if (mine) Gravity.END else Gravity.START

        // The line above a reply: "You replied to Aisha" and the words that were answered
        if (message.replyToId.isNotEmpty()) {
            cell.addView(replyPreview(message, side))
        }

        val bubble: View = when {
            message.deleted -> deletedBubble(mine)
            message.type == MSG_IMAGE -> imageBubble(message)
            message.type == MSG_POST -> postBubble(message, mine)
            else -> textBubble(message, mine)
        }
        attachGestures(bubble, message)
        cell.addView(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = side })

        // A heart hangs off the corner of the bubble
        if (message.hearts.isNotEmpty() && !message.deleted) {
            val heart = ImageView(this)
            heart.setBackgroundResource(R.drawable.bg_circle)
            heart.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.white))
            heart.elevation = dp(this, 2).toFloat()
            heart.setImageResource(R.drawable.ic_heart_filled)
            tintImage(heart, getColor(R.color.love))
            heart.setPadding(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 4))
            val heartParams = LinearLayout.LayoutParams(dp(this, 22), dp(this, 22))
            heartParams.gravity = side
            heartParams.topMargin = dp(this, -8)
            heartParams.marginStart = dp(this, 10)
            heartParams.marginEnd = dp(this, 10)
            cell.addView(heart, heartParams)
        }

        if (message.edited && !message.deleted) {
            val edited = TextView(this, null, 0, R.style.Kin_Text_Secondary)
            edited.text = "Edited"
            edited.textSize = 12f
            cell.addView(edited, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = side })
        }
        container.addView(cell, params)
    }

    private fun maxBubbleWidth(): Int = (resources.displayMetrics.widthPixels * 0.75).toInt()

    private fun textBubble(message: Message, mine: Boolean): View {
        val layout = if (mine) R.layout.item_message_sent else R.layout.item_message_received
        val bubble = LayoutInflater.from(this).inflate(layout, null) as TextView
        bubble.text = message.text
        bubble.maxWidth = maxBubbleWidth()
        return bubble
    }

    private fun deletedBubble(mine: Boolean): View {
        val bubble = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        bubble.text = if (mine) "You deleted this message" else "This message was deleted"
        bubble.setTypeface(null, Typeface.ITALIC)
        bubble.setBackgroundResource(R.drawable.bg_round_18)
        bubble.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.chip))
        bubble.setPadding(dp(this, 14), dp(this, 9), dp(this, 14), dp(this, 9))
        return bubble
    }

    private fun imageBubble(message: Message): View {
        val picture = ImageView(this)
        picture.scaleType = ImageView.ScaleType.CENTER_CROP
        picture.setBackgroundResource(R.drawable.bg_round_18)
        picture.clipToOutline = true
        picture.layoutParams = LinearLayout.LayoutParams(dp(this, 220), dp(this, 260))
        PostImages.showLoaded(picture, "msg_${message.id}") { done -> Backend.loadMessageImage(peerUid, message.id, done) }
        return picture
    }

    /** A shared post: a small card that opens the post's comments. */
    private fun postBubble(message: Message, mine: Boolean): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_round_18)
        card.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(if (mine) R.color.teal_tint else R.color.chip))
        card.setPadding(dp(this, 14), dp(this, 10), dp(this, 14), dp(this, 10))

        val title = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        title.text = "Shared a post"
        title.textSize = 13f
        card.addView(title)

        val body = TextView(this, null, 0, R.style.Kin_Text)
        body.text = message.text
        body.maxWidth = maxBubbleWidth()
        body.maxLines = 4
        body.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(body)

        val open = TextView(this, null, 0, R.style.Kin_Text_Link)
        open.text = "Open post"
        open.textSize = 14f
        card.addView(open)
        return card
    }

    private fun replyPreview(message: Message, side: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        val me = Backend.myUid
        val peerFirst = (peer?.name ?: "them").substringBefore(' ')
        val repliedToMe = message.replyToSender == me
        val label = when {
            message.fromMe && repliedToMe -> "You replied to yourself"
            message.fromMe -> "You replied to $peerFirst"
            repliedToMe -> "$peerFirst replied to you"
            else -> "$peerFirst replied to themselves"
        }
        val labelView = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        labelView.text = label
        labelView.textSize = 13f
        box.addView(labelView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = side })

        val quoted = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        quoted.text = message.replyToText
        quoted.textSize = 14f
        quoted.maxLines = 2
        quoted.ellipsize = android.text.TextUtils.TruncateAt.END
        quoted.maxWidth = maxBubbleWidth()
        quoted.setBackgroundResource(R.drawable.bg_round_18)
        quoted.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.cream))
        quoted.setPadding(dp(this, 12), dp(this, 6), dp(this, 12), dp(this, 14))
        box.addView(quoted, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = side
            bottomMargin = dp(this@ChatActivity, -10)
        })
        return box
    }

    // ------------------------------------------------------------------ touching a message

    /** Double-tap puts a heart, long-press opens the menu, tap opens a shared post. */
    private fun attachGestures(view: View, message: Message) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!message.deleted) {
                    Backend.toggleHeart(peerUid, message)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showMessageMenu(message)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (message.type == MSG_POST && !message.deleted) {
                    startActivity(Intent(this@ChatActivity, CommentsActivity::class.java).putExtra(EXTRA_POST_ID, message.postId))
                }
                return true
            }
        })
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    /** Reply, Heart, Copy for everybody; Edit and Delete only for my own message in its first 5 minutes. */
    private fun showMessageMenu(message: Message) {
        val items = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (!message.deleted) {
            items.add("Reply")
            actions.add { setReply(message) }
            items.add(if (message.heartedByMe) "Remove heart" else "Heart")
            actions.add { Backend.toggleHeart(peerUid, message) }
            if (message.type == MSG_TEXT) {
                items.add("Copy")
                actions.add {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // After 5 minutes canChange() is false, so these two simply are not offered any more
        if (message.canChange()) {
            if (message.type == MSG_TEXT) {
                items.add("Edit")
                actions.add { askToEdit(message) }
            }
            items.add("Delete")
            actions.add { confirmDelete(message) }
        }
        if (items.isEmpty()) {
            return
        }
        AlertDialog.Builder(this)
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun askToEdit(message: Message) {
        val field = EditText(this)
        field.setText(message.text)
        field.setSelection(field.text.length)
        field.setPadding(dp(this, 20), dp(this, 20), dp(this, 20), dp(this, 20))
        AlertDialog.Builder(this)
            .setTitle("Edit message")
            .setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isNotEmpty() && text != message.text) {
                    Backend.editMessage(peerUid, message, text) { done ->
                        if (!done) {
                            Toast.makeText(this, "A message can only be edited for 5 minutes", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(message: Message) {
        AlertDialog.Builder(this)
            .setMessage("Delete this message?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                Backend.deleteMessage(peerUid, message) { done ->
                    if (!done) {
                        Toast.makeText(this, "A message can only be deleted for 5 minutes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}
