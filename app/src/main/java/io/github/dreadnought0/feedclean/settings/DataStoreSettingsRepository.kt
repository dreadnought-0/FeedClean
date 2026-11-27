package io.github.dreadnought0.feedclean.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/**
 * [SettingsRepository] over Preferences DataStore (file `feedclean_settings`, app-private).
 *
 * Every [FeedCleanSettings] field has its own key; enums are stored by name (unknown names fall back to the
 * default). [FeedCleanSettings.sanitized] is applied on every write and on every read, so hard floors/caps hold
 * even for a hand-edited or older file.
 *
 * [settings] starts as `FeedCleanSettings()` (automation off) until the file has been read.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsRepository {

    /** Production constructor: uses the process-wide DataStore for [Context] (never created twice). */
    constructor(context: Context, scope: CoroutineScope) : this(context.applicationContext.feedCleanSettingsStore, scope)

    override val settings: StateFlow<FeedCleanSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, FeedCleanSettings())

    override suspend fun update(transform: (FeedCleanSettings) -> FeedCleanSettings) {
        dataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toSettings()).sanitized())
        }
    }

    companion object {
        /** DataStore file name (`files/datastore/feedclean_settings.preferences_pb`). */
        const val FILE_NAME = "feedclean_settings"
    }
}

/** Process-wide singleton DataStore; the delegate guarantees one instance per file. */
private val Context.feedCleanSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreSettingsRepository.FILE_NAME,
)

/** Preference keys. Never rename a key: that silently resets the user's setting. */
internal object SettingsKeys {
    val DETECTION_ENABLED = booleanPreferencesKey("detection_enabled")
    val DETECT_PAID_AD = booleanPreferencesKey("detect_paid_ad")
    val DETECT_PROMOTED = booleanPreferencesKey("detect_promoted")
    val DETECT_SHOP_ANCHOR = booleanPreferencesKey("detect_shop_anchor")
    val DETECT_LIVE_SHOPPING = booleanPreferencesKey("detect_live_shopping")
    val OCR_FALLBACK_ENABLED = booleanPreferencesKey("ocr_fallback_enabled")
    val SELECTOR_OVERRIDE_URI = stringPreferencesKey("selector_override_uri")

    val CURATE_FEED_ENABLED = booleanPreferencesKey("curate_feed_enabled")
    val AUTO_SKIP_ORGANIC_SHOP = booleanPreferencesKey("auto_skip_organic_shop")
    val AUTO_SKIP_PAID_ADS = booleanPreferencesKey("auto_skip_paid_ads")
    val AUTO_SKIP_PROMOTED = booleanPreferencesKey("auto_skip_promoted")
    val AUTO_SKIP_LIVE_SHOPPING = booleanPreferencesKey("auto_skip_live_shopping")
    val SKIP_SETTLE_DELAY_MS = intPreferencesKey("skip_settle_delay_ms")
    val CONSECUTIVE_SKIP_CAP = intPreferencesKey("consecutive_skip_cap")
    val SKIP_COVER_ENABLED = booleanPreferencesKey("skip_cover_enabled")
    val AUTO_SKIP_IN_FOR_YOU = booleanPreferencesKey("auto_skip_in_for_you")
    val AUTO_SKIP_IN_OTHER_HOME_TABS = booleanPreferencesKey("auto_skip_in_other_home_tabs")
    val AUTO_SKIP_IN_SEARCH = booleanPreferencesKey("auto_skip_in_search")
    val AUTO_SKIP_IN_OTHER_FEEDS = booleanPreferencesKey("auto_skip_in_other_feeds")
    val DECISION_LOG_ENABLED = booleanPreferencesKey("decision_log_enabled")
    val COVER_STYLE = stringPreferencesKey("cover_style")

    val NOT_INTERESTED_MANUAL_ENABLED = booleanPreferencesKey("not_interested_manual_enabled")
    val NOT_INTERESTED_AUTOMATED = booleanPreferencesKey("not_interested_automated")
    val NOT_INTERESTED_RISK_ACKNOWLEDGED = booleanPreferencesKey("not_interested_risk_acknowledged")
    val NOT_INTERESTED_REASON = stringPreferencesKey("not_interested_reason")

    val SHOW_BUBBLE = booleanPreferencesKey("show_bubble")
    val BUBBLE_X = intPreferencesKey("bubble_x")
    val BUBBLE_Y = intPreferencesKey("bubble_y")

    val BLOCK_STYLE = stringPreferencesKey("block_style")
    val MIN_SECONDS_BETWEEN_ACTIONS = intPreferencesKey("min_seconds_between_actions")
    val MAX_ACTIONS_PER_HOUR = intPreferencesKey("max_actions_per_hour")

