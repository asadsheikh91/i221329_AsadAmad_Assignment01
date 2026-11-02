package com.kinnect.i221329_i221069

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

// Posts, reactions and comments in the Realtime Database.
//
//   posts/{postId}                    authorId, authorName, text, imageCount, commentCount, createdAt
//   posts/{postId}/reactions/{uid}    Like | Love | Haha | Wow | Sad | Angry   (one key per person, so one reaction each)
//   postImages/{postId}/{0,1,2...}    the pictures of a post (Base64), kept apart so the feed stays light
//   comments/{postId}/{commentId}     authorId, authorName, text, createdAt, parentId ("" or the comment it answers),
//                                     hasImage, likes/{uid}: true
//   commentImages/{commentId}         the picture of a comment (Base64)

val REACTIONS = arrayOf("Like", "Love", "Haha", "Wow", "Sad", "Angry")

/** Reads a post node. */
fun DataSnapshot.toPost(): Post {
    val reactions = HashMap<String, String>()
    for (r in child("reactions").children) {
        val uid = r.key
        val type = r.value as? String
        if (uid != null && type != null) {
            reactions[uid] = type
        }
    }
    return Post(
        key ?: "",
        text("authorId"),
        text("authorName").ifEmpty { "Kinnect user" },
        text("text"),
        number("imageCount").toInt(),
        reactions,
        number("commentCount").toInt(),
        number("createdAt")
    )
}

/**
 * Shares a post: [images] are the pictures as Base64 text (none for a text post, several for a collage).
 * Shortcut: the pictures live inside the database instead of Firebase Storage, which needs a paid plan.
 */
