package com.asadamad.i221329

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Screen 16: edit profile. Save stores the details in Firestore and returns them to the profile screen.
//   public:  name, username, website, bio and the cover photo      private (only you can read it): phone, gender
class EditProfileActivity : AppCompatActivity() {

    private var newCover: String? = null    // the chosen cover photo as Base64, saved together with the profile
    private var coverLoading = false

    // Result of the gallery picker: show the chosen picture as the cover photo
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                coverLoading = true
                ImageTools.encode(this, uri, 1000, 250_000, ImageTools.FILTER_NONE) { encoded ->
                    coverLoading = false
                    if (encoded == null) {
                        Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                    } else {
                        newCover = encoded
                        findViewById<ImageView>(R.id.coverPreview).setImageBitmap(ImageTools.fromBase64(encoded))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_edit_profile)

        val root = findViewById<View>(R.id.root)
        val nameField = findViewById<EditText>(R.id.etName)
        val bioField = findViewById<EditText>(R.id.etBio)

        // The fields without an id, in the order they appear on the screen:
        // name, username, website, bio, email, phone, gender
        val fields = allViews(root).filterIsInstance<EditText>()
        val usernameField = fields[1]
        val websiteField = fields[2]
        val phoneField = fields[5]
        val genderField = fields[6]

        // Start from the saved profile
        val me = Backend.me
        nameField.setText(me?.name ?: "")
        bioField.setText(me?.bio ?: "")
        usernameField.setText(me?.username ?: "")
        websiteField.setText(me?.website ?: "")
        findViewById<EditText>(R.id.etEmail).setText(me?.email ?: "")
        phoneField.setText("")
        genderField.setText("")
        Backend.loadPrivate { phone, gender ->
            phoneField.setText(phone)
            genderField.setText(gender)
        }
        me?.let { findTextExact(root, "JW")?.text = it.initials }

        // The cover photo that is saved now
        Backend.myUid?.let { uid ->
            Backend.loadCover(uid) { cover ->
                if (cover != null && newCover == null) {
                    findViewById<ImageView>(R.id.coverPreview).setImageBitmap(ImageTools.fromBase64(cover))
                }
            }
        }

        linkBack(this, R.id.btnCancel)

        // Profile pictures are the coloured initials, so the "Edit" next to them has nothing to change
        allViews(root).filterIsInstance<TextView>()
            .filter { it.text.toString() == "Edit" && it.id != R.id.btnEditCover }
            .forEach {
                it.setOnClickListener {
                    Toast.makeText(this, "Your picture is made from your initials", Toast.LENGTH_SHORT).show()
                }
            }

        // Cover photo "Edit" opens the gallery (implicit intent)
        findViewById<View>(R.id.btnEditCover).setOnClickListener {
            val intent = Intent()
            intent.setAction(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            pickCoverLauncher.launch(intent)
        }

        val saveButton = findViewById<View>(R.id.btnSave)
        saveButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val bio = bioField.text.toString().trim()
            val username = usernameField.text.toString().trim().lowercase().replace(" ", "")
            val website = websiteField.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Your name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (coverLoading) {
                Toast.makeText(this, "Preparing your cover photo...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            Backend.updateProfile(name, bio, username, website) { saved ->
                if (!saved) {
                    saveButton.isEnabled = true
                    Toast.makeText(this, "Could not save your profile. Check your connection.", Toast.LENGTH_LONG).show()
                    return@updateProfile
                }
                Backend.savePrivate(phoneField.text.toString().trim(), genderField.text.toString().trim())

                // Hand them back to the profile screen and close
                val finishUp = {
                    val result = Intent()
                    result.putExtra(EXTRA_NAME, name)
                    result.putExtra(EXTRA_BIO, bio)
                    setResult(RESULT_OK, result)
                    finish()
                }
                val cover = newCover
                if (cover == null) {
                    finishUp()
                } else {
                    Backend.saveCover(cover) { finishUp() }
                }
            }
        }
    }
}
