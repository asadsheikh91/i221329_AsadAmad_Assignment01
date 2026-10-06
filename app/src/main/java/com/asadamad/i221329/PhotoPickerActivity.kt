package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 8: photo picker opened from Create post.
class PhotoPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_media_picker)

        // Fill the 16 photo tiles
        bindMedia(findViewById(R.id.media01), R.drawable.art_sand)
        bindMedia(findViewById(R.id.media02), R.drawable.art_teal)
        bindMedia(findViewById(R.id.media03), R.drawable.art_lilac)
        bindMedia(findViewById(R.id.media04), R.drawable.art_blue)
        bindMedia(findViewById(R.id.media05), R.drawable.art_green)
        bindMedia(findViewById(R.id.media06), R.drawable.art_rose)
        bindMedia(findViewById(R.id.media07), R.drawable.art_night)
        bindMedia(findViewById(R.id.media08), R.drawable.art_teal)
        bindMedia(findViewById(R.id.media09), R.drawable.art_lilac)
        bindMedia(findViewById(R.id.media10), R.drawable.art_sand)
        bindMedia(findViewById(R.id.media11), R.drawable.art_green)
        bindMedia(findViewById(R.id.media12), R.drawable.art_blue)
        bindMedia(findViewById(R.id.media13), R.drawable.art_rose)
        bindMedia(findViewById(R.id.media14), R.drawable.art_night)
        bindMedia(findViewById(R.id.media15), R.drawable.art_blue)
        bindMedia(findViewById(R.id.media16), R.drawable.art_sand)

        // Both buttons return to Create post
        linkBack(this, R.id.btnCancel)
        linkBack(this, R.id.btnNext)
    }
}
