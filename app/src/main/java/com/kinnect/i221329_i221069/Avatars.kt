package com.kinnect.i221329_i221069

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory

// Profile pictures. Every avatar in the design is a coloured circle with initials; a person who has uploaded
// a profile picture (Edit profile) gets the picture inside that same circle instead.
//
// bindAvatar() is called wherever an avatar is shown. It puts the initials in at once and, if the person has a
// picture, swaps it in as soon as it is loaded (pictures are small, about 10 KB, and remembered).

private val avatarPictures = LruCache<String, Bitmap>(60)      // people who have a picture
private val avatarMissing = HashSet<String>()                  // people we know have none (this session)
private val avatarLoading = HashMap<String, MutableList<TextView>>()

/** Shows the avatar of [uid]: their picture when they have one, otherwise the initials of [name]. */
fun bindAvatar(view: TextView, uid: String, name: String) {
    view.tag = "avatar:$uid"
    val picture = avatarPictures.get(uid)
    if (picture != null) {
        showAvatarPicture(view, picture)
        return
    }
    showAvatarInitials(view, uid, name)
    if (uid in avatarMissing) {
        return
    }
    // Several rows can ask for the same person at once: load once, then fill all of them
    val waiting = avatarLoading[uid]
    if (waiting != null) {
        waiting.add(view)
        return
    }
    avatarLoading[uid] = mutableListOf(view)
    Backend.loadAvatar(uid) { base64 ->
        val views = avatarLoading.remove(uid) ?: mutableListOf()
        val bitmap = if (base64 == null) null else ImageTools.fromBase64(base64)
        if (bitmap == null) {
            avatarMissing.add(uid)
        } else {
            avatarPictures.put(uid, bitmap)
            for (waitingView in views) {
                // The row may have been reused for somebody else in the meantime
                if (waitingView.tag == "avatar:$uid") {
                    showAvatarPicture(waitingView, bitmap)
                }
            }
        }
    }
}

/** Remembers a picture that was just uploaded, so the new one shows up everywhere straight away. */
fun rememberAvatar(uid: String, base64: String) {
    val bitmap = ImageTools.fromBase64(base64) ?: return
    avatarMissing.remove(uid)
    avatarPictures.put(uid, bitmap)
}

private fun showAvatarInitials(view: TextView, uid: String, name: String) {
    view.setBackgroundResource(R.drawable.bg_circle)
    view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(view.context, avatarColorOf(uid)))
    view.text = initialsOf(name)
}

private fun showAvatarPicture(view: TextView, picture: Bitmap) {
    val round = RoundedBitmapDrawableFactory.create(view.resources, picture)
    round.isCircular = true
    view.background = round
    view.backgroundTintList = null
    view.text = ""
}

/** Shows a picture in an avatar circle without saving anything (a preview while editing the profile). */
fun previewAvatar(view: TextView, picture: Bitmap) {
    showAvatarPicture(view, picture)
}
