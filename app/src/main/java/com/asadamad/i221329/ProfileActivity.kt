package com.asadamad.i221329

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Screen 15: your own profile.
class ProfileActivity : AppCompatActivity() {

    // Edit profile sends the new name and bio back through this launcher
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val name = it.data?.getStringExtra(EXTRA_NAME)
            val bio = it.data?.getStringExtra(EXTRA_BIO)
            showDetails(name, bio)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_profile)

        // Show what was saved last time (if the user ever edited the profile)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        showDetails(prefs.getString(KEY_NAME, null), prefs.getString(KEY_BIO, null))

        linkBack(this, R.id.btnBack)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnAddStory, StoryCameraActivity::class.java)

        // Edit profile is opened for a result so we get the changes back
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            editLauncher.launch(Intent(this, EditProfileActivity::class.java))
        }
    }

    // Writes the name and bio into the screen; null means "keep what the layout already shows"
    private fun showDetails(name: String?, bio: String?) {
        if (name != null) {
            findViewById<TextView>(R.id.tvName).text = name
            findViewById<TextView>(R.id.tvTitle).text = name
        }
        if (bio != null) {
            findViewById<TextView>(R.id.tvBio).text = bio
        }
    }
}
