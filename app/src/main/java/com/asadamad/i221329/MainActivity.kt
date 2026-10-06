package com.asadamad.i221329

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

// Screen 1 (launcher): shows the logo for a moment, then opens Log in on its own.
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Wait 1.5 seconds, then move on. finish() removes the splash from the back stack.
        handler.postDelayed({
            openScreen(this, LoginActivity::class.java)
            finish()
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the user leaves early, make sure the delayed jump never fires
        handler.removeCallbacksAndMessages(null)
    }
}
