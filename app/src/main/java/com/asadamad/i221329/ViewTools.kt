package com.asadamad.i221329

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

// Small helpers for finding views that have no id in the layout files.
// The layouts are the finished design and are never edited, so the screens that need to change a
// text or a picture without an id reach it by its position or by the text it shows right now.

/** Converts dp to pixels. */
fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

/** The child of [parent] at [index], as a ViewGroup. */
fun childGroup(parent: View, index: Int): ViewGroup = (parent as ViewGroup).getChildAt(index) as ViewGroup

/** The child of [parent] at [index]. */
fun childView(parent: View, index: Int): View = (parent as ViewGroup).getChildAt(index)

/** Every view below [root] (not including it), parents before their children. */
fun allViews(root: View): List<View> {
    val found = ArrayList<View>()
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            found.add(child)
            found.addAll(allViews(child))
        }
    }
    return found
}

/** The first TextView below [root] whose text contains [part] (null when there is none). */
fun findTextView(root: View, part: String): TextView? {
    return allViews(root).filterIsInstance<TextView>().firstOrNull { it.text.toString().contains(part) }
}

/** The first TextView below [root], whatever it says. */
fun firstTextView(root: View): TextView? {
    return allViews(root).filterIsInstance<TextView>().firstOrNull()
}

/** Shows or hides a view without moving its neighbours around. */
fun setVisible(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.GONE
}

/** The first TextView below [root] whose text is exactly [text]. */
fun findTextExact(root: View, text: String): TextView? {
    return allViews(root).filterIsInstance<TextView>().firstOrNull { it.text.toString() == text }
}

/** A button that looks like the ones in the layouts (teal, or grey when [grey] is true). */
fun styledButton(context: Context, label: String, grey: Boolean): Button {
    val button = Button(context)
    button.text = label
    button.isAllCaps = false
    button.setTypeface(null, Typeface.BOLD)
    button.textSize = 15f
    button.minHeight = 0
    button.minimumHeight = 0
    button.minWidth = 0
    button.minimumWidth = 0
    button.setPadding(0, 0, 0, 0)
    button.stateListAnimator = null
    button.setBackgroundResource(R.drawable.bg_round_12)
    button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, if (grey) R.color.chip else R.color.teal))
    button.setTextColor(ContextCompat.getColor(context, if (grey) R.color.text_primary else R.color.white))
    return button
}

/** A text with the look of one of the Kin.Text styles, for rows that are built in code. */
fun styledText(context: Context, text: String, style: Int): TextView {
    val view = TextView(context, null, 0, style)
    view.text = text
    return view
}
