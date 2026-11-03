package io.github.mungbean2435.bookvoice.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun missingOrUnknownValueFallsBackToSystem() {
        assertEquals(ThemeMode.FollowSystem, ThemeMode.from(null))
        assertEquals(ThemeMode.FollowSystem, ThemeMode.from(""))
        assertEquals(ThemeMode.FollowSystem, ThemeMode.from("bogus"))
    }

    @Test
    fun parsesStoredValues() {
        assertEquals(ThemeMode.FollowSystem, ThemeMode.from("system"))
        assertEquals(ThemeMode.Light, ThemeMode.from("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.from("dark"))
    }

    @Test
    fun prefValuesAreStableForBackup() {
        assertEquals("system", ThemeMode.FollowSystem.prefValue)
        assertEquals("light", ThemeMode.Light.prefValue)
        assertEquals("dark", ThemeMode.Dark.prefValue)
    }
}