fun Backend.createPost(text: String, images: List<String>, done: (Boolean) -> Unit) {
    val uid = myUid
    val name = me?.name
    if (uid == null || name == null) {
        done(false)
        return
    }
    val postId = ref("posts").push().key ?: run {
        done(false)
        return
    }
    val updates = HashMap<String, Any?>()
    updates["posts/$postId/authorId"] = uid
    updates["posts/$postId/authorName"] = name
    updates["posts/$postId/text"] = text
    updates["posts/$postId/imageCount"] = images.size
    updates["posts/$postId/commentCount"] = 0
    updates["posts/$postId/createdAt"] = ServerValue.TIMESTAMP
    images.forEachIndexed { index, picture -> updates["postImages/$postId/$index"] = picture }
    root.updateChildren(updates)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** The 80 newest posts of everybody, newest first. The Home screen keeps the ones of the user and their friends. */
fun Backend.listenPosts(onChange: (List<Post>) -> Unit): ListenerRegistration {
    return listen(ref("posts").orderByChild("createdAt").limitToLast(80)) { snap ->
        onChange(snap.children.map { it.toPost() }.sortedByDescending { it.time })
    }
}

/** The newest posts of one person, newest first (their photos on their profile). */
fun Backend.loadPostsOf(uid: String, done: (List<Post>) -> Unit) {
    once(ref("posts").orderByChild("authorId").equalTo(uid).limitToLast(30)) { snap ->
        done(snap?.children?.map { it.toPost() }?.sortedByDescending { it.time } ?: emptyList())
    }
}

/** The 30 newest posts of everybody, newest first (to pick one to share into a chat). */
fun Backend.loadRecentPosts(done: (List<Post>) -> Unit) {
    once(ref("posts").orderByChild("createdAt").limitToLast(30)) { snap ->
        done(snap?.children?.map { it.toPost() }?.sortedByDescending { it.time } ?: emptyList())
    }
}

/** One post, now and whenever it changes; null means it has been deleted. */
fun Backend.listenPost(postId: String, onChange: (Post?) -> Unit): ListenerRegistration {
    return listen(ref("posts/$postId")) { onChange(if (it.exists()) it.toPost() else null) }
}

/** Picture number [index] of a post as Base64 text. */
fun Backend.loadPostImage(postId: String, index: Int, done: (String?) -> Unit) {
    once(ref("postImages/$postId/$index")) { done(it?.value as? String) }
}

/** A tap on Like: likes the post, or takes the reaction back (whatever it was). */
fun Backend.toggleLike(post: Post) {
    if (post.likedByMe) {
        removeReaction(post)
    } else {
        reactToPost(post, "Like")
    }
}

/** Reacts to a post. Reacting again with the same reaction takes it back; another one replaces it. */
fun Backend.reactToPost(post: Post, type: String) {
    if (post.myReaction == type) {
        removeReaction(post)
        return
    }
    val uid = myUid ?: return
    ref("posts/${post.id}/reactions/$uid").setValue(type)
    sendNotice(post.authorId, "like", post.text.take(60), post.id, "like_${post.id}_$uid")
}

fun Backend.removeReaction(post: Post) {
    val uid = myUid ?: return
    ref("posts/${post.id}/reactions/$uid").removeValue()
    removeNotice(post.authorId, "like_${post.id}_$uid")
}

fun Backend.deletePost(postId: String) {
    val updates = HashMap<String, Any?>()
    updates["posts/$postId"] = null
    updates["postImages/$postId"] = null
    updates["comments/$postId"] = null
    root.updateChildren(updates)
}

// ---------------------------------------------------------------------- comments

private fun DataSnapshot.toComment() = Comment(
    key ?: "",
    text("authorId"),
    text("authorName").ifEmpty { "Kinnect user" },
    text("text"),
    number("createdAt"),
    text("parentId"),
    flag("hasImage"),
    keys("likes")
)

/** Every comment and reply of one post, oldest first. */
fun Backend.listenComments(postId: String, onChange: (List<Comment>) -> Unit): ListenerRegistration {
    return listen(ref("comments/$postId").orderByChild("createdAt")) { snap ->
        onChange(snap.children.map { it.toComment() })
    }
}

/**
 * Adds a comment, or a reply when [parentId] is the id of another comment. [imageBase64] is an optional
 * picture. The post's comment counter goes up in the same write.
 */
fun Backend.addComment(
    postId: String, text: String, parentId: String, imageBase64: String?,
    parentAuthorId: String?, done: (Boolean) -> Unit
) {
    val uid = myUid
    val name = me?.name
    if (uid == null || name == null) {
        done(false)
        return
    }
    val commentId = ref("comments/$postId").push().key ?: run {
        done(false)
        return
    }
    val updates = HashMap<String, Any?>()
    updates["comments/$postId/$commentId/authorId"] = uid
    updates["comments/$postId/$commentId/authorName"] = name
    updates["comments/$postId/$commentId/text"] = text
    updates["comments/$postId/$commentId/createdAt"] = ServerValue.TIMESTAMP
    updates["comments/$postId/$commentId/parentId"] = parentId
    updates["comments/$postId/$commentId/hasImage"] = imageBase64 != null
    if (imageBase64 != null) {
        updates["commentImages/$commentId"] = imageBase64
    }
    updates["posts/$postId/commentCount"] = ServerValue.increment(1)
    root.updateChildren(updates)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }

    // Tell the author of the post, and the author of the comment that was answered
    val snippet = text.ifEmpty { "sent a photo" }.take(60)
    once(ref("posts/$postId/authorId")) { snap ->
        val author = snap?.value as? String
        if (author != null) {
            sendNotice(author, "comment", snippet, postId)
        }
    }
    if (parentAuthorId != null) {
        sendNotice(parentAuthorId, "reply", snippet, postId)
    }
}

/** Likes a comment, or takes the like back. */
fun Backend.toggleCommentLike(postId: String, comment: Comment) {
    val uid = myUid ?: return
    val like = ref("comments/$postId/${comment.id}/likes/$uid")
    if (comment.likedByMe) like.removeValue() else like.setValue(true)
}

fun Backend.loadCommentImage(commentId: String, done: (String?) -> Unit) {
    once(ref("commentImages/$commentId")) { done(it?.value as? String) }
}
