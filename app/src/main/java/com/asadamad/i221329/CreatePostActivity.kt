package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 7: create post. Photo/video and Camera open their own screens.
class CreatePostActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_create_post)

        // Close (X) and Post both go back to the feed
        linkBack(this, R.id.btnClose)
        linkBack(this, R.id.btnPost)

        linkTo(this, R.id.optionPhoto, PhotoPickerActivity::class.java)
        linkTo(this, R.id.optionCamera, StoryCameraActivity::class.java)
    }
}
