package io.github.dreadnought0.feedclean.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier

/** Runs on the JVM: Preferences DataStore over a temp file, real dispatchers (no virtual time). */
class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    /** Every field differs from its default (enforced by [fixtureChangesEveryField]) and is already sanitized. */
    private val everyFieldChanged = FeedCleanSettings(
        detectionEnabled = false,
        detectPaidAd = false,
        detectPromoted = false,
        detectShopAnchor = false,
        detectLiveShopping = false,
        ocrFallbackEnabled = true,
        selectorOverrideUri = "content://com.example.docs/document/selectors.json",
        curateFeedEnabled = true,
        autoSkipOrganicShop = false,
        autoSkipPaidAds = true,
        autoSkipPromoted = true,
        autoSkipLiveShopping = true,
        skipSettleDelayMs = 450,
        consecutiveSkipCap = 12,
        skipCoverEnabled = true, // default is false
        coverStyle = CoverStyle.LABELLED,
        autoSkipInForYou = false,
        autoSkipInOtherHomeTabs = true,
        autoSkipInSearch = true,
        autoSkipInOtherFeeds = true,
        notInterestedManualEnabled = false,
        notInterestedAutomated = true,
        notInterestedRiskAcknowledged = true,
        notInterestedReason = NotInterestedReason.PREFER_SHOPPING,
        showBubble = false,
        bubbleX = 120,
        bubbleY = 640,
        blockStyle = BlockStyle.STOP_AT_TIKTOK_DIALOG,
        minSecondsBetweenActions = 9,
        maxActionsPerHour = 30,
        killSwitchEngaged = true,
        autoSkipOnlyWhenScreenOn = false,
        sessionTrackingEnabled = true,
        decisionLogEnabled = false,
        saveScreenshots = true,
        publicExportIncludeSubjectiveTags = true,
    )

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "test_settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun repository() = DataStoreSettingsRepository(dataStore, scope)

    private fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(10_000, block) }

    /** Guard: a new FeedCleanSettings field must be added to the fixture (and therefore to the mapping). */
    @Test
    fun fixtureChangesEveryField() {
        val defaults = FeedCleanSettings()
        val fields = FeedCleanSettings::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        check(fields.size >= 31) { "Expected every settings field, found ${fields.map { it.name }}" }
        fields.forEach { field ->
            field.isAccessible = true
            assertNotEquals("Fixture must change '${field.name}'", field.get(defaults), field.get(everyFieldChanged))
        }
        assertEquals(everyFieldChanged, everyFieldChanged.sanitized())
    }

    @Test
    fun emptyFileYieldsDefaults() = blocking {
        val repo = repository()
        assertEquals(FeedCleanSettings(), repo.settings.value)
        assertEquals(FeedCleanSettings(), dataStore.data.first().toSettings())
    }

    @Test
    fun roundTripsEveryField() = blocking {
        val repo = repository()
        repo.update { everyFieldChanged }
        assertEquals(everyFieldChanged, dataStore.data.first().toSettings())
        assertEquals(everyFieldChanged, repo.settings.first { it == everyFieldChanged })
    }

    @Test
    fun roundTripsBackToDefaults() = blocking {
        val repo = repository()
        repo.update { everyFieldChanged }
        repo.update { FeedCleanSettings() }
        assertEquals(FeedCleanSettings(), dataStore.data.first().toSettings())
        assertNull(dataStore.data.first()[SettingsKeys.SELECTOR_OVERRIDE_URI])
    }

    @Test
    fun updatesApplyToThePersistedValue() = blocking {
        val repo = repository()
        repo.update { it.copy(bubbleX = 10) }
        repo.update { it.copy(bubbleY = 20) }
        repo.update { it.copy(curateFeedEnabled = !it.curateFeedEnabled) }
        val expected = FeedCleanSettings(bubbleX = 10, bubbleY = 20, curateFeedEnabled = true)
        assertEquals(expected, repo.settings.first { it == expected })
    }

    @Test
    fun sanitizesFloorsAndCapsOnWrite() = blocking {
        val repo = repository()
        repo.update {
            it.copy(
                minSecondsBetweenActions = 1,
                maxActionsPerHour = 500,
                skipSettleDelayMs = 99_999,
                consecutiveSkipCap = 0,
            )
        }
        val stored = dataStore.data.first()
        assertEquals(FeedCleanSettings.MIN_SECONDS_FLOOR, stored[SettingsKeys.MIN_SECONDS_BETWEEN_ACTIONS])
        assertEquals(FeedCleanSettings.MAX_PER_HOUR_CAP, stored[SettingsKeys.MAX_ACTIONS_PER_HOUR])
        assertEquals(FeedCleanSettings.MAX_SETTLE_DELAY_MS, stored[SettingsKeys.SKIP_SETTLE_DELAY_MS])
        assertEquals(1, stored[SettingsKeys.CONSECUTIVE_SKIP_CAP])

        repo.update { it.copy(maxActionsPerHour = 0) }
        assertEquals(1, dataStore.data.first()[SettingsKeys.MAX_ACTIONS_PER_HOUR])
    }

    @Test
    fun sanitizesOutOfRangeValuesOnRead() = blocking {
        dataStore.edit {
            it[SettingsKeys.MIN_SECONDS_BETWEEN_ACTIONS] = 0
            it[SettingsKeys.MAX_ACTIONS_PER_HOUR] = 1_000
            it[SettingsKeys.SKIP_SETTLE_DELAY_MS] = -5
        }
        val settings = repository().settings.first { it.maxActionsPerHour != FeedCleanSettings().maxActionsPerHour }
        assertEquals(FeedCleanSettings.MIN_SECONDS_FLOOR, settings.minSecondsBetweenActions)
        assertEquals(FeedCleanSettings.MAX_PER_HOUR_CAP, settings.maxActionsPerHour)
        assertEquals(0, settings.skipSettleDelayMs)
    }

    @Test
    fun unknownEnumNamesFallBackToDefaults() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.COVER_STYLE to "SPARKLES",
            SettingsKeys.BLOCK_STYLE to "",
            SettingsKeys.NOT_INTERESTED_REASON to "PREFER_SHOPPING",
        )
        val settings = prefs.toSettings()
        assertEquals(CoverStyle.BLANK, settings.coverStyle)
        assertEquals(BlockStyle.ONE_TAP_FULL_FLOW, settings.blockStyle)
        assertEquals(NotInterestedReason.PREFER_SHOPPING, settings.notInterestedReason)
    }

    @Test
    fun enumsAreStoredByName() {
        val prefs = mutablePreferencesOf()
        prefs.writeSettings(everyFieldChanged)
        assertEquals("LABELLED", prefs[SettingsKeys.COVER_STYLE])
        assertEquals("STOP_AT_TIKTOK_DIALOG", prefs[SettingsKeys.BLOCK_STYLE])
        assertEquals("PREFER_SHOPPING", prefs[SettingsKeys.NOT_INTERESTED_REASON])
    }
}
