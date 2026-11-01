package com.asadamad.i221329

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration

// Runs once when the app starts. Its only job is to make the phone ring: while any Kinnect screen
// is open, it watches Firestore for a call addressed to the signed-in user and opens the call screen.
class KinnectApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CallWatcher.install(this)
    }
}

// Shortcut: incoming calls are only noticed while the app is on screen. Ringing a phone whose app is
// closed needs a push notification (Firebase Cloud Messaging plus a Cloud Function), which is not set up.
object CallWatcher : Application.ActivityLifecycleCallbacks {

    // A "ringing" call older than this is a leftover and is ignored
    private const val RING_WINDOW_MS = 60_000L

    private lateinit var app: Application
    private var startedScreens = 0
    private var registration: ListenerRegistration? = null
    private val handled = HashSet<String>()

    fun install(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(this)
        // Log in, sign up and log out all change who we are listening for
        Backend.addAuthListener { refresh() }
    }

    private fun refresh() {
        registration?.remove()
        registration = null
        val uid = Backend.myUid
        if (uid == null || startedScreens == 0) {
            return
        }
        registration = Backend.db.collection("calls")
            .whereEqualTo("calleeId", uid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { ring(it.document) }
            }
    }

    private fun ring(call: DocumentSnapshot) {
        if (!handled.add(call.id)) {
            return
        }
        val age = System.currentTimeMillis() - (call.getLong("createdAtMs") ?: 0L)
        if (age > RING_WINDOW_MS) {
            return
        }
        // Already talking to someone: the caller sees "busy"
        if (Backend.activeCallId != null) {
            call.reference.update("status", "busy")
            return
        }
        val video = call.getBoolean("video") == true
        val screen = if (video) VideoCallActivity::class.java else VoiceCallActivity::class.java
        val intent = Intent(app, screen)
        intent.putExtra(EXTRA_CALL_ID, call.id)
        intent.putExtra(EXTRA_PEER_UID, call.getString("callerId"))
        intent.putExtra(EXTRA_PEER_NAME, call.getString("callerName"))
        intent.putExtra(EXTRA_INCOMING, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    override fun onActivityStarted(activity: Activity) {
        startedScreens++
        if (startedScreens == 1) {
            refresh()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedScreens--
        if (startedScreens == 0) {
            refresh()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
