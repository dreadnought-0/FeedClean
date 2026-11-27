package io.github.dreadnought0.feedclean.data.db

import androidx.room.TypeConverter
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

/**
 * Room type converters. Every enum is stored by `name` (entries are never renamed); [Signals] as JSON.
 * Room handles NULL columns itself, so the converters are non-null.
 */
class Converters {
    @TypeConverter
    fun signalsToJson(value: Signals): String = SignalsJson.encode(value)

    @TypeConverter
    fun jsonToSignals(value: String): Signals = SignalsJson.decode(value)

    @TypeConverter
    fun accountStatusToName(value: AccountStatus): String = value.name

    @TypeConverter
    fun nameToAccountStatus(value: String): AccountStatus = AccountStatus.valueOf(value)

    @TypeConverter
    fun handleSourceToName(value: HandleSource): String = value.name

    @TypeConverter
    fun nameToHandleSource(value: String): HandleSource = HandleSource.valueOf(value)

    @TypeConverter
    fun sightingSourceToName(value: SightingSource): String = value.name

    @TypeConverter
    fun nameToSightingSource(value: String): SightingSource = SightingSource.valueOf(value)

    @TypeConverter
    fun userActionToName(value: UserAction): String = value.name

    @TypeConverter
    fun nameToUserAction(value: String): UserAction = UserAction.valueOf(value)

    @TypeConverter
    fun skipReasonToName(value: SkipReason): String = value.name

    @TypeConverter
    fun nameToSkipReason(value: String): SkipReason = SkipReason.valueOf(value)

    @TypeConverter
    fun blockMethodToName(value: BlockMethod): String = value.name

    @TypeConverter
    fun nameToBlockMethod(value: String): BlockMethod = BlockMethod.valueOf(value)

    @TypeConverter
    fun actionKindToName(value: ActionKind): String = value.name

    @TypeConverter
    fun nameToActionKind(value: String): ActionKind = ActionKind.valueOf(value)

    @TypeConverter
    fun tagKindToName(value: TagKind): String = value.name

    @TypeConverter
    fun nameToTagKind(value: String): TagKind = TagKind.valueOf(value)

    @TypeConverter
    fun tagSourceToName(value: TagSource): String = value.name

    @TypeConverter
    fun nameToTagSource(value: String): TagSource = TagSource.valueOf(value)

    @TypeConverter
    fun auditSourceToName(value: AuditSource): String = value.name

    @TypeConverter
    fun nameToAuditSource(value: String): AuditSource = AuditSource.valueOf(value)

    @TypeConverter
    fun auditFindingKindToName(value: AuditFindingKind): String = value.name

    @TypeConverter
    fun nameToAuditFindingKind(value: String): AuditFindingKind = AuditFindingKind.valueOf(value)
}
