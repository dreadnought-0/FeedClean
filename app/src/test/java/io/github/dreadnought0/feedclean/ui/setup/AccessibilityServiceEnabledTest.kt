package io.github.dreadnought0.feedclean.ui.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceEnabledTest {
    private val pkg = "io.github.dreadnought0.feedclean"
    private val cls = "io.github.dreadnought0.feedclean.service.FeedCleanAccessibilityService"

    private fun enabled(setting: String?) = isAccessibilityServiceEnabled(setting, pkg, cls)

    @Test
    fun nullOrEmptyMeansDisabled() {
        assertFalse(enabled(null))
        assertFalse(enabled(""))
    }

    @Test
    fun matchesFullAndShortForms() {
        assertTrue(enabled("$pkg/$cls"))
        assertTrue(enabled("$pkg/.service.FeedCleanAccessibilityService"))
    }

    @Test
    fun matchesAmongOtherServices() {
        assertTrue(enabled("com.example.reader/.ReaderService:$pkg/.service.FeedCleanAccessibilityService"))
    }

    @Test
    fun otherServicesDoNotMatch() {
        assertFalse(enabled("com.example.reader/.ReaderService"))
        assertFalse(enabled("$pkg/.service.OtherService"))
        assertFalse(enabled("com.example/$cls"))
        assertFalse(enabled("malformed-entry"))
    }
}
