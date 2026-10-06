package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 10: story editor. Sharing sends the user to "Your story".
class StoryEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_editor)

        linkBack(this, R.id.btnClose)

        // The "Your story" pill and the arrow button both share the story
        linkTo(this, R.id.btnYourStory, MyStoryActivity::class.java)
        linkTo(this, R.id.btnShare, MyStoryActivity::class.java)
    }
}
