package com.asadamad.i221329

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 9: story camera. The preview is a picture, but we do ask for the camera permission
// the way a real camera screen would.
class StoryCameraActivity : AppCompatActivity() {

    // Result of the permission dialog
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Camera Permission Granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_camera)

        // Ask only if we do not have the permission yet
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        linkBack(this, R.id.btnClose)

        // Shutter goes to the editor, the gallery thumbnail goes to the photo picker
        linkTo(this, R.id.btnShutter, StoryEditorActivity::class.java)
        linkTo(this, R.id.btnGallery, PhotoPickerActivity::class.java)
    }
}
