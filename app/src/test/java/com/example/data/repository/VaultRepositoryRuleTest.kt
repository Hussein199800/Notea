package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.NoteBackupSerializer
import com.example.data.local.NoteEntity
import com.example.data.local.SyncConflictResolver
import com.example.data.model.Note
import com.example.data.model.PinSecurity
import com.example.security.CryptoManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveNotes_authenticatedOwner_succeedsAndSyncs() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VaultRepository(firestore)

        val encryptedBody = CryptoManager.encrypt("Private encrypted note in Arabic: بسم الله")
        assertTrue(CryptoManager.isEncryptedPayload(encryptedBody))
        assertNotEquals("Private encrypted note in Arabic: بسم الله", encryptedBody)
        assertEquals("Private encrypted note in Arabic: بسم الله", CryptoManager.decrypt(encryptedBody))

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "ملاحظاتي الخاصة",
                content = encryptedBody,
                category = Note.buildCategoryString("secret", isFavorite = true, isTrashed = false),
                colorIndex = 2,
                isPinned = true,
                isLocked = true,
                notePinHash = PinSecurity.hashPin("1234"),
                lockHint = "Year"
            )
        }
        assertTrue(createResult.isSuccess)
        val noteId = createResult.getOrThrow()
        assertTrue(noteId.isNotBlank())

        val notes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes(uid).first { list -> list.any { it.id == noteId } }
        }
        val fetched = notes.first { it.id == noteId }
        assertEquals("ملاحظاتي الخاصة", fetched.title)
        assertTrue(fetched.isLocked)
        assertTrue(fetched.isFavorite)
        assertEquals("secret", fetched.baseCategory)
        assertEquals(
            "Private encrypted note in Arabic: بسم الله",
            CryptoManager.decrypt(fetched.content)
        )
        assertTrue(PinSecurity.verifyPin("1234", fetched.notePinHash))
    }

    @Test
    fun syncConflictResolution_andJsonBackupRoundTrip_succeeds() {
        val olderLocal = NoteEntity(
            id = "note_uuid_1",
            userId = "u1",
            title = "Old Local Title",
            content = "Old content",
            category = "personal",
            colorIndex = 0,
            isPinned = false,
            isLocked = false,
            notePinHash = "",
            lockHint = "",
            isArchived = false,
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L,
            pendingSync = true
        )
        val newerRemote = olderLocal.copy(
            title = "عنوان محدث سحابياً",
            updatedAtMillis = 5000L,
            pendingSync = false
        )

        val winner = SyncConflictResolver.resolveWinner(olderLocal, newerRemote)
        assertEquals("عنوان محدث سحابياً", winner.title)
        assertEquals(5000L, winner.updatedAtMillis)

        val exportedJson = NoteBackupSerializer.exportToJson(listOf(winner.toDomainNote()))
        val reimported = NoteBackupSerializer.importFromJson(exportedJson, "u1")
        assertEquals(1, reimported.size)
        assertEquals("عنوان محدث سحابياً", reimported.first().title)
    }

    @Test
    fun saveAndObserveUserSettings_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = VaultRepository(firestore)
        val pinHash = PinSecurity.hashPin("9999")

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveUserSettings(
                appLockEnabled = true,
                appPinHash = pinHash,
                languageCode = "ar"
            )
        }
        assertTrue(saveResult.isSuccess)

        val settings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserSettings(uid).first { it != null && it.appLockEnabled }
        }
        assertNotNull(settings)
        assertEquals("ar", settings?.languageCode)
        assertEquals(pinHash, settings?.appPinHash)
    }

    @Test
    fun getNoteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = VaultRepository(firestore)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createNote(
                title = "Alice Private Note",
                content = "Strictly private"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = VaultRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getNoteById(aliceUid, noteId)
        }
        assertTrue(crossReadResult.isFailure)
        val firestoreEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = VaultRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeNotes(aliceUid).first()
            }
            fail("Expected unauthenticated observeNotes to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                firestoreException.code
            )
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_vault@test.com"
        const val BOB_EMAIL = "bob_vault@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
