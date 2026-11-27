package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMergeTest {
    private val target = AccountEntity(
        id = 1,
        handle = "real.handle",
        handleSource = HandleSource.PROFILE,
        displayName = "Old Name",
        displayNameSkeleton = "old name",
        firstSeenAt = 50,
        lastSeenAt = 100,
        notes = "target note",
    )
    private val source = AccountEntity(
        id = 2,
        handle = null,
        handleSource = null,
        displayName = "New Name",
        displayNameSkeleton = "new name",
        firstSeenAt = 10,
        lastSeenAt = 200,
        status = AccountStatus.FALSE_POSITIVE,
        accountCategory = "Shop",
        notes = "source note",
        profilePhotoHash = 42L,
    )

    @Test
    fun mergesSeenRangeNameStatusAndFields() {
        val merged = mergeAccountFields(target, source)
        assertEquals(1L, merged.id)
        assertEquals("real.handle", merged.handle)
        assertEquals(HandleSource.PROFILE, merged.handleSource)
        assertEquals("New Name", merged.displayName)
        assertEquals("new name", merged.displayNameSkeleton)
        assertEquals(10L, merged.firstSeenAt)
        assertEquals(200L, merged.lastSeenAt)
        assertEquals(AccountStatus.FALSE_POSITIVE, merged.status)
        assertEquals("Shop", merged.accountCategory)
        assertEquals("target note\nsource note", merged.notes)
        assertEquals(42L, merged.profilePhotoHash)
    }

    @Test
    fun targetVerdictAndNewerNameWin() {
        val blockedTarget = target.copy(status = AccountStatus.BLOCKED, lastSeenAt = 300, accountCategory = "Creator")
        val merged = mergeAccountFields(blockedTarget, source)
        assertEquals(AccountStatus.BLOCKED, merged.status)
        assertEquals("Old Name", merged.displayName)
        assertEquals("Creator", merged.accountCategory)
        assertEquals(300L, merged.lastSeenAt)
    }

    @Test
    fun missingTargetNameIsFilledFromSource() {
        val merged = mergeAccountFields(target.copy(displayName = null, displayNameSkeleton = null, lastSeenAt = 999), source)
        assertEquals("New Name", merged.displayName)
    }

    @Test
    fun identicalOrBlankNotesAreNotDuplicated() {
        assertEquals("same", mergeAccountFields(target.copy(notes = "same"), source.copy(notes = "same")).notes)
        assertEquals("only", mergeAccountFields(target.copy(notes = " "), source.copy(notes = "only")).notes)
        assertNull(mergeAccountFields(target.copy(notes = null), source.copy(notes = null)).notes)
    }

    @Test
    fun suspectedOriginalNeverBecomesSelfReference() {
        assertNull(mergeAccountFields(target, source.copy(suspectedOriginalOf = 1)).suspectedOriginalOf)
        assertNull(mergeAccountFields(target.copy(suspectedOriginalOf = 2), source).suspectedOriginalOf)
        assertEquals(7L, mergeAccountFields(target, source.copy(suspectedOriginalOf = 7)).suspectedOriginalOf)
    }
}
