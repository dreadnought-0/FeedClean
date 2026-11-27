package io.github.dreadnought0.feedclean.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dreadnought0.feedclean.ui.data.DataTransferAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackOpsTest {
    private fun stack(vararg keys: NavKey): MutableList<NavKey> = mutableListOf(*keys)

    @Test
    fun selectingATabPutsItAboveHome() {
        val s = stack(HomeKey)
        s.selectTopLevel(SettingsKey)
        assertEquals(listOf(HomeKey, SettingsKey), s)
        assertEquals(SettingsKey, s.currentTopLevel())
    }

    @Test
    fun switchingTabsDropsPushedScreens() {
        val s = stack(HomeKey, SettingsKey, DataTransferKey(DataTransferAction.IMPORT))
        s.selectTopLevel(AccountsKey)
        assertEquals(listOf(HomeKey, AccountsKey), s)
    }

    @Test
    fun reselectingTheCurrentTabPopsToItsRoot() {
        val s = stack(HomeKey, AccountsKey, AccountDetailKey(7))
        s.selectTopLevel(AccountsKey)
        assertEquals(listOf(HomeKey, AccountsKey), s)
    }

    @Test
    fun selectingHomeClearsEverythingAboveIt() {
        val s = stack(HomeKey, StatsKey)
        s.selectTopLevel(HomeKey)
        assertEquals(listOf(HomeKey), s)
        val t = stack(HomeKey, SetupKey, DebugKey)
        t.selectTopLevel(HomeKey)
        assertEquals(listOf(HomeKey), t)
    }

    @Test
    fun malformedStackIsRepaired() {
        val s = stack(StatsKey, DebugKey)
        s.selectTopLevel(SettingsKey)
        assertEquals(listOf(HomeKey, SettingsKey), s)
    }

    @Test
    fun currentTopLevelIgnoresPushedScreens() {
        assertEquals(HomeKey, stack(HomeKey, SetupKey, DebugKey).currentTopLevel())
        assertEquals(SettingsKey, stack(HomeKey, SettingsKey, DataTransferKey()).currentTopLevel())
    }

    @Test
    fun pushIgnoresDoubleTaps() {
        val s = stack(HomeKey)
        s.push(DebugKey)
        s.push(DebugKey)
        assertEquals(listOf(HomeKey, DebugKey), s)
    }

    @Test
    fun popNeverRemovesTheRoot() {
        val s = stack(HomeKey, AuditKey)
        assertTrue(s.pop())
        assertFalse(s.pop())
        assertEquals(listOf(HomeKey), s)
    }
}
