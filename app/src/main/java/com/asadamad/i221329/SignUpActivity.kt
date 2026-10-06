package com.asadamad.i221329

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

// Screen 3: create account (step 2 of 3).
class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_create_account)

        // Back arrow and the "Log in" link both return to Log in
        linkBack(this, R.id.btnBack)
        linkBack(this, R.id.tvLogIn)

        // Creating the account opens Home. SignUp closes itself so Back from Home returns to Log in.
        findViewById<View>(R.id.btnCreateAccount).setOnClickListener {
            openScreen(this, HomeActivity::class.java)
            finish()
        }
    }
}
