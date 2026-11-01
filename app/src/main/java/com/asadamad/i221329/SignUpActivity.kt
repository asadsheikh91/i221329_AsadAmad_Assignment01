package com.asadamad.i221329

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Screen 3: create account. Makes a Firebase account and its profile, then opens Home.
class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_create_account)

        // Back arrow and the "Log in" link both return to Log in
        linkBack(this, R.id.btnBack)
        linkBack(this, R.id.tvLogIn)

        val button = findViewById<Button>(R.id.btnCreateAccount)
        button.setOnClickListener {
            val first = findViewById<EditText>(R.id.etFirstName).text.toString().trim()
            val last = findViewById<EditText>(R.id.etLastName).text.toString().trim()
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val password = findViewById<EditText>(R.id.etPassword).text.toString()

            if (first.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Enter your name and email", Toast.LENGTH_SHORT).show()
            } else if (password.length < 6) {
                Toast.makeText(this, "The password needs at least 6 characters", Toast.LENGTH_SHORT).show()
            } else {
                button.isEnabled = false
                Backend.signUp(first, last, email, password) { error ->
                    button.isEnabled = true
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    } else {
                        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        editor.putString(KEY_RECENT_EMAIL, email)
                        editor.putString(KEY_NAME, Backend.me?.name)
                        editor.apply()

                        // Home becomes the only screen, so Back from Home leaves the app
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.putExtra(EXTRA_FIRST_NAME, first)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    }
                }
            }
        }
    }
}