    val KILL_SWITCH_ENGAGED = booleanPreferencesKey("kill_switch_engaged")
    val AUTO_SKIP_ONLY_WHEN_SCREEN_ON = booleanPreferencesKey("auto_skip_only_when_screen_on")

    val SESSION_TRACKING_ENABLED = booleanPreferencesKey("session_tracking_enabled")
    val SAVE_SCREENSHOTS = booleanPreferencesKey("save_screenshots")
    val PUBLIC_EXPORT_INCLUDE_SUBJECTIVE_TAGS = booleanPreferencesKey("public_export_include_subjective_tags")
}

/** Reads every field (missing keys → defaults) and sanitizes. */
internal fun Preferences.toSettings(): FeedCleanSettings {
    val d = FeedCleanSettings()
    val k = SettingsKeys
    return FeedCleanSettings(
        detectionEnabled = this[k.DETECTION_ENABLED] ?: d.detectionEnabled,
        detectPaidAd = this[k.DETECT_PAID_AD] ?: d.detectPaidAd,
        detectPromoted = this[k.DETECT_PROMOTED] ?: d.detectPromoted,
        detectShopAnchor = this[k.DETECT_SHOP_ANCHOR] ?: d.detectShopAnchor,
        detectLiveShopping = this[k.DETECT_LIVE_SHOPPING] ?: d.detectLiveShopping,
        ocrFallbackEnabled = this[k.OCR_FALLBACK_ENABLED] ?: d.ocrFallbackEnabled,
        selectorOverrideUri = this[k.SELECTOR_OVERRIDE_URI] ?: d.selectorOverrideUri,
        curateFeedEnabled = this[k.CURATE_FEED_ENABLED] ?: d.curateFeedEnabled,
        autoSkipOrganicShop = this[k.AUTO_SKIP_ORGANIC_SHOP] ?: d.autoSkipOrganicShop,
        autoSkipPaidAds = this[k.AUTO_SKIP_PAID_ADS] ?: d.autoSkipPaidAds,
        autoSkipPromoted = this[k.AUTO_SKIP_PROMOTED] ?: d.autoSkipPromoted,
        autoSkipLiveShopping = this[k.AUTO_SKIP_LIVE_SHOPPING] ?: d.autoSkipLiveShopping,
        skipSettleDelayMs = this[k.SKIP_SETTLE_DELAY_MS] ?: d.skipSettleDelayMs,
        consecutiveSkipCap = this[k.CONSECUTIVE_SKIP_CAP] ?: d.consecutiveSkipCap,
        skipCoverEnabled = this[k.SKIP_COVER_ENABLED] ?: d.skipCoverEnabled,
        autoSkipInForYou = this[k.AUTO_SKIP_IN_FOR_YOU] ?: d.autoSkipInForYou,
        autoSkipInOtherHomeTabs = this[k.AUTO_SKIP_IN_OTHER_HOME_TABS] ?: d.autoSkipInOtherHomeTabs,
        autoSkipInSearch = this[k.AUTO_SKIP_IN_SEARCH] ?: d.autoSkipInSearch,
        autoSkipInOtherFeeds = this[k.AUTO_SKIP_IN_OTHER_FEEDS] ?: d.autoSkipInOtherFeeds,
        decisionLogEnabled = this[k.DECISION_LOG_ENABLED] ?: d.decisionLogEnabled,
        coverStyle = enumOrDefault(this[k.COVER_STYLE], d.coverStyle),
        notInterestedManualEnabled = this[k.NOT_INTERESTED_MANUAL_ENABLED] ?: d.notInterestedManualEnabled,
        notInterestedAutomated = this[k.NOT_INTERESTED_AUTOMATED] ?: d.notInterestedAutomated,
        notInterestedRiskAcknowledged = this[k.NOT_INTERESTED_RISK_ACKNOWLEDGED] ?: d.notInterestedRiskAcknowledged,
        notInterestedReason = enumOrDefault(this[k.NOT_INTERESTED_REASON], d.notInterestedReason),
        showBubble = this[k.SHOW_BUBBLE] ?: d.showBubble,
        bubbleX = this[k.BUBBLE_X] ?: d.bubbleX,
        bubbleY = this[k.BUBBLE_Y] ?: d.bubbleY,
        blockStyle = enumOrDefault(this[k.BLOCK_STYLE], d.blockStyle),
        minSecondsBetweenActions = this[k.MIN_SECONDS_BETWEEN_ACTIONS] ?: d.minSecondsBetweenActions,
        maxActionsPerHour = this[k.MAX_ACTIONS_PER_HOUR] ?: d.maxActionsPerHour,
        killSwitchEngaged = this[k.KILL_SWITCH_ENGAGED] ?: d.killSwitchEngaged,
        autoSkipOnlyWhenScreenOn = this[k.AUTO_SKIP_ONLY_WHEN_SCREEN_ON] ?: d.autoSkipOnlyWhenScreenOn,
        sessionTrackingEnabled = this[k.SESSION_TRACKING_ENABLED] ?: d.sessionTrackingEnabled,
        saveScreenshots = this[k.SAVE_SCREENSHOTS] ?: d.saveScreenshots,
        publicExportIncludeSubjectiveTags =
            this[k.PUBLIC_EXPORT_INCLUDE_SUBJECTIVE_TAGS] ?: d.publicExportIncludeSubjectiveTags,
    ).sanitized()
}

