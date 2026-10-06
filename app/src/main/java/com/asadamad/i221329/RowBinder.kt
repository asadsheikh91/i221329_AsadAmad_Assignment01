package com.asadamad.i221329

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

// Fills in the repeated row layouts (item_*.xml).
// Each row is an <include> inside a screen; the Activity calls one function below per row.

private fun setText(row: View, id: Int, value: String) {
    row.findViewById<TextView>(id).text = value
}

// Colours the white circle background of an avatar or badge
private fun tint(view: View, colorRes: Int) {
    view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(view.context, colorRes))
}

/** One card in the Home stories row (item_story). */
fun bindStory(row: View, art: Int, initials: String, color: Int, name: String) {
    row.findViewById<ImageView>(R.id.storyArt).setImageResource(art)
    setText(row, R.id.tvInitials, initials)
    tint(row.findViewById(R.id.tvInitials), color)
    setText(row, R.id.tvName, name)
}

/** A top-level comment bubble (item_comment). */
fun bindComment(row: View, initials: String, color: Int, name: String, message: String) {
    setText(row, R.id.tvInitials, initials)
    tint(row.findViewById(R.id.tvInitials), color)
    setText(row, R.id.tvName, name)
    setText(row, R.id.tvText, message)
}

/** A friend request or suggestion (item_friend). */
fun bindFriend(
    row: View, initials: String, color: Int, name: String, age: String,
    mutual: String, primary: String, secondary: String
) {
    setText(row, R.id.tvInitials, initials)
    tint(row.findViewById(R.id.tvInitials), color)
    setText(row, R.id.tvName, name)
    setText(row, R.id.tvAge, age)
    setText(row, R.id.tvMutual, mutual)
    setText(row, R.id.btnPrimary, primary)
    setText(row, R.id.btnSecondary, secondary)
}

/** A notification (item_notification). [message] is a string resource because it has bold names. */
fun bindNotification(
    row: View, initials: String, color: Int, message: Int, time: String,
    isNew: Boolean, badgeColor: Int, badgeIcon: Int
) {
    setText(row, R.id.tvInitials, initials)
    tint(row.findViewById(R.id.tvInitials), color)
    row.findViewById<TextView>(R.id.tvText).setText(message)

    val timeView = row.findViewById<TextView>(R.id.tvTime)
    timeView.text = time
    if (isNew) {
        // New notifications show the time in bold teal
        timeView.setTextColor(ContextCompat.getColor(row.context, R.color.teal))
        timeView.setTypeface(null, Typeface.BOLD)
    }

    val badge = row.findViewById<ImageView>(R.id.notifBadge)
    badge.setImageResource(badgeIcon)
    tint(badge, badgeColor)
}

/** A conversation row (item_chat). */
fun bindChat(row: View, initials: String, color: Int, name: String, preview: String) {
    setText(row, R.id.tvInitials, initials)
    tint(row.findViewById(R.id.tvInitials), color)
    setText(row, R.id.tvName, name)
    setText(row, R.id.tvPreview, preview)
}

/** A chat bubble (item_message_sent or item_message_received): the include itself is the TextView. */
fun bindMessage(bubble: View, message: String) {
    (bubble as TextView).text = message
}

/** A marketplace listing (item_marketplace). */
fun bindListing(row: View, art: Int, price: String, name: String, place: String) {
    row.findViewById<ImageView>(R.id.listingArt).setImageResource(art)
    setText(row, R.id.tvPrice, price)
    setText(row, R.id.tvName, name)
    setText(row, R.id.tvPlace, place)
}

/** One photo tile in the media picker (item_media): the include itself is the ImageView. */
fun bindMedia(tile: View, art: Int) {
    (tile as ImageView).setImageResource(art)
}
