package com.asadamad.i221329

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoActivityResumedException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Two user flows that go through several screens.
// Both start on Log in so the splash timer does not get in the way.
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    // Flow 1: Log in -> Home -> Comments -> Back -> Home
    @Test
    fun comments_backReturnsToHome() {
        // Log in lands on Home
        onView(withId(R.id.btnLogIn)).perform(click())
        onView(withId(R.id.tvComposerHint)).check(matches(isDisplayed()))

        // The Comment button opens the Comments screen
        onView(withId(R.id.btnComment)).perform(scrollTo(), click())
        onView(withText("Comments")).check(matches(isDisplayed()))

        // Back returns to Home with the composer visible again
        pressBack()
        onView(withId(R.id.tvComposerHint)).check(matches(isDisplayed()))
    }

    // Flow 2: Log in -> Menu tab -> Log out -> Log in, with nothing left in the back stack
    @Test
    fun logOut_clearsBackStack() {
        onView(withId(R.id.btnLogIn)).perform(click())

        // Open the Menu tab and tap Log out
        onView(withId(R.id.tabMenu)).perform(click())
        onView(withId(R.id.btnLogOut)).perform(scrollTo(), click())

        // We are on Log in again
        onView(withId(R.id.btnLogIn)).check(matches(isDisplayed()))

        // Back from here must leave the app: no earlier screen is left to return to
        try {
            pressBack()
            fail("Back should have left the app, but another screen was still on the stack")
        } catch (expected: NoActivityResumedException) {
            // This is the result we want
        }
    }
}
