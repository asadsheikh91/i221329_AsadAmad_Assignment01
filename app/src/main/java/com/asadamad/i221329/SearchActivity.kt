package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 13: search results. Omar Farooq opens his profile.
class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_search)

        linkBack(this, R.id.btnBack)
        linkTo(this, R.id.resultOmarF, UserProfileActivity::class.java)
    }
}
