package com.kinnect.i221329_i221069

import com.google.firebase.database.ServerValue

// The stories part of the backend. A story is a photo or a short video that disappears after 24 hours.
//
//   stories/{id}             authorId, authorName, type (image | video), thumb (small Base64 picture), text,
//                            audience, audienceUids/{uid}, viewedBy/{uid}, likedBy/{uid}: Like | Love | Haha,
//                            createdAt, expiresAt, chunks (how many pieces the media is stored in)
//   storyMedia/{id}/{000,001..}  the picture or video as Base64 text, cut into pieces of about 500 KB
//                            so a video never has to travel as one huge value
//
// audience is "friends" (all your friends) or "close" (only the people in audienceUids).

const val STORY_LIFETIME_MS = 24L * 60 * 60 * 1000
const val AUDIENCE_FRIENDS = "friends"
const val AUDIENCE_CLOSE = "close"
const val STORY_IMAGE = "image"
const val STORY_VIDEO = "video"

/** The pieces a story's media is cut into. */
private const val CHUNK_SIZE = 500_000

data class Story(
    val id: String,
    val authorId: String,
    val authorName: String,
    val thumb: String,
    val text: String,
    val audience: String,
    val audienceUids: List<String>,
    val viewedBy: List<String>,
    val likedBy: List<String>,
    val time: Long,
    val type: String = STORY_IMAGE,
    val expiresAt: Long = time + STORY_LIFETIME_MS
) {
    val seenByMe: Boolean get() = authorId == Backend.myUid || Backend.myUid in viewedBy
    val isVideo: Boolean get() = type == STORY_VIDEO
}

/**
 * Whether [myUid] may see [story]: their own, or a friend's story that was shared with everyone,
 * or with a close-friends list they are on.
 * Shortcut: this is decided on the phone, not by the security rules. Fine for a class project.
 */
fun canSeeStory(story: Story, myUid: String, friendUids: Set<String>): Boolean {
    if (story.authorId == myUid) {
        return true
    }
    if (story.authorId !in friendUids) {
        return false
    }
    return story.audience != AUDIENCE_CLOSE || myUid in story.audienceUids
}

/** "Friends" or "Close friends", shown under your own story. */
fun audienceLabel(story: Story): String = if (story.audience == AUDIENCE_CLOSE) "Close friends" else "Friends"

/**
 * Publishes a story. [media] is the big picture (or the video) as Base64 text and [thumb] the small
 * picture shown in the Home row. [type] is image or video.
 */
fun Backend.createStory(
    media: String, thumb: String, type: String, text: String, audience: String, audienceUids: List<String>,
    done: (Boolean) -> Unit
) {
    val uid = myUid
    val name = me?.name
    if (uid == null || name == null) {
        done(false)
        return
    }
    val storyId = ref("stories").push().key ?: run {
        done(false)
        return
    }
    val now = System.currentTimeMillis()
    val pieces = media.chunked(CHUNK_SIZE)

    val updates = HashMap<String, Any?>()
    updates["stories/$storyId/authorId"] = uid
    updates["stories/$storyId/authorName"] = name
    updates["stories/$storyId/type"] = type
    updates["stories/$storyId/thumb"] = thumb
    updates["stories/$storyId/text"] = text
    updates["stories/$storyId/audience"] = audience
    for (friend in audienceUids) {
        updates["stories/$storyId/audienceUids/$friend"] = true
    }
    updates["stories/$storyId/createdAt"] = ServerValue.TIMESTAMP
    updates["stories/$storyId/expiresAt"] = now + STORY_LIFETIME_MS
    updates["stories/$storyId/chunks"] = pieces.size
    pieces.forEachIndexed { index, piece -> updates["storyMedia/$storyId/" + index.toString().padStart(3, '0')] = piece }
    root.updateChildren(updates)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** Every story that has not expired yet, from everyone. The screens keep the ones the user may see. */
fun Backend.listenStories(onChange: (List<Story>) -> Unit): ListenerRegistration {
    return listen(ref("stories").orderByChild("expiresAt").startAt(System.currentTimeMillis().toDouble())) { snap ->
        val stories = snap.children.map { doc ->
            Story(
                doc.key ?: "",
                doc.text("authorId"),
                doc.text("authorName").ifEmpty { "Kinnect user" },
                doc.text("thumb"),
                doc.text("text"),
                doc.text("audience").ifEmpty { AUDIENCE_FRIENDS },
                doc.keys("audienceUids"),
                doc.keys("viewedBy"),
                doc.keys("likedBy"),
                doc.number("createdAt"),
                doc.text("type").ifEmpty { STORY_IMAGE },
                doc.number("expiresAt")
            )
        }
        // Oldest first, the way a person posts them and the way they are watched
        onChange(stories.sortedBy { it.time })
    }
}

/** The big picture (or the video) of a story as Base64 text (null when it cannot be loaded). */
fun Backend.loadStoryMedia(storyId: String, done: (String?) -> Unit) {
    once(ref("storyMedia/$storyId")) { snap ->
        if (snap == null || !snap.exists()) {
            done(null)
        } else {
            // The pieces are named 000, 001, ... so the database returns them in order
            done(snap.children.joinToString("") { it.value as? String ?: "" })
        }
    }
}

/** Adds the signed-in user to "Seen by" (not for their own stories). */
fun Backend.markStoryViewed(story: Story) {
    val uid = myUid ?: return
    if (story.authorId == uid || uid in story.viewedBy) {
        return
    }
    ref("stories/${story.id}/viewedBy/$uid").setValue(ServerValue.TIMESTAMP)
}

/** A quick reaction to a story. [kind] is "Like", "Love" or "Haha". */
fun Backend.reactToStory(story: Story, kind: String) {
    val uid = myUid ?: return
    ref("stories/${story.id}/likedBy/$uid").setValue(kind)
    sendNotice(story.authorId, "story_like", kind, story.id, "storylike_${story.id}_$uid")
}

/** A written reply to a story: it lands in the chat with the author and in their notifications. */
fun Backend.replyToStory(story: Story, text: String) {
    sendMessage(story.authorId, "Replied to your story: $text")
    sendNotice(story.authorId, "story_reply", text.take(60), story.id)
}

fun Backend.deleteStory(storyId: String) {
    val updates = HashMap<String, Any?>()
    updates["stories/$storyId"] = null
    updates["storyMedia/$storyId"] = null
    root.updateChildren(updates)
}

/** Removes my stories that are older than 24 hours (they are hidden anyway; this also frees the space). */
fun Backend.deleteExpiredStories() {
    val uid = myUid ?: return
    once(ref("stories").orderByChild("authorId").equalTo(uid)) { snap ->
        val now = System.currentTimeMillis()
        snap?.children?.forEach {
            if (it.number("expiresAt") in 1 until now) {
                deleteStory(it.key ?: return@forEach)
            }
        }
    }
}
