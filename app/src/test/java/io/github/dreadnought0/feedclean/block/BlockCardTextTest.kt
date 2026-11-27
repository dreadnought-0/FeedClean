package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.overlay.ActionMessage
import io.github.dreadnought0.feedclean.overlay.ActionMessages
import io.github.dreadnought0.feedclean.overlay.CardActionKind
import io.github.dreadnought0.feedclean.overlay.OverlayTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** How block steps and abort reasons reach the overlay card. */
class BlockCardTextTest {

    @Test
    fun everyBlockStepHasItsOwnProgressText() {
        for (step in BlockStep.entries) {
            assertNotEquals(step.name, R.string.overlay_step_working, OverlayTexts.step(CardActionKind.BLOCK, step.name))
        }
        assertEquals(R.string.overlay_step_dismiss_overlay, OverlayTexts.step(CardActionKind.BLOCK, BlockStep.DISMISS_OVERLAY.name))
    }

    @Test
    fun overlayNotDismissedGetsItsOwnMessage() {
        val m = ActionMessages.forBlock(
            BlockResult.Aborted(BlockStep.DISMISS_OVERLAY, BlockAbortReason.OVERLAY_NOT_DISMISSED, "h", returnedToFeed = false),
        )

        assertEquals(ActionMessage.Type.BLOCK_OVERLAY_NOT_DISMISSED, m.type)
        assertEquals("DISMISS_OVERLAY · OVERLAY_NOT_DISMISSED", m.arg)
        assertFalse(m.ok)
    }

    @Test
    fun otherAbortsKeepTheGenericMessage() {
        val m = ActionMessages.forBlock(
            BlockResult.Aborted(BlockStep.OPEN_SHARE_SHEET, BlockAbortReason.TAP_FAILED, "h", returnedToFeed = false),
        )

        assertEquals(ActionMessage.Type.BLOCK_STOPPED, m.type)
    }
}
