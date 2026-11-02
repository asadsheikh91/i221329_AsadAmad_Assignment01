package com.kinnect.i221329_i221069

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.SetOptions
import kotlin.math.abs

// The whole backend of Kinnect in one place: Firebase Authentication for accounts and
// Cloud Firestore for everything else. Screens call the functions below and never touch Firebase directly.
//
// Firestore layout (see firestore.rules for who may read and write what):
//   users/{uid}                           name, email, bio
//   friendships/{uidA_uidB}               users[2], from, to, status = pending | accepted
//   chats/{uidA_uidB}                     members[2], lastMessage, lastSender, lastTime
//   chats/{uidA_uidB}/messages/{id}       sender, text, time
//   posts/{id}                            authorId, authorName, text, imageBase64, likedBy[], commentCount
//   posts/{id}/comments/{id}              authorId, authorName, text, time
//   calls/{callId}                        callerId, calleeId, video, status, offer, answer ...
//   calls/{callId}/callerCandidates/{id}  WebRTC ICE candidates (see WebRtcCall)
//   calls/{callId}/calleeCandidates/{id}
//   users/{uid}/media/cover               imageBase64 (cover photo, kept apart so lists of people stay small)
//   users/{uid}/private/details           phone, gender (only the owner can read it)
//   notifications/{id}                    toUid, fromUid, fromName, type, text, refId, read   (Notifications.kt)
//   stories/{id}                          author, thumbnail, sticker text, viewedBy[], likedBy[]   (Stories.kt)
//   stories/{id}/data/image               the full size picture of a story
//   listings/{id}                         marketplace listing   (Marketplace.kt)

/** A person on Kinnect. [friends] are the uids of their friends (used for friend counts and mutual friends). */
data class UserInfo(
    val uid: String,
    val name: String,
    val email: String,
    val bio: String,
    val username: String = "",
    val website: String = "",
    val friends: List<String> = emptyList(),
    val joined: Long = 0L
) {
    val initials: String get() = initialsOf(name)
    val color: Int get() = avatarColorOf(uid)
}

/** One row of the friendships collection seen from the signed-in user. */
data class Friendship(val otherUid: String, val sentByMe: Boolean, val accepted: Boolean)

data class Message(val fromMe: Boolean, val text: String)

data class ChatSummary(
    val otherUid: String,
    val lastMessage: String,
    val lastFromMe: Boolean,
    val lastTime: Long,
    val unread: Boolean
)

data class Post(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val imageBase64: String?,
    val likedBy: List<String>,
    val commentCount: Int,
    val time: Long,
    val reactions: Map<String, String> = emptyMap()   // person's uid -> Like, Love, Haha, Wow, Sad or Angry
) {
    val likedByMe: Boolean get() = Backend.myUid in likedBy
    val myReaction: String? get() = reactions[Backend.myUid]
}

data class Comment(val authorId: String, val authorName: String, val text: String, val time: Long)

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

/** Two people always share the same document id, whoever of them created it. */
fun pairId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

object Backend {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val myUid: String? get() = auth.currentUser?.uid
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /** The signed-in user's profile, filled in by logIn / signUp / loadMe. */
    var me: UserInfo? = null
        private set

    /** Id of the call screen that is open right now (a second incoming call is answered with "busy"). */
    var activeCallId: String? = null

    fun addAuthListener(onChange: () -> Unit) {
        auth.addAuthStateListener { onChange() }
    }

    // ------------------------------------------------------------------ accounts

