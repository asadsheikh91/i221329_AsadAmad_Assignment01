package com.asadamad.i221329

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 11: watching a friend's stories (opened from the Home row with the friend's uid).
// Their stories play one after the other. Reply in the box (it goes to your chat with them) or
// tap Like / Love / Haha to react (they get a notification).
class StoryViewerActivity : AppCompatActivity() {

    private var storyListener: ListenerRegistration? = null
    private var friendListener: ListenerRegistration? = null

    private var allStories: List<Story>? = null
    private var friendUids: Set<String>? = null
    private var player: StoryPlayer? = null
    private var current: Story? = null

    private lateinit var authorUid: String
    private lateinit var art: ImageView
    private lateinit var avatar: TextView
    private lateinit var nameView: TextView
    private lateinit var timeView: TextView
    private lateinit var sticker: TextView
    private lateinit var replyField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_viewer)

        authorUid = intent.getStringExtra(EXTRA_UID) ?: run {
            finish()
            return
        }

        // The views of the design have no ids, so they are found by their place in the layout:
        // root > column > [story card, reply row]
        val column = childGroup(findViewById(R.id.root), 0)
        val card = childGroup(column, 0)
        art = card.getChildAt(0) as ImageView
        val header = card.getChildAt(2) as ViewGroup
        avatar = header.getChildAt(0) as TextView
        val names = header.getChildAt(1) as ViewGroup
        nameView = names.getChildAt(0) as TextView
        timeView = (names.getChildAt(1) as ViewGroup).getChildAt(0) as TextView
        sticker = card.getChildAt(3) as TextView
        val replyRow = column.getChildAt(1) as ViewGroup
        replyField = replyRow.getChildAt(0) as EditText

        // Nothing of the sample story stays on screen while the real one loads
        art.setImageDrawable(null)
        setVisible(sticker, false)

        linkBack(this, R.id.btnClose)

        // Send the reply with the keyboard's send key; typing holds the story
        replyField.imeOptions = EditorInfo.IME_ACTION_SEND
        replyField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendReply()
                true
            } else {
                false
            }
        }
        replyField.setOnFocusChangeListener { _, focused -> player?.hold("typing", focused) }

        // Quick reactions
        replyRow.getChildAt(1).setOnClickListener { react(it, "Like") }
        replyRow.getChildAt(2).setOnClickListener { react(it, "Love") }
        replyRow.getChildAt(3).setOnClickListener { react(it, "Haha") }
    }

    override fun onStart() {
        super.onStart()
        if (!::authorUid.isInitialized) {
            return
        }
        storyListener = Backend.listenStories {
            allStories = it
            startWhenReady()
        }
        friendListener = Backend.listenFriendships { list ->
            friendUids = list.filter { it.accepted }.map { it.otherUid }.toSet()
            startWhenReady()
        }
    }

    override fun onStop() {
        super.onStop()
        storyListener?.remove()
        storyListener = null
        friendListener?.remove()
        friendListener = null
        player?.hold("screen", true)
    }

    override fun onResume() {
        super.onResume()
        player?.hold("screen", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.stop()
    }

    /** Starts the player once the stories and the friend list have both arrived (only the first time). */
    private fun startWhenReady() {
        val stories = allStories ?: return
        val friends = friendUids ?: return
        val me = Backend.myUid ?: return
        if (player != null) {
            return
        }

        val theirs = stories.filter { it.authorId == authorUid && canSeeStory(it, me, friends) }
        if (theirs.isEmpty()) {
            Toast.makeText(this, "This story is no longer available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val column = childGroup(findViewById(R.id.root), 0)
        val card = childGroup(column, 0)
        player = StoryPlayer(
            this, findViewById(R.id.storyProgress), card, theirs,
            { show(it) },
            { finish() }
        )
        // Start with the first story that has not been watched yet
        val firstUnseen = theirs.indexOfFirst { !it.seenByMe }
        player?.start(if (firstUnseen >= 0) firstUnseen else 0)
    }

    private fun show(story: Story) {
        current = story
        showStoryPicture(art, story) { current?.id == story.id }

        avatar.text = initialsOf(story.authorName)
        avatar.backgroundTintList = ColorStateList.valueOf(getColor(avatarColorOf(story.authorId)))
        nameView.text = story.authorName
        timeView.text = timeAgo(story.time) + " ·"

        sticker.text = story.text
        setVisible(sticker, story.text.isNotEmpty())

        replyField.setText("")
        Backend.markStoryViewed(story)
    }

    private fun sendReply() {
        val story = current ?: return
        val text = replyField.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        Backend.replyToStory(story, text)
        replyField.setText("")
        replyField.clearFocus()
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(replyField.windowToken, 0)
        Toast.makeText(this, "Reply sent to ${story.authorName}", Toast.LENGTH_SHORT).show()
    }

    private fun react(button: View, kind: String) {
        val story = current ?: return
        Backend.reactToStory(story, kind)
        // A little pop, so the tap is felt
        button.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        Toast.makeText(this, "You reacted to ${story.authorName}'s story", Toast.LENGTH_SHORT).show()
    }
}
