package com.kinnect.i221329_i221069

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 7: create post. Write something, optionally attach a photo from the gallery or the camera, then Post.
class CreatePostActivity : AppCompatActivity() {

    private var photo: String? = null       // the chosen photo as Base64 (see PostImages)
    private var encoding = false
    private var cameraUri: Uri? = null      // where the camera app is saving the photo

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            attach(uri)
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) {
            attach(uri)
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_create_post)

        Backend.me?.let {
            findViewById<TextView>(R.id.tvPostAuthor).text = it.name
            findViewById<TextView>(R.id.tvPostInitials).text = it.initials
        }

        linkBack(this, R.id.btnClose)
        findViewById<View>(R.id.optionPhoto).setOnClickListener { pickPhoto.launch("image/*") }
        findViewById<View>(R.id.optionCamera).setOnClickListener { openCamera() }

        val postButton = findViewById<Button>(R.id.btnPost)
        postButton.setOnClickListener {
            val text = findViewById<EditText>(R.id.etPostText).text.toString().trim()
            if (text.isEmpty() && photo == null) {
                Toast.makeText(this, "Write something or add a photo", Toast.LENGTH_SHORT).show()
            } else if (encoding) {
                Toast.makeText(this, "Preparing your photo...", Toast.LENGTH_SHORT).show()
            } else {
                postButton.isEnabled = false
                Backend.createPost(text, photo) { posted ->
                    if (posted) {
                        finish()
                    } else {
                        postButton.isEnabled = true
                        Toast.makeText(this, "Could not post. Check your connection.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Opens the phone's camera app; the photo comes back through [takePhoto]. */
    private fun openCamera() {
        // The camera app refuses to start for an app that declares the permission but was not given it
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val uri = newCameraPhotoUri(this)
        cameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "No camera app found. Choose a photo instead.", Toast.LENGTH_LONG).show()
        }
    }

    /** Shrinks the picture, remembers it for the post and shows it as a preview. */
    private fun attach(uri: Uri) {
        encoding = true
        PostImages.encode(this, uri) { encoded ->
            encoding = false
            if (encoded == null) {
                Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
            } else {
                photo = encoded
                findViewById<ImageView>(R.id.ivPostPreview).apply {
                    visibility = View.VISIBLE
                    setImageURI(uri)
                }
            }
        }
    }
}
