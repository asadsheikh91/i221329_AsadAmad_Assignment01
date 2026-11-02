package com.kinnect.i221329_i221069

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 4: home feed. Posts, stories and the number of unread chats all come live from Firestore.
class HomeActivity : AppCompatActivity() {

    private val listeners = ArrayList<ListenerRegistration>()

    private var stories: List<Story> = emptyList()
    private var allPosts: List<Post> = emptyList()
    private var friendUids: Set<String> = emptySet()
    private var friendsLoaded = false
    private var createAvatar: TextView? = null   // the avatar on the "Create story" card

    // Android 13 and later: the user has to allow push notifications
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)
        askForNotificationPermission()
        createAvatar = findTextExact(findViewById(R.id.storyCreate), "JW")

        // Greeting uses the signed-in user's profile (loaded from Firestore if we do not have it yet)
        showGreeting(Backend.me)
        if (Backend.me == null) {
            Backend.loadMe { showGreeting(it) }
        }

        setupTabs(this, TAB_HOME)

        // The three sample story cards of the design are templates: real cards are added in showStories()
        for (id in intArrayOf(R.id.storyOmar, R.id.storySara, R.id.storyHamza)) {
            setVisible(findViewById(id), false)
        }

        // Header
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnChats, ChatsActivity::class.java)
        setVisible(firstTextView(findViewById(R.id.btnChats))!!, false)

        // Composer
        linkTo(this, R.id.composerAvatar, ProfileActivity::class.java)
        linkTo(this, R.id.tvComposerHint, CreatePostActivity::class.java)
        linkTo(this, R.id.btnComposerPhoto, CreatePostActivity::class.java)

        // Stories: the first card makes a new one
        linkTo(this, R.id.storyCreate, StoryCameraActivity::class.java)
    }

    // Everything on this screen follows Firestore while it is visible
    override fun onStart() {
        super.onStart()
        // Posts of everybody arrive; the feed keeps the ones of the user and their friends
        listeners.add(Backend.listenPosts {
            allPosts = it
            showPosts()
        })

        listeners.add(Backend.listenFriendships { list ->
            friendUids = list.filter { it.accepted }.map { it.otherUid }.toSet()
            friendsLoaded = true
            showStories()
            showPosts()
        })
        // My own stories older than 24 hours are removed
        Backend.deleteExpiredStories()
        listeners.add(Backend.listenStories {
            stories = it
            showStories()
        })

        // The number on the chat button is the number of conversations with something unread
        listeners.add(Backend.listenChats { chats ->
            val unread = chats.count { it.unread }
            val badge = firstTextView(findViewById(R.id.btnChats))!!
            badge.text = if (unread > 99) "99+" else unread.toString()
            setVisible(badge, unread > 0)
        })
    }

    override fun onStop() {
        super.onStop()
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun showPosts() {
        if (!friendsLoaded) {
            return
        }
        val me = Backend.myUid
        val posts = allPosts.filter { it.authorId == me || it.authorId in friendUids }
        val container = findViewById<LinearLayout>(R.id.postsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (post in posts) {
            val row = inflater.inflate(R.layout.item_post, container, false)
            bindPost(row, post, this)
            container.addView(row)
            // A band between posts, like between the composer and the stories
            val band = View(this)
            band.setBackgroundColor(getColor(R.color.band))
            container.addView(band, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (8 * resources.displayMetrics.density).toInt()
            ))
        }
        findViewById<View>(R.id.tvNoPosts).visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
    }

    /** One card per person who has a story I may see: mine first, then people with unseen stories. */
    private fun showStories() {
        val myUid = Backend.myUid ?: return
        val row = findViewById<View>(R.id.storyOmar).parent as LinearLayout
        val template = findViewById<View>(R.id.storyOmar)

        // Throw away the cards of the last time
        for (i in row.childCount - 1 downTo 0) {
            if (row.getChildAt(i).tag == "story") {
                row.removeViewAt(i)
            }
        }

        val visible = stories.filter { canSeeStory(it, myUid, friendUids) }
        val byAuthor = visible.groupBy { it.authorId }
        val authors = byAuthor.keys.sortedWith(
            compareBy<String> { it != myUid }
                .thenBy { author -> byAuthor.getValue(author).all { it.seenByMe } }
                .thenByDescending { author -> byAuthor.getValue(author).maxOf { it.time } }
        )

        val inflater = LayoutInflater.from(this)
        for (author in authors) {
            val list = byAuthor.getValue(author)
            val latest = list.last()
            val card = inflater.inflate(R.layout.item_story, row, false)
            val name = if (author == myUid) "Your story" else latest.authorName
            bindStoryCard(card, initialsOf(latest.authorName), avatarColorOf(author), name)
            bindAvatar(card.findViewById(R.id.tvInitials), author, latest.authorName)
            PostImages.show(card.findViewById<ImageView>(R.id.storyArt), "story_thumb_${latest.id}", latest.thumb)

            // Stories I have already watched are a little dimmer
            card.alpha = if (list.all { it.seenByMe } && author != myUid) 0.6f else 1f
            card.tag = "story"
            card.setOnClickListener {
                if (author == myUid) {
                    openScreen(this, MyStoryActivity::class.java)
                } else {
                    startActivity(Intent(this, StoryViewerActivity::class.java).putExtra(EXTRA_UID, author))
                }
            }
            row.addView(card, LinearLayout.LayoutParams(template.layoutParams as LinearLayout.LayoutParams))
        }
    }

    private fun showGreeting(me: UserInfo?) {
        val firstName = me?.name?.substringBefore(' ') ?: intent.getStringExtra(EXTRA_FIRST_NAME)
        if (firstName != null) {
            findViewById<TextView>(R.id.tvComposerHint).text = "What's on your mind, " + firstName + "?"
        }
        if (me != null) {
            bindAvatar(findViewById(R.id.composerAvatar), me.uid, me.name)
            // The avatar on the "Create story" card shows the same picture
            createAvatar?.let { bindAvatar(it, me.uid, me.name) }
            sendNewUsersToProfileSetup(me)
        }
    }

    /** A first-time user picks a unique username and a bio (Edit profile) before using the app. */
    private fun sendNewUsersToProfileSetup(me: UserInfo) {
        if (!me.profileDone) {
            startActivity(Intent(this, EditProfileActivity::class.java).putExtra(EXTRA_SETUP, true))
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
