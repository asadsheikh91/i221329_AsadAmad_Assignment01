package com.asadamad.i221329

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

// The stories part of the backend. A story is a photo that disappears after 24 hours.
//
//   stories/{id}              authorId, authorName, thumbBase64, text, audience, audienceUids[],
//                             viewedBy[], likedBy[], createdAtMs, expiresAtMs
//   stories/{id}/data/image   authorId, imageBase64 (the big picture; the small one in the story
//                             document is what the Home row shows, so that row stays light)
//
// audience is "friends" (all your friends) or "close" (only the people in audienceUids).

const val STORY_LIFETIME_MS = 24L * 60 * 60 * 1000
const val AUDIENCE_FRIENDS = "friends"
const val AUDIENCE_CLOSE = "close"

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
    val time: Long
) {
    val seenByMe: Boolean get() = authorId == Backend.myUid || Backend.myUid in viewedBy
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

/** Publishes a story. [picture] has the big and the small version of the photo. */
fun Backend.createStory(
    picture: EncodedPicture, text: String, audience: String, audienceUids: List<String>,
    done: (Boolean) -> Unit
) {
    val uid = myUid
    val name = me?.name
    if (uid == null || name == null) {
        done(false)
        return
    }
    val now = System.currentTimeMillis()
    val story = db.collection("stories").document()
    val batch = db.batch()
    batch.set(
        story,
        hashMapOf(
            "authorId" to uid,
            "authorName" to name,
            "thumbBase64" to picture.thumb,
            "text" to text,
            "audience" to audience,
            "audienceUids" to audienceUids,
            "viewedBy" to emptyList<String>(),
            "likedBy" to emptyList<String>(),
            "createdAtMs" to now,
            "expiresAtMs" to now + STORY_LIFETIME_MS,
            "createdAt" to FieldValue.serverTimestamp()
        )
    )
    batch.set(
        story.collection("data").document("image"),
        hashMapOf("authorId" to uid, "imageBase64" to picture.full)
    )
    batch.commit()
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** Every story that has not expired yet, from everyone. The screens keep the ones the user may see. */
fun Backend.listenStories(onChange: (List<Story>) -> Unit): ListenerRegistration {
    return db.collection("stories")
        .whereGreaterThan("expiresAtMs", System.currentTimeMillis())
        .addSnapshotListener { snap, _ ->
            if (snap != null) {
                val stories = snap.documents.map { doc ->
                    Story(
                        doc.id,
                        doc.getString("authorId") ?: "",
                        doc.getString("authorName") ?: "Kinnect user",
                        doc.getString("thumbBase64") ?: "",
                        doc.getString("text") ?: "",
                        doc.getString("audience") ?: AUDIENCE_FRIENDS,
                        (doc.get("audienceUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        (doc.get("viewedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        doc.getLong("createdAtMs") ?: 0L
                    )
                }
                // Oldest first, the way a person posts them and the way they are watched
                onChange(stories.sortedBy { it.time })
            }
        }
}

/** The big picture of a story as Base64 text (null when it cannot be loaded). */
fun Backend.loadStoryImage(storyId: String, done: (String?) -> Unit) {
    db.collection("stories").document(storyId).collection("data").document("image").get()
        .addOnSuccessListener { done(it.getString("imageBase64")) }
        .addOnFailureListener { done(null) }
}

/** Adds the signed-in user to "Seen by" (not for their own stories). */
fun Backend.markStoryViewed(story: Story) {
    val uid = myUid ?: return
    if (story.authorId == uid || uid in story.viewedBy) {
        return
    }
    db.collection("stories").document(story.id).update("viewedBy", FieldValue.arrayUnion(uid))
}

/** A quick reaction to a story. [kind] is "Like", "Love" or "Haha". */
fun Backend.reactToStory(story: Story, kind: String) {
    val uid = myUid ?: return
    db.collection("stories").document(story.id).update("likedBy", FieldValue.arrayUnion(uid))
    sendNotice(story.authorId, "story_like", kind, story.id, "storylike_${story.id}_$uid")
}

/** A written reply to a story: it lands in the chat with the author and in their notifications. */
fun Backend.replyToStory(story: Story, text: String) {
    sendMessage(story.authorId, "Replied to your story: $text")
    sendNotice(story.authorId, "story_reply", text.take(60), story.id)
}

fun Backend.deleteStory(storyId: String) {
    val story = db.collection("stories").document(storyId)
    val batch = db.batch()
    batch.delete(story.collection("data").document("image"))
    batch.delete(story)
    batch.commit()
}
