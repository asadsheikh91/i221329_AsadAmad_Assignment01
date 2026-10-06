package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 14: friends tab. Tapping a person opens their profile.
class FriendsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_friends)

        setupTabs(this, TAB_FRIENDS)

        // Friend requests
        bindFriend(findViewById(R.id.requestSara), "SI", R.color.avatar_indigo, "Sara Iqbal", "3d", "8 mutual friends", "Confirm", "Delete")
        bindFriend(findViewById(R.id.requestBilal), "BA", R.color.avatar_purple, "Bilal Ahmed", "1w", "14 mutual friends", "Confirm", "Delete")
        bindFriend(findViewById(R.id.requestNoor), "NF", R.color.avatar_green, "Noor Fatima", "2w", "3 mutual friends", "Confirm", "Delete")
        bindFriend(findViewById(R.id.requestDaniyal), "DS", R.color.avatar_crimson, "Daniyal Shah", "3w", "21 mutual friends", "Confirm", "Delete")

        // People you may know
        bindFriend(findViewById(R.id.suggestOmarS), "OS", R.color.avatar_indigo, "Omar Siddiqui", "", "12 mutual friends", "Add friend", "Remove")
        bindFriend(findViewById(R.id.suggestOmarT), "OT", R.color.avatar_brown, "Omar Tariq", "", "3 mutual friends", "Add friend", "Remove")
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        // Friend requests and suggestions
        linkTo(this, R.id.requestSara, UserProfileActivity::class.java)
        linkTo(this, R.id.requestBilal, UserProfileActivity::class.java)
        linkTo(this, R.id.requestNoor, UserProfileActivity::class.java)
        linkTo(this, R.id.requestDaniyal, UserProfileActivity::class.java)
        linkTo(this, R.id.suggestOmarS, UserProfileActivity::class.java)
        linkTo(this, R.id.suggestOmarT, UserProfileActivity::class.java)
    }
}
