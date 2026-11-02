package com.kinnect.i221329_i221069

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Two Espresso tests of workflows that really talk to Firebase (Authentication and the Realtime Database).
//
// They need an account that already exists in your Firebase project and has finished the profile setup.
// Pass it to the test run (nothing is stored in the code):
//
//   ./gradlew connectedAndroidTest ^
//     -Pandroid.testInstrumentationRunnerArguments.testEmail=you@example.com ^
//     -Pandroid.testInstrumentationRunnerArguments.testPassword=secret1 ^
//     -Pandroid.testInstrumentationRunnerArguments.testFirstName=Asad
//
// In Android Studio: Run > Edit Configurations > the test > Instrumentation arguments (the same three names).
// Without them the tests are skipped instead of failing.
@RunWith(AndroidJUnit4::class)
class BackendFlowTest {

    // Both tests start on Log in so the 5 second splash does not get in the way
    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    private lateinit var email: String
    private lateinit var password: String
    private lateinit var firstName: String

    @Before
    fun readAccount() {
        val arguments = InstrumentationRegistry.getArguments()
        email = arguments.getString("testEmail") ?: ""
        password = arguments.getString("testPassword") ?: ""
        firstName = arguments.getString("testFirstName") ?: ""
        assumeTrue("Pass testEmail, testPassword and testFirstName (see the comment above this class)", email.isNotEmpty() && password.isNotEmpty())
    }

    /** Espresso only looks once, but the database answers a moment later: look again until [check] passes. */
    private fun waitFor(timeoutMs: Long = 20_000, check: () -> Unit) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                check()
                return
            } catch (e: Throwable) {
                if (System.currentTimeMillis() > end) {
                    throw e
                }
                Thread.sleep(300)
            }
        }
    }

    private fun logIn() {
        onView(withId(R.id.etEmail)).perform(replaceText(email))
        onView(withId(R.id.etPassword)).perform(replaceText(password))
        closeSoftKeyboard()
        onView(withId(R.id.btnLogIn)).perform(click())
    }

    // Workflow 1: Log in with Firebase Authentication -> Home greets the user with the name from the database
    // -> the Menu shows the same profile. This goes through Auth, a database read and two screens.
    @Test
    fun logIn_showsTheRealProfile() {
        logIn()

        // Home: the composer says "What's on your mind, <first name>?" once the profile has been read
        if (firstName.isNotEmpty()) {
            waitFor { onView(withText("What's on your mind, $firstName?")).check(matches(isDisplayed())) }
        } else {
            waitFor { onView(withId(R.id.tvComposerHint)).check(matches(isDisplayed())) }
        }

        // Menu tab: the profile row shows the user's name, read from the same node
        onView(withId(R.id.tabMenu)).perform(click())
        waitFor {
            onView(withId(R.id.tvMenuName)).check(matches(isDisplayed()))
            if (firstName.isNotEmpty()) {
                onView(withText(org.hamcrest.Matchers.containsString(firstName))).check(matches(isDisplayed()))
            }
        }
    }

    // Workflow 2: Log in -> Create post -> write a text -> Post -> the new post is read back from the database
    // and appears at the top of the Home feed.
    @Test
    fun createPost_appearsInTheFeed() {
        logIn()
        waitFor { onView(withId(R.id.tvComposerHint)).check(matches(isDisplayed())) }

        // A text nobody else wrote, so the test finds exactly its own post
        val text = "Espresso post " + System.currentTimeMillis()

        onView(withId(R.id.tvComposerHint)).perform(click())
        onView(withId(R.id.etPostText)).perform(replaceText(text))
        closeSoftKeyboard()
        onView(withId(R.id.btnPost)).perform(click())

        // Back on Home the post comes back through the live listener
        waitFor { onView(withText(text)).perform(scrollTo()).check(matches(isDisplayed())) }
    }
}
