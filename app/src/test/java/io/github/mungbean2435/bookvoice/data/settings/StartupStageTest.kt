package io.github.mungbean2435.bookvoice.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 启动顺序不能由 UI 猜：两个布尔值任一未读到都是 Loading；
 * 读到之后固定先免责、再引导、最后主界面。
 */
class StartupStageTest {

    @Test
    fun nullStateIsLoading() {
        assertEquals(StartupStage.Loading, startupStage(null))
    }

    @Test
    fun freshInstallStartsAtDisclaimer() {
        assertEquals(
            StartupStage.Disclaimer,
            startupStage(
                StartupState(
                    disclaimerAccepted = false,
                    onboardingCompleted = false
                )
            )
        )
    }

    @Test
    fun acceptedDisclaimerGoesToOnboarding() {
        assertEquals(
            StartupStage.Onboarding,
            startupStage(
                StartupState(
                    disclaimerAccepted = true,
                    onboardingCompleted = false
                )
            )
        )
    }

    @Test
    fun completedOnboardingGoesToMain() {
        assertEquals(
            StartupStage.Main,
            startupStage(
                StartupState(
                    disclaimerAccepted = true,
                    onboardingCompleted = true
                )
            )
        )
    }

    @Test
    fun disclaimerWinsWhenBothFlagsAreInconsistent() {
        assertEquals(
            StartupStage.Disclaimer,
            startupStage(
                StartupState(
                    disclaimerAccepted = false,
                    onboardingCompleted = true
                )
            )
        )
    }

    @Test
    fun languageFallsBackToChinese() {
        assertEquals(AppLanguage.Zh, AppLanguage.from(null))
        assertEquals(AppLanguage.Zh, AppLanguage.from(""))
        assertEquals(AppLanguage.En, AppLanguage.from(AppLanguage.En.name))
    }
}