/** Writes every field. A null [FeedCleanSettings.selectorOverrideUri] removes its key. */
internal fun MutablePreferences.writeSettings(s: FeedCleanSettings) {
    val k = SettingsKeys
    this[k.DETECTION_ENABLED] = s.detectionEnabled
    this[k.DETECT_PAID_AD] = s.detectPaidAd
    this[k.DETECT_PROMOTED] = s.detectPromoted
    this[k.DETECT_SHOP_ANCHOR] = s.detectShopAnchor
    this[k.DETECT_LIVE_SHOPPING] = s.detectLiveShopping
    this[k.OCR_FALLBACK_ENABLED] = s.ocrFallbackEnabled
    val uri = s.selectorOverrideUri
    if (uri == null) remove(k.SELECTOR_OVERRIDE_URI) else this[k.SELECTOR_OVERRIDE_URI] = uri
    this[k.CURATE_FEED_ENABLED] = s.curateFeedEnabled
    this[k.AUTO_SKIP_ORGANIC_SHOP] = s.autoSkipOrganicShop
    this[k.AUTO_SKIP_PAID_ADS] = s.autoSkipPaidAds
    this[k.AUTO_SKIP_PROMOTED] = s.autoSkipPromoted
    this[k.AUTO_SKIP_LIVE_SHOPPING] = s.autoSkipLiveShopping
    this[k.SKIP_SETTLE_DELAY_MS] = s.skipSettleDelayMs
    this[k.CONSECUTIVE_SKIP_CAP] = s.consecutiveSkipCap
    this[k.SKIP_COVER_ENABLED] = s.skipCoverEnabled
    this[k.AUTO_SKIP_IN_FOR_YOU] = s.autoSkipInForYou
    this[k.AUTO_SKIP_IN_OTHER_HOME_TABS] = s.autoSkipInOtherHomeTabs
    this[k.AUTO_SKIP_IN_SEARCH] = s.autoSkipInSearch
    this[k.AUTO_SKIP_IN_OTHER_FEEDS] = s.autoSkipInOtherFeeds
    this[k.DECISION_LOG_ENABLED] = s.decisionLogEnabled
    this[k.COVER_STYLE] = s.coverStyle.name
    this[k.NOT_INTERESTED_MANUAL_ENABLED] = s.notInterestedManualEnabled
    this[k.NOT_INTERESTED_AUTOMATED] = s.notInterestedAutomated
    this[k.NOT_INTERESTED_RISK_ACKNOWLEDGED] = s.notInterestedRiskAcknowledged
    this[k.NOT_INTERESTED_REASON] = s.notInterestedReason.name
    this[k.SHOW_BUBBLE] = s.showBubble
    this[k.BUBBLE_X] = s.bubbleX
    this[k.BUBBLE_Y] = s.bubbleY
    this[k.BLOCK_STYLE] = s.blockStyle.name
    this[k.MIN_SECONDS_BETWEEN_ACTIONS] = s.minSecondsBetweenActions
    this[k.MAX_ACTIONS_PER_HOUR] = s.maxActionsPerHour
    this[k.KILL_SWITCH_ENGAGED] = s.killSwitchEngaged
    this[k.AUTO_SKIP_ONLY_WHEN_SCREEN_ON] = s.autoSkipOnlyWhenScreenOn
    this[k.SESSION_TRACKING_ENABLED] = s.sessionTrackingEnabled
    this[k.SAVE_SCREENSHOTS] = s.saveScreenshots
    this[k.PUBLIC_EXPORT_INCLUDE_SUBJECTIVE_TAGS] = s.publicExportIncludeSubjectiveTags
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
