package io.github.dreadnought0.feedclean.data.model

/** A tag that can be put on accounts. [name] is unique and normalized (see [TagNames.normalize]). */
data class Tag(
    val id: Long,
    val name: String,
    val kind: TagKind,
)

/** Persisted by name; never rename entries. */
enum class TagKind {
    /** Derived from on-screen labels (objective). */
    SIGNAL,

    /** Set by the user (subjective). */
    MANUAL,
}

/** Who put a tag on an account. Persisted by name; never rename entries. */
enum class TagSource { AUTO_DETECTED, MANUAL }

/** A tag on one account. The same tag can be present once per [source]. */
data class TagAssignment(
    val tag: Tag,
    val source: TagSource,
    val createdAt: Long,
)
