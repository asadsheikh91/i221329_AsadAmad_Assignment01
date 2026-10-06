package com.asadamad.i221329

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Screen 4: home feed. Everything on it is a shortcut to another screen.
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_home)

        // Greeting uses the first name sent by Log in (falls back to the layout text if missing)
        val firstName = intent.getStringExtra(EXTRA_FIRST_NAME)
        if (firstName != null) {
            findViewById<TextView>(R.id.tvComposerHint).text = "What's on your mind, " + firstName + "?"
        }

        setupTabs(this, TAB_HOME)

        // The three story cards share one layout, so fill each one in
        bindStory(findViewById(R.id.storyOmar), R.drawable.art_tall_night, "OF", R.color.avatar_green, "Omar Farooq")
        bindStory(findViewById(R.id.storySara), R.drawable.art_rose, "SI", R.color.avatar_indigo, "Sara Iqbal")
        bindStory(findViewById(R.id.storyHamza), R.drawable.art_green, "HA", R.color.avatar_brown, "Hamza Ali")

        // Header
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnChats, ChatsActivity::class.java)

        // Composer
        linkTo(this, R.id.composerAvatar, ProfileActivity::class.java)
        linkTo(this, R.id.tvComposerHint, CreatePostActivity::class.java)
        linkTo(this, R.id.btnComposerPhoto, CreatePostActivity::class.java)

        // Stories
        linkTo(this, R.id.storyCreate, StoryCameraActivity::class.java)
        linkTo(this, R.id.storyOmar, StoryViewerActivity::class.java)
        linkTo(this, R.id.storySara, StoryViewerActivity::class.java)
        linkTo(this, R.id.storyHamza, StoryViewerActivity::class.java)

        // Post: tapping Like or the photos opens the post with the reaction picker
        linkTo(this, R.id.btnLike, PostDetailActivity::class.java)
        linkTo(this, R.id.photoCollage, PostDetailActivity::class.java)
        linkTo(this, R.id.btnComment, CommentsActivity::class.java)
    }
}
