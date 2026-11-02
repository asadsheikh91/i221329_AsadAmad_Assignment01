package com.kinnect.i221329_i221069

import kotlin.math.abs

// The plain data classes of Kinnect and the small helpers that turn them into text. No Firebase in here,
// so these can be tested without a phone.

/** A person on Kinnect. */
data class UserInfo(
    val uid: String,
    val name: String,
    val email: String,
    val bio: String,
    val username: String = "",
    val website: String = "",
    val joined: Long = 0L,
    val profileDone: Boolean = true      // false until a new user has picked a username (Edit profile, first time)
) {
    val initials: String get() = initialsOf(name)
    val color: Int get() = avatarColorOf(uid)
    val usernameLower: String get() = username.lowercase()
}

/** One row of the friend system seen from the signed-in user. */
data class Friendship(val otherUid: String, val sentByMe: Boolean, val accepted: Boolean)

// Kinds of chat message
const val MSG_TEXT = "text"
const val MSG_IMAGE = "image"
const val MSG_POST = "post"
const val MSG_SYSTEM = "system"

/** A message can be edited or deleted by its sender for this long after it was sent. */
const val EDIT_WINDOW_MS = 5 * 60 * 1000L

data class Message(
    val id: String,
    val sender: String,
    val type: String,
    val text: String,
    val postId: String,            // for a shared post
    val replyToId: String,         // the message this one answers ("" when it is not a reply)
    val replyToText: String,
    val replyToSender: String,
    val time: Long,
    val edited: Boolean,
    val deleted: Boolean,
    val hearts: List<String>       // who put a heart on it
) {
    val fromMe: Boolean get() = sender == Backend.myUid
    val heartedByMe: Boolean get() = Backend.myUid in hearts

    /** True while the sender may still edit or delete it (and only for the sender). */
    fun canChange(now: Long = System.currentTimeMillis()): Boolean =
        fromMe && !deleted && type != MSG_SYSTEM && now - time < EDIT_WINDOW_MS
}

data class ChatSummary(
    val otherUid: String,
    val lastMessage: String,
    val lastFromMe: Boolean,
    val lastTime: Long,
    val unreadCount: Int
) {
    val unread: Boolean get() = unreadCount > 0
}

data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val imageCount: Int,
    val reactions: Map<String, String>,   // person's uid -> Like, Love, Haha, Wow, Sad or Angry (one per person)
    val commentCount: Int,
    val time: Long
) {
    val likedBy: List<String> get() = reactions.keys.toList()
    val likedByMe: Boolean get() = Backend.myUid in reactions
    val myReaction: String? get() = reactions[Backend.myUid]
}

data class Comment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val time: Long,
    val parentId: String,          // "" for a comment, the id of the comment it answers for a reply
    val hasImage: Boolean,
    val likedBy: List<String>
) {
    val likedByMe: Boolean get() = Backend.myUid in likedBy
}

private val AVATAR_COLORS = intArrayOf(
    R.color.avatar_purple, R.color.avatar_indigo, R.color.avatar_green, R.color.avatar_brown,
    R.color.avatar_crimson, R.color.avatar_steel, R.color.avatar_olive
)

/** "Aisha Khan" -> "AK". */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "K"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** The same person always gets the same avatar colour. */
fun avatarColorOf(uid: String): Int = AVATAR_COLORS[abs(uid.hashCode()) % AVATAR_COLORS.size]

/** "now", "5m", "3h", "2d" for the chat list. */
fun timeAgo(millis: Long): String {
    if (millis <= 0) return ""
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> minutes.toString() + "m"
        minutes < 60 * 24 -> (minutes / 60).toString() + "h"
        else -> (minutes / (60 * 24)).toString() + "d"
    }
}

/** Two people always share the same id, whoever of them created it. */
fun pairId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

/**
 * Letters, numbers and underscores, 3 to 20 characters. No dots: the username is also a key in the database
 * (usernames/{name}) and the Realtime Database does not allow a dot in a key.
 */
fun isValidUsername(name: String): Boolean = Regex("^[a-z0-9_]{3,20}$").matches(name.lowercase())

/** A first suggestion for the username field: "Aisha Khan" -> "aisha_khan". */
fun suggestUsername(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), "_").filter { it.isLetterOrDigit() || it == '_' }.take(20)

/** 0 (nothing typed) to 4 (strong): how good a password is, for the strength bar of Create account. */
fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var score = 0
    if (password.length >= 6) score++
    if (password.length >= 10) score++
    if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
    if (password.any { !it.isLetterOrDigit() } || (password.any { it.isUpperCase() } && password.any { it.isLowerCase() })) score++
    return score
}
