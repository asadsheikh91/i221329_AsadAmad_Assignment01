package com.kinnect.i221329_i221069

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Screen 6: the comments of one post, live from the database.
//  - every comment shows its time, Like and Reply; replies hang under their comment with a branch line
//  - Like puts a like on a comment (the count shows in a small chip on the bubble)
//  - Reply fills the "Replying to ..." bar; Cancel throws the reply away
//  - the camera icon adds a photo to the comment
//  - "Most relevant" switches between the most liked and the newest comments
class CommentsActivity : AppCompatActivity() {

    private var commentsListener: ListenerRegistration? = null
    private var postListener: ListenerRegistration? = null
    private lateinit var postId: String

    private var comments: List<Comment> = emptyList()
    private var newestFirst = false

    // What the next comment will be: an answer to this comment, with this photo
    private var replyTo: Comment? = null
    private var photo: String? = null
    private var photoLoading = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            photoLoading = true
            ImageTools.encode(this, uri, 800, 150_000, ImageTools.FILTER_NONE) { encoded ->
                photoLoading = false
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    photo = encoded
                    showReplyBar()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_comments)

        postId = intent.getStringExtra(EXTRA_POST_ID) ?: run {
            finish()
            return
        }

        linkBack(this, R.id.btnBack)

        val input = findViewById<EditText>(R.id.etComment)
        findViewById<View>(R.id.btnSendComment).setOnClickListener { send(input) }
        findViewById<View>(R.id.btnCancelReply).setOnClickListener {
            replyTo = null
            photo = null
            showReplyBar()
        }

        // The camera icon of the bar (it has no id: found by its description)
        allViews(findViewById(R.id.root)).firstOrNull { it.contentDescription == "Add photo" }
            ?.setOnClickListener { pickPhoto.launch("image/*") }

