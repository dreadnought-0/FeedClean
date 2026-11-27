package io.github.dreadnought0.feedclean

import android.content.Context
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.ActionLogRepository
import io.github.dreadnought0.feedclean.data.AuditRepository
import io.github.dreadnought0.feedclean.data.BlockEventRepository
import io.github.dreadnought0.feedclean.data.DataLayer
import io.github.dreadnought0.feedclean.data.SessionRepository
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.StatsRepository
import io.github.dreadnought0.feedclean.detect.NameNormalizer
import io.github.dreadnought0.feedclean.selectors.SelectorProvider
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI. One instance per process, owned by [FeedCleanApp]. The app UI, the accessibility
 * service, and the share target all run in the same process and share this container.
 */
class AppContainer(val appContext: Context) {

    /** Long-lived scope for app-wide work (keeps the settings StateFlow hot). Never cancelled. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Room database + repositories + DataStore settings. Built once per process. */
    val dataLayer: DataLayer = DataLayer(
        context = appContext,
        scope = applicationScope,
        skeletonOf = NameNormalizer::skeleton,
    )

    val settingsRepository: SettingsRepository get() = dataLayer.settingsRepository
    val accountRepository: AccountRepository get() = dataLayer.accountRepository
    val sightingRepository: SightingRepository get() = dataLayer.sightingRepository
    val blockEventRepository: BlockEventRepository get() = dataLayer.blockEventRepository
    val actionLogRepository: ActionLogRepository get() = dataLayer.actionLogRepository
    val sessionRepository: SessionRepository get() = dataLayer.sessionRepository
    val statsRepository: StatsRepository get() = dataLayer.statsRepository
    val auditRepository: AuditRepository get() = dataLayer.auditRepository

    /**
     * The ONE shared rate limiter. Blocks and automated "Not interested" draw from the same
     * persisted budget. Never construct another RateLimiter in production code.
     */
    val rateLimiter: RateLimiter = RateLimiter(dataLayer.actionLogRepository, dataLayer.settingsRepository.settings)

    /** Active selector config + ScreenParser; follows the selector-override setting. */
    val selectorProvider: SelectorProvider = SelectorProvider(appContext, dataLayer.settingsRepository, applicationScope)
}
