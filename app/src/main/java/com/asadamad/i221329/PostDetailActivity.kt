package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 5: Lina's post with the reaction picker open.
class PostDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_post_detail)

        linkBack(this, R.id.btnBack)

        // Picking a reaction (or tapping the dim area) closes the picker and returns to the feed
        linkBack(this, R.id.scrim)
        linkBack(this, R.id.reactLike)
        linkBack(this, R.id.reactLove)
        linkBack(this, R.id.reactHaha)
        linkBack(this, R.id.reactWow)
        linkBack(this, R.id.reactSad)
        linkBack(this, R.id.reactAngry)
    }
}
