package com.kinnect.i221329_i221069

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import com.google.firebase.database.DataSnapshot

// Runs once when the app starts. It does three jobs for the whole app:
//  1. Online / offline: the user counts as online while any Kinnect screen is on screen.
//  2. It makes the phone ring: while a screen is open it watches the database for a call addressed to the
//     signed-in user and opens the call screen.
//  3. It creates the notification channel that push notifications (FCM) are shown in.
class KinnectApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createMessageChannel()
        CallWatcher.install(this)
    }

    private fun createMessageChannel() {
        val channel = NotificationChannel(MESSAGE_CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
        channel.description = "New messages and screenshots in your chats"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val MESSAGE_CHANNEL = "messages"
    }
}

// Shortcut: incoming calls are only noticed while the app is on screen. Ringing a phone whose app is
// closed needs a push notification for calls too (the same FCM mechanism that is used for messages).
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
        Backend.addAuthListener {
            if (Backend.isLoggedIn) {
                Backend.onSignedIn()
            }
            refresh()
        }
    }

    private fun refresh() {
        registration?.remove()
        registration = null
        val uid = Backend.myUid
        if (uid == null || startedScreens == 0) {
            return
        }
        registration = Backend.listen(Backend.ref("calls").orderByChild("calleeId").equalTo(uid)) { snap ->
            snap.children.forEach { ring(it) }
        }
    }

    private fun ring(call: DataSnapshot) {
        val id = call.key ?: return
        if (call.text("status") != "ringing" || !handled.add(id)) {
            return
        }
        val age = System.currentTimeMillis() - call.number("createdAtMs")
        if (age > RING_WINDOW_MS) {
            return
        }
        // Already talking to someone: the caller sees "busy"
        if (Backend.activeCallId != null) {
            call.ref.child("status").setValue("busy")
            return
        }
        val video = call.flag("video")
        val screen = if (video) VideoCallActivity::class.java else VoiceCallActivity::class.java
        val intent = Intent(app, screen)
        intent.putExtra(EXTRA_CALL_ID, id)
        intent.putExtra(EXTRA_PEER_UID, call.text("callerId"))
        intent.putExtra(EXTRA_PEER_NAME, call.text("callerName"))
        intent.putExtra(EXTRA_INCOMING, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    override fun onActivityStarted(activity: Activity) {
        startedScreens++
        if (startedScreens == 1) {
            Backend.setAppVisible(true)
            refresh()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedScreens--
        if (startedScreens == 0) {
            Backend.setAppVisible(false)
            refresh()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
