package com.kinnect.i221329_i221069

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

// Chat in the Realtime Database. A conversation between two people has the id pairId(a, b).
//
//   messages/{chatId}/{messageId}    sender, type (text | image | post | system), text, postId,
//                                    replyToId, replyToText, replyToSender, createdAt, editedAt, deleted,
//                                    hearts/{uid}: true
//   messageImages/{chatId}/{id}      the picture of an image message (Base64)
//   userChats/{uid}/{otherUid}       what the Chats list shows for one person: lastMessage, lastSender,
//                                    lastTime and unread (a counter that goes up when the other person writes)
//   pushQueue/{id}                   a request for a push notification (read by the sender in
//                                    notification-sender/, which talks to Firebase Cloud Messaging)

private fun DataSnapshot.toMessage() = Message(
    key ?: "",
    text("sender"),
    text("type").ifEmpty { MSG_TEXT },
    text("text"),
    text("postId"),
    text("replyToId"),
    text("replyToText"),
    text("replyToSender"),
    number("createdAt"),
    number("editedAt") > 0,
    flag("deleted"),
    keys("hearts")
)

/** The line the Chats list shows for a message. */
private fun previewOf(type: String, text: String): String = when (type) {
    MSG_IMAGE -> "Sent a photo"
    MSG_POST -> "Shared a post"
    else -> text
}

/**
 * Writes one message and updates both people's chat lists in a single atomic write.
 * [replyTo] is the message this one answers (null for a normal message).
 */
private fun Backend.pushMessage(
    otherUid: String, type: String, text: String, postId: String,
    imageBase64: String?, replyTo: Message?, push: Boolean = true
) {
    val uid = myUid ?: return
    val chatId = pairId(uid, otherUid)
    val messageId = ref("messages/$chatId").push().key ?: return
    val preview = previewOf(type, text)
    val base = "messages/$chatId/$messageId"

    val updates = HashMap<String, Any?>()
    updates["$base/sender"] = uid
    updates["$base/type"] = type
    updates["$base/text"] = text
    updates["$base/postId"] = postId
    updates["$base/createdAt"] = ServerValue.TIMESTAMP
    updates["$base/deleted"] = false
    if (replyTo != null) {
        updates["$base/replyToId"] = replyTo.id
        updates["$base/replyToText"] = if (replyTo.deleted) "Message deleted" else previewOf(replyTo.type, replyTo.text).take(80)
        updates["$base/replyToSender"] = replyTo.sender
    }
    if (imageBase64 != null) {
        updates["messageImages/$chatId/$messageId"] = imageBase64
    }
    // My list: nothing new for me. Their list: one more unread message.
    updates["userChats/$uid/$otherUid/lastMessage"] = preview
    updates["userChats/$uid/$otherUid/lastSender"] = uid
    updates["userChats/$uid/$otherUid/lastTime"] = ServerValue.TIMESTAMP
    updates["userChats/$uid/$otherUid/unread"] = 0
    updates["userChats/$otherUid/$uid/lastMessage"] = preview
    updates["userChats/$otherUid/$uid/lastSender"] = uid
    updates["userChats/$otherUid/$uid/lastTime"] = ServerValue.TIMESTAMP
    updates["userChats/$otherUid/$uid/unread"] = ServerValue.increment(1)
    root.updateChildren(updates)

    if (!push) {
        return
    }
    // Ask the sender for a push notification to the other phone
    val kind = if (type == MSG_SYSTEM) "screenshot" else "message"
    val body = if (type == MSG_SYSTEM) text else preview
    val queue = HashMap<String, Any?>()
    queue["toUid"] = otherUid
    queue["fromUid"] = uid
    queue["title"] = me?.name ?: "Kinnect"
    queue["body"] = body
    queue["kind"] = kind
    queue["createdAt"] = ServerValue.TIMESTAMP
    ref("pushQueue").push().setValue(queue)
}

/** Sends a text message, optionally as a reply to [replyTo]. */
fun Backend.sendMessage(otherUid: String, text: String, replyTo: Message? = null) {
    pushMessage(otherUid, MSG_TEXT, text, "", null, replyTo)
}

/** Sends a picture (already shrunk and turned into Base64 text). */
fun Backend.sendImageMessage(otherUid: String, imageBase64: String, replyTo: Message? = null) {
    pushMessage(otherUid, MSG_IMAGE, "", "", imageBase64, replyTo)
}

