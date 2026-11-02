package com.kinnect.i221329_i221069

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging

// The whole backend of Kinnect in a few files: Firebase Authentication for accounts and the Firebase
// Realtime Database for everything else. Screens call the functions here and never touch Firebase directly.
//   Backend.kt         accounts, profiles, usernames, presence (online / offline), search, friends
//   PostsBackend.kt    posts, reactions, comments
//   ChatBackend.kt     conversations, messages, screenshots
//   Notifications.kt   the bell
//   Stories.kt         24 hour stories
//   Marketplace.kt     listings
//   CallBackend.kt     call set-up and call logs (the audio/video itself is Agora, see AgoraCall.kt)
//
// The Realtime Database is one big JSON tree. Who may read and write which part is in database.rules.json.
//
//   users/{uid}                  name, nameLower, lastNameLower, username, usernameLower, email, bio, website,
//                                profileDone, createdAt
//   usernames/{usernameLower}    uid           (makes a username unique)
//   avatars/{uid}                profile picture (Base64)        covers/{uid}  cover photo (Base64)
//   userPrivate/{uid}            phone, gender
//   presence/{uid}               online, lastSeen
//   friends/{uid}/{otherUid}     true          friends (written for both people)
//   requests/{toUid}/{fromUid}   time          friend requests I received
//   sent/{fromUid}/{toUid}       time          friend requests I sent
//   fcmTokens/{uid}/{token}      true          where to send push notifications
//   (posts, comments, chats, stories, notifications, listings, calls: see the other files)

/** Something a screen can stop listening to (when the screen goes away). */
class ListenerRegistration(private val onRemove: () -> Unit) {
    fun remove() {
        onRemove()
    }
}

// Small readers for the values of a snapshot. They never throw when a value has another type than expected.
fun DataSnapshot.text(name: String): String = child(name).value as? String ?: ""
fun DataSnapshot.number(name: String): Long = (child(name).value as? Number)?.toLong() ?: 0L
fun DataSnapshot.flag(name: String): Boolean = child(name).value as? Boolean ?: false
fun DataSnapshot.keys(name: String): List<String> = child(name).children.mapNotNull { it.key }

object Backend {

    private const val TAG = "Kinnect"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    /** The one database connection of the app, created the first time it is needed. */
    internal val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    /** The root of the database. */
    val root: DatabaseReference get() = database.reference

    fun ref(path: String): DatabaseReference = root.child(path)

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

    // ------------------------------------------------------------------ listening helpers

