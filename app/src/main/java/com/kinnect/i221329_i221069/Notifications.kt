package com.kinnect.i221329_i221069

import com.google.firebase.database.ServerValue

// The notifications part of the backend. Something happens to you (a reaction, a comment, a friend request,
// a reaction to your story) and a small node is written into your inbox:
//
//   notifications/{toUid}/{id}   fromUid, fromName, type, text, refId, read, createdAt
//
// type is one of: like, comment, reply, request, accepted, story_like, story_reply, screenshot
// refId is the post / story / person the notification is about, so tapping it can open the right screen.
// Some ids are made from their parts (like_<post>_<person>) so reacting twice only ever makes one notification.

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
    val inbox = ref("notifications/$toUid")
    val key = id ?: inbox.push().key ?: return
    val notice = hashMapOf<String, Any?>(
        "fromUid" to uid,
        "fromName" to name,
        "type" to type,
        "text" to text,
        "refId" to refId,
        "read" to false,
        "createdAt" to ServerValue.TIMESTAMP
    )
    inbox.child(key).setValue(notice)
}

/** Takes a notification back (an unliked post, a cancelled friend request) from [ownerUid]'s inbox. */
fun Backend.removeNotice(ownerUid: String, id: String) {
    ref("notifications/$ownerUid/$id").removeValue()
}

/** Every notification of the signed-in user, newest first, now and whenever it changes. */
fun Backend.listenNotices(onChange: (List<Notice>) -> Unit): ListenerRegistration {
    val uid = myUid!!
    return listen(ref("notifications/$uid")) { snap ->
        val notices = snap.children.map { doc ->
            Notice(
                doc.key ?: "",
                doc.text("type"),
                doc.text("fromUid"),
                doc.text("fromName").ifEmpty { "Someone" },
                doc.text("text"),
                doc.text("refId"),
                doc.number("createdAt"),
                doc.flag("read")
            )
        }
        onChange(notices.sortedByDescending { it.time })
    }
}

/** Marks notifications as seen (the red number on the bell goes down). */
fun Backend.markNoticesRead(ids: List<String>) {
    val uid = myUid ?: return
    if (ids.isEmpty()) {
        return
    }
    val updates = HashMap<String, Any?>()
    for (id in ids) {
        updates["notifications/$uid/$id/read"] = true
    }
    root.updateChildren(updates)
}
