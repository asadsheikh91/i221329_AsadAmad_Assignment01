package com.kinnect.i221329_i221069

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Screen 19: menu tab. Opens your profile, the marketplace and friends, shows help and settings, and logs out.
class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_menu)

        setupTabs(this, TAB_MENU)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)

        val root = findViewById<View>(R.id.root)
        Backend.me?.let {
            findViewById<TextView>(R.id.tvMenuName).text = it.name
            findTextExact(root, "JW")?.text = it.initials
        }

        linkTo(this, R.id.menuProfile, ProfileActivity::class.java)
        linkTo(this, R.id.menuMarketplace, MarketplaceActivity::class.java)
        linkTo(this, R.id.menuFriends, FriendsActivity::class.java)

        // Rows without an id are found by their label (the label's parent is the whole tappable row)
        rowByLabel(root, "Help & support") { showHelp() }
        rowByLabel(root, "Settings & privacy") { showSettings() }
        for (label in listOf("Memories", "Saved", "Groups", "Events", "Also from SMD")) {
            rowByLabel(root, label) { comingSoon(label) }
        }
        findTextExact(root, "See more")?.setOnClickListener { comingSoon("More shortcuts") }
        allViews(root).firstOrNull { it.contentDescription == "Theme" }?.setOnClickListener { comingSoon("Dark mode") }

        // Log out signs out of Firebase and empties the back stack, so Back from Log in leaves the app
        findViewById<View>(R.id.btnLogOut).setOnClickListener {
            logOut(this)
        }
    }

    private fun rowByLabel(root: View, label: String, action: () -> Unit) {
        (findTextExact(root, label)?.parent as? View)?.setOnClickListener { action() }
    }

    private fun comingSoon(what: String) {
        Toast.makeText(this, "$what is not available yet", Toast.LENGTH_SHORT).show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Help & support")
            .setMessage(
                "Friends: search for someone and tap Add friend. They get a notification and can confirm.\n\n" +
                    "Stories: share a photo from Home. Friends see it for 24 hours.\n\n" +
                    "Calls: open a chat and tap the phone or camera icon. Calls ring while Kinnect is open.\n\n" +
                    "Marketplace: tap Sell to list something and message the seller from the listing."
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSettings() {
        val email = Backend.me?.email ?: ""
        AlertDialog.Builder(this)
            .setTitle("Settings & privacy")
            .setMessage("Signed in as $email")
            .setPositiveButton("Close", null)
            .setNeutralButton("Change password") { _, _ ->
                Backend.sendPasswordReset(email) { error ->
                    Toast.makeText(this, error ?: "Check your inbox for a link to choose a new password", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Log out") { _, _ -> logOut(this) }
            .show()
    }
}
