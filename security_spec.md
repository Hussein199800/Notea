# Notea Security Specification (v2)

## Encryption model
- **Master key (MK):** random 256-bit key per account. Encrypts locked-note content (`ENCv2:` AES-256-GCM, body + checklist together) and attachments of locked notes (file-level AES-GCM).
- **On device:** MK is stored only wrapped by a non-exportable Android Keystore AES key (`notea_device_kek_v2`). It is excluded from Android auto-backup.
- **Across devices:** MK is wrapped by PBKDF2-HMAC-SHA256(vault passphrase, 210k iterations, random salt) into a *vault envelope* stored at `users/{uid}/vault/key` and inside `.notea` backups. Only ciphertext leaves the device; a forgotten passphrase cannot be recovered.
- **No plaintext fallback:** any encryption failure aborts the write (`CryptoException` / `VaultLockedException`); encryption is round-trip verified before storing.
- **PINs** gate the UI only (app lock / note lock) and are never keys. Stored as salted PBKDF2 (`p2$iter$salt$hash`); legacy SHA-256 hashes are still accepted and upgraded on next successful entry.
- **Legacy `ENCv1`** payloads are decrypt-only and re-encrypted to v2 once the vault is ready. Payloads made with another device's Keystore remain untouched (not readable there by design of v1).
- The vault is created only after the server confirms the account has none; rules forbid replacing the key id.

## Data invariants (Firestore rules)
1. Strict per-user isolation for `users/{uid}`, `notes`, `folders`, `vault` (`request.auth.uid == uid`, `userId` field match).
2. Field allowlists (`hasOnly`) and type/size checks for v1 + v2 note fields; colorIndex 0–7; content ≤ 700k chars; tags ≤ 50; checklist ≤ 500.
3. Locked notes may not carry plaintext checklist items.
4. `createdAt`/`userId` immutable on update; client timestamps tolerated up to 1 day of clock skew.
5. Vault envelope: owner-only, cannot be deleted, KDF iterations ≥ 100k, key id cannot change.
6. Owner may `get` a not-yet-existing document (pre-write conflict check).

Tests: `firestore.test.js` (10 emulator tests) and `SyncEmulatorTest` (end-to-end sync, conflict, ciphertext-only uploads).
