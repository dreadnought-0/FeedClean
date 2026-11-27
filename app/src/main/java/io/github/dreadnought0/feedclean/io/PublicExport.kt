package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The public export file (docs/EXPORT_SCHEMA.md, "Public export"). */
@Serializable
data class PublicExportFile(
    @SerialName("schema_version") val schemaVersion: Int = PublicExport.SCHEMA_VERSION,
    /** `YYYY-MM` (UTC). */
    val generated: String,
    @SerialName("app_version") val appVersion: String,
    val accounts: List<PublicAccount>,
)

/** One account in the public export. Nothing else about the account is included. */
@Serializable
data class PublicAccount(
    val handle: String,
    val tags: List<String>,
    /** `YYYY-MM` (UTC) of the first sighting. */
    @SerialName("first_reported") val firstReported: String,
    @SerialName("evidence_type") val evidenceType: List<String>,
)

/**
 * Builds the public export. Pure. Privacy by construction: the output types only have handle,
 * tags, a month, and evidence kinds, so notes, display names, screenshots, exact timestamps, sightings,
 * sessions and block history cannot leak.
 *
 * Included accounts: a known handle, not marked false positive, and at least one allowed tag.
 * Allowed tags: [OBJECTIVE_TAGS] always; [SUBJECTIVE_TAGS] only when the user turned on
 * `publicExportIncludeSubjectiveTags`. Tags the user created themselves are never included (they may be
 * personal).
 */
object PublicExport {
    const val SCHEMA_VERSION: Int = 1

    /** Tags derived from on-screen labels. Included by default. */
    val OBJECTIVE_TAGS: Set<String> = setOf(
        TagNames.PAID_AD,
        TagNames.PROMOTED,
        TagNames.SHOP_ANCHOR,
        TagNames.ORGANIC_SHOP,
        TagNames.LIVE_SHOPPING,
    )

    /** The user's own judgement. Opt-in per export. */
    val SUBJECTIVE_TAGS: Set<String> = setOf(
        TagNames.AI_GENERATED,
        TagNames.SEXUAL_CONTENT,
        TagNames.IMPERSONATOR,
        TagNames.REPOSTED,
        TagNames.BOT_LIKE,
    )

    /** `evidence_type` vocabulary. Consumers ignore unknown values. */
    object Evidence {
        /** The "Ad" / "Sponsored" disclosure label was on screen. */
        const val AD_LABEL = "ad_label"

        /** A "Promotional content" / "Paid partnership" label was on screen. */
        const val PROMOTED_LABEL = "promoted_label"

        /** The TikTok Shop product anchor was on screen. */
        const val SHOP_ANCHOR = "shop_anchor"

        /** The profile showed a Shop (seller) marker. */
        const val SHOP_CREATOR = "shop_creator"

        /** A LIVE shopping label was on screen. */
        const val LIVE_SHOPPING_LABEL = "live_shopping_label"

        /** The user tagged the account by hand (no label detected for this tag). */
        const val MANUAL_REPORT = "manual_report"
    }

    private val monthFormat = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** `YYYY-MM` (UTC) of [millis]. */
    fun month(millis: Long): String = monthFormat.format(Instant.ofEpochMilli(millis))

    /**
     * Builds the export from each account with its tag assignments.
     *
     * @param includeSubjectiveTags the `publicExportIncludeSubjectiveTags` setting.
     */
    fun build(
        accounts: List<Pair<Account, List<TagAssignment>>>,
        includeSubjectiveTags: Boolean,
        nowMillis: Long,
        appVersion: String,
    ): PublicExportFile {
        val allowed = if (includeSubjectiveTags) OBJECTIVE_TAGS + SUBJECTIVE_TAGS else OBJECTIVE_TAGS
        val entries = accounts
            .filter { (account, _) -> account.status != AccountStatus.FALSE_POSITIVE }
            .mapNotNull { (account, assignments) ->
                val handle = account.handle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val byTag = assignments
                    .groupBy { TagNames.normalize(it.tag.name) }
                    .filterKeys { it in allowed }
                if (byTag.isEmpty()) return@mapNotNull null
                val autoDetected = byTag.filterValues { list -> list.any { it.source == TagSource.AUTO_DETECTED } }.keys
                PublicAccount(
                    handle = handle,
                    tags = byTag.keys.sorted(),
                    firstReported = month(account.firstSeenAt),
                    evidenceType = byTag.keys.map { evidenceFor(it, it in autoDetected, autoDetected) }
                        .distinct()
                        .sorted(),
                )
            }
            .distinctBy { it.handle }
            .sortedBy { it.handle }
        return PublicExportFile(generated = month(nowMillis), appVersion = appVersion, accounts = entries)
    }

    /** The file as pretty-printed JSON. */
    fun encode(file: PublicExportFile): String = json.encodeToString(PublicExportFile.serializer(), file)

    private fun evidenceFor(tag: String, detected: Boolean, detectedTags: Set<String>): String {
        if (!detected) return Evidence.MANUAL_REPORT
        return when (tag) {
            TagNames.PAID_AD -> Evidence.AD_LABEL
            TagNames.PROMOTED -> Evidence.PROMOTED_LABEL
            TagNames.SHOP_ANCHOR -> Evidence.SHOP_ANCHOR
            TagNames.ORGANIC_SHOP ->
                if (TagNames.SHOP_ANCHOR in detectedTags) Evidence.SHOP_ANCHOR else Evidence.SHOP_CREATOR
            TagNames.LIVE_SHOPPING -> Evidence.LIVE_SHOPPING_LABEL
            else -> Evidence.MANUAL_REPORT
        }
    }
}
