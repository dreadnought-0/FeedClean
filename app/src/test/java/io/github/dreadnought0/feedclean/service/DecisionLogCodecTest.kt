package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.FeedContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionLogCodecTest {

    private val page = DecisionLogEntry(
        atMillis = 1_764_100_000_000,
        event = DecisionEvent.PAGE,
        context = FeedContext.SEARCH,
        kinds = listOf(DetectionRules.Kind.PAID_AD, DetectionRules.Kind.SHOP),
        decision = "skip:PAID_AD",
        gate = "hold:CONTEXT_OFF",
        logged = true,
    )

    @Test
    fun pageLineFormat() {
        assertEquals(
            """{"t":1764100000000,"ev":"page","ctx":"SEARCH","kinds":["PAID_AD","SHOP"],"dec":"skip:PAID_AD",""" +
                """"gate":"hold:CONTEXT_OFF","logged":true}""",
            DecisionLogCodec.encode(page),
        )
    }

    @Test
    fun watchAndCardLinesCarryOnlyTheirFields() {
        val watch = DecisionLogCodec.encode(
            DecisionLogEntry(
                atMillis = 5, event = DecisionEvent.WATCH, context = FeedContext.FOR_YOU,
                kinds = listOf(DetectionRules.Kind.SHOP), newKinds = listOf(DetectionRules.Kind.SHOP),
                decision = "skip:ORGANIC_SHOP", gate = "go", outcome = "skipped", logged = true,
                sinceFirstReadMillis = 800, trigger = WatchTrigger.SIGNALS_GREW,
            ),
        )
        assertEquals(
            setOf("t", "ev", "ctx", "kinds", "new", "dec", "gate", "out", "logged", "dt", "why"),
            Json.parseToJsonElement(watch).jsonObject.keys,
        )
        val card = DecisionLogCodec.encode(
            DecisionLogEntry(
                atMillis = 5, event = DecisionEvent.CARD, action = DecisionCardAction.BLOCK,
                result = "Aborted", step = "CHECK_DIALOG", reason = "DIALOG_HANDLE_MISMATCH",
            ),
        )
        assertEquals(
            """{"t":5,"ev":"card","act":"BLOCK","res":"Aborted","step":"CHECK_DIALOG","reason":"DIALOG_HANDLE_MISMATCH"}""",
            card,
        )
    }

    @Test
    fun roundTrips() {
        assertEquals(page, DecisionLogCodec.decode(DecisionLogCodec.encode(page)))
    }

    @Test
    fun damagedOrForeignLinesAreSkippedNotFatal() {
        assertNull(DecisionLogCodec.decode(""))
        assertNull(DecisionLogCodec.decode("{\"t\":1,\"ev\":\"page\""))
        assertNull(DecisionLogCodec.decode("{\"t\":1,\"ev\":\"no-such-event\"}"))
        assertNull(DecisionLogCodec.decode("not json"))
        // A newer version's extra key is ignored.
        assertEquals(1L, DecisionLogCodec.decode("{\"t\":1,\"ev\":\"page\",\"future\":3}")?.atMillis)
    }

    @Test
    fun labelFieldsOnlyEverHoldEnumNameTokens() {
        val leaky = page.copy(
            decision = "Creator Name",
            gate = "hold:caption with spaces #fyp",
            outcome = "@some.handle",
            notInterested = "Product · 1k sold",
            result = "💥",
            step = "x".repeat(65),
            reason = "ok_TOKEN:2",
        )
        val line = DecisionLogCodec.encode(leaky)
        listOf("Creator Name", "caption", "#fyp", "@some.handle", "Product", "1k sold", "💥", "x".repeat(65)).forEach {
            assertFalse("leaked '$it' in $line", line.contains(it))
        }
        val decoded = DecisionLogCodec.decode(line)!!
        assertEquals(DecisionLogCodec.INVALID, decoded.decision)
        assertEquals(DecisionLogCodec.INVALID, decoded.gate)
        assertEquals(DecisionLogCodec.INVALID, decoded.outcome)
        assertEquals(DecisionLogCodec.INVALID, decoded.notInterested)
        assertEquals(DecisionLogCodec.INVALID, decoded.result)
        assertEquals(DecisionLogCodec.INVALID, decoded.step)
        assertEquals("ok_TOKEN:2", decoded.reason)
    }

    @Test
    fun theSchemaHasNoFieldForScreenText() {
        // Every key a line can ever have. Adding a field means reviewing it against the privacy contract.
        val all = DecisionLogEntry.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName) }
        assertEquals(
            listOf("t", "ev", "ctx", "kinds", "new", "dec", "gate", "out", "ni", "logged", "dt", "why", "act", "res", "step", "reason"),
            all,
        )
        assertTrue(all.none { it.contains("name", true) || it.contains("handle", true) || it.contains("caption", true) })
    }
}
