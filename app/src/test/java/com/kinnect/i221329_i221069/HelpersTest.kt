package com.kinnect.i221329_i221069

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Tests of the plain logic that needs no phone and no Firebase.
class HelpersTest {

    private fun story(author: String, audience: String = AUDIENCE_FRIENDS, close: List<String> = emptyList()) =
        Story("s1", author, "Name", "", "", audience, close, emptyList(), emptyList(), 0L)

    @Test
    fun initialsUseTheFirstTwoWords() {
        assertEquals("AK", initialsOf("Aisha Khan"))
        assertEquals("OB", initialsOf("  omar   bin  farooq "))
        assertEquals("S", initialsOf("sara"))
        assertEquals("K", initialsOf("   "))
    }

    @Test
    fun twoPeopleShareOneDocumentId() {
        assertEquals("alice_bob", pairId("alice", "bob"))
        assertEquals("alice_bob", pairId("bob", "alice"))
    }

    @Test
    fun pricesHaveThousandsSeparators() {
        assertEquals("Rs 45,000", formatPrice(45000))
        assertEquals("Rs 800", formatPrice(800))
        assertEquals("Rs 1,250,000", formatPrice(1250000))
    }

    @Test
    fun timeAgoIsEmptyWithoutATime() {
        assertEquals("", timeAgo(0))
        assertEquals("now", timeAgo(System.currentTimeMillis()))
        assertEquals("5m", timeAgo(System.currentTimeMillis() - 5 * 60_000 - 500))
        assertEquals("3h", timeAgo(System.currentTimeMillis() - 3 * 3_600_000L - 500))
    }

    @Test
    fun joinedTextNeedsADate() {
        assertEquals("", joinedText(0))
        assertTrue(joinedText(1_700_000_000_000L).startsWith("Joined "))
    }

    @Test
    fun yourOwnStoryIsAlwaysVisible() {
        assertTrue(canSeeStory(story("me", AUDIENCE_CLOSE), "me", emptySet()))
    }

    @Test
    fun aFriendsStoryIsVisibleToFriendsOnly() {
        assertTrue(canSeeStory(story("bob"), "me", setOf("bob")))
        assertFalse(canSeeStory(story("bob"), "me", setOf("cara")))
    }

    @Test
    fun aCloseFriendsStoryIsVisibleToThoseOnTheList() {
        val story = story("bob", AUDIENCE_CLOSE, listOf("me"))
        assertTrue(canSeeStory(story, "me", setOf("bob")))
        assertFalse(canSeeStory(story, "cara", setOf("bob")))
    }

    @Test
    fun usernamesAreLettersNumbersAndUnderscoresOnly() {
        assertTrue(isValidUsername("asad_amad"))
        assertTrue(isValidUsername("Asad_99"))          // capitals are lowered before they are saved
        assertFalse(isValidUsername("ab"))              // too short
        assertFalse(isValidUsername("a".repeat(21)))    // too long
        assertFalse(isValidUsername("asad.amad"))       // a dot cannot be a key in the Realtime Database
        assertFalse(isValidUsername("asad amad"))
        assertFalse(isValidUsername("asad#1"))
    }

    @Test
    fun usernameSuggestionComesFromTheName() {
        assertEquals("aisha_khan", suggestUsername("Aisha Khan"))
        assertEquals("omar_bin_farooq", suggestUsername("  Omar   bin Farooq "))
        assertEquals("sara", suggestUsername("Sara!"))
    }

    @Test
    fun passwordStrengthGrowsWithBetterPasswords() {
        assertEquals(0, passwordStrength(""))
        assertEquals(1, passwordStrength("abcdef"))
        assertEquals(2, passwordStrength("abcdef12"))
        assertTrue(passwordStrength("Abcdef12!x") > passwordStrength("abcdef12"))
        assertEquals(4, passwordStrength("Abcdef12!xyz"))
    }

    @Test
    fun storyLabelsFollowTheAudience() {
        assertEquals("Friends", audienceLabel(story("me")))
        assertEquals("Close friends", audienceLabel(story("me", AUDIENCE_CLOSE)))
    }
}
