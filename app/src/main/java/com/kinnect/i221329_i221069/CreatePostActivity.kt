package com.kinnect.i221329_i221069

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 7: create post. Write something, add one or more photos (Photo picker, screen 8, or the camera), then Post.
// The photos of a post are shown in the feed as a collage with "+N" (see showPostPictures in PostUi.kt).
class CreatePostActivity : AppCompatActivity() {

    private val maxPhotos = 10

    private val photos = ArrayList<String>()        // the chosen photos as Base64 (see PostImages)
    private var preparing = 0                       // photos that are still being shrunk
    private var cameraUri: Uri? = null              // where the camera app is saving the photo

    private lateinit var strip: LinearLayout        // the row of small previews

    // The photo picker answers with a list of photos (EXTRA_PICKED_URIS)
    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uris = it.data?.getStringArrayListExtra(EXTRA_PICKED_URIS)
        if (it.resultCode == RESULT_OK && uris != null) {
            addPhotos(uris.map { text -> Uri.parse(text) })
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) {
            addPhotos(listOf(uri))
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
            bindAvatar(findViewById(R.id.tvPostInitials), it.uid, it.name)
        }

        // The single preview of the design is replaced by a row that can hold several photos
        val single = findViewById<ImageView>(R.id.ivPostPreview)
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        scroll.addView(strip)
        scroll.visibility = View.GONE
        val parent = single.parent as ViewGroup
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(dp(this, 16), dp(this, 16), dp(this, 16), 0)
        parent.addView(scroll, parent.indexOfChild(single) + 1, params)

        linkBack(this, R.id.btnClose)
        findViewById<View>(R.id.optionPhoto).setOnClickListener {
            val intent = Intent(this, PhotoPickerActivity::class.java).putExtra(EXTRA_MULTIPLE, true)
            pickerLauncher.launch(intent)
        }
        findViewById<View>(R.id.optionCamera).setOnClickListener { openCamera() }

        val postButton = findViewById<Button>(R.id.btnPost)
        postButton.setOnClickListener {
            val text = findViewById<EditText>(R.id.etPostText).text.toString().trim()
            if (text.isEmpty() && photos.isEmpty()) {
                Toast.makeText(this, "Write something or add a photo", Toast.LENGTH_SHORT).show()
            } else if (preparing > 0) {
                Toast.makeText(this, "Preparing your photos...", Toast.LENGTH_SHORT).show()
            } else {
                postButton.isEnabled = false
                Backend.createPost(text, photos.toList()) { posted ->
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

    /** Shrinks the pictures one after the other (so they keep their order) and adds them to the post. */
    private fun addPhotos(uris: List<Uri>) {
        for (uri in uris) {
            if (photos.size + preparing >= maxPhotos) {
                Toast.makeText(this, "A post can have up to $maxPhotos photos", Toast.LENGTH_SHORT).show()
                break
            }
            preparing++
            PostImages.encode(this, uri) { encoded ->
                preparing--
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    photos.add(encoded)
                    showStrip()
                }
            }
        }
    }

    /** One small square per chosen photo; tapping one offers to remove it. */
    private fun showStrip() {
        strip.removeAllViews()
        (strip.parent as View).visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
        val size = dp(this, 120)
        for (index in photos.indices) {
            val thumb = ImageView(this)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.setBackgroundResource(R.drawable.bg_round_12)
            thumb.clipToOutline = true
            thumb.setImageBitmap(ImageTools.fromBase64(photos[index]))
            thumb.setOnClickListener {
                AlertDialog.Builder(this)
                    .setMessage("Remove this photo from the post?")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Remove") { _, _ ->
                        photos.removeAt(index)
                        showStrip()
                    }
                    .show()
            }
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = dp(this, 8)
            strip.addView(thumb, params)
        }
    }
}
