package com.kinnect.i221329_i221069

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 10: story editor. The photo comes from the camera or the picker. The tools at the top add a text sticker,
// emoji, a filter or the names of friends; the share targets at the bottom publish the story (see Stories.kt).
class StoryEditorActivity : AppCompatActivity() {

    private lateinit var photo: ImageView
    private lateinit var sticker: TextView

    private var photoUri: Uri? = null
    private var stickerText = ""
    private val tagged = ArrayList<String>()     // names of the friends tagged in the story
    private var filter = ImageTools.FILTER_NONE
    private var sharing = false

    private var people: Map<String, UserInfo> = emptyMap()
    private var friendUids: List<String> = emptyList()
    private var friendListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_story_editor)

        photoUri = intent.data
        if (photoUri == null) {
            Toast.makeText(this, "No photo to edit", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // The views of the design have no ids, so they are found by their place in the layout:
        // root > column > [photo card, share row]; the photo card holds picture, close, tools, sticker
        val column = childGroup(findViewById(R.id.root), 0)
        val card = childGroup(column, 0)
        photo = card.getChildAt(0) as ImageView
        val tools = card.getChildAt(2) as ViewGroup
        sticker = card.getChildAt(3) as TextView
        val shareRow = column.getChildAt(1) as ViewGroup

        // The photo and an empty sticker (the design's "Saturday plans" was only a sample)
        photo.setImageDrawable(null)
        ImageTools.load(this, photoUri!!, 1080) { bitmap ->
            if (bitmap == null) {
                Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                photo.setImageBitmap(bitmap)
            }
        }
        refreshSticker()
        sticker.setOnClickListener { askForText() }

        linkBack(this, R.id.btnClose)

        // Tools: tag people, text, stickers, music, effects
        tools.getChildAt(0).setOnClickListener { tagPeople() }
        tools.getChildAt(1).setOnClickListener { askForText() }
        tools.getChildAt(2).setOnClickListener { pickEmoji() }
        tools.getChildAt(3).setOnClickListener {
            Toast.makeText(this, "Music is not available yet", Toast.LENGTH_SHORT).show()
        }
        tools.getChildAt(4).setOnClickListener { pickFilter() }

        // Share targets: "Your story" and the arrow share with all friends, "Close friends" asks who
        Backend.me?.let { findTextExact(shareRow, "JW")?.text = it.initials }
        findViewById<View>(R.id.btnYourStory).setOnClickListener { share(AUDIENCE_FRIENDS, emptyList()) }
        findViewById<View>(R.id.btnShare).setOnClickListener { share(AUDIENCE_FRIENDS, emptyList()) }
        shareRow.getChildAt(1).setOnClickListener { chooseCloseFriends() }
    }

    override fun onStart() {
        super.onStart()
        Backend.loadPeople { people = it }
        friendListener = Backend.listenFriendships { list ->
            friendUids = list.filter { it.accepted }.map { it.otherUid }
        }
    }

    override fun onStop() {
        super.onStop()
        friendListener?.remove()
        friendListener = null
    }

    // ------------------------------------------------------------------ sticker and tools

    /** The text on the photo: what was typed, then "with Aisha, Omar" for tagged friends. */
    private fun composedText(): String {
        val parts = ArrayList<String>()
        if (stickerText.isNotBlank()) {
            parts.add(stickerText.trim())
        }
        if (tagged.isNotEmpty()) {
            parts.add("with " + tagged.joinToString(", "))
        }
        return parts.joinToString("\n")
    }

    private fun refreshSticker() {
        val text = composedText()
        sticker.text = text
        setVisible(sticker, text.isNotEmpty())
    }

    private fun askForText() {
        val field = EditText(this)
        field.setText(stickerText)
        field.setSelection(field.text.length)
        field.hint = "Write something"
        val box = dp(this, 20)
        field.setPadding(box, box, box, box)
        AlertDialog.Builder(this)
            .setTitle("Text on your story")
            .setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                stickerText = field.text.toString()
                refreshSticker()
            }
            .show()
    }

    private fun pickEmoji() {
        val emoji = arrayOf(
            "😀", "😍", "😂", "🔥", "🎉",
            "❤️", "👍", "🌴", "✨", "🌙"
        )
        AlertDialog.Builder(this)
            .setTitle("Add a sticker")
            .setItems(emoji) { _, which ->
                stickerText = (stickerText + " " + emoji[which]).trim()
                refreshSticker()
            }
            .show()
    }

    private fun pickFilter() {
        AlertDialog.Builder(this)
            .setTitle("Effects")
            .setSingleChoiceItems(ImageTools.FILTER_NAMES, filter) { dialog, which ->
                filter = which
                val matrix = ImageTools.matrixOf(which)
                if (matrix == null) photo.clearColorFilter() else photo.colorFilter = ColorMatrixColorFilter(matrix)
                dialog.dismiss()
            }
            .show()
    }

    /** Lets the user tick friends who appear in the story. */
    private fun tagPeople() {
        val friends = friendUids.mapNotNull { people[it] }
        if (friends.isEmpty()) {
            Toast.makeText(this, "Add some friends to tag them", Toast.LENGTH_SHORT).show()
            return
        }
        val names = friends.map { it.name }.toTypedArray()
        val checked = BooleanArray(friends.size) { names[it] in tagged }
        AlertDialog.Builder(this)
            .setTitle("Tag friends")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                tagged.clear()
                for (i in names.indices) {
                    if (checked[i]) tagged.add(names[i])
                }
                refreshSticker()
            }
            .show()
    }

    // ------------------------------------------------------------------ sharing

    /** Asks which friends make up the "close friends" list for this story, remembers it, and shares. */
    private fun chooseCloseFriends() {
        val friends = friendUids.mapNotNull { people[it] }
        if (friends.isEmpty()) {
            Toast.makeText(this, "Add some friends first, then you can pick close friends", Toast.LENGTH_LONG).show()
            return
        }
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val before = prefs.getStringSet("close_friends", emptySet()) ?: emptySet()
        val names = friends.map { it.name }.toTypedArray()
        val checked = BooleanArray(friends.size) { friends[it].uid in before }

        AlertDialog.Builder(this)
            .setTitle("Share with close friends")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share") { _, _ ->
                val chosen = friends.filterIndexed { i, _ -> checked[i] }.map { it.uid }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, "Pick at least one friend", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putStringSet("close_friends", chosen.toSet()).apply()
                    share(AUDIENCE_CLOSE, chosen)
                }
            }
            .show()
    }

    private fun share(audience: String, audienceUids: List<String>) {
        val uri = photoUri ?: return
        if (sharing) {
            return
        }
        sharing = true
        Toast.makeText(this, "Sharing your story...", Toast.LENGTH_SHORT).show()

        ImageTools.encodePicture(this, uri, filter) { picture ->
            if (picture == null) {
                sharing = false
                Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                return@encodePicture
            }
            Backend.createStory(picture, composedText(), audience, audienceUids) { shared ->
                sharing = false
                if (shared) {
                    // Tell the camera we are done, then show the story
                    setResult(RESULT_OK)
                    startActivity(Intent(this, MyStoryActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Could not share your story. Check your connection.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
