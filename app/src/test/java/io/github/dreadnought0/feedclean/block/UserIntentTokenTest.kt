package io.github.dreadnought0.feedclean.block

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIntentTokenTest {

    @Test
    fun freshForTenSecondsAfterTheClick() {
        val token = UserIntentToken.fromUserClick("test.block", nowMillis = 1_000)

        assertTrue(token.isFresh(1_000))
        assertTrue(token.isFresh(11_000))
        assertFalse(token.isFresh(11_001))
    }

    @Test
    fun tokenFromTheFutureIsNotFresh() {
        val token = UserIntentToken.fromUserClick("test.block", nowMillis = 5_000)

        assertFalse(token.isFresh(4_999))
    }

    @Test
    fun customMaxAge() {
        val token = UserIntentToken.fromUserClick("test.block", nowMillis = 0)

        assertTrue(token.isFresh(2_000, maxAgeMillis = 2_000))
        assertFalse(token.isFresh(2_001, maxAgeMillis = 2_000))
    }

    @Test
    fun blankSourceIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { UserIntentToken.fromUserClick(" ", 0) }
    }

    @Test
    fun onlyFactoryCanCreateTokens() {
        val constructors = UserIntentToken::class.java.declaredConstructors.filterNot { it.isSynthetic }

        assertTrue(constructors.isNotEmpty())
        assertTrue(constructors.all { Modifier.isPrivate(it.modifiers) })
    }

    @Test
    fun tokenIsSingleUse() {
        val token = UserIntentToken.fromUserClick("test.block", nowMillis = 0)

        assertNull(acceptUserIntent(token, nowMillis = 100, maxAgeMillis = 10_000))
        assertTrue(token.isConsumed)
        assertEquals(RejectReason.TOKEN_ALREADY_USED, acceptUserIntent(token, nowMillis = 200, maxAgeMillis = 10_000))
    }

    @Test
    fun missingAndStaleTokensAreRejected() {
        val token = UserIntentToken.fromUserClick("test.block", nowMillis = 0)

        assertEquals(RejectReason.MISSING_TOKEN, acceptUserIntent(null, nowMillis = 0, maxAgeMillis = 10_000))
        assertEquals(RejectReason.STALE_TOKEN, acceptUserIntent(token, nowMillis = 10_001, maxAgeMillis = 10_000))
        assertFalse("a stale token is not consumed", token.isConsumed)
    }
}
