package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 11: viewing a friend's story.
class StoryViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_viewer)

        linkBack(this, R.id.btnClose)
    }
}
