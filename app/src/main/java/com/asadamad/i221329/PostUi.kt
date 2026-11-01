package com.asadamad.i221329

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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import java.util.concurrent.Executors

// Photos in posts, stories and listings. A photo is shrunk and stored as text (Base64) inside its
// Firestore document, so Kinnect needs no Firebase Storage. Decoding happens off the main thread and is cached.
object PostImages {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Reads the picture at [uri], shrinks it and returns it as Base64 JPEG (null if it cannot be read). */
    fun encode(context: Context, uri: Uri, done: (String?) -> Unit) {
        // Firestore documents may be 1 MB; 600 KB of picture leaves plenty of room
        ImageTools.encode(context, uri, 1024, 600_000, ImageTools.FILTER_NONE, done)
    }

    /** Shows the photo stored under [key] in [view] (decoded in the background, remembered afterwards). */
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

/** "You and 3 others", "5 likes", "You" ... the line next to the like circles. */
fun likesText(post: Post): String {
    val likes = post.likedBy.size
    return when {
        likes == 0 -> ""
        post.likedByMe && likes == 1 -> "You"
        post.likedByMe -> "You and ${likes - 1} other" + if (likes - 1 == 1) "" else "s"
        else -> "$likes like" + if (likes == 1) "" else "s"
    }
}

/** Fills one feed post (item_post) and wires its Like, Comment and Delete buttons. */
fun bindPost(row: View, post: Post, activity: Activity) {
    row.findViewById<TextView>(R.id.tvInitials).apply {
        text = initialsOf(post.authorName)
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, avatarColorOf(post.authorId)))
    }
    row.findViewById<TextView>(R.id.tvAuthor).text = post.authorName
    row.findViewById<TextView>(R.id.tvTime).text = timeAgo(post.time) + " ·"

    val text = row.findViewById<TextView>(R.id.tvPostText)
    text.text = post.text
    text.visibility = if (post.text.isEmpty()) View.GONE else View.VISIBLE

    val image = row.findViewById<ImageView>(R.id.postImage)
    if (post.imageBase64 != null) {
        image.visibility = View.VISIBLE
        PostImages.show(image, post.id, post.imageBase64)
    } else {
        image.visibility = View.GONE
    }

    // Likes and comments
    val likes = post.likedBy.size
    row.findViewById<View>(R.id.likeCircles).visibility = if (likes == 0) View.GONE else View.VISIBLE
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
    // A tap likes (or takes the like back); holding the button opens the reaction picker
    row.findViewById<View>(R.id.btnLike).setOnClickListener { Backend.toggleLike(post) }
    row.findViewById<View>(R.id.btnLike).setOnLongClickListener {
        activity.startActivity(Intent(activity, PostDetailActivity::class.java).putExtra(EXTRA_POST_ID, post.id))
        true
    }

    row.findViewById<View>(R.id.btnComment).setOnClickListener {
        val intent = Intent(activity, CommentsActivity::class.java)
        intent.putExtra(EXTRA_POST_ID, post.id)
        intent.putExtra(EXTRA_LIKE_COUNT, likes)
        activity.startActivity(intent)
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
