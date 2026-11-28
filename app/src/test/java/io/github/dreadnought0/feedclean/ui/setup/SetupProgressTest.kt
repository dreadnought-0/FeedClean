package io.github.dreadnought0.feedclean.ui.setup

import io.github.dreadnought0.feedclean.armed.HeroOutline
import io.github.dreadnought0.feedclean.ui.common.CardTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {

    private val allOn = SetupStatus(targetAppInstalled = true, serviceEnabled = true, batteryUnrestricted = true)

    private fun progress(
        status: SetupStatus = allOn,
        serviceConnected: Boolean = true,
    ) = setupProgress(status, serviceConnected)

    @Test
    fun `the required steps are TikTok, accessibility and battery, in checklist order`() {
        val p = progress()
        assertEquals(
            listOf(SetupStepId.TARGET_APP, SetupStepId.ACCESSIBILITY, SetupStepId.BATTERY),
            p.steps.map { it.id },
        )
        assertEquals(3, p.total)
        assertEquals(3, p.completed)
        assertTrue(p.allComplete)
    }

    @Test
    fun `completed count is dynamic over whichever steps are met`() {
        assertEquals(0, progress(SetupStatus.Unknown, serviceConnected = false).completed)
        assertEquals(1, progress(allOn.copy(serviceEnabled = false, batteryUnrestricted = false), serviceConnected = false).completed)
        assertEquals(2, progress(allOn.copy(batteryUnrestricted = false)).completed)
        assertEquals(3, progress().completed)
    }

    @Test
    fun `accessibility is done only when it is enabled and the service is actually running`() {
        assertFalse(progress(allOn, serviceConnected = false).done(SetupStepId.ACCESSIBILITY))
        assertFalse(progress(allOn.copy(serviceEnabled = false), serviceConnected = true).done(SetupStepId.ACCESSIBILITY))
        assertTrue(progress(allOn, serviceConnected = true).done(SetupStepId.ACCESSIBILITY))
    }

    @Test
    fun `hero starts collapsed and is teal only when every required step is complete`() {
        val ready = progress()
        assertTrue(heroStartsCollapsed(ready))
        assertEquals(HeroOutline.READY, heroOutline(ready))

        val noBattery = progress(allOn.copy(batteryUnrestricted = false))
        assertFalse(heroStartsCollapsed(noBattery))
        assertEquals(HeroOutline.SETUP_MISSING, heroOutline(noBattery))

        val noService = progress(allOn, serviceConnected = false)
        assertFalse(heroStartsCollapsed(noService))
        assertEquals(HeroOutline.SETUP_MISSING, heroOutline(noService))

        val noTikTok = progress(allOn.copy(targetAppInstalled = false))
        assertEquals(2, noTikTok.completed)
        assertTrue(noTikTok.accessibilityAndBatteryComplete)
        assertFalse(noTikTok.allComplete)
        assertFalse(heroStartsCollapsed(noTikTok))
        assertEquals(HeroOutline.SETUP_MISSING, heroOutline(noTikTok))
    }

    @Test
    fun `enabled-but-not-running is a missing accessibility step, not a complete one`() {
        val p = progress(allOn.copy(serviceEnabled = true), serviceConnected = false)
        assertFalse(p.done(SetupStepId.ACCESSIBILITY))
        assertTrue(p.done(SetupStepId.TARGET_APP))
        assertTrue(p.done(SetupStepId.BATTERY))
        assertEquals(2, p.completed)
        assertEquals(HeroOutline.SETUP_MISSING, heroOutline(p))
        assertFalse(heroStartsCollapsed(p))
    }

    @Test
    fun `each required step is red only when that step itself is not done`() {
        val bothMissing = progress(
            allOn.copy(serviceEnabled = false, batteryUnrestricted = false),
            serviceConnected = false,
        )
        assertEquals(CardTone.NORMAL, setupStepTone(bothMissing, SetupStepId.TARGET_APP))
        assertEquals(CardTone.DESTRUCTIVE, setupStepTone(bothMissing, SetupStepId.ACCESSIBILITY))
        assertEquals(CardTone.DESTRUCTIVE, setupStepTone(bothMissing, SetupStepId.BATTERY))

        val onlyBattery = progress(allOn.copy(batteryUnrestricted = false))
        assertEquals(CardTone.NORMAL, setupStepTone(onlyBattery, SetupStepId.TARGET_APP))
        assertEquals(CardTone.NORMAL, setupStepTone(onlyBattery, SetupStepId.ACCESSIBILITY))
        assertEquals(CardTone.DESTRUCTIVE, setupStepTone(onlyBattery, SetupStepId.BATTERY))

        val onlyAccess = progress(allOn, serviceConnected = false)
        assertEquals(CardTone.NORMAL, setupStepTone(onlyAccess, SetupStepId.TARGET_APP))
        assertEquals(CardTone.DESTRUCTIVE, setupStepTone(onlyAccess, SetupStepId.ACCESSIBILITY))
        assertEquals(CardTone.NORMAL, setupStepTone(onlyAccess, SetupStepId.BATTERY))

        val onlyTikTok = progress(allOn.copy(targetAppInstalled = false))
        assertEquals(CardTone.DESTRUCTIVE, setupStepTone(onlyTikTok, SetupStepId.TARGET_APP))
        assertEquals(CardTone.NORMAL, setupStepTone(onlyTikTok, SetupStepId.ACCESSIBILITY))
        assertEquals(CardTone.NORMAL, setupStepTone(onlyTikTok, SetupStepId.BATTERY))

        val ready = progress()
        SetupStepId.entries.forEach { id ->
            assertEquals(CardTone.NORMAL, setupStepTone(ready, id))
        }
    }
}
