package com.kinnect.i221329_i221069

import android.Manifest
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Screen 8: photo picker. The 16 tiles show the newest photos on the phone.
//  - Opened from a story: tap one photo, then Next, and it goes back to the story.
//  - Opened from Create post (EXTRA_MULTIPLE): tap several photos (they get the numbers 1, 2, 3 ... in the order
//    they were tapped, up to 10), then Next (N).
// Without access to the photos (or without any photo) the tiles keep the design's pictures and the phone's
// own picker opens instead.
class PhotoPickerActivity : AppCompatActivity() {

    private val tileIds = intArrayOf(
        R.id.media01, R.id.media02, R.id.media03, R.id.media04,
        R.id.media05, R.id.media06, R.id.media07, R.id.media08,
        R.id.media09, R.id.media10, R.id.media11, R.id.media12,
        R.id.media13, R.id.media14, R.id.media15, R.id.media16
    )
    private val tiles = ArrayList<ImageView>()
    private val cells = ArrayList<ViewGroup?>()        // the frame around each tile (teal border = chosen)
    private val badges = ArrayList<TextView?>()        // the number on a chosen tile (multiple mode)
    private val photos = arrayOfNulls<Uri>(16)         // the photo behind each tile (null = still the design's picture)
    private val chosen = ArrayList<Int>()              // tile numbers in the order they were chosen
    private var multiple = false
    private lateinit var preview: ImageView            // the big picture above the grid
    private lateinit var nextButton: TextView

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            loadPhotos()
        } else {
            Toast.makeText(this, "Allow photo access to see your pictures here", Toast.LENGTH_SHORT).show()
        }
    }

    // The phone's own picker (used when a tile is only the design's picture)
    private val systemPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            finishWith(if (multiple) uris.take(MAX_PHOTOS) else uris.take(1))
        }
    }

    // A video chosen from the phone (the Video tab of the design): it goes back like a photo
    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            finishWith(listOf(uri))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_media_picker)
        multiple = intent.getBooleanExtra(EXTRA_MULTIPLE, false)
        // Videos are for stories only (a post is made of photos)
        findTextExact(findViewById(R.id.root), "Video")?.setOnClickListener {
            if (multiple) {
                Toast.makeText(this, "A post is made of photos", Toast.LENGTH_SHORT).show()
            } else {
                videoPicker.launch("video/*")
            }
        }

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
            tile.setOnClickListener { onTileTapped(i) }
            tiles.add(tile)

            // The design marks some tiles as chosen ("1", "2") and one as a video ("0:23"); real photos are not.
            // The teal frame of a chosen tile is kept, but it now follows what the user taps (see refreshSelection()).
            val cell = tile.parent
            if (cell is RelativeLayout) {
                for (c in 0 until cell.childCount) {
                    if (cell.getChildAt(c) !== tile) {
                        cell.getChildAt(c).visibility = View.GONE
                    }
                }
                cell.setBackgroundColor(Color.TRANSPARENT)
                cell.setPadding(0, 0, 0, 0)
                cells.add(cell)
                badges.add(makeBadge(cell))
            } else {
                cells.add(null)
                badges.add(null)
            }
        }

        // The big picture above the grid: the child of the RelativeLayout around the "SELECT MULTIPLE" pill
        val root = findViewById<View>(R.id.root)
        val pill = findTextExact(root, "SELECT MULTIPLE")!!
        preview = (pill.parent.parent as ViewGroup).getChildAt(0) as ImageView
        (pill.parent as View).setOnClickListener {
            val message = if (multiple) "Tap photos to add them to your post" else "A story is made from one photo"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Cancel closes, Next hands over the chosen photos
        linkBack(this, R.id.btnCancel)
        nextButton = findViewById(R.id.btnNext)
        nextButton.text = "Next"
        nextButton.setOnClickListener { onNext() }

        if (hasPhotoAccess()) {
            loadPhotos()
        } else {
            permissionLauncher.launch(photoPermission())
        }
    }

    /** The round number in the corner of a chosen photo. */
    private fun makeBadge(cell: RelativeLayout): TextView {
        val badge = TextView(this)
        badge.gravity = Gravity.CENTER
        badge.setBackgroundResource(R.drawable.bg_react_like)
        badge.setTextColor(Color.WHITE)
        badge.textSize = 13f
        badge.setTypeface(null, android.graphics.Typeface.BOLD)
        badge.includeFontPadding = false
        badge.visibility = View.GONE
        val params = RelativeLayout.LayoutParams(dp(this, 26), dp(this, 26))
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        params.addRule(RelativeLayout.ALIGN_PARENT_END)
        params.setMargins(0, dp(this, 6), dp(this, 6), 0)
        cell.addView(badge, params)
        return badge
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
            chosen.add(0)
            refreshSelection()
        }
    }

    /** Teal frame, number and the big preview follow what is chosen. */
    private fun refreshSelection() {
        for (i in cells.indices) {
            val cell = cells[i] ?: continue
            val order = chosen.indexOf(i)
            if (order >= 0) {
                cell.setBackgroundColor(getColor(R.color.teal))
                cell.setPadding(dp(this, 3), dp(this, 3), dp(this, 3), dp(this, 3))
            } else {
                cell.setBackgroundColor(Color.TRANSPARENT)
                cell.setPadding(0, 0, 0, 0)
            }
            badges[i]?.let {
                it.visibility = if (multiple && order >= 0) View.VISIBLE else View.GONE
                it.text = (order + 1).toString()
            }
        }
        nextButton.text = if (multiple && chosen.size > 0) "Next (${chosen.size})" else "Next"

        // The preview shows the photo that was chosen last
        val last = chosen.lastOrNull() ?: return
        val uri = photos[last] ?: return
        ImageTools.load(this, uri, 1080) { bitmap ->
            if (bitmap != null && chosen.lastOrNull() == last) {
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
        if (multiple) {
            // Tapping a chosen photo takes it out again and the numbers close up
            if (position in chosen) {
                chosen.remove(position)
            } else if (chosen.size < MAX_PHOTOS) {
                chosen.add(position)
            } else {
                Toast.makeText(this, "You can add up to $MAX_PHOTOS photos", Toast.LENGTH_SHORT).show()
            }
        } else {
            chosen.clear()
            chosen.add(position)
        }
        refreshSelection()
    }

    private fun onNext() {
        val uris = chosen.mapNotNull { photos[it] }
        if (uris.isNotEmpty()) {
            finishWith(uris)
        } else if (photos.all { it == null }) {
            systemPicker.launch("image/*")
        } else {
            Toast.makeText(this, "Tap a photo first", Toast.LENGTH_SHORT).show()
        }
    }

    /** Hands the chosen photos back: the first one in data, all of them in EXTRA_PICKED_URIS. */
    private fun finishWith(uris: List<Uri>) {
        val result = Intent()
        result.data = uris.first()
        result.putStringArrayListExtra(EXTRA_PICKED_URIS, ArrayList(uris.map { it.toString() }))
        val clip = ClipData.newRawUri("photos", uris.first())
        for (uri in uris.drop(1)) {
            clip.addItem(ClipData.Item(uri))
        }
        result.clipData = clip
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        private const val MAX_PHOTOS = 10
    }
}
