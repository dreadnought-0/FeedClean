package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.db.dao.TagDao
import io.github.dreadnought0.feedclean.data.db.entity.AccountTagEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource

/** Shared "put a tag on an account" logic. Callers run it inside a transaction. */
internal class TagWriter(private val tagDao: TagDao) {
    /** Assigns [rawName] (normalized) to [accountId]; creates the tag if needed. Idempotent. */
    suspend fun assign(accountId: Long, rawName: String, source: TagSource, nowMillis: Long) {
        val name = TagNames.normalize(rawName)
        require(name.isNotEmpty()) { "Tag name is blank" }
        val kindIfNew = if (source == TagSource.AUTO_DETECTED) TagKind.SIGNAL else TagKind.MANUAL
        val tagId = tagIdFor(name, kindIfNew)
        tagDao.insertAssignment(AccountTagEntity(accountId, tagId, source, nowMillis))
    }

    private suspend fun tagIdFor(name: String, kindIfNew: TagKind): Long {
        tagDao.byName(name)?.let { return it.id }
        val inserted = tagDao.insertTag(TagEntity(name = name, kind = kindIfNew))
        return if (inserted > 0) inserted else checkNotNull(tagDao.byName(name)).id
    }
}
