package com.kinnect.i221329_i221069

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Screen 12: your own stories (the ones from the last 24 hours), with who has seen the current one
// and the hearts that came in. Opened after sharing a story, from the Home row and from a notification.
class MyStoryActivity : AppCompatActivity() {

    private var listener: ListenerRegistration? = null
    private var mine: List<Story> = emptyList()
    private var loaded = false
    private var people: Map<String, UserInfo> = emptyMap()
    private var player: StoryPlayer? = null
    private var current: Story? = null

    private lateinit var art: ImageView
    private lateinit var media: StoryMediaView
    private lateinit var subtitle: TextView
    private lateinit var sticker: TextView
    private lateinit var hearts: ViewGroup
    private lateinit var viewers: ViewGroup
    private lateinit var seenBy: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_my_story)

        // The views of the design have no ids, so they are found by their place in the layout:
        // root > column > [story card, bottom row]
        val column = childGroup(findViewById(R.id.root), 0)
        val card = childGroup(column, 0)
        art = card.getChildAt(0) as ImageView
        val header = card.getChildAt(2) as ViewGroup
        subtitle = (header.getChildAt(1) as ViewGroup).getChildAt(1) as TextView
        // The avatar of the design shows the sample's initials; yours replace them
        Backend.me?.let { bindAvatar(header.getChildAt(0) as TextView, it.uid, it.name) }
        sticker = card.getChildAt(3) as TextView
        hearts = card.getChildAt(4) as ViewGroup
        val bottom = column.getChildAt(1) as ViewGroup
        viewers = bottom.getChildAt(0) as ViewGroup
        seenBy = viewers.getChildAt(1) as TextView
        // Photo or video of the story (added after the lookups above, because it takes a place in the card)
        media = StoryMediaView(this, card, art)

        // Nothing of the sample story stays on screen while the real one loads
        art.setImageDrawable(null)
        setVisible(sticker, false)
        setVisible(hearts, false)

        linkBack(this, R.id.btnClose)

        viewers.setOnClickListener { showViewers() }
        bottom.getChildAt(1).setOnClickListener { openScreen(this, StoryCameraActivity::class.java) }   // Create
        bottom.getChildAt(2).setOnClickListener {                                                          // Highlight
            Toast.makeText(this, "Highlights are not available yet", Toast.LENGTH_SHORT).show()
        }
        bottom.getChildAt(3).setOnClickListener {                                                          // Send
            Toast.makeText(this, "Sending stories is not available yet", Toast.LENGTH_SHORT).show()
        }
        bottom.getChildAt(4).setOnClickListener { askToDelete() }                                          // More
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople { people = it; showCounters() }
        listener = Backend.listenStories { all ->
            val me = Backend.myUid
            mine = all.filter { it.authorId == me }
            if (!loaded) {
                loaded = true
                startPlayer()
            } else {
                showCounters()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
        player?.hold("screen", true)
    }

    override fun onResume() {
        super.onResume()
        player?.hold("screen", false)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.stop()
        if (::media.isInitialized) {
            media.release()
        }
    }

    /** (Re)starts at the newest story; the older ones are before it. */
    private fun startPlayer() {
        player?.stop()
        player = null
        if (mine.isEmpty()) {
            Toast.makeText(this, "You have no story right now", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val column = childGroup(findViewById(R.id.root), 0)
        val card = childGroup(column, 0)
        player = StoryPlayer(
            this, findViewById(R.id.storyProgress), card, mine,
            { show(it) },
            { finish() }
        )
        player?.onHoldChanged = { media.setHeld(it) }
        player?.start(mine.size - 1)
    }

    private fun show(story: Story) {
        current = story
        media.show(story) { duration -> player?.videoReady(story.id, duration) }
        subtitle.text = timeAgo(story.time) + " · " + audienceLabel(story)
        sticker.text = story.text
        setVisible(sticker, story.text.isNotEmpty())
        showCounters()
    }

    /** "Seen by", the viewers' avatars and the floating hearts, taken from the latest data of the current story. */
    private fun showCounters() {
        val shown = current ?: return
        val story = mine.firstOrNull { it.id == shown.id } ?: shown

        val seen = story.viewedBy
        seenBy.text = if (seen.isEmpty()) "No views yet" else "Seen by ${seen.size}"

        // The design has room for three avatars
        val avatars = viewers.getChildAt(0) as ViewGroup
        for (i in 0 until avatars.childCount) {
            val avatar = avatars.getChildAt(i) as TextView
            val viewer = if (i < seen.size) people[seen[i]] else null
            if (viewer == null) {
                setVisible(avatar, false)
            } else {
                setVisible(avatar, true)
                bindAvatar(avatar, viewer.uid, viewer.name)
            }
        }

        // One heart per reaction, up to the five of the design (the biggest ones are at the bottom)
        val likes = story.likedBy.size
        setVisible(hearts, likes > 0)
        for (i in 0 until hearts.childCount) {
            setVisible(hearts.getChildAt(i), i >= hearts.childCount - likes)
        }
    }

    private fun showViewers() {
        val story = current?.let { shown -> mine.firstOrNull { it.id == shown.id } ?: shown } ?: return
        val names = story.viewedBy.map { people[it]?.name ?: "Kinnect user" }
        val builder = AlertDialog.Builder(this).setTitle("Seen by")
        if (names.isEmpty()) {
            builder.setMessage("No one has seen this story yet.")
        } else {
            builder.setItems(names.toTypedArray(), null)
        }
        builder.setPositiveButton("Close", null).show()
    }

    private fun askToDelete() {
        val story = current ?: return
        AlertDialog.Builder(this)
            .setItems(arrayOf("Delete this story")) { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Delete this story?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        Backend.deleteStory(story.id)
                        mine = mine.filter { it.id != story.id }
                        startPlayer()
                    }
                    .show()
            }
            .show()
    }
}
