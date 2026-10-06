package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 23: marketplace tab.
class MarketplaceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_marketplace)

        setupTabs(this, TAB_MARKETPLACE)

        bindListing(findViewById(R.id.listingBike), R.drawable.art_green, "Rs 45,000", "Road bike, 21-speed", "Clifton")
        bindListing(findViewById(R.id.listingChair), R.drawable.art_rose, "Rs 8,500", "Rattan armchair", "DHA Phase 6")
        bindListing(findViewById(R.id.listingDesk), R.drawable.art_blue, "Rs 15,000", "Solid wood study desk", "Gulshan")
        bindListing(findViewById(R.id.listingPots), R.drawable.art_sand, "Rs 3,200", "Ceramic plant pots, set of 3", "Saddar")
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnProfile, ProfileActivity::class.java)
    }
}