    /** Creates the account and its profile document. [done] gets null on success or an error message. */
    fun signUp(firstName: String, lastName: String, email: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val name = "$firstName $lastName".trim()
                val username = "$firstName.$lastName".trim('.').replace(" ", "").lowercase()
                val profile = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "bio" to "",
                    "username" to username,
                    "website" to "",
                    "friends" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).set(profile)
                    .addOnSuccessListener {
                        me = UserInfo(uid, name, email, "", username, "", emptyList(), System.currentTimeMillis())
                        done(null)
                    }
                    .addOnFailureListener { done(it.message ?: "Could not save your profile") }
            }
            .addOnFailureListener { done(it.message ?: "Could not create the account") }
    }

    fun logIn(email: String, password: String, done: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { loadMe { done(null) } }
            .addOnFailureListener { done(it.message ?: "Could not log in") }
    }

    fun sendPasswordReset(email: String, done: (String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.message ?: "Could not send the email") }
    }

    fun logOut() {
        auth.signOut()
        me = null
    }

    // ------------------------------------------------------------------ profiles

    fun loadMe(done: (UserInfo?) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done(null)
            return
        }
        loadUser(uid) {
            me = it
            done(it)
        }
    }

    fun loadUser(uid: String, done: (UserInfo?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { done(if (it.exists()) it.toUser() else null) }
            .addOnFailureListener { done(null) }
    }

    fun updateProfile(name: String, bio: String, username: String, website: String, done: (Boolean) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done(false)
            return
        }
        val changes = mapOf("name" to name, "bio" to bio, "username" to username, "website" to website)
        db.collection("users").document(uid).update(changes)
            .addOnSuccessListener {
                me = me?.copy(name = name, bio = bio, username = username, website = website)
                    ?: UserInfo(uid, name, "", bio, username, website)
                done(true)
            }
            .addOnFailureListener { done(false) }
    }

    /** Phone number and gender: private information that only the owner can read. */
    fun loadPrivate(done: (phone: String, gender: String) -> Unit) {
        val uid = myUid ?: return
        db.collection("users").document(uid).collection("private").document("details").get()
            .addOnSuccessListener { done(it.getString("phone") ?: "", it.getString("gender") ?: "") }
            .addOnFailureListener { done("", "") }
    }

    fun savePrivate(phone: String, gender: String) {
        val uid = myUid ?: return
        val details = hashMapOf("phone" to phone, "gender" to gender)
        db.collection("users").document(uid).collection("private").document("details").set(details)
    }

    /** The cover photo of a profile as Base64 text (null when the person has none). */
    fun loadCover(uid: String, done: (String?) -> Unit) {
        db.collection("users").document(uid).collection("media").document("cover").get()
            .addOnSuccessListener { done(it.getString("imageBase64")) }
            .addOnFailureListener { done(null) }
    }

    fun saveCover(imageBase64: String, done: (Boolean) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done(false)
            return
        }
        db.collection("users").document(uid).collection("media").document("cover")
            .set(hashMapOf("imageBase64" to imageBase64))
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }

    /**
     * Everyone except the signed-in user, keyed by uid.
     * Shortcut: fetches the first 200 profiles and lets the screens filter them. Fine for a class project,
     * a real app would run a server-side search.
     */
    fun loadPeople(done: (Map<String, UserInfo>) -> Unit) {
        db.collection("users").limit(200).get()
            .addOnSuccessListener { snap ->
                val people = LinkedHashMap<String, UserInfo>()
                for (doc in snap.documents) {
                    if (doc.id != myUid) {
                        people[doc.id] = doc.toUser()
                    }
                }
                done(people)
            }
            .addOnFailureListener { done(emptyMap()) }
    }

    private fun DocumentSnapshot.toUser() = UserInfo(
        id,
        getString("name") ?: "Kinnect user",
        getString("email") ?: "",
        getString("bio") ?: "",
        getString("username") ?: "",
        getString("website") ?: "",
        (get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )

    // ------------------------------------------------------------------ friends

    private fun friendshipDoc(otherUid: String) =
        db.collection("friendships").document(pairId(myUid!!, otherUid))

    private fun DocumentSnapshot.toFriendship(uid: String): Friendship? {
        val users = (get("users") as? List<*>)?.filterIsInstance<String>() ?: return null
        val other = users.firstOrNull { it != uid } ?: return null
        return Friendship(other, getString("from") == uid, getString("status") == "accepted")
    }

    /** Calls [onChange] with every request and friendship of the signed-in user, now and whenever it changes. */
    fun listenFriendships(onChange: (List<Friendship>) -> Unit): ListenerRegistration {
        val uid = myUid!!
        return db.collection("friendships").whereArrayContains("users", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    onChange(snap.documents.mapNotNull { it.toFriendship(uid) })
                }
            }
    }

    /** The relationship with one person; null means no request exists. */
    fun listenFriendship(otherUid: String, onChange: (Friendship?) -> Unit): ListenerRegistration {
        val uid = myUid!!
        return friendshipDoc(otherUid).addSnapshotListener { snap, _ ->
            if (snap != null) {
                onChange(if (snap.exists()) snap.toFriendship(uid) else null)
            }
        }
    }

    fun sendFriendRequest(otherUid: String) {
        val uid = myUid ?: return
        val request = hashMapOf(
            "users" to listOf(uid, otherUid).sorted(),
            "from" to uid,
            "to" to otherUid,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        friendshipDoc(otherUid).set(request)
        sendNotice(otherUid, "request", "", uid, "request_${uid}_$otherUid")
    }

    fun acceptFriendRequest(otherUid: String) {
        val uid = myUid ?: return
        friendshipDoc(otherUid).update("status", "accepted")
        // Shortcut: each profile keeps a list of its friends' uids for the friend count and mutual friends.
        // These two writes are best effort; the friendship itself is the friendships document above.
        db.collection("users").document(uid).update("friends", FieldValue.arrayUnion(otherUid))
        db.collection("users").document(otherUid).update("friends", FieldValue.arrayUnion(uid))
        removeNotice("request_${otherUid}_$uid")
        sendNotice(otherUid, "accepted", "", uid, "accepted_${uid}_$otherUid")
    }

    /** Declines a request, cancels one we sent, or unfriends. */
    fun removeFriendship(otherUid: String) {
        val uid = myUid ?: return
        friendshipDoc(otherUid).delete()
        db.collection("users").document(uid).update("friends", FieldValue.arrayRemove(otherUid))
        db.collection("users").document(otherUid).update("friends", FieldValue.arrayRemove(uid))
        removeNotice("request_${uid}_$otherUid")
        removeNotice("request_${otherUid}_$uid")
        removeNotice("accepted_${uid}_$otherUid")
        removeNotice("accepted_${otherUid}_$uid")
    }

    // ------------------------------------------------------------------ chat

    private fun chatDoc(otherUid: String) = db.collection("chats").document(pairId(myUid!!, otherUid))

    fun sendMessage(otherUid: String, text: String) {
        val uid = myUid ?: return
        val chat = chatDoc(otherUid)
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()
        batch.set(
            chat,
            hashMapOf(
                "members" to listOf(uid, otherUid).sorted(),
                "lastMessage" to text,
                "lastSender" to uid,
                "lastTime" to now
            ),
            SetOptions.merge()
        )
        batch.set(
            chat.collection("messages").document(),
            hashMapOf("sender" to uid, "text" to text, "time" to now)
        )
        batch.commit()
    }

    /** Remembers that the signed-in user has read the conversation up to now (drives the unread badge). */
    fun markChatRead(otherUid: String) {
        val uid = myUid ?: return
        // Fails quietly when there is no conversation yet, which is fine
        chatDoc(otherUid).update("reads.$uid", FieldValue.serverTimestamp())
    }

    /** All messages with one person, oldest first. */
    fun listenMessages(otherUid: String, onChange: (List<Message>) -> Unit): ListenerRegistration {
        val uid = myUid!!
        return chatDoc(otherUid).collection("messages").orderBy("time")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    onChange(snap.documents.map {
                        Message(it.getString("sender") == uid, it.getString("text") ?: "")
                    })
                }
            }
    }

    /** The conversation list, newest first. */
    fun listenChats(onChange: (List<ChatSummary>) -> Unit): ListenerRegistration {
        val uid = myUid!!
        return db.collection("chats").whereArrayContains("members", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val chats = snap.documents.mapNotNull { doc ->
                        val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>()
                        val other = members?.firstOrNull { it != uid }
                        if (other == null) {
                            null
                        } else {
                            val time = doc.getTimestamp("lastTime", ServerTimestampBehavior.ESTIMATE)
                            val lastTime = time?.toDate()?.time ?: 0L
                            val reads = doc.get("reads", ServerTimestampBehavior.ESTIMATE) as? Map<*, *>
                            val readAt = (reads?.get(uid) as? Timestamp)?.toDate()?.time ?: 0L
                            val fromMe = doc.getString("lastSender") == uid
                            ChatSummary(
                                other,
                                doc.getString("lastMessage") ?: "",
                                fromMe,
                                lastTime,
                                !fromMe && lastTime > readAt
                            )
                        }
                    }
                    // Sorted here instead of with orderBy so Firestore needs no composite index
                    onChange(chats.sortedByDescending { it.lastTime })
                }
            }
    }

    // ------------------------------------------------------------------ posts

    /**
     * Shares a post. [imageBase64] is the photo as text (see PostImages.kt) or null for a text post.
     * Shortcut: the photo lives inside the post document instead of Firebase Storage, because Storage
     * needs a paid plan. Photos are shrunk to stay well under Firestore's 1 MB document limit.
     */
    fun createPost(text: String, imageBase64: String?, done: (Boolean) -> Unit) {
        val uid = myUid
        val name = me?.name
        if (uid == null || name == null) {
            done(false)
            return
        }
        val post = hashMapOf(
            "authorId" to uid,
            "authorName" to name,
            "text" to text,
            "imageBase64" to imageBase64,
            "likedBy" to emptyList<String>(),
            "reactions" to emptyMap<String, String>(),
            "commentCount" to 0,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("posts").add(post)
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }

    /** The 50 newest posts, newest first. */
    fun listenPosts(onChange: (List<Post>) -> Unit): ListenerRegistration {
        return db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    onChange(snap.documents.map { it.toPost() })
                }
            }
    }

    private fun DocumentSnapshot.toPost(): Post {
        val time = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)
        return Post(
            id,
            getString("authorId") ?: "",
            getString("authorName") ?: "Kinnect user",
            getString("text") ?: "",
            getString("imageBase64"),
            (get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            (getLong("commentCount") ?: 0L).toInt(),
            time?.toDate()?.time ?: 0L,
            (get("reactions") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
        )
    }

    /** The newest posts of one person, newest first (their photos on their profile). */
    fun loadPostsOf(uid: String, done: (List<Post>) -> Unit) {
        // Sorted here instead of with orderBy so Firestore needs no composite index
        db.collection("posts").whereEqualTo("authorId", uid).limit(30).get()
            .addOnSuccessListener { snap -> done(snap.documents.map { it.toPost() }.sortedByDescending { it.time }) }
            .addOnFailureListener { done(emptyList()) }
    }

    /** A tap on Like: likes the post, or takes the like (whatever the reaction was) back. */
    fun toggleLike(post: Post) {
        if (post.likedByMe) {
            val uid = myUid ?: return
            db.collection("posts").document(post.id).update(
                mapOf("likedBy" to FieldValue.arrayRemove(uid), "reactions.$uid" to FieldValue.delete())
            )
            removeNotice("like_${post.id}_$uid")
        } else {
            reactToPost(post, "Like")
        }
    }

    /** Reacts to a post. [type] is Like, Love, Haha, Wow, Sad or Angry; reacting again replaces the old reaction. */
    fun reactToPost(post: Post, type: String) {
        val uid = myUid ?: return
        db.collection("posts").document(post.id).update(
            mapOf("likedBy" to FieldValue.arrayUnion(uid), "reactions.$uid" to type)
        )
        sendNotice(post.authorId, "like", post.text.take(60), post.id, "like_${post.id}_$uid")
    }

    /** One post, now and whenever it changes; null means it has been deleted. */
    fun listenPost(postId: String, onChange: (Post?) -> Unit): ListenerRegistration {
        return db.collection("posts").document(postId).addSnapshotListener { snap, _ ->
            if (snap != null) {
                onChange(if (snap.exists()) snap.toPost() else null)
            }
        }
    }

    fun deletePost(postId: String) {
        db.collection("posts").document(postId).delete()
    }

    /** Comments on one post, oldest first. */
    fun listenComments(postId: String, onChange: (List<Comment>) -> Unit): ListenerRegistration {
        return db.collection("posts").document(postId).collection("comments").orderBy("time")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    onChange(snap.documents.map { doc ->
                        val time = doc.getTimestamp("time", ServerTimestampBehavior.ESTIMATE)
                        Comment(
                            doc.getString("authorId") ?: "",
                            doc.getString("authorName") ?: "Kinnect user",
                            doc.getString("text") ?: "",
                            time?.toDate()?.time ?: 0L
                        )
                    })
                }
            }
    }

    fun addComment(postId: String, text: String) {
        val uid = myUid ?: return
        val name = me?.name ?: return
        val post = db.collection("posts").document(postId)
        val batch = db.batch()
        batch.set(
            post.collection("comments").document(),
            hashMapOf("authorId" to uid, "authorName" to name, "text" to text, "time" to FieldValue.serverTimestamp())
        )
        batch.update(post, "commentCount", FieldValue.increment(1))
        batch.commit()
        // Tell the author of the post (we only have the post id here, so look the author up)
        post.get().addOnSuccessListener { doc ->
            val author = doc.getString("authorId")
            if (author != null) {
                sendNotice(author, "comment", text.take(60), postId)
            }
        }
    }

    // ------------------------------------------------------------------ calls

    fun newCallId(): String = db.collection("calls").document().id

    fun callDoc(callId: String): DocumentReference = db.collection("calls").document(callId)
}
