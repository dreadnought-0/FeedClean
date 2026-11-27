package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.ui.stats.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRowsTest {
    private fun sighting(
        id: Long,
        at: Long,
        signals: Signals = Signals(),
        action: UserAction = UserAction.NONE,
        verified: Boolean? = null,
        skipReason: SkipReason? = null,
        source: SightingSource = SightingSource.OVERLAY,
    ) = TimelineEntry.SightingEntry(
        Sighting(
            id = id, accountId = 1, seenAt = at, videoUrl = null, pageSignature = "sig$id", signals = signals,
            source = source, userAction = action, actionVerified = verified, skipReason = skipReason,
            screenshotPath = null, sessionId = null,
        ),
    )

    private fun block(id: Long, at: Long, verified: Boolean, method: BlockMethod = BlockMethod.ASSISTED, reason: String? = null, step: String? = null) =
        TimelineEntry.BlockEntry(
            BlockEvent(id = id, accountId = 1, attemptedAt = at, method = method, verified = verified, failureReason = reason, stepReached = step),
        )

    @Test
    fun rowsAreNewestFirstAcrossKinds() {
        val rows = timelineRows(listOf(sighting(1, at = 100), block(1, at = 300, verified = true), sighting(2, at = 200)))
        assertEquals(listOf("block-1", "sighting-2", "sighting-1"), rows.map { it.key })
    }

    @Test
    fun autoSkipShowsSignalsAndReason() {
        val row = sighting(
            1, at = 1, signals = Signals(hasShopAnchor = true), action = UserAction.AUTO_SKIP,
            skipReason = SkipReason.ORGANIC_SHOP,
        ).toRowText()

        assertEquals(TimelineKind.SIGHTING, row.kind)
        assertEquals(UiText.Res(R.string.screens_timeline_auto_skipped), row.title)
        assertEquals(listOf(R.string.screens_signal_organic_shop, R.string.screens_signal_shop_anchor), row.signalLabels)
        assertEquals(
            listOf(UiText.Res(R.string.screens_timeline_reason, listOf(UiText.Res(R.string.screens_skip_reason_organic_shop)))),
            row.details,
        )
        assertEquals(TimelineOutcome.NONE, row.outcome)
    }

    @Test
    fun paidAdSightingShowsTheRawLabelAndNoVerification() {
        val row = sighting(2, at = 1, signals = Signals(isPaidAd = true, disclosureLabel = " Sponsored ")).toRowText()
        assertEquals(UiText.Res(R.string.screens_timeline_seen), row.title)
        assertEquals(listOf(R.string.screens_signal_paid_ad), row.signalLabels)
        assertEquals(listOf(UiText.Res(R.string.screens_timeline_label, listOf("Sponsored"))), row.details)
    }

    @Test
    fun notInterestedVerifiedAndFailed() {
        val ok = sighting(3, at = 1, action = UserAction.NOT_INTERESTED, verified = true).toRowText()
        assertEquals(UiText.Res(R.string.screens_timeline_not_interested), ok.title)
        assertEquals(TimelineOutcome.VERIFIED, ok.outcome)
        assertEquals(listOf(UiText.Res(R.string.screens_timeline_verified)), ok.details)

        val failed = sighting(4, at = 1, action = UserAction.NOT_INTERESTED, verified = false).toRowText()
        assertEquals(TimelineOutcome.FAILED, failed.outcome)
        assertEquals(listOf(UiText.Res(R.string.screens_timeline_not_verified)), failed.details)
    }

    @Test
    fun verifiedFlagIgnoredWithoutAnAction() {
        val row = sighting(5, at = 1, verified = false).toRowText()
        assertEquals(TimelineOutcome.NONE, row.outcome)
        assertTrue(row.details.isEmpty())
    }

    @Test
    fun sharedSightingNamesItsSource() {
        val row = sighting(6, at = 1, action = UserAction.LOG, source = SightingSource.SHARE).toRowText()
        assertEquals(UiText.Res(R.string.screens_timeline_logged), row.title)
        assertEquals(listOf(UiText.Res(R.string.screens_source_share)), row.details)
    }

    @Test
    fun verifiedBlockShowsMethodOnly() {
        val row = block(7, at = 1, verified = true, reason = "ignored", step = "IGNORED").toRowText()
        assertEquals(TimelineKind.BLOCK, row.kind)
        assertEquals(UiText.Res(R.string.screens_timeline_blocked), row.title)
        assertEquals(TimelineOutcome.VERIFIED, row.outcome)
        assertEquals(listOf(UiText.Res(R.string.screens_block_method_assisted)), row.details)
    }

    @Test
    fun failedBlockShowsStepAndReason() {
        val row = block(8, at = 1, verified = false, reason = "handle mismatch", step = "CHECK_DIALOG").toRowText()
        assertEquals(UiText.Res(R.string.screens_timeline_block_failed), row.title)
        assertEquals(TimelineOutcome.FAILED, row.outcome)
        assertEquals(
            listOf(
                UiText.Res(R.string.screens_block_method_assisted),
                UiText.Res(R.string.screens_timeline_step, listOf("CHECK_DIALOG")),
                UiText.Res(R.string.screens_timeline_failure, listOf("handle mismatch")),
            ),
            row.details,
        )
    }

    @Test
    fun importedBlockSaysImported() {
        val row = block(9, at = 1, verified = true, method = BlockMethod.IMPORTED).toRowText()
        assertEquals(listOf(UiText.Res(R.string.screens_block_method_imported)), row.details)
    }

    @Test
    fun everyUserActionHasATitle() {
        val titles = UserAction.entries.map { it.timelineTitleRes() }
        assertEquals(UserAction.entries.size, titles.distinct().size)
    }
}
