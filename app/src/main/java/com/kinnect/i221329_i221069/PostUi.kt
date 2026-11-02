package com.kinnect.i221329_i221069

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import java.util.concurrent.Executors

// Pictures of posts, stories, comments and chats. A picture is shrunk and stored as text (Base64) in the
// database, so Kinnect needs no Firebase Storage. Decoding happens off the main thread and is cached.
object PostImages {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Reads the picture at [uri], shrinks it and returns it as Base64 JPEG (null if it cannot be read). */
    fun encode(context: Context, uri: Uri, done: (String?) -> Unit) {
        // A post can have many pictures, so each one is kept small (about 900 pixels, 250 KB)
        ImageTools.encode(context, uri, 900, 250_000, ImageTools.FILTER_NONE, done)
    }

    /** Shows the picture stored under [key] in [view] (decoded in the background, remembered afterwards). */
    fun show(view: ImageView, key: String, base64: String) {
        view.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        worker.execute {
            val bitmap = ImageTools.fromBase64(base64)
            if (bitmap != null) {
                cache.put(key, bitmap)
                main.post {
                    // The row may have been reused for another picture while we were decoding
                    if (view.tag == key) {
                        view.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    /**
     * Shows a picture that still lives in the database. [fetch] loads its Base64 text (it is only called
     * when the picture is not in memory yet).
     */
    fun showLoaded(view: ImageView, key: String, fetch: ((String?) -> Unit) -> Unit) {
        view.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }
        view.setImageDrawable(null)
        fetch { base64 ->
            if (base64 != null && view.tag == key) {
                show(view, key, base64)
            }
        }
    }

    /** Picture number [index] of post [postId]. */
    fun showPostImage(view: ImageView, postId: String, index: Int) {
        showLoaded(view, "post_${postId}_$index") { done -> Backend.loadPostImage(postId, index, done) }
    }
}

/** Dresses a Like button (its icon and its label) for [reaction]; null means the post is not reacted to. */
fun showReaction(context: Context, icon: ImageView, label: TextView, reaction: String?) {
    val tint = ContextCompat.getColor(context, when (reaction) {
        null -> R.color.text_secondary
        "Love" -> R.color.love
        "Haha", "Wow", "Sad" -> R.color.gold
        "Angry" -> R.color.rust
        else -> R.color.teal
    })
    icon.setImageResource(when (reaction) {
        "Love" -> R.drawable.ic_heart_filled
        "Haha" -> R.drawable.ic_face_haha
        "Wow" -> R.drawable.ic_face_wow
        "Sad" -> R.drawable.ic_face_sad
        "Angry" -> R.drawable.ic_face_angry
        else -> R.drawable.ic_like
    })
    ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint))
    label.setTextColor(tint)
    label.text = when (reaction) {
        null -> "Like"
        "Like" -> "Liked"
        else -> reaction
    }
}

/** "You and 3 others", "5 likes", "You" ... the line next to the reaction circles. */
fun likesText(post: Post): String {
    val likes = post.likedBy.size
    return when {
        likes == 0 -> ""
        post.likedByMe && likes == 1 -> "You"
        post.likedByMe -> "You and ${likes - 1} other" + if (likes - 1 == 1) "" else "s"
        else -> "$likes like" + if (likes == 1) "" else "s"
    }
}

/**
 * Fills the (up to three) overlapping circles of the design with the reactions people really chose,
 * the most used first. [circles] is the row that holds the three round icons.
 */
fun showReactionCircles(circles: ViewGroup, post: Post) {
    val ranked = post.reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    for (i in 0 until circles.childCount) {
        val circle = circles.getChildAt(i) as ImageView
        val type = ranked.getOrNull(i)
        if (type == null) {
            circle.visibility = View.GONE
            continue
        }
        circle.visibility = View.VISIBLE
        circle.setBackgroundResource(when (type) {
            "Love" -> R.drawable.bg_react_love
            "Haha", "Wow", "Sad" -> R.drawable.bg_react_gold
            "Angry" -> R.drawable.bg_react_rust
            else -> R.drawable.bg_react_like
        })
        circle.setImageResource(when (type) {
            "Love" -> R.drawable.ic_heart_filled
            "Haha" -> R.drawable.ic_face_haha
            "Wow" -> R.drawable.ic_face_wow
            "Sad" -> R.drawable.ic_face_sad
            "Angry" -> R.drawable.ic_face_angry
            else -> R.drawable.ic_thumb_filled
        })
        // Thumb and heart are white on the coloured circle; the faces are drawn in their own colours
        val white = type == "Like" || type == "Love"
        ImageViewCompat.setImageTintList(
            circle, if (white) ColorStateList.valueOf(ContextCompat.getColor(circle.context, R.color.white)) else null
        )
    }
}

/** Shows the pictures of a post: one big photo, or the collage with "+N" for the rest. */
fun showPostPictures(row: View, post: Post) {
    val single = row.findViewById<ImageView>(R.id.postImage)
    val collage = row.findViewById<View>(R.id.photoCollage)
    val count = post.imageCount

    single.visibility = if (count == 1) View.VISIBLE else View.GONE
    collage.visibility = if (count >= 2) View.VISIBLE else View.GONE
    if (count == 1) {
        PostImages.showPostImage(single, post.id, 0)
    } else if (count >= 2) {
        PostImages.showPostImage(row.findViewById(R.id.collageLeft), post.id, 0)
        PostImages.showPostImage(row.findViewById(R.id.collageTop), post.id, 1)
        // With only two pictures the second one fills the whole right side
        val bottomCell = row.findViewById<View>(R.id.collageBottomCell)
        bottomCell.visibility = if (count >= 3) View.VISIBLE else View.GONE
        if (count >= 3) {
            PostImages.showPostImage(row.findViewById(R.id.collageBottom), post.id, 2)
        }
        val more = row.findViewById<TextView>(R.id.tvMorePhotos)
        more.visibility = if (count > 3) View.VISIBLE else View.GONE
        more.text = "+" + (count - 3)
    }
}

/** Asks which friend to send a post to and shares it into that chat. */
fun sharePostWithFriend(activity: Activity, post: Post) {
    val me = Backend.myUid ?: return
    Backend.loadFriendUids(me) { friendUids ->
        Backend.loadPeople { people ->
            val friends = friendUids.mapNotNull { people[it] }
            if (friends.isEmpty()) {
                Toast.makeText(activity, "Add a friend first to share posts", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(activity)
                    .setTitle("Send to")
                    .setItems(friends.map { it.name }.toTypedArray()) { _, which ->
                        Backend.sendPostMessage(friends[which].uid, post)
                        Toast.makeText(activity, "Sent to ${friends[which].name}", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
}

/** Fills one feed post (item_post) and wires its Like, Comment, Share and Delete buttons. */
fun bindPost(row: View, post: Post, activity: Activity) {
    bindAvatar(row.findViewById(R.id.tvInitials), post.authorId, post.authorName)
    row.findViewById<TextView>(R.id.tvAuthor).text = post.authorName
    row.findViewById<TextView>(R.id.tvTime).text = timeAgo(post.time) + " ·"

    val text = row.findViewById<TextView>(R.id.tvPostText)
    text.text = post.text
    text.visibility = if (post.text.isEmpty()) View.GONE else View.VISIBLE

    showPostPictures(row, post)

    // Reactions and comments
    val likes = post.likedBy.size
    val circles = row.findViewById<ViewGroup>(R.id.likeCircles)
    circles.visibility = if (likes == 0) View.GONE else View.VISIBLE
    showReactionCircles(circles, post)
    row.findViewById<TextView>(R.id.tvLikes).text = likesText(post)
    row.findViewById<TextView>(R.id.tvComments).text = when (post.commentCount) {
        0 -> ""
        1 -> "1 comment"
        else -> "${post.commentCount} comments"
    }

    // The Like button shows your reaction: a teal thumb for Like, a heart for Love, a face for the others
    showReaction(
        activity, row.findViewById(R.id.ivLike), row.findViewById(R.id.tvLike),
        if (post.likedByMe) (post.myReaction ?: "Like") else null
    )
    // A tap likes (or takes the reaction back); holding the button opens the reaction picker
    row.findViewById<View>(R.id.btnLike).setOnClickListener { Backend.toggleLike(post) }
    row.findViewById<View>(R.id.btnLike).setOnLongClickListener {
        activity.startActivity(Intent(activity, PostDetailActivity::class.java).putExtra(EXTRA_POST_ID, post.id))
        true
    }

    row.findViewById<View>(R.id.btnComment).setOnClickListener {
        val intent = Intent(activity, CommentsActivity::class.java)
        intent.putExtra(EXTRA_POST_ID, post.id)
        activity.startActivity(intent)
    }

    // Share sends the post to a friend's chat (the row of the design has no id: it is the Share label's parent)
    findTextExact(row, "Share")?.let { label ->
        (label.parent as View).setOnClickListener { sharePostWithFriend(activity, post) }
    }

    // Only the author can delete a post
    val delete = row.findViewById<View>(R.id.btnDelete)
    if (post.authorId == Backend.myUid) {
        delete.visibility = View.VISIBLE
        delete.setOnClickListener {
            AlertDialog.Builder(activity)
                .setMessage("Delete this post?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> Backend.deletePost(post.id) }
                .show()
        }
    } else {
        delete.visibility = View.GONE
    }
}
