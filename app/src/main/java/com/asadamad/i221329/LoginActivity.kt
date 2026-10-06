package com.asadamad.i221329

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

// Screen 2: log in. The fields are pre-filled with dummy data, any tap on Log in goes to Home.
class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_login)

        // Both the button and the "recent login" card sign the user in
        findViewById<View>(R.id.btnLogIn).setOnClickListener { logIn() }
        findViewById<View>(R.id.recentLogin).setOnClickListener { logIn() }

        linkTo(this, R.id.btnCreateAccount, SignUpActivity::class.java)
    }

    private fun logIn() {
        val email = findViewById<EditText>(R.id.etEmail).text.toString()

        // Remember the last account that logged in (SharedPreferences, private to this app)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_RECENT_EMAIL, email)
        editor.commit()

        // Send the first name to Home so it can greet the user
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra(EXTRA_FIRST_NAME, "Jacob")
        startActivity(intent)

        // Log in is not part of the back stack once we are in
        finish()
    }
}
