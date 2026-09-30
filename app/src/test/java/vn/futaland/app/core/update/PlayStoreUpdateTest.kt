package vn.futaland.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update dialog is purely store-driven: it may appear only when the
 * Play build is newer than the installed one, and "Để sau" snoozes exactly
 * that Play versionCode for [PlayStoreUpdateManager.SNOOZE_MS] — never forever.
 */
class PlayStoreUpdateTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `newer play build prompts`() {
        assertTrue(PlayStoreUpdateManager.shouldPrompt(32, 31, 0))
    }

    @Test
    fun `same or older play build never prompts`() {
        assertFalse(PlayStoreUpdateManager.shouldPrompt(31, 31, 0))
        assertFalse(PlayStoreUpdateManager.shouldPrompt(30, 31, 0))
    }

    @Test
    fun `snooze keeps the same build quiet only inside the window`() {
        val justNow = now - 60_000L
        val overADayAgo = now - (PlayStoreUpdateManager.SNOOZE_MS + 60_000L)
        assertFalse(PlayStoreUpdateManager.shouldPrompt(32, 31, 32, justNow, now))
        assertTrue(PlayStoreUpdateManager.shouldPrompt(32, 31, 32, overADayAgo, now))
    }

    @Test
    fun `a newer play build is never covered by an older snooze`() {
        assertTrue(PlayStoreUpdateManager.shouldPrompt(33, 31, 32, now - 60_000L, now))
    }

    @Test
    fun `legacy or skewed snooze never mutes forever`() {
        // Written by an older build: versionCode stored without any timestamp.
        assertTrue(PlayStoreUpdateManager.shouldPrompt(32, 31, 32, 0L, now))
        // Clock moved backwards: timestamp lies in the future.
        assertTrue(PlayStoreUpdateManager.shouldPrompt(32, 31, 32, now + 3_600_000L, now))
    }

    @Test
    fun `invalid version codes never prompt`() {
        assertFalse(PlayStoreUpdateManager.shouldPrompt(0, 31, 0))
        assertFalse(PlayStoreUpdateManager.shouldPrompt(32, 0, 0))
    }
}
