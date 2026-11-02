package com.kinnect.i221329_i221069

import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

// The notifications part of the backend. Something happens to you (a like, a comment, a friend request,
// a reaction to your story) and a small document is written into the notifications collection:
//
//   notifications/{id}   toUid, fromUid, fromName, type, text, refId, read, createdAt
//
// type is one of: like, comment, request, accepted, story_like, story_reply
// refId is the post / story / person the notification is about, so tapping it can open the right screen.
// Some ids are made from their parts (like_<post>_<person>) so liking twice only ever makes one notification.

data class Notice(
    val id: String,
    val type: String,
    val fromUid: String,
    val fromName: String,
    val text: String,
    val refId: String,
    val time: Long,
    val read: Boolean
)

/** Tells [toUid] that something happened. Does nothing when you are notifying yourself. */
fun Backend.sendNotice(toUid: String, type: String, text: String, refId: String, id: String? = null) {
    val uid = myUid ?: return
    val name = me?.name ?: return
    if (toUid == uid) {
        return
    }
    val collection = db.collection("notifications")
    val doc = if (id == null) collection.document() else collection.document(id)
    val notice = hashMapOf(
        "toUid" to toUid,
        "fromUid" to uid,
        "fromName" to name,
        "type" to type,
        "text" to text,
        "refId" to refId,
        "read" to false,
        "createdAt" to FieldValue.serverTimestamp()
    )
    doc.set(notice)
}

/** Takes a notification back (an unliked post, a cancelled friend request). */
fun Backend.removeNotice(id: String) {
    db.collection("notifications").document(id).delete()
}

/** Every notification of the signed-in user, newest first, now and whenever it changes. */
fun Backend.listenNotices(onChange: (List<Notice>) -> Unit): ListenerRegistration {
    val uid = myUid!!
    return db.collection("notifications").whereEqualTo("toUid", uid)
        .addSnapshotListener { snap, _ ->
            if (snap != null) {
                val notices = snap.documents.map { doc ->
                    val time = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)
                    Notice(
                        doc.id,
                        doc.getString("type") ?: "",
                        doc.getString("fromUid") ?: "",
                        doc.getString("fromName") ?: "Someone",
                        doc.getString("text") ?: "",
                        doc.getString("refId") ?: "",
                        time?.toDate()?.time ?: 0L,
                        doc.getBoolean("read") == true
                    )
                }
                // Sorted here instead of with orderBy so Firestore needs no composite index
                onChange(notices.sortedByDescending { it.time })
            }
        }
}

/** Marks notifications as seen (the red number on the bell goes down). */
fun Backend.markNoticesRead(ids: List<String>) {
    if (ids.isEmpty()) {
        return
    }
    val batch = db.batch()
    for (id in ids) {
        batch.update(db.collection("notifications").document(id), "read", true)
    }
    batch.commit()
}
