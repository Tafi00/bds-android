package vn.futaland.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update dialog is purely store-driven: it may appear only when the
 * Play build is newer than the installed one, and "Để sau" snoozes exactly
 * that Play versionCode.
 */
class PlayStoreUpdateTest {

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
    fun `snoozed version stays silent until play ships newer`() {
        assertFalse(PlayStoreUpdateManager.shouldPrompt(32, 31, 32))
        assertTrue(PlayStoreUpdateManager.shouldPrompt(33, 31, 32))
    }

    @Test
    fun `invalid version codes never prompt`() {
        assertFalse(PlayStoreUpdateManager.shouldPrompt(0, 31, 0))
        assertFalse(PlayStoreUpdateManager.shouldPrompt(32, 0, 0))
    }
}
