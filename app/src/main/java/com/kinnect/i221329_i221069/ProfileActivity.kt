package com.kinnect.i221329_i221069

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Screen 15: your own profile. Name, bio, cover photo, join date and friends all come from Firestore.
class ProfileActivity : AppCompatActivity() {

    private var people: Map<String, UserInfo> = emptyMap()
    private var friendUids: List<String> = emptyList()
    private var listener: ListenerRegistration? = null

    // Views of the design that have no id: found once, before any text on the screen changes
    private lateinit var friendCountViews: List<TextView>
    private lateinit var friendAvatars: List<TextView>
    private var joinedView: TextView? = null

    // The camera buttons of the design: a new profile picture or a new cover photo, saved at once
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            ImageTools.encode(this, uri, 256, 30_000, ImageTools.FILTER_NONE) { encoded ->
                val uid = Backend.myUid
                if (encoded == null || uid == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    rememberAvatar(uid, encoded)
                    bindAvatar(findViewById(R.id.tvInitials), uid, Backend.me?.name ?: "")
                    Backend.saveAvatar(encoded) { saved ->
                        if (!saved) Toast.makeText(this, "Could not save the picture", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val pickCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            ImageTools.encode(this, uri, 1000, 250_000, ImageTools.FILTER_NONE) { encoded ->
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    Backend.saveCover(encoded) { saved ->
                        if (saved) showCover() else Toast.makeText(this, "Could not save the cover photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Edit profile sends the new name and bio back through this launcher
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val name = it.data?.getStringExtra(EXTRA_NAME)
            val bio = it.data?.getStringExtra(EXTRA_BIO)
            showDetails(name, bio)
            showCover()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_profile)

        val root = findViewById<View>(R.id.root)
        friendCountViews = allViews(root).filterIsInstance<TextView>().filter { it.text.toString().contains("834") }
        friendAvatars = listOfNotNull(
            findTextExact(root, "LM"), findTextExact(root, "AK"), findTextExact(root, "OF")
        )
        joinedView = findTextView(root, "Joined")

        // The design's sample details (employer, school, town) are not part of a Kinnect profile
        for (sample in listOf("Pixsellz", "FAST-NUCES", "Karachi, Pakistan")) {
            findTextView(root, sample)?.let { setVisible(it.parent as View, false) }
        }
        // Friend circles stay hidden until the friends have loaded
        // (invisible, not gone: the circles share the row equally, so the others must keep their place)
        friendAvatars.forEach { it.visibility = View.INVISIBLE }

        val me = Backend.me
        if (me != null) {
            showDetails(me.name, me.bio)
            showJoined(me.joined)
        } else {
            Backend.loadMe {
                showDetails(it?.name, it?.bio)
                showJoined(it?.joined ?: 0L)
            }
        }
        showCover()

        linkBack(this, R.id.btnBack)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnAddStory, StoryCameraActivity::class.java)
        findTextExact(root, "Find friends")?.setOnClickListener { openScreen(this, SearchActivity::class.java) }

        // Edit profile is opened for a result so we get the changes back
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            editLauncher.launch(Intent(this, EditProfileActivity::class.java))
        }

        // The little camera buttons on the cover and on the avatar
        allViews(root).firstOrNull { it.contentDescription == "Change profile photo" }?.setOnClickListener { pickAvatar.launch("image/*") }
        allViews(root).firstOrNull { it.contentDescription == "Change cover photo" }?.setOnClickListener { pickCover.launch("image/*") }

        // The round "more" button next to Edit profile
        allViews(root).firstOrNull { it.contentDescription == "More" }?.setOnClickListener {
            AlertDialog.Builder(this)
                .setItems(arrayOf("Log out")) { _, _ -> logOut(this) }
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople {
            people = it
            showFriends()
        }
        listener = Backend.listenFriendships { list ->
            friendUids = list.filter { it.accepted }.map { it.otherUid }
            showFriends()
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    // Writes the name and bio into the screen; null means "keep what the layout already shows"
    private fun showDetails(name: String?, bio: String?) {
        if (name != null) {
            findViewById<TextView>(R.id.tvName).text = name
            findViewById<TextView>(R.id.tvTitle).text = name
            Backend.myUid?.let { bindAvatar(findViewById(R.id.tvInitials), it, name) }
        }
        if (bio != null) {
            findViewById<TextView>(R.id.tvBio).text = bio
        }
    }

    private fun showJoined(joined: Long) {
        val text = joinedText(joined)
        val view = joinedView ?: return
        if (text.isEmpty()) {
            setVisible(view.parent as View, false)
        } else {
            setVisible(view.parent as View, true)
            view.text = text
        }
    }

    private fun showCover() {
        val uid = Backend.myUid ?: return
        Backend.loadCover(uid) { cover ->
            if (cover != null) {
                // The key changes with the picture, so a new cover is never shown as the old one
                PostImages.show(findViewById<ImageView>(R.id.coverImage), "cover_${uid}_${cover.hashCode()}", cover)
            }
        }
    }

    /** The friend count (twice in the design) and the first three friends' circles. */
    private fun showFriends() {
        friendCountViews.forEach { it.text = friendCountText(this, friendUids.size) }
        for (i in friendAvatars.indices) {
            val friend = if (i < friendUids.size) people[friendUids[i]] else null
            val circle = friendAvatars[i]
            if (friend == null) {
                circle.visibility = View.INVISIBLE
            } else {
                setVisible(circle, true)
                bindAvatar(circle, friend.uid, friend.name)
            }
        }
    }
}
