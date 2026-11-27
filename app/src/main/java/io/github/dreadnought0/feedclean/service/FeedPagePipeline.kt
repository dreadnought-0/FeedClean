package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.detect.CurationPolicy
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.Profile
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * Database side of one new feed page. Pure over the repository interfaces.
 *
 * For each new page:
 *  1. Enrich [io.github.dreadnought0.feedclean.detect.Signals.isShopCreator] from the database: the account
 *     found by the page's probable handle (or the account the page resolves to) has category "Shop".
 *  2. `upsertFromFeed` the creator (provisional by display name unless the probable handle is already known).
 *  3. Log a sighting (source OVERLAY, deduplicated by the repository) if an enabled detection fired.
 *  4. Decide keep/skip with [CurationPolicy] on the enriched signals.
 *  5. Work out the "seen after block" warning.
 *
 * Session counting and the auto-skip itself are the caller's job. Later reads of the same page go through [refine].
 */
class FeedPagePipeline(
    private val accounts: AccountRepository,
    private val sightings: SightingRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Runs steps 1–5 for [page]. */
    suspend fun process(page: FeedVideo, settings: FeedCleanSettings, sessionId: Long?): ProcessedPage {
        val now = clock()
        val probable = page.probableHandle
        val byHandle = probable?.let { accounts.findByHandle(it) }
        val accountId = accounts.upsertFromFeed(page.displayName, probable, now)
        val account = if (byHandle?.id == accountId) byHandle else accounts.getAccount(accountId)

        val shopCreator = isShopCategory(byHandle?.accountCategory) || isShopCategory(account?.accountCategory)
        val enriched = if (shopCreator && !page.signals.isShopCreator) {
            page.copy(signals = page.signals.copy(isShopCreator = true))
        } else {
            page
        }

        val sightingId = if (DetectionRules.shouldRecord(enriched.signals, settings)) {
            sightings.recordSighting(
                accountId = accountId,
                pageSignature = enriched.pageSignature,
                signals = enriched.signals,
                source = SightingSource.OVERLAY,
                nowMillis = now,
                sessionId = sessionId,
            ).id
        } else {
            null
        }

        return ProcessedPage(
            page = enriched,
            accountId = accountId,
            knownHandle = account?.handle,
            sightingId = sightingId,
            decision = CurationPolicy.decide(enriched.signals, settings),
            blockedMatch = blockedMatch(account, page.displayName),
            processedAtMillis = now,
        )
    }

    /**
     * A later read ([later]) of the page [processed] during its watch window (FeedCurator): merges the signals
     * ([DetectionRules.merge]) and, if a detection signal appeared ([DetectionRules.grew]), updates the page's
     * sighting with the merged signals (or logs one, under [recordSignature], if the page had none and an enabled
     * detection now fires) and re-decides with [CurationPolicy]. Returns null when nothing new was detected.
     *
     * The account is not touched again (it was attributed on the first read) and the session is not counted again.
     *
     * @param recordSignature the signature the page's sightings are keyed by: its FIRST read's, which differs from
     *   [later]'s after a caption/sound refinement.
     */
    suspend fun refine(
        processed: ProcessedPage,
        later: FeedVideo,
        settings: FeedCleanSettings,
        sessionId: Long?,
        recordSignature: String = processed.pageSignature,
    ): Refinement? {
        val merged = DetectionRules.merge(processed.signals, later.signals)
        if (!DetectionRules.grew(processed.signals, merged)) return null
        val now = clock()
        val sightingId = processed.sightingId?.also { sightings.updateSignals(it, merged, now) }
            ?: if (DetectionRules.shouldRecord(merged, settings)) {
                val result = sightings.recordSighting(
                    accountId = processed.accountId,
                    pageSignature = recordSignature,
                    signals = merged,
                    source = SightingSource.OVERLAY,
                    nowMillis = now,
                    sessionId = sessionId,
                )
                if (result.deduplicated) sightings.updateSignals(result.id, merged, now)
                result.id
            } else {
                null
            }
        val before = DetectionRules.detectedKinds(processed.signals, settings).toSet()
        return Refinement(
            page = processed.copy(
                page = later.copy(signals = merged),
                sightingId = sightingId,
                decision = CurationPolicy.decide(merged, settings),
            ),
            newKinds = DetectionRules.detectedKinds(merged, settings).filterNot { it in before },
        )
    }

    /**
     * A profile with a readable @handle: if it is a Shop seller, remember that (category "Shop") on the
     * handle-keyed account so future feed pages of this creator are enriched. Non-Shop profiles are not stored.
     * Returns the account id when something was written.
     */
    suspend fun recordProfile(profile: Profile): Long? {
        val handle = profile.handle ?: return null
        if (!profile.isShopCreator) return null
        val id = accounts.ensureHandleAccount(handle, HandleSource.PROFILE, profile.displayName, clock())
        val current = accounts.getAccount(id)
        if (!isShopCategory(current?.accountCategory)) accounts.setAccountCategory(id, SHOP_CATEGORY)
        return id
    }

    private suspend fun blockedMatch(account: Account?, displayName: String?): BlockedMatch {
        if (account != null && account.status == AccountStatus.BLOCKED) {
            return if (account.handle != null) BlockedMatch.STRONG else BlockedMatch.POSSIBLE
        }
        val name = displayName?.takeIf { it.isNotBlank() } ?: return BlockedMatch.NONE
        return if (accounts.findByDisplayName(name).any { it.status == AccountStatus.BLOCKED }) {
            BlockedMatch.POSSIBLE
        } else {
            BlockedMatch.NONE
        }
    }

    companion object {
        /** Stored in `accounts.account_category` for Shop sellers/creators. */
        const val SHOP_CATEGORY = "Shop"

        fun isShopCategory(category: String?): Boolean = category?.trim()?.equals(SHOP_CATEGORY, ignoreCase = true) == true
    }
}
