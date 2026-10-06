package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 17: another person's profile, reached from search and friend requests.
class UserProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_user_profile)

        linkBack(this, R.id.btnBack)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnMessage, ChatActivity::class.java)
    }
}
