package com.asadamad.i221329

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 8: photo picker. The 16 tiles show the newest photos on the phone; tap one, then Next, and the
// picture goes back to the screen that opened this one. Without access to the photos (or without any photo)
// the tiles keep the design's pictures and the phone's own picker opens instead.
class PhotoPickerActivity : AppCompatActivity() {

    private val tileIds = intArrayOf(
        R.id.media01, R.id.media02, R.id.media03, R.id.media04,
        R.id.media05, R.id.media06, R.id.media07, R.id.media08,
        R.id.media09, R.id.media10, R.id.media11, R.id.media12,
        R.id.media13, R.id.media14, R.id.media15, R.id.media16
    )
    private val tiles = ArrayList<ImageView>()
    private val cells = ArrayList<android.view.ViewGroup?>()   // the frame around each tile (its teal border marks the chosen one)
    private val photos = arrayOfNulls<Uri>(16)     // the photo behind each tile (null = still the design's picture)
    private var selected = -1
    private lateinit var preview: ImageView         // the big picture above the grid

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            loadPhotos()
        } else {
            Toast.makeText(this, "Allow photo access to see your pictures here", Toast.LENGTH_SHORT).show()
        }
    }

    private val systemPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            finishWith(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_media_picker)

        // The 16 tiles start with the design's pictures
        val art = intArrayOf(
            R.drawable.art_sand, R.drawable.art_teal, R.drawable.art_lilac, R.drawable.art_blue,
            R.drawable.art_green, R.drawable.art_rose, R.drawable.art_night, R.drawable.art_teal,
            R.drawable.art_lilac, R.drawable.art_sand, R.drawable.art_green, R.drawable.art_blue,
            R.drawable.art_rose, R.drawable.art_night, R.drawable.art_blue, R.drawable.art_sand
        )
        for (i in tileIds.indices) {
            val tile = findViewById<ImageView>(tileIds[i])
            bindMedia(tile, art[i])
            // The design marks some tiles as chosen ("1", "2") and one as a video ("0:23"); real photos are not.
            // The teal frame of a chosen tile is kept, but it now follows what the user taps (see select()).
            val cell = tile.parent
            if (cell is android.widget.RelativeLayout) {
                for (c in 0 until cell.childCount) {
                    if (cell.getChildAt(c) !== tile) {
                        cell.getChildAt(c).visibility = android.view.View.GONE
                    }
                }
                cell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                cell.setPadding(0, 0, 0, 0)
                cells.add(cell)
            } else {
                cells.add(null)
            }
            tile.setOnClickListener { onTileTapped(i) }
            tiles.add(tile)
        }

        // The big picture above the grid: the child of the RelativeLayout around the "SELECT MULTIPLE" pill
        val root = findViewById<android.view.View>(R.id.root)
        val pill = findTextExact(root, "SELECT MULTIPLE")!!
        preview = (pill.parent.parent as android.view.ViewGroup).getChildAt(0) as ImageView
        (pill.parent as android.view.View).setOnClickListener {
            Toast.makeText(this, "A story is made from one photo", Toast.LENGTH_SHORT).show()
        }

        // Cancel closes, Next hands over the chosen photo
        linkBack(this, R.id.btnCancel)
        findViewById<TextView>(R.id.btnNext).text = "Next"
        findViewById<android.view.View>(R.id.btnNext).setOnClickListener { onNext() }

        if (hasPhotoAccess()) {
            loadPhotos()
        } else {
            permissionLauncher.launch(photoPermission())
        }
    }

    private fun photoPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun hasPhotoAccess(): Boolean {
        return ContextCompat.checkSelfPermission(this, photoPermission()) == PackageManager.PERMISSION_GRANTED
    }

    /** Puts the newest photos of the phone into the tiles. */
    private fun loadPhotos() {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            MediaStore.Images.Media.DATE_ADDED + " DESC"
        ) ?: return

        var found = 0
        cursor.use {
            while (it.moveToNext() && found < tiles.size) {
                val uri = ContentUris.withAppendedId(collection, it.getLong(0))
                val position = found
                photos[position] = uri
                ImageTools.load(this, uri, 320) { bitmap ->
                    if (bitmap != null) {
                        tiles[position].setImageBitmap(bitmap)
                    } else {
                        photos[position] = null
                    }
                }
                found++
            }
        }
        // Tiles beyond the photos that exist keep the design's pictures but are not selectable
        if (found > 0) {
            select(0)
        }
    }

    /** Marks tile [position] as the chosen one (teal frame) and shows its photo in the big preview. */
    private fun select(position: Int) {
        selected = position
        for (i in cells.indices) {
            val cell = cells[i] ?: continue
            if (i == position) {
                cell.setBackgroundColor(getColor(R.color.teal))
                cell.setPadding(dp(this, 3), dp(this, 3), dp(this, 3), dp(this, 3))
            } else {
                cell.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                cell.setPadding(0, 0, 0, 0)
            }
        }
        val uri = photos[position] ?: return
        ImageTools.load(this, uri, 1080) { bitmap ->
            if (bitmap != null && selected == position) {
                preview.setImageBitmap(bitmap)
            }
        }
    }

    private fun onTileTapped(position: Int) {
        if (photos[position] == null) {
            // This tile is only a picture of the design: use the phone's own picker instead
            systemPicker.launch("image/*")
            return
        }
        select(position)
    }

    private fun onNext() {
        val uri = if (selected >= 0) photos[selected] else null
        if (uri != null) {
            finishWith(uri)
        } else if (photos.all { it == null }) {
            systemPicker.launch("image/*")
        } else {
            Toast.makeText(this, "Tap a photo first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWith(uri: Uri) {
        val result = Intent()
        result.data = uri
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, result)
        finish()
    }
}
