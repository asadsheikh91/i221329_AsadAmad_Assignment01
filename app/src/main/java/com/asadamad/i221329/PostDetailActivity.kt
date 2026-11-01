package com.asadamad.i221329

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 5: one post with the reaction picker open. It is reached by holding the Like button of a post
// in the feed. Pick a reaction to react (it is saved on the post and the author is notified); tap the dim
// area or the back arrow to close without reacting.
class PostDetailActivity : AppCompatActivity() {

    private var postListener: ListenerRegistration? = null
    private var commentListener: ListenerRegistration? = null
    private var post: Post? = null
    private lateinit var postId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_post_detail)

        postId = intent.getStringExtra(EXTRA_POST_ID) ?: run {
            finish()
            return
        }

        linkBack(this, R.id.btnBack)
        linkBack(this, R.id.scrim)

        // Each reaction of the picker reacts to the post and closes the picker
        react(R.id.reactLike, "Like")
        react(R.id.reactLove, "Love")
        react(R.id.reactHaha, "Haha")
        react(R.id.reactWow, "Wow")
        react(R.id.reactSad, "Sad")
        react(R.id.reactAngry, "Angry")
    }

    override fun onStart() {
        super.onStart()
        if (!::postId.isInitialized) {
            return
        }
        postListener = Backend.listenPost(postId) { loaded ->
            if (loaded == null) {
                Toast.makeText(this, "This post has been deleted", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                post = loaded
                showPost(loaded)
            }
        }
        commentListener = Backend.listenComments(postId) { showComment(it.lastOrNull()) }
    }

    override fun onStop() {
        super.onStop()
        postListener?.remove()
        postListener = null
        commentListener?.remove()
        commentListener = null
    }

    private fun react(viewId: Int, type: String) {
        findViewById<View>(viewId).setOnClickListener {
            val current = post
            if (current == null) {
                Toast.makeText(this, "Still loading the post", Toast.LENGTH_SHORT).show()
            } else {
                Backend.reactToPost(current, type)
                finish()
            }
        }
    }

    /** Header, text, photo, counts and the Like button of the post. */
    private fun showPost(post: Post) {
        val root = findViewById<View>(R.id.root)

        // Title bar: "Lina's post"
        findTextView(root, "'s post")?.text = post.authorName.substringBefore(' ') + "'s post"

        // Header: avatar, bold name, time
        val header = findViewById<ViewGroup>(R.id.postHeader)
        (header.getChildAt(0) as TextView).apply {
            text = initialsOf(post.authorName)
            backgroundTintList = ColorStateList.valueOf(getColor(avatarColorOf(post.authorId)))
        }
        val column = header.getChildAt(1) as ViewGroup
        (column.getChildAt(0) as TextView).text = boldThen(this, post.authorName, "")
        ((column.getChildAt(1) as ViewGroup).getChildAt(0) as TextView).text = timeAgo(post.time) + " ·"

        val text = findViewById<TextView>(R.id.postText)
        text.text = post.text
        setVisible(text, post.text.isNotEmpty())

        val photo = findViewById<ImageView>(R.id.photo)
        if (post.imageBase64 != null) {
            setVisible(photo, true)
            PostImages.show(photo, post.id, post.imageBase64)
        } else {
            setVisible(photo, false)
        }

        // Counts: [reaction circles, who reacted, number of comments]
        val counts = findViewById<ViewGroup>(R.id.counts)
        setVisible(counts.getChildAt(0), post.likedBy.isNotEmpty())
        (counts.getChildAt(1) as TextView).text = likesText(post)
        (counts.getChildAt(2) as TextView).text = when (post.commentCount) {
            0 -> ""
            1 -> "1 comment"
            else -> "${post.commentCount} comments"
        }

        // Actions: [divider, row of Like / Comment / Share]
        val buttons = (findViewById<ViewGroup>(R.id.actions).getChildAt(1)) as ViewGroup
        val like = buttons.getChildAt(0) as ViewGroup
        showReaction(
            this, like.getChildAt(0) as ImageView, like.getChildAt(1) as TextView,
            if (post.likedByMe) (post.myReaction ?: "Like") else null
        )
        like.setOnClickListener { Backend.toggleLike(post) }
        buttons.getChildAt(1).setOnClickListener {
            val intent = Intent(this, CommentsActivity::class.java)
            intent.putExtra(EXTRA_POST_ID, post.id)
            intent.putExtra(EXTRA_LIKE_COUNT, post.likedBy.size)
            startActivity(intent)
        }
        buttons.getChildAt(2).setOnClickListener {
            Toast.makeText(this, "Sharing is not available yet", Toast.LENGTH_SHORT).show()
        }
    }

    /** The newest comment under the post (the design shows one comment); no comments hides the section. */
    private fun showComment(comment: Comment?) {
        val section = findViewById<ViewGroup>(R.id.commentsSection)
        setVisible(section, comment != null)
        if (comment == null) {
            return
        }
        val row = section.getChildAt(2) as ViewGroup
        (row.getChildAt(0) as TextView).apply {
            text = initialsOf(comment.authorName)
            backgroundTintList = ColorStateList.valueOf(getColor(avatarColorOf(comment.authorId)))
        }
        val bubble = row.getChildAt(1) as ViewGroup
        (bubble.getChildAt(0) as TextView).text = comment.authorName
        (bubble.getChildAt(1) as TextView).text = comment.text
    }
}
