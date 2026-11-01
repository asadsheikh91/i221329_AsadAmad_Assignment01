package com.asadamad.i221329

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Texts of the profile screens. The design writes numbers and names in bold dark text inside a
// grey sentence ("834 friends"), so these build the same look from real values.

/** [bold] in bold dark text, then [rest] in the normal colour of the sentence. */
fun boldThen(context: Context, bold: String, rest: String): SpannableStringBuilder {
    val builder = SpannableStringBuilder(bold)
    builder.setSpan(StyleSpan(Typeface.BOLD), 0, bold.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    builder.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_primary)),
        0, bold.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    builder.append(rest)
    return builder
}

/** "1 friend", "834 friends" with the number in bold. */
fun friendCountText(context: Context, count: Int): CharSequence {
    return boldThen(context, count.toString(), if (count == 1) " friend" else " friends")
}

/** "Joined March 2019" (empty when the join date is not known yet). */
fun joinedText(millis: Long): String {
    if (millis <= 0) {
        return ""
    }
    return "Joined " + SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))
}

/** "Friends with Aisha Khan", "Friends with Aisha Khan and Omar Farooq", "... and 3 others". */
fun mutualFriendsText(context: Context, names: List<String>): CharSequence {
    val builder = SpannableStringBuilder("Friends with ")
    fun addName(name: String) {
        val start = builder.length
        builder.append(name)
        builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_primary)),
            start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    when {
        names.size == 1 -> addName(names[0])
        names.size == 2 -> {
            addName(names[0])
            builder.append(" and ")
            addName(names[1])
        }
        else -> {
            addName(names[0])
            builder.append(", ")
            addName(names[1])
            builder.append(" and ")
            addName("${names.size - 2} other" + if (names.size - 2 == 1) "" else "s")
        }
    }
    return builder
}
