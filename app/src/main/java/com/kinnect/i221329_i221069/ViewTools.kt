package com.kinnect.i221329_i221069

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat

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

/** Colours an icon. */
fun tintImage(view: ImageView, color: Int) {
    ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(color))
}

/** Makes the eye icon of a password field show and hide the password. */
fun wirePasswordEye(root: View, field: EditText) {
    val eye = allViews(root).firstOrNull { it.contentDescription == "Show password" } ?: return
    var shown = false
    eye.setOnClickListener {
        shown = !shown
        field.transformationMethod = if (shown) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        field.setSelection(field.text.length)
        eye.alpha = if (shown) 1f else 0.6f
    }
}

/**
 * Makes the strength bar under the password field of Create account follow what is typed: three bars and a word
 * (Weak, Medium, Good, Strong), see passwordStrength(). The bar and the word have no ids; the word is found by its text.
 */
fun wirePasswordStrength(root: View, field: EditText) {
    val label = findTextExact(root, "Medium") ?: return
    val bars = (label.parent as ViewGroup).let { row -> (0 until 3).map { row.getChildAt(it) } }
    fun show(password: String) {
        val score = passwordStrength(password)
        val filled = minOf(score, 3)
        val color = when (score) {
            0, 1, 2 -> R.color.rust
            3 -> R.color.gold
            else -> R.color.online
        }
        for (i in bars.indices) {
            val tint = if (i < filled) color else R.color.strength_empty
            bars[i].backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, tint))
        }
        label.text = when (score) {
            0 -> ""
            1 -> "Weak"
            2 -> "Medium"
            3 -> "Good"
            else -> "Strong"
        }
        label.setTextColor(ContextCompat.getColor(root.context, color))
    }
    field.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            show(s?.toString() ?: "")
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
    show(field.text.toString())
}
