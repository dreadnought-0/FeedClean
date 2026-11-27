package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals

/** Accounts-list row: the account plus aggregates computed in SQL. */
data class AccountSummaryRow(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "sighting_count") val sightingCount: Int,
    @ColumnInfo(name = "last_action") val lastAction: UserAction?,
)

/** (account, tag name) pair, used to attach tag names to list rows. */
data class AccountTagNameRow(
    @ColumnInfo(name = "account_id") val accountId: Long,
    val name: String,
)

/** A tag on an account with the assignment's source and time. */
data class TagAssignmentRow(
    @Embedded val tag: TagEntity,
    val source: TagSource,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** The columns of a sighting the stats need. */
data class SightingStatRow(
    @ColumnInfo(name = "seen_at") val seenAt: Long,
    @ColumnInfo(name = "signals_json") val signals: Signals,
    @ColumnInfo(name = "user_action") val userAction: UserAction,
    @ColumnInfo(name = "action_verified") val actionVerified: Boolean?,
)

/** Session start time and page count, for the daily "seen" figure. */
data class SessionSeenRow(
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "videos_seen") val videosSeen: Int,
)
