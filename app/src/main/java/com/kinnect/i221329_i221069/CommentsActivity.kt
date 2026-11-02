package com.kinnect.i221329_i221069

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration

// Screen 6: the comments on one post, live from Firestore.
class CommentsActivity : AppCompatActivity() {

    private var listener: ListenerRegistration? = null
    private lateinit var postId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_comments)

        postId = intent.getStringExtra(EXTRA_POST_ID) ?: run {
            finish()
            return
        }

        linkBack(this, R.id.btnBack)

        // The strip under the title shows how many people liked the post
        val likes = intent.getIntExtra(EXTRA_LIKE_COUNT, 0)
        if (likes == 0) {
            findViewById<View>(R.id.likesRow).visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.tvLikeCount).text = "$likes like" + if (likes == 1) "" else "s"
        }

        val input = findViewById<EditText>(R.id.etComment)
        findViewById<View>(R.id.btnSendComment).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                Backend.addComment(postId, text)
                input.setText("")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::postId.isInitialized) {
            listener = Backend.listenComments(postId) { showComments(it) }
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun showComments(comments: List<Comment>) {
        val container = findViewById<LinearLayout>(R.id.commentsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (comment in comments) {
            val row = inflater.inflate(R.layout.item_comment, container, false)
            bindComment(row, initialsOf(comment.authorName), avatarColorOf(comment.authorId), comment.authorName, comment.text)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (10 * resources.displayMetrics.density).toInt()
            container.addView(row, params)
        }
        findViewById<View>(R.id.tvNoComments).visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE

        val scroll = findViewById<ScrollView>(R.id.commentsScroll)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
