package com.asadamad.i221329

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Screen 6: comments and replies for the post.
class CommentsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_comments)

        linkBack(this, R.id.btnBack)

        // Top-level comments (the replies are laid out directly in the screen)
        bindComment(
            findViewById(R.id.commentAisha), "AK", R.color.avatar_crimson, "Aisha Khan",
            "That sky is unreal. Which part of the beach was this?"
        )
        bindComment(
            findViewById(R.id.commentHamza), "HA", R.color.avatar_brown, "Hamza Ali",
            "Same spot, one year ago:"
        )
        bindComment(
            findViewById(R.id.commentMaya), "MC", R.color.avatar_olive, "Maya Chen",
            "Framing this one."
        )
    }
}