        // "Most relevant" / "Newest"
        findTextExact(findViewById(R.id.root), "Most relevant")?.let { label ->
            (label.parent as View).setOnClickListener {
                newestFirst = !newestFirst
                label.text = if (newestFirst) "Newest" else "Most relevant"
                showComments()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::postId.isInitialized) {
            return
        }
        commentsListener = Backend.listenComments(postId) {
            comments = it
            showComments()
        }
        // The strip under the title: who reacted to the post, live
        postListener = Backend.listenPost(postId) { post ->
            val row = findViewById<ViewGroup>(R.id.likesRow)
            if (post == null || post.likedBy.isEmpty()) {
                row.visibility = View.GONE
            } else {
                row.visibility = View.VISIBLE
                showReactionCircles(row.getChildAt(0) as ViewGroup, post)
                findViewById<TextView>(R.id.tvLikeCount).text = likesText(post)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        commentsListener?.remove()
        commentsListener = null
        postListener?.remove()
        postListener = null
    }

    // ------------------------------------------------------------------ writing

    private fun send(input: EditText) {
        val text = input.text.toString().trim()
        if (text.isEmpty() && photo == null) {
            return
        }
        if (photoLoading) {
            Toast.makeText(this, "Preparing your photo...", Toast.LENGTH_SHORT).show()
            return
        }
        // A reply to a reply is attached to the top-level comment, with the name in front
        val target = replyTo
        val parentId = if (target == null) "" else target.parentId.ifEmpty { target.id }
        val shown = if (target != null && target.parentId.isNotEmpty()) "@${target.authorName} $text" else text
        Backend.addComment(postId, shown, parentId, photo, target?.authorId) { saved ->
            if (!saved) {
                Toast.makeText(this, "Could not send. Check your connection.", Toast.LENGTH_SHORT).show()
            }
        }
        input.setText("")
        replyTo = null
        photo = null
        showReplyBar()
    }

    /** The "Replying to ..." line is shown while a reply or a photo is waiting to be sent. */
    private fun showReplyBar() {
        val bar = findViewById<View>(R.id.replyBar)
        val target = replyTo
        val line = findViewById<TextView>(R.id.tvReplyingTo)
        if (target == null && photo == null) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        val who = if (target != null) boldThen(this, "", "Replying to ").append(boldThen(this, target.authorName, "")) else null
        line.text = when {
            who != null && photo != null -> who.append("  ·  photo added")
            who != null -> who
            else -> "Photo added"
        }
        if (target != null) {
            findViewById<EditText>(R.id.etComment).requestFocus()
        }
    }

    // ------------------------------------------------------------------ showing

    private fun showComments() {
        val container = findViewById<LinearLayout>(R.id.commentsContainer)
        container.removeAllViews()

        val tops = comments.filter { it.parentId.isEmpty() }
        val ordered = if (newestFirst) tops.sortedByDescending { it.time } else tops.sortedWith(compareByDescending<Comment> { it.likedBy.size }.thenBy { it.time })
        val inflater = LayoutInflater.from(this)

        for (top in ordered) {
            container.addView(commentBlock(inflater, container, top, false), blockParams(10))
            // The replies of this comment, oldest first
            for (reply in comments.filter { it.parentId == top.id }.sortedBy { it.time }) {
                container.addView(commentBlock(inflater, container, reply, true), blockParams(2))
            }
        }
        findViewById<View>(R.id.tvNoComments).visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun blockParams(topMarginDp: Int): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(this, topMarginDp)
        return params
    }

    /** A comment (or a reply): avatar and bubble, the like chip, a photo, and the Time / Like / Reply line. */
    private fun commentBlock(inflater: LayoutInflater, parent: ViewGroup, comment: Comment, isReply: Boolean): View {
        val block = LinearLayout(this)
        block.orientation = LinearLayout.VERTICAL

        val row = inflater.inflate(R.layout.item_comment, block, false)
        bindComment(row, initialsOf(comment.authorName), avatarColorOf(comment.authorId), comment.authorName, comment.text)
        bindAvatar(row.findViewById(R.id.tvInitials), comment.authorId, comment.authorName)
        row.findViewById<View>(R.id.tvText).visibility = if (comment.text.isEmpty()) View.GONE else View.VISIBLE
        row.setOnClickListener { openUserProfile(this, comment.authorId) }
        if (isReply) {
            // Replies are a little smaller and sit under the comment they answer
            val avatar = row.findViewById<View>(R.id.tvInitials)
            avatar.layoutParams = avatar.layoutParams.apply { width = dp(this@CommentsActivity, 28); height = dp(this@CommentsActivity, 28) }
        }
        block.addView(row)

        // Like count chip, pulled up so it overlaps the bottom of the bubble (like the design)
        if (comment.likedBy.isNotEmpty()) {
            block.addView(likeChip(comment), chipParams())
        }

        // A photo sent with the comment
        if (comment.hasImage) {
            val picture = ImageView(this)
            picture.scaleType = ImageView.ScaleType.CENTER_CROP
            picture.setBackgroundResource(R.drawable.bg_round_12)
            picture.clipToOutline = true
            PostImages.showLoaded(picture, "comment_${comment.id}") { done -> Backend.loadCommentImage(comment.id, done) }
            val params = LinearLayout.LayoutParams(dp(this, 200), dp(this, 150))
            params.marginStart = dp(this, if (isReply) 52 else 60)
            params.topMargin = dp(this, 6)
            block.addView(picture, params)
        }

        block.addView(actionLine(comment, isReply))

        if (isReply) {
            // The curved thread branch of the design on the left of every reply
            val wrapper = LinearLayout(this)
            wrapper.orientation = LinearLayout.HORIZONTAL
            val branch = ImageView(this)
            branch.setImageResource(R.drawable.ic_thread_branch)
            tintImage(branch, getColor(R.color.border))
            val branchParams = LinearLayout.LayoutParams(dp(this, 24), dp(this, 28))
            branchParams.marginStart = dp(this, 38)
            wrapper.addView(branch, branchParams)
            wrapper.addView(block, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return wrapper
        }
        return block
    }

    private fun chipParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.END
        params.topMargin = dp(this, -10)
        params.marginEnd = dp(this, 20)
        return params
    }

    /** The small chip with a thumb and the number of likes. */
    private fun likeChip(comment: Comment): View {
        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.setBackgroundResource(R.drawable.bg_round_12)
        chip.backgroundTintList = ColorStateList.valueOf(getColor(R.color.white))
        chip.elevation = dp(this, 2).toFloat()
        chip.setPadding(dp(this, 6), dp(this, 2), dp(this, 8), dp(this, 2))
        val icon = ImageView(this)
        icon.setBackgroundResource(R.drawable.bg_react_like)
        icon.setImageResource(R.drawable.ic_thumb_filled)
        icon.setPadding(dp(this, 3), dp(this, 3), dp(this, 3), dp(this, 3))
        tintImage(icon, getColor(R.color.white))
        chip.addView(icon, LinearLayout.LayoutParams(dp(this, 18), dp(this, 18)))
        val count = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        count.text = comment.likedBy.size.toString()
        count.textSize = 13f
        count.setPadding(dp(this, 4), 0, 0, 0)
        chip.addView(count)
        return chip
    }

    /** "2h   Like   Reply" under a comment. Like turns teal once you liked it. */
    private fun actionLine(comment: Comment, isReply: Boolean): View {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        val start = if (isReply) 44 else 52
        line.setPadding(dp(this, start), dp(this, 4), 0, 0)

        val time = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        time.text = timeAgo(comment.time).ifEmpty { "now" }
        time.textSize = 14f
        line.addView(time)

        val like = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        like.text = "Like"
        like.textSize = 14f
        like.setTypeface(null, Typeface.BOLD)
        like.setTextColor(getColor(if (comment.likedByMe) R.color.teal else R.color.text_secondary))
        like.setPadding(dp(this, 16), dp(this, 6), dp(this, 6), dp(this, 6))
        like.setOnClickListener { Backend.toggleCommentLike(postId, comment) }
        line.addView(like)

        val reply = TextView(this, null, 0, R.style.Kin_Text_Secondary)
        reply.text = "Reply"
        reply.textSize = 14f
        reply.setTypeface(null, Typeface.BOLD)
        reply.setPadding(dp(this, 10), dp(this, 6), dp(this, 6), dp(this, 6))
        reply.setOnClickListener {
            replyTo = comment
            showReplyBar()
        }
        line.addView(reply)
        return line
    }
}
