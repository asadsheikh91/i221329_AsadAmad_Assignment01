package com.kinnect.i221329_i221069

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Screen 17: another person's profile, reached from search, friend requests, notifications and the friends list.
// The first button follows the friendship: Add friend -> Requested -> Friends (or Accept request).
// Friend count, mutual friends, join date, cover photo and recent photos all come from Firestore.
class UserProfileActivity : AppCompatActivity() {

    private var friendListener: ListenerRegistration? = null
    private var myFriendsListener: ListenerRegistration? = null
    private var uid: String? = null
    private var friendship: Friendship? = null

    private var user: UserInfo? = null
    private var people: Map<String, UserInfo> = emptyMap()
    private var myFriendUids: Set<String> = emptySet()
    private var theirFriends: List<String> = emptyList()

    // Views of the design that have no id: found once, before any text on the screen changes
    private lateinit var countLine: TextView
    private lateinit var mutualLine: TextView
    private lateinit var mutualAvatars: List<TextView>
    private var joinedLine: TextView? = null
    private lateinit var photoTitleRow: View
    private lateinit var photoTiles: ViewGroup
    private lateinit var cover: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_user_profile)

        linkBack(this, R.id.btnBack)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        val root = findViewById<View>(R.id.root)
        countLine = findTextView(root, "1,204")!!
        mutualLine = findTextView(root, "Friends with")!!
        mutualAvatars = listOfNotNull(findTextExact(root, "AK"), findTextExact(root, "LM"), findTextExact(root, "ZR"))
        joinedLine = findTextView(root, "Joined")
        photoTitleRow = findTextExact(root, "Photos")!!.parent as View
        val scrollContent = (photoTitleRow.parent as ViewGroup)
        photoTiles = scrollContent.getChildAt(scrollContent.childCount - 1) as ViewGroup
        cover = allViews(scrollContent).filterIsInstance<ImageView>().first()

        // The design's sample details (job, town) are not part of a Kinnect profile; mutual friends and photos wait for data
        for (sample in listOf("Photographer", "Karachi, Pakistan")) {
            findTextView(root, sample)?.let { setVisible(it.parent as View, false) }
        }
        setVisible(mutualLine.parent as View, false)
        setVisible(photoTitleRow, false)
        setVisible(photoTiles, false)

        uid = intent.getStringExtra(EXTRA_UID)
        val other = uid ?: return   // opened without a person: keep the layout as it is

        findViewById<View>(R.id.btnMessage).setOnClickListener { openChat(this, other) }
        findViewById<View>(R.id.btnAddFriend).setOnClickListener { onFriendButton(other) }

        Backend.loadUser(other) { loaded ->
            if (loaded != null) {
                user = loaded
                findViewById<TextView>(R.id.tvTitle).text = loaded.name
                findViewById<TextView>(R.id.tvName).text = loaded.name
                findViewById<TextView>(R.id.tvBio).text = loaded.bio
                bindAvatar(findViewById(R.id.tvInitials), loaded.uid, loaded.name)
                showJoined(loaded.joined)
                showFriendInfo()
            }
        }
        Backend.loadCover(other) { image ->
            if (image != null) {
                PostImages.show(cover, "cover_${other}_${image.hashCode()}", image)
            }
        }
        Backend.loadFriendUids(other) {
            theirFriends = it
            showFriendInfo()
        }
        Backend.loadPostsOf(other) { posts -> showPhotos(posts.filter { it.imageCount > 0 }.take(3)) }
    }

    override fun onStart() {
        super.onStart()
        val other = uid ?: return
        friendListener = Backend.listenFriendship(other) {
            friendship = it
            findViewById<TextView>(R.id.tvAddFriend).text = when {
                it == null -> "Add friend"
                it.accepted -> "Friends"
                it.sentByMe -> "Requested"
                else -> "Accept request"
            }
        }
        Backend.loadPeople {
            people = it
            showFriendInfo()
        }
        myFriendsListener = Backend.listenFriendships { list ->
            myFriendUids = list.filter { it.accepted }.map { it.otherUid }.toSet()
            showFriendInfo()
        }
    }

    override fun onStop() {
        super.onStop()
        friendListener?.remove()
        friendListener = null
        myFriendsListener?.remove()
        myFriendsListener = null
    }

    private fun onFriendButton(other: String) {
        val f = friendship
        when {
            f == null -> Backend.sendFriendRequest(other)
            f.accepted || f.sentByMe -> Backend.removeFriendship(other)   // unfriend / cancel the request
            else -> Backend.acceptFriendRequest(other)
        }
    }

    private fun showJoined(joined: Long) {
        val text = joinedText(joined)
        val line = joinedLine ?: return
        if (text.isEmpty()) {
            setVisible(line.parent as View, false)
        } else {
            line.text = text
        }
    }

    /** "112 friends · 4 mutual" and "Friends with Aisha Khan and 3 others". */
    private fun showFriendInfo() {
        val shown = user ?: return
        val mutual = theirFriends.filter { it in myFriendUids && it != Backend.myUid }

        val count = theirFriends.size
        val line = friendCountText(this, count)
        if (mutual.isEmpty()) {
            countLine.text = line
        } else {
            val builder = android.text.SpannableStringBuilder(line)
            builder.append(" · ")
            builder.append(boldThen(this, mutual.size.toString(), " mutual"))
            countLine.text = builder
        }

        val named = mutual.mapNotNull { people[it] }
        if (named.isEmpty()) {
            setVisible(mutualLine.parent as View, false)
            return
        }
        setVisible(mutualLine.parent as View, true)
        mutualLine.text = mutualFriendsText(this, named.map { it.name })
        for (i in mutualAvatars.indices) {
            val circle = mutualAvatars[i]
            if (i < named.size) {
                setVisible(circle, true)
                bindAvatar(circle, named[i].uid, named[i].name)
            } else {
                circle.visibility = View.INVISIBLE
            }
        }
    }

    /** The person's three newest photos in the three tiles of the design. */
    private fun showPhotos(posts: List<Post>) {
        if (posts.isEmpty()) {
            return
        }
        setVisible(photoTitleRow, true)
        setVisible(photoTiles, true)
        for (i in 0 until photoTiles.childCount) {
            val tile = photoTiles.getChildAt(i) as ImageView
            if (i < posts.size) {
                setVisible(tile, true)
                PostImages.showPostImage(tile, posts[i].id, 0)
            } else {
                // Keep the tile's place so the pictures keep their size, but leave it empty
                tile.visibility = View.INVISIBLE
            }
        }
    }
}
