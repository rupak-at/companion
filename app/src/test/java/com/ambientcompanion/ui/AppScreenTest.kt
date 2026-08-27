package com.ambientcompanion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScreenTest {
    @Test
    fun `home leaves system back available to close the app`() {
        assertNull(AppScreen.HOME.parent())
    }

    @Test
    fun `top level screens return home`() {
        listOf(AppScreen.CUSTOMIZE, AppScreen.SETTINGS, AppScreen.PREVIEW).forEach { screen ->
            assertEquals(AppScreen.HOME, screen.parent())
        }
    }

    @Test
    fun `settings detail screens return settings`() {
        listOf(
            AppScreen.SCREEN_AWARENESS,
            AppScreen.WELLBEING,
            AppScreen.PRIVACY,
            AppScreen.APP_PROFILES,
            AppScreen.DEBUG,
        ).forEach { screen ->
            assertEquals(AppScreen.SETTINGS, screen.parent())
        }
    }
}
