package com.asadamad.i221329

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Screen 16: edit profile. Save stores the details and returns them to the profile screen.
class EditProfileActivity : AppCompatActivity() {

    // Result of the gallery picker: show the chosen picture as the cover photo
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val uri = it.data?.data
            findViewById<ImageView>(R.id.coverPreview).setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_edit_profile)

        val nameField = findViewById<EditText>(R.id.etName)
        val bioField = findViewById<EditText>(R.id.etBio)

        // Start from the saved values when there are any
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedName = prefs.getString(KEY_NAME, null)
        val savedBio = prefs.getString(KEY_BIO, null)
        if (savedName != null) {
            nameField.setText(savedName)
        }
        if (savedBio != null) {
            bioField.setText(savedBio)
        }

        linkBack(this, R.id.btnCancel)

        // Cover photo "Edit" opens the gallery (implicit intent)
        findViewById<View>(R.id.btnEditCover).setOnClickListener {
            val intent = Intent()
            intent.setAction(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            pickCoverLauncher.launch(intent)
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = nameField.text.toString()
            val bio = bioField.text.toString()

            // Keep the details so they survive closing the app
            val editor = prefs.edit()
            editor.putString(KEY_NAME, name)
            editor.putString(KEY_BIO, bio)
            editor.commit()

            // Hand them back to the profile screen and close
            val result = Intent()
            result.putExtra(EXTRA_NAME, name)
            result.putExtra(EXTRA_BIO, bio)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
