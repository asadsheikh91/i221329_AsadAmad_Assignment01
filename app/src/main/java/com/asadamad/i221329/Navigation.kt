package com.asadamad.i221329

import android.app.Activity
import android.content.Intent
import android.view.View

// Small helpers shared by every screen so each Activity stays short.

// Name of the SharedPreferences file and the keys stored in it
const val PREFS_NAME = "kinnect_prefs"
const val KEY_RECENT_EMAIL = "recent_email"
const val KEY_NAME = "profile_name"
const val KEY_BIO = "profile_bio"

// Keys of the extras passed between screens
const val EXTRA_FIRST_NAME = "first_name"
const val EXTRA_NAME = "name"
const val EXTRA_BIO = "bio"

// Positions of the five main tabs (Home, Friends, Marketplace, Notifications, Menu)
const val TAB_HOME = 0
const val TAB_FRIENDS = 1
const val TAB_MARKETPLACE = 2
const val TAB_NOTIFICATIONS = 3
const val TAB_MENU = 4
const val NO_TAB = -1

/** Opens [target] on top of the current screen (explicit intent). */
fun openScreen(from: Activity, target: Class<*>) {
    from.startActivity(Intent(from, target))
}

/** Opens [target] when the view with [viewId] is tapped. */
fun linkTo(from: Activity, viewId: Int, target: Class<*>) {
    from.findViewById<View>(viewId).setOnClickListener {
        openScreen(from, target)
    }
}

/** Closes the screen when the view with [viewId] is tapped (back arrows, Cancel, close buttons). */
fun linkBack(activity: Activity, viewId: Int) {
    activity.findViewById<View>(viewId).setOnClickListener {
        activity.finish()
    }
}

/**
 * Wires the five-tab bar.
 * Home always sits at the bottom of the stack, so:
 *  - tapping Home just closes the current tab screen,
 *  - tapping any other tab opens it and, if we are not on Home, closes the tab we came from.
 * That way Back from any tab always lands on the previous screen without piling up tabs.
 */
fun setupTabs(activity: Activity, current: Int) {
    bindTab(activity, R.id.tabHome, current, TAB_HOME, HomeActivity::class.java)
    bindTab(activity, R.id.tabFriends, current, TAB_FRIENDS, FriendsActivity::class.java)
    bindTab(activity, R.id.tabMarketplace, current, TAB_MARKETPLACE, MarketplaceActivity::class.java)
    bindTab(activity, R.id.tabNotifications, current, TAB_NOTIFICATIONS, NotificationsActivity::class.java)
    bindTab(activity, R.id.tabMenu, current, TAB_MENU, MenuActivity::class.java)
}

private fun bindTab(activity: Activity, viewId: Int, current: Int, tab: Int, target: Class<*>) {
    activity.findViewById<View>(viewId).setOnClickListener {
        if (tab != current) {
            if (tab == TAB_HOME) {
                // Home is already underneath us
                activity.finish()
            } else {
                openScreen(activity, target)
                if (current != TAB_HOME) {
                    activity.finish()
                }
            }
        }
    }
}

/** Goes back to Log in and throws away every screen that was open before. */
fun logOut(activity: Activity) {
    val intent = Intent(activity, LoginActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.startActivity(intent)
}
