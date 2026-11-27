package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.SessionRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import io.github.dreadnought0.feedclean.detect.Signals

/** Room-backed [SessionRepository]. */
class RoomSessionRepository(db: FeedCleanDatabase) : SessionRepository {
    private val dao = db.sessionDao()

    override suspend fun start(nowMillis: Long): Long = dao.insert(SessionEntity(startedAt = nowMillis))

    override suspend fun end(id: Long, nowMillis: Long) = dao.end(id, nowMillis)

    override suspend fun endAllOpen(nowMillis: Long): Int = dao.endAllOpen(nowMillis)

    override suspend fun recordPage(id: Long, signals: Signals, skipped: Boolean) {
        val inc = PageIncrements.of(signals, skipped)
        dao.addPage(id, ad = inc.ad, promoted = inc.promoted, shop = inc.shop, live = inc.live, skipped = inc.skipped)
    }
}

/** Per-page session counter increments (each 0 or 1). Pure, see [SessionRepository.recordPage]. */
internal data class PageIncrements(val ad: Int, val promoted: Int, val shop: Int, val live: Int, val skipped: Int) {
    companion object {
        fun of(signals: Signals, skipped: Boolean) = PageIncrements(
            ad = signals.isPaidAd.toInt(),
            promoted = signals.isPromoted.toInt(),
            shop = signals.isOrganicShop.toInt(),
            live = (signals.isLive || signals.isLiveShopping).toInt(),
            skipped = skipped.toInt(),
        )

        private fun Boolean.toInt(): Int = if (this) 1 else 0
    }
}
