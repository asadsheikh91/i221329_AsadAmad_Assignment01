package com.kinnect.i221329_i221069

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Push notifications with Firebase Cloud Messaging (FCM).
//
// How a push gets here:
//   1. A user sends a message. ChatBackend writes a request into pushQueue/ in the database.
//   2. The notification sender (notification-sender/ or the Cloud Function in functions/) sees the request,
//      looks up the receiver's tokens in fcmTokens/{uid} and asks FCM to deliver a data message.
//   3. FCM wakes this service on the receiver's phone, even if the app is closed, and onMessageReceived shows it.
//
// Data messages (not "notification" messages) are used so this code always runs and can open the right chat.
class KinnectMessagingService : FirebaseMessagingService() {

    /** FCM gave this phone a new token: save it under the user so the sender can find the phone. */
    override fun onNewToken(token: String) {
        Backend.saveFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val fromUid = data["fromUid"] ?: return
        val title = data["title"] ?: "Kinnect"
        val body = data["body"] ?: ""

        // Do not disturb the user with a banner for the chat they are looking at right now
        if (ChatActivity.openPeer == fromUid) {
            return
        }
        // On Android 13 and later the user has to allow notifications first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Tapping the notification opens the chat with the sender
        val open = Intent(this, ChatActivity::class.java)
        open.putExtra(EXTRA_UID, fromUid)
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(
            this, fromUid.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, KinnectApp.MESSAGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_filled)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        // One notification per sender: a new message replaces the old one
        NotificationManagerCompat.from(this).notify(fromUid.hashCode(), notification)
    }
}