/** Shares a post of the feed into the conversation. */
fun Backend.sendPostMessage(otherUid: String, post: Post) {
    val summary = post.authorName + ": " + post.text.ifEmpty { "Photo post" }.take(80)
    pushMessage(otherUid, MSG_POST, summary, post.id, null, null)
}

/** A grey line in the conversation ("Voice call 2:13"). It makes no push notification. */
fun Backend.sendSystemMessage(otherUid: String, text: String) {
    pushMessage(otherUid, MSG_SYSTEM, text, "", null, null, push = false)
}

/** "Aisha took a screenshot" appears in the conversation and the other person is notified. */
fun Backend.reportScreenshot(otherUid: String) {
    val name = me?.name ?: return
    val first = name.substringBefore(' ')
    pushMessage(otherUid, MSG_SYSTEM, "$first took a screenshot", "", null, null)
    sendNotice(otherUid, "screenshot", "", myUid ?: "")
}

/**
 * Changes the text of one of my messages. Only possible for 5 minutes after sending (the database rules
 * check this too). The message then shows "Edited".
 */
fun Backend.editMessage(otherUid: String, message: Message, newText: String, done: (Boolean) -> Unit) {
    val uid = myUid
    if (uid == null || !message.canChange() || message.type != MSG_TEXT) {
        done(false)
        return
    }
    val chatId = pairId(uid, otherUid)
    val updates = HashMap<String, Any?>()
    updates["messages/$chatId/${message.id}/text"] = newText
    updates["messages/$chatId/${message.id}/editedAt"] = ServerValue.TIMESTAMP
    root.updateChildren(updates)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** Deletes one of my messages (within 5 minutes). It stays in the list as "This message was deleted". */
fun Backend.deleteMessage(otherUid: String, message: Message, done: (Boolean) -> Unit) {
    val uid = myUid
    if (uid == null || !message.canChange()) {
        done(false)
        return
    }
    val chatId = pairId(uid, otherUid)
    val updates = HashMap<String, Any?>()
    updates["messages/$chatId/${message.id}/deleted"] = true
    updates["messages/$chatId/${message.id}/text"] = ""
    updates["messageImages/$chatId/${message.id}"] = null
    root.updateChildren(updates)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** Puts a heart on a message, or takes it back. */
fun Backend.toggleHeart(otherUid: String, message: Message) {
    val uid = myUid ?: return
    val heart = ref("messages/${pairId(uid, otherUid)}/${message.id}/hearts/$uid")
    if (message.heartedByMe) heart.removeValue() else heart.setValue(true)
}

/** The picture of an image message as Base64 text. */
fun Backend.loadMessageImage(otherUid: String, messageId: String, done: (String?) -> Unit) {
    val uid = myUid ?: return
    once(ref("messageImages/${pairId(uid, otherUid)}/$messageId")) { done(it?.value as? String) }
}

/** Everything said with one person, oldest first, live. */
fun Backend.listenMessages(otherUid: String, onChange: (List<Message>) -> Unit): ListenerRegistration {
    val uid = myUid!!
    return listen(ref("messages/${pairId(uid, otherUid)}").orderByChild("createdAt").limitToLast(300)) { snap ->
        onChange(snap.children.map { it.toMessage() })
    }
}

/** The conversation list, newest first, with a counter of unread messages. */
fun Backend.listenChats(onChange: (List<ChatSummary>) -> Unit): ListenerRegistration {
    val uid = myUid!!
    return listen(ref("userChats/$uid")) { snap ->
        val chats = snap.children.mapNotNull { entry ->
            val other = entry.key
            val time = entry.number("lastTime")
            if (other == null || time == 0L) {
                null
            } else {
                ChatSummary(
                    other,
                    entry.text("lastMessage"),
                    entry.text("lastSender") == uid,
                    time,
                    if (entry.text("lastSender") == uid) 0 else entry.number("unread").toInt()
                )
            }
        }
        onChange(chats.sortedByDescending { it.lastTime })
    }
}

/** The signed-in user has read the conversation: its unread counter goes back to zero. */
fun Backend.markChatRead(otherUid: String) {
    val uid = myUid ?: return
    ref("userChats/$uid/$otherUid/unread").setValue(0)
}

/** The uids of everybody who is online right now, live (green dots in Chats and Friends). */
fun Backend.listenOnline(onChange: (Set<String>) -> Unit): ListenerRegistration {
    return listen(ref("presence")) { snap ->
        onChange(snap.children.filter { it.flag("online") }.mapNotNull { it.key }.toSet())
    }
}
