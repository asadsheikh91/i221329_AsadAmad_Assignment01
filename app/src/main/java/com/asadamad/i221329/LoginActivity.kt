package com.asadamad.i221329

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Screen 2: log in with the email and password of a Kinnect account (Firebase Authentication).
class LoginActivity : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_login)

        emailField = findViewById(R.id.etEmail)
        passwordField = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogIn)

        showRecentLogin()

        loginButton.setOnClickListener { logIn() }
        linkTo(this, R.id.btnCreateAccount, SignUpActivity::class.java)

        findViewById<View>(R.id.tvForgot).setOnClickListener { resetPassword() }
    }

    // "Recent login": the last account that signed in on this phone. Tapping it fills in the email.
    private fun showRecentLogin() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val email = prefs.getString(KEY_RECENT_EMAIL, null)
        val card = findViewById<View>(R.id.recentLogin)
        val label = findViewById<View>(R.id.tvRecentLabel)

        if (email.isNullOrEmpty()) {
            card.visibility = View.GONE
            label.visibility = View.GONE
            return
        }
        // The red "2" on the card in the design is a sample count of missed notifications
        findTextExact(card, "2")?.let { setVisible(it, false) }
        val name = prefs.getString(KEY_NAME, null) ?: email
        findViewById<TextView>(R.id.tvRecentName).text = name
        findViewById<TextView>(R.id.tvRecentInitials).text = initialsOf(name)
        card.setOnClickListener {
            emailField.setText(email)
            passwordField.requestFocus()
        }
    }

    private fun logIn() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter your email and password", Toast.LENGTH_SHORT).show()
            return
        }

        loginButton.isEnabled = false
        Backend.logIn(email, password) { error ->
            loginButton.isEnabled = true
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@logIn
            }

            // Remember the last account that logged in (SharedPreferences, private to this app)
            val me = Backend.me
            val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            editor.putString(KEY_RECENT_EMAIL, email)
            editor.putString(KEY_NAME, me?.name)
            editor.apply()

            // Send the first name to Home so it can greet the user
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra(EXTRA_FIRST_NAME, me?.name?.substringBefore(' '))
            startActivity(intent)

            // Log in is not part of the back stack once we are in
            finish()
        }
    }

    private fun resetPassword() {
        val email = emailField.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Type your email first", Toast.LENGTH_SHORT).show()
            return
        }
        Backend.sendPasswordReset(email) { error ->
            val message = error ?: "Check your inbox for a reset link"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
