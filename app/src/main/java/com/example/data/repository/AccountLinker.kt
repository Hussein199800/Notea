package com.example.data.repository

import androidx.room.withTransaction
import com.example.AppContainer
import com.example.LOCAL_UID
import com.example.core.security.KeyVault
import com.example.core.security.NoteCipher
import com.example.core.security.VaultEnvelope
import com.example.data.sync.SyncEngine

/**
 * Moves everything created in "no account" mode into a newly signed-in account, so nothing is lost
 * when the user starts syncing. Locked notes keep working:
 * - account has no vault yet  -> the local vault key becomes the account's vault key;
 * - account already has a vault with another key -> local locked notes are re-encrypted with the
 *   account key (needs the account's vault passphrase).
 */
class AccountLinker(private val c: AppContainer) {
    sealed interface Result {
        data class Done(val notes: Int) : Result
        data object NothingToLink : Result
        data object NeedsAccountPassphrase : Result
        data object WrongPassphrase : Result
        data object NeedsInternet : Result
    }

    suspend fun hasLocalData(): Boolean = c.db.ownershipDao().countNotes(LOCAL_UID) > 0 ||
        c.repository.allFolders(LOCAL_UID).isNotEmpty() || c.keyVault.masterKey(LOCAL_UID) != null

    suspend fun link(uid: String, accountPassphrase: CharArray? = null): Result {
        if (!hasLocalData()) return Result.NothingToLink
        val vault = c.keyVault
        val localMk = vault.masterKey(LOCAL_UID)
        var reencryptTo: ByteArray? = null
        if (localMk != null) {
            val localEnv = vault.localEnvelope(LOCAL_UID)
            val remote = c.syncEngine.checkRemoteVault(uid)
            val hasLocked = c.db.noteDao().getLocked(LOCAL_UID).isNotEmpty() ||
                c.db.attachmentDao().allForUser(LOCAL_UID).any { it.encrypted }
            when (remote) {
                SyncEngine.RemoteVault.Unknown -> return Result.NeedsInternet
                SyncEngine.RemoteVault.Missing -> {
                    // The on-device vault becomes the account vault (uploaded on session start).
                    if (vault.masterKey(uid) == null && localEnv != null) vault.installMasterKey(uid, localMk, localEnv)
                }
                is SyncEngine.RemoteVault.Exists -> {
                    val env: VaultEnvelope = remote.envelope
                    if (env.keyId == KeyVault.keyId(localMk)) {
                        vault.installMasterKey(uid, localMk, env)
                    } else if (hasLocked) {
                        val accountMk = vault.masterKey(uid)?.takeIf { KeyVault.keyId(it) == env.keyId }
                            ?: accountPassphrase?.let { vault.openEnvelope(uid, env, it) }
                            ?: return if (accountPassphrase == null) Result.NeedsAccountPassphrase else Result.WrongPassphrase
                        vault.installMasterKey(uid, accountMk, env)
                        reencryptTo = accountMk
                    } else vault.rememberEnvelope(uid, env)
                }
            }
        }
        // Re-encrypt local locked content with the account key before re-owning it.
        // All ciphertexts are computed first; nothing is written if any of them fails.
        val target = reencryptTo
        val reencrypted = if (target != null && localMk != null) {
            c.db.noteDao().getLocked(LOCAL_UID).filter { NoteCipher.isV2(it.body) }
                .map { n -> n.copy(body = NoteCipher.encryptText(target, NoteCipher.decryptText(localMk, n.body))) }
        } else emptyList()
        if (target != null && localMk != null) {
            c.db.attachmentDao().allForUser(LOCAL_UID).filter { it.encrypted && it.localPath.isNotBlank() }.forEach { a ->
                // Idempotent: a file already converted by an interrupted earlier run is left as is.
                runCatching { c.attachmentStore.transform(LOCAL_UID, a.localPath, localMk, target) }
            }
        }
        // Files first (same relative paths), then rows.
        val from = c.attachmentStore.userDir(LOCAL_UID)
        val to = c.attachmentStore.userDir(uid)
        from.listFiles()?.forEach { f -> if (!f.renameTo(java.io.File(to, f.name))) { f.copyTo(java.io.File(to, f.name), true); f.delete() } }
        val own = c.db.ownershipDao()
        val moved = c.db.withTransaction {
            if (reencrypted.isNotEmpty()) c.db.noteDao().upsertAll(reencrypted)
            val targetTags = c.db.tagDao().allTags(uid).associateBy { it.normalized }
            c.db.tagDao().allTags(LOCAL_UID).forEach { t ->
                val existing = targetTags[t.normalized]
                if (existing != null) { own.repointTag(t.id, existing.id); own.dropTagRefs(t.id); c.db.tagDao().delete(t.id) }
                else own.moveTag(t.id, uid)
            }
            own.moveFolders(LOCAL_UID, uid)
            own.moveAttachments(LOCAL_UID, uid)
            own.moveReminders(LOCAL_UID, uid)
            own.moveTemplates(LOCAL_UID, uid)
            own.moveSearchIndex(LOCAL_UID, uid)
            own.moveNotes(LOCAL_UID, uid)
        }
        if (!c.settings.hasLocalLockSettings(uid) && c.settings.hasLocalLockSettings(LOCAL_UID)) {
            c.settings.setAppLock(uid, c.settings.appLockEnabled(LOCAL_UID), c.settings.appLockHash(LOCAL_UID))
        }
        c.settings.setLocalMode(false)
        return Result.Done(moved)
    }
}