    /** Calls [onChange] now and every time the data at [query] changes. */
    fun listen(query: Query, onChange: (DataSnapshot) -> Unit): ListenerRegistration {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Listener cancelled: " + error.message)
            }
        }
        query.addValueEventListener(listener)
        return ListenerRegistration { query.removeEventListener(listener) }
    }

    /** Reads the data at [query] once. [done] gets null when it cannot be read. */
    fun once(query: Query, done: (DataSnapshot?) -> Unit) {
        query.get()
            .addOnSuccessListener { done(it) }
            .addOnFailureListener {
                Log.w(TAG, "Read failed: " + it.message)
                done(null)
            }
    }

    // ------------------------------------------------------------------ accounts

    /** Creates the account and its profile node. [done] gets null on success or an error message. */
    fun signUp(firstName: String, lastName: String, email: String, password: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val name = "$firstName $lastName".trim()
                val profile = hashMapOf<String, Any?>(
                    "name" to name,
                    "nameLower" to name.lowercase(),
                    "lastNameLower" to name.lowercase().substringAfter(' ', ""),
                    "email" to email,
                    "bio" to "",
                    "username" to "",
                    "usernameLower" to "",
                    "website" to "",
                    "profileDone" to false,
                    "createdAt" to ServerValue.TIMESTAMP
                )
                ref("users/$uid").setValue(profile)
                    .addOnSuccessListener {
                        me = UserInfo(uid, name, email, "", profileDone = false, joined = System.currentTimeMillis())
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

    /** Marks the user offline, forgets this phone's push token, signs out. */
    fun logOut() {
        val uid = myUid
        if (uid != null) {
            ref("presence/$uid").setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                ref("fcmTokens/$uid/$token").removeValue()
            }
        }
        stopPresence()
        auth.signOut()
        me = null
    }

    /** Called whenever somebody signs in: starts presence and registers this phone for push notifications. */
    fun onSignedIn() {
        startPresence()
        saveFcmToken()
    }

    // ------------------------------------------------------------------ push token

    /** Remembers this phone's FCM token under the user, so the message sender knows where to push. */
    fun saveFcmToken() {
        val uid = myUid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            ref("fcmTokens/$uid/$token").setValue(true)
        }
    }

    fun saveFcmToken(token: String) {
        val uid = myUid ?: return
        ref("fcmTokens/$uid/$token").setValue(true)
    }

    // ------------------------------------------------------------------ online / offline

    private var connectionQuery: Query? = null
    private var connectionListener: ValueEventListener? = null
    private var appVisible = false

    /**
     * Keeps presence/{uid} true while the app is on screen. When the phone loses its connection the server
     * itself writes "offline" (onDisconnect), so a crashed app or a dead battery cannot stay online.
     */
    fun startPresence() {
        val uid = myUid ?: return
        stopPresence()
        val connected = database.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true) {
                    val node = ref("presence/$uid")
                    node.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
                    if (appVisible) {
                        node.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        connected.addValueEventListener(listener)
        connectionQuery = connected
        connectionListener = listener
    }

    private fun stopPresence() {
        val query = connectionQuery
        val listener = connectionListener
        if (query != null && listener != null) {
            query.removeEventListener(listener)
        }
        connectionQuery = null
        connectionListener = null
    }

    /** Called when the first Kinnect screen appears (true) and when the last one goes away (false). */
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        val uid = myUid ?: return
        ref("presence/$uid").setValue(mapOf("online" to visible, "lastSeen" to ServerValue.TIMESTAMP))
    }

    /** Is [uid] online right now, and when were they last seen? */
    fun listenPresence(uid: String, onChange: (online: Boolean, lastSeen: Long) -> Unit): ListenerRegistration {
        return listen(ref("presence/$uid")) { onChange(it.flag("online"), it.number("lastSeen")) }
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
        once(ref("users/$uid")) { snap ->
            done(if (snap != null && snap.exists()) snap.toUser() else null)
        }
    }

    private fun DataSnapshot.toUser() = UserInfo(
        key ?: "",
        text("name").ifEmpty { "Kinnect user" },
        text("email"),
        text("bio"),
        text("username"),
        text("website"),
        number("createdAt"),
        child("profileDone").value as? Boolean ?: true
    )

    /**
     * Saves the profile. The username must be unique: usernames/{name} holds the uid of its owner and the
     * database rules only let one person claim a free name. [done] gets null on success or a message.
     */
    fun saveProfile(name: String, bio: String, username: String, website: String, done: (String?) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done("You are not signed in")
            return
        }
        val lower = username.trim().lowercase()
        if (!isValidUsername(lower)) {
            done("Username: 3 to 20 letters, numbers or underscores (no spaces or dots)")
            return
        }
        once(ref("usernames/$lower")) { snap ->
            val owner = snap?.value as? String
            if (snap == null) {
                done("Could not check the username. Check your connection.")
            } else if (owner != null && owner != uid) {
                done("That username is taken. Try another one.")
            } else {
                val old = me?.usernameLower ?: ""
                val updates = HashMap<String, Any?>()
                updates["users/$uid/name"] = name
                updates["users/$uid/nameLower"] = name.lowercase()
                updates["users/$uid/lastNameLower"] = name.lowercase().substringAfter(' ', "")
                updates["users/$uid/bio"] = bio
                updates["users/$uid/username"] = lower
                updates["users/$uid/usernameLower"] = lower
                updates["users/$uid/website"] = website
                updates["users/$uid/profileDone"] = true
                updates["usernames/$lower"] = uid
                if (old.isNotEmpty() && old != lower) {
                    updates["usernames/$old"] = null
                }
                root.updateChildren(updates)
                    .addOnSuccessListener {
                        me = (me ?: UserInfo(uid, name, "", bio)).copy(
                            name = name, bio = bio, username = lower, website = website, profileDone = true
                        )
                        done(null)
                    }
                    .addOnFailureListener { done("Could not save your profile (the username may have just been taken).") }
            }
        }
    }

    /** Phone number and gender: private information that only the owner can read. */
    fun loadPrivate(done: (phone: String, gender: String) -> Unit) {
        val uid = myUid ?: return
        once(ref("userPrivate/$uid")) { done(it?.text("phone") ?: "", it?.text("gender") ?: "") }
    }

    fun savePrivate(phone: String, gender: String) {
        val uid = myUid ?: return
        ref("userPrivate/$uid").setValue(mapOf("phone" to phone, "gender" to gender))
    }

    /** The cover photo of a profile as Base64 text (null when the person has none). */
    fun loadCover(uid: String, done: (String?) -> Unit) {
        once(ref("covers/$uid")) { done(it?.value as? String) }
    }

    fun saveCover(imageBase64: String, done: (Boolean) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done(false)
            return
        }
        ref("covers/$uid").setValue(imageBase64)
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }

    /** The profile picture of a person as Base64 text (null when they have none). */
    fun loadAvatar(uid: String, done: (String?) -> Unit) {
        once(ref("avatars/$uid")) { done(it?.value as? String) }
    }

    fun saveAvatar(imageBase64: String, done: (Boolean) -> Unit) {
        val uid = myUid
        if (uid == null) {
            done(false)
            return
        }
        ref("avatars/$uid").setValue(imageBase64)
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }

    // ------------------------------------------------------------------ people and search

    /** Everyone except the signed-in user, keyed by uid (the first 300 profiles). */
    fun loadPeople(done: (Map<String, UserInfo>) -> Unit) {
        once(ref("users").limitToFirst(300)) { snap ->
            val people = LinkedHashMap<String, UserInfo>()
            snap?.children?.forEach {
                val uid = it.key
                if (uid != null && uid != myUid) {
                    people[uid] = it.toUser()
                }
            }
            done(people)
        }
    }

    /**
     * People whose username, name or last name starts with [text]. Three small queries on indexed fields
     * (see .indexOn in database.rules.json) so the database does the searching, not the phone.
     */
    fun searchUsers(text: String, done: (List<UserInfo>) -> Unit) {
        val lower = text.trim().lowercase().removePrefix("@")
        if (lower.isEmpty()) {
            done(emptyList())
            return
        }
        val found = LinkedHashMap<String, UserInfo>()
        var pending = 3
        for (field in listOf("usernameLower", "nameLower", "lastNameLower")) {
            val query = ref("users").orderByChild(field).startAt(lower).endAt(lower + "").limitToFirst(25)
            once(query) { snap ->
                snap?.children?.forEach {
                    val uid = it.key
                    if (uid != null && uid != myUid) {
                        found[uid] = it.toUser()
                    }
                }
                pending--
                if (pending == 0) {
                    done(found.values.toList())
                }
            }
        }
    }

    // ------------------------------------------------------------------ friends

    /** Calls [onChange] with every request and friendship of the signed-in user, now and whenever it changes. */
    fun listenFriendships(onChange: (List<Friendship>) -> Unit): ListenerRegistration {
        val uid = myUid!!
        var friends: List<String>? = null
        var incoming: List<String>? = null
        var outgoing: List<String>? = null

        fun emit() {
            val f = friends
            val i = incoming
            val o = outgoing
            if (f == null || i == null || o == null) {
                return
            }
            val list = ArrayList<Friendship>()
            f.forEach { list.add(Friendship(it, false, true)) }
            i.filter { it !in f }.forEach { list.add(Friendship(it, false, false)) }
            o.filter { it !in f }.forEach { list.add(Friendship(it, true, false)) }
            onChange(list)
        }

        val a = listen(ref("friends/$uid")) { friends = it.children.mapNotNull { c -> c.key }; emit() }
        val b = listen(ref("requests/$uid")) { incoming = it.children.mapNotNull { c -> c.key }; emit() }
        val c = listen(ref("sent/$uid")) { outgoing = it.children.mapNotNull { c2 -> c2.key }; emit() }
        return ListenerRegistration {
            a.remove()
            b.remove()
            c.remove()
        }
    }

    /** The relationship with one person; null means no request exists. */
    fun listenFriendship(otherUid: String, onChange: (Friendship?) -> Unit): ListenerRegistration {
        return listenFriendships { list -> onChange(list.firstOrNull { it.otherUid == otherUid }) }
    }

    /** The ids of somebody's friends (for friend counts and mutual friends). */
    fun loadFriendUids(uid: String, done: (List<String>) -> Unit) {
        once(ref("friends/$uid")) { done(it?.children?.mapNotNull { c -> c.key } ?: emptyList()) }
    }

    fun sendFriendRequest(otherUid: String) {
        val uid = myUid ?: return
        val updates = HashMap<String, Any?>()
        updates["requests/$otherUid/$uid"] = ServerValue.TIMESTAMP
        updates["sent/$uid/$otherUid"] = ServerValue.TIMESTAMP
        root.updateChildren(updates)
        sendNotice(otherUid, "request", "", uid, "request_${uid}_$otherUid")
    }

    /** The receiver of a request confirms it: both people become friends and the request is gone. */
    fun acceptFriendRequest(otherUid: String) {
        val uid = myUid ?: return
        val updates = HashMap<String, Any?>()
        updates["friends/$uid/$otherUid"] = true
        updates["friends/$otherUid/$uid"] = true
        updates["requests/$uid/$otherUid"] = null
        updates["sent/$otherUid/$uid"] = null
        root.updateChildren(updates)
        removeNotice(uid, "request_${otherUid}_$uid")
        sendNotice(otherUid, "accepted", "", uid, "accepted_${uid}_$otherUid")
    }

    /** Declines a request, cancels one we sent, or unfriends. */
    fun removeFriendship(otherUid: String) {
        val uid = myUid ?: return
        val updates = HashMap<String, Any?>()
        updates["friends/$uid/$otherUid"] = null
        updates["friends/$otherUid/$uid"] = null
        updates["requests/$uid/$otherUid"] = null
        updates["requests/$otherUid/$uid"] = null
        updates["sent/$uid/$otherUid"] = null
        updates["sent/$otherUid/$uid"] = null
        root.updateChildren(updates)
        removeNotice(uid, "request_${otherUid}_$uid")
        removeNotice(otherUid, "request_${uid}_$otherUid")
        removeNotice(otherUid, "accepted_${uid}_$otherUid")
        removeNotice(uid, "accepted_${otherUid}_$uid")
    }
}
