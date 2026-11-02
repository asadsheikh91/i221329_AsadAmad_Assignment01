package com.kinnect.i221329_i221069

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** A new empty file where the phone's camera app can save a photo (see res/xml/file_paths.xml). */
fun newCameraPhotoUri(context: Context): Uri {
    val folder = File(context.cacheDir, "camera")
    folder.mkdirs()
    val file = File.createTempFile("photo_", ".jpg", folder)
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}

// Screen 9: story camera. The shutter opens the phone's camera app and brings the photo to the story editor;
// the gallery thumbnail opens the photo picker. The picture on this screen is the design's camera preview.
class StoryCameraActivity : AppCompatActivity() {

    private var photoUri: Uri? = null   // where the camera app is saving the photo

    // Result of the permission dialog; when it is granted the shutter works straight away
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = photoUri
        if (taken && uri != null) {
            openEditor(uri)
        }
    }

    // The photo picker answers with the chosen picture in the intent's data
    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            openEditor(uri)
        }
    }

    // When the story has been shared the editor says so, and the camera closes too
    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_camera)

        photoUri = savedInstanceState?.getString("photo_uri")?.let { Uri.parse(it) }

        // Ask only if we do not have the permission yet
        if (!hasCameraPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        linkBack(this, R.id.btnClose)

        // Shutter takes a photo, the gallery thumbnail opens the photo picker
        findViewById<android.view.View>(R.id.btnShutter).setOnClickListener { takePhoto() }
        findViewById<android.view.View>(R.id.btnGallery).setOnClickListener {
            pickerLauncher.launch(Intent(this, PhotoPickerActivity::class.java))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("photo_uri", photoUri?.toString())
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun takePhoto() {
        // The camera app refuses to start for an app that declares the permission but was not given it
        if (!hasCameraPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val uri = newCameraPhotoUri(this)
        photoUri = uri
        try {
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "No camera app found. Use the gallery instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openEditor(uri: Uri) {
        val intent = Intent(this, StoryEditorActivity::class.java)
        intent.data = uri
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        editorLauncher.launch(intent)
    }
}
