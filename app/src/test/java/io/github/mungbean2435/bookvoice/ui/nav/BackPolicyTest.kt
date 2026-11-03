package io.github.mungbean2435.bookvoice.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class BackPolicyTest {

    private fun resolve(
        editorOpen: Boolean = false,
        chaptersOpen: Boolean = false,
        playerOverlayOpen: Boolean = false,
        onSettingsTab: Boolean = false,
        settingsPageOpen: Boolean = false,
        onBookmarksTab: Boolean = false
    ) = BackPolicy.resolve(
        editorOpen = editorOpen,
        chaptersOpen = chaptersOpen,
        playerOverlayOpen = playerOverlayOpen,
        onSettingsTab = onSettingsTab,
        settingsPageOpen = settingsPageOpen,
        onBookmarksTab = onBookmarksTab
    )

    @Test
    fun `editor owns back even when settings page is open`() {
        assertEquals(
            BackAction.None,
            resolve(editorOpen = true, onSettingsTab = true, settingsPageOpen = true)
        )
    }

    @Test
    fun `player chapters overlay closes before the overlay itself`() {
        assertEquals(
            BackAction.CloseChapters,
            resolve(chaptersOpen = true, playerOverlayOpen = true)
        )
    }

    @Test
    fun `player overlay collapses back to the tab underneath`() {
        assertEquals(BackAction.CollapsePlayer, resolve(playerOverlayOpen = true))
    }

    @Test
    fun `settings subpage returns to settings home`() {
        assertEquals(
            BackAction.SettingsToHome,
            resolve(onSettingsTab = true, settingsPageOpen = true)
        )
    }

    @Test
    fun `settings home switches to shelf instead of exiting`() {
        assertEquals(
            BackAction.SettingsToShelf,
            resolve(onSettingsTab = true, settingsPageOpen = false)
        )
    }

    @Test
    fun `bookmarks tab switches to shelf instead of exiting`() {
        assertEquals(BackAction.SettingsToShelf, resolve(onBookmarksTab = true))
    }

    @Test
    fun `settings subpage wins over bookmarks flag`() {
        assertEquals(
            BackAction.SettingsToHome,
            resolve(onSettingsTab = true, settingsPageOpen = true, onBookmarksTab = true)
        )
    }

    @Test
    fun `shelf tab falls through to system default`() {
        assertEquals(BackAction.None, resolve())
    }

    @Test
    fun `settings page flag on another tab does not hijack back`() {
        assertEquals(BackAction.None, resolve(settingsPageOpen = true))
    }
}
