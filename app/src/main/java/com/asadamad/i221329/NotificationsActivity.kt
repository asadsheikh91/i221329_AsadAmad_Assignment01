package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 18: notifications tab.
class NotificationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_notifications)

        setupTabs(this, TAB_NOTIFICATIONS)

        // New
        bindNotification(findViewById(R.id.notifReaction), "AK", R.color.avatar_crimson, R.string.notif_reacted, "5m", true, R.color.love, R.drawable.ic_heart_filled)
        bindNotification(findViewById(R.id.notifRequest), "SI", R.color.avatar_indigo, R.string.notif_request, "20m", true, R.color.teal, R.drawable.ic_user)

        // Earlier
        bindNotification(findViewById(R.id.notifReply), "ZR", R.color.avatar_steel, R.string.notif_reply, "2h", false, R.color.avatar_green, R.drawable.ic_comment)
        bindNotification(findViewById(R.id.notifGroup), "DC", R.color.avatar_olive, R.string.notif_group, "5h", false, R.color.avatar_indigo, R.drawable.ic_friends)
        bindNotification(findViewById(R.id.notifTagged), "HA", R.color.avatar_brown, R.string.notif_tagged, "Yesterday", false, R.color.gold, R.drawable.ic_tag)
        bindNotification(findViewById(R.id.notifLiked), "LM", R.color.avatar_purple, R.string.notif_liked, "2d", false, R.color.teal, R.drawable.ic_thumb_filled)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        // The friend request notification opens the sender's profile
        linkTo(this, R.id.notifRequest, UserProfileActivity::class.java)
    }
}
