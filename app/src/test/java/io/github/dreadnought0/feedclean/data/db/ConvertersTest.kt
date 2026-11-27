package io.github.dreadnought0.feedclean.data.db

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    private val fullSignals = Signals(
        isPaidAd = true,
        isPromoted = true,
        hasShopAnchor = true,
        isShopCreator = true,
        isLive = true,
        isLiveShopping = true,
        isLiveEvent = true,
        disclosureLabel = "Ad",
        anchorText = "Free shipping · 1.2K sold \"quoted\"",
        ctaText = "Shop now",
    )

    @Test
    fun signalsRoundTripEveryField() {
        assertEquals(fullSignals, converters.jsonToSignals(converters.signalsToJson(fullSignals)))
        assertEquals(Signals(), converters.jsonToSignals(converters.signalsToJson(Signals())))
    }

    @Test
    fun signalsJsonEncodesDefaultsButNotDerivedProperties() {
        val json = converters.signalsToJson(Signals())
        assertTrue(json, json.contains("\"isPaidAd\":false"))
        assertTrue(json, json.contains("\"disclosureLabel\":null"))
        assertFalse(json, json.contains("isOrganicShop"))
        assertFalse(json, json.contains("hasAnySignal"))
    }

    @Test
    fun signalsJsonIgnoresUnknownFields() {
        val json = """{"isPaidAd":true,"someFutureField":42,"nested":{"a":[1,2]},"disclosureLabel":"Ad"}"""
        assertEquals(Signals(isPaidAd = true, disclosureLabel = "Ad"), converters.jsonToSignals(json))
    }

    @Test
    fun signalsJsonFillsMissingFieldsWithDefaults() {
        assertEquals(Signals(hasShopAnchor = true), converters.jsonToSignals("""{"hasShopAnchor":true}"""))
        assertEquals(Signals(), converters.jsonToSignals("{}"))
    }

    @Test
    fun malformedSignalsJsonYieldsEmptySignals() {
        assertEquals(Signals(), converters.jsonToSignals("not json"))
        assertEquals(Signals(), converters.jsonToSignals("[]"))
        assertEquals(Signals(), converters.jsonToSignals(""))
    }

    @Test
    fun enumsAreStoredByName() {
        AccountStatus.entries.forEach {
            assertEquals(it.name, converters.accountStatusToName(it))
            assertEquals(it, converters.nameToAccountStatus(it.name))
        }
        HandleSource.entries.forEach {
            assertEquals(it.name, converters.handleSourceToName(it))
            assertEquals(it, converters.nameToHandleSource(it.name))
        }
        SightingSource.entries.forEach {
            assertEquals(it.name, converters.sightingSourceToName(it))
            assertEquals(it, converters.nameToSightingSource(it.name))
        }
        UserAction.entries.forEach {
            assertEquals(it.name, converters.userActionToName(it))
            assertEquals(it, converters.nameToUserAction(it.name))
        }
        SkipReason.entries.forEach {
            assertEquals(it.name, converters.skipReasonToName(it))
            assertEquals(it, converters.nameToSkipReason(it.name))
        }
        BlockMethod.entries.forEach {
            assertEquals(it.name, converters.blockMethodToName(it))
            assertEquals(it, converters.nameToBlockMethod(it.name))
        }
        ActionKind.entries.forEach {
            assertEquals(it.name, converters.actionKindToName(it))
            assertEquals(it, converters.nameToActionKind(it.name))
        }
        TagKind.entries.forEach {
            assertEquals(it.name, converters.tagKindToName(it))
            assertEquals(it, converters.nameToTagKind(it.name))
        }
        TagSource.entries.forEach {
            assertEquals(it.name, converters.tagSourceToName(it))
            assertEquals(it, converters.nameToTagSource(it.name))
        }
        AuditSource.entries.forEach {
            assertEquals(it.name, converters.auditSourceToName(it))
            assertEquals(it, converters.nameToAuditSource(it.name))
        }
        AuditFindingKind.entries.forEach {
            assertEquals(it.name, converters.auditFindingKindToName(it))
            assertEquals(it, converters.nameToAuditFindingKind(it.name))
        }
    }

    /** Stored names are part of the on-disk format: renaming an entry breaks existing databases. */
    @Test
    fun persistedEnumNamesAreStable() {
        assertEquals(
            listOf("SEEN", "BLOCKED", "FALSE_POSITIVE", "UNBLOCKED_SUSPECTED"),
            AccountStatus.entries.map { it.name },
        )
        assertEquals(
            listOf("PROFILE", "DIALOG", "SOUND_HEURISTIC", "RESOLVE", "IMPORT"),
            HandleSource.entries.map { it.name },
        )
        assertEquals(listOf("OVERLAY", "SHARE", "RECON"), SightingSource.entries.map { it.name })
        assertEquals(
            listOf("NONE", "BLOCK", "SKIP", "AUTO_SKIP", "NOT_INTERESTED", "LOG", "FALSE_POSITIVE"),
            UserAction.entries.map { it.name },
        )
        assertEquals(listOf("ASSISTED", "MANUAL", "IMPORTED"), BlockMethod.entries.map { it.name })
        assertEquals(listOf("SIGNAL", "MANUAL"), TagKind.entries.map { it.name })
        assertEquals(listOf("AUTO_DETECTED", "MANUAL"), TagSource.entries.map { it.name })
        assertEquals(listOf("DATA_EXPORT", "MANUAL"), AuditSource.entries.map { it.name })
        assertEquals(
            listOf("MISSING_FROM_BLOCKLIST", "SEEN_AFTER_BLOCK", "IN_EXPORT_NOT_IN_DB"),
            AuditFindingKind.entries.map { it.name },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownEnumNameFailsLoudly() {
        converters.nameToUserAction("bogus")
    }
}
