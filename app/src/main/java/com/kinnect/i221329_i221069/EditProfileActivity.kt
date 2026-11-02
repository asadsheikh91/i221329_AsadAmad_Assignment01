package com.kinnect.i221329_i221069

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

// Screen 16: edit profile.
//   public:  profile picture, cover photo, name, username, website, bio      private (only you can read it): phone, gender
// A first-time user (EXTRA_SETUP) lands here right after Create account: they must choose a username that nobody
// else has before they can reach Home. The username is checked against the database when Save is tapped.
class EditProfileActivity : AppCompatActivity() {

    private var setup = false
    private var newAvatar: String? = null   // the chosen profile picture as Base64, saved together with the profile
    private var newCover: String? = null    // the chosen cover photo as Base64
    private var preparing = 0               // pictures that are still being shrunk

    private lateinit var avatarView: TextView

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            preparing++
            ImageTools.encode(this, uri, 256, 30_000, ImageTools.FILTER_NONE) { encoded ->
                preparing--
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    newAvatar = encoded
                    ImageTools.fromBase64(encoded)?.let { previewAvatar(avatarView, it) }
                }
            }
        }
    }

    private val pickCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            preparing++
            ImageTools.encode(this, uri, 1000, 250_000, ImageTools.FILTER_NONE) { encoded ->
                preparing--
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    newCover = encoded
                    findViewById<ImageView>(R.id.coverPreview).setImageBitmap(ImageTools.fromBase64(encoded))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_edit_profile)
        setup = intent.getBooleanExtra(EXTRA_SETUP, false)

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
        // A first-time user gets a suggestion made from their name
        usernameField.setText(if (me != null && me.username.isEmpty()) suggestUsername(me.name) else (me?.username ?: ""))
        usernameField.hint = "Choose a unique username"
        websiteField.setText(me?.website ?: "")
        findViewById<EditText>(R.id.etEmail).setText(me?.email ?: "")
        phoneField.setText("")
        genderField.setText("")
        Backend.loadPrivate { phone, gender ->
            phoneField.setText(phone)
            genderField.setText(gender)
        }

        // The profile picture: the avatar circle (it has no id: its text is the design's sample initials)
        avatarView = findTextExact(root, "JW")!!
        me?.let { bindAvatar(avatarView, it.uid, it.name) }
        avatarView.setOnClickListener { pickAvatar.launch("image/*") }
        allViews(root).filterIsInstance<TextView>()
            .filter { it.text.toString() == "Edit" && it.id != R.id.btnEditCover }
            .forEach { it.setOnClickListener { pickAvatar.launch("image/*") } }

        // The cover photo that is saved now
        Backend.myUid?.let { uid ->
            Backend.loadCover(uid) { cover ->
                if (cover != null && newCover == null) {
                    findViewById<ImageView>(R.id.coverPreview).setImageBitmap(ImageTools.fromBase64(cover))
                }
            }
        }

        // Cancel closes. A first-time user cannot leave before they have chosen a username.
        val cancel = findViewById<View>(R.id.btnCancel)
        if (setup) {
            cancel.visibility = View.INVISIBLE
            findTextExact(root, "Edit profile")?.text = "Set up your profile"
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(this@EditProfileActivity, "Choose a username to continue", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            linkBack(this, R.id.btnCancel)
        }

        // Cover photo "Edit" and the camera button open the gallery
        findViewById<View>(R.id.btnEditCover).setOnClickListener { pickCover.launch("image/*") }
        allViews(root).firstOrNull { it.contentDescription == "Change cover photo" }
            ?.setOnClickListener { pickCover.launch("image/*") }

        val saveButton = findViewById<View>(R.id.btnSave)
        saveButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val bio = bioField.text.toString().trim()
            val username = usernameField.text.toString().trim().lowercase().removePrefix("@")
            val website = websiteField.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Your name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                Toast.makeText(this, "Choose a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (preparing > 0) {
                Toast.makeText(this, "Preparing your photos...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveButton.isEnabled = false
            Backend.saveProfile(name, bio, username, website) { error ->
                if (error != null) {
                    saveButton.isEnabled = true
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    return@saveProfile
                }
                Backend.savePrivate(phoneField.text.toString().trim(), genderField.text.toString().trim())
                saveImages { finishUp(name, bio) }
            }
        }
    }

    /** Saves the new profile picture and cover photo (if any), then calls [then]. */
    private fun saveImages(then: () -> Unit) {
        val avatar = newAvatar
        val cover = newCover
        var waiting = 0
        fun oneDone() {
            waiting--
            if (waiting == 0) then()
        }
        if (avatar == null && cover == null) {
            then()
            return
        }
        if (avatar != null) {
            waiting++
            Backend.myUid?.let { rememberAvatar(it, avatar) }
            Backend.saveAvatar(avatar) { oneDone() }
        }
        if (cover != null) {
            waiting++
            Backend.saveCover(cover) { oneDone() }
        }
    }

    private fun finishUp(name: String, bio: String) {
        if (setup) {
            // The profile is complete: Home becomes the only screen, so Back from Home leaves the app
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra(EXTRA_FIRST_NAME, name.substringBefore(' '))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        } else {
            // Hand them back to the profile screen and close
            val result = Intent()
            result.putExtra(EXTRA_NAME, name)
            result.putExtra(EXTRA_BIO, bio)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}
