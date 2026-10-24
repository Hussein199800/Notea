# VaultNotes Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document in `/users/{userId}` and `/users/{userId}/notes/{noteId}` belongs exclusively to `userId == request.auth.uid`. Cross-user reads, writes, lists, and deletes are strictly forbidden.
2. **Schema & Size Bounds**: Every string field (`title`, `content`, `category`, `appPinHash`, `notePinHash`, `lockHint`, `languageCode`) has strict `.size()` limits to prevent Denial-of-Wallet storage exhaustion.
3. **Immutable Provenance**: On `update`, `userId` and `createdAt` are strictly immutable (`incoming().userId == existing().userId` and `incoming().createdAt == existing().createdAt`).
4. **Server Timestamp Enforcement**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects bounded by `<= request.time`.
5. **Shadow Field Rejection**: `hasOnly()` enforces exact field allowlists on both `create` and `update`.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read/Write**: Caller with `request.auth == null` attempting `get`, `list`, `create`, `update`, or `delete` on `/users/{userId}/notes/{noteId}`.
2. **Cross-User Note Espionage**: Authenticated user `alice_123` attempting `get` or `list` on `/users/bob_456/notes`.
3. **Identity Spoofing on Create**: Authenticated user `alice_123` creating a note under `/users/alice_123/notes/n1` with `userId: "bob_456"`.
4. **Shadow/Ghost Field Injection**: Creating or updating a note with an undeclared field `isAdmin: true` or `unboundedBlob: "..."`.
5. **Immutable Field Mutation**: Updating an existing note to change `createdAt` or `userId`.
6. **Title Size Overflow**: Creating or updating a note with `title` exceeding 200 characters or empty (`""`).
7. **Content Size Overflow**: Creating or updating a note with `content` exceeding 20,000 characters.
8. **Invalid Color Index**: Setting `colorIndex` to `-1`, `99`, or a string `"red"` instead of `int` in `0..5`.
9. **Future Timestamp Spoofing**: Passing a client timestamp in the future (`> request.time`) for `createdAt` or `updatedAt`.
10. **Unfiltered Collection Query**: Querying `/users/{userId}/notes` without matching `userId` ownership constraints.
11. **Invalid Language Code in Settings**: Writing `languageCode: "invalid_lang"` to `/users/{userId}`.
12. **Explicit Null on Required Field**: Updating `title: null` or `isLocked: null` on an existing note.
