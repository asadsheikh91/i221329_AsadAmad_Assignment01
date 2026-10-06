package com.asadamad.i221329

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

// Screen 19: menu tab. Opens your profile and logs out.
class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_menu)

        setupTabs(this, TAB_MENU)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        linkTo(this, R.id.menuProfile, ProfileActivity::class.java)
        linkTo(this, R.id.menuMarketplace, MarketplaceActivity::class.java)
        linkTo(this, R.id.menuFriends, FriendsActivity::class.java)

        // Log out empties the back stack, so Back from Log in leaves the app
        findViewById<View>(R.id.btnLogOut).setOnClickListener {
            logOut(this)
        }
    }
}
