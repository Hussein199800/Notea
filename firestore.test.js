const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validNotePayload(userId, now = new Date()) {
  return {
    userId,
    title: "Secret Vault Note",
    content: "Encrypted thoughts synced across devices.",
    category: "personal",
    colorIndex: 1,
    isPinned: true,
    isLocked: true,
    notePinHash: "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
    lockHint: "First pet",
    isArchived: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validSettingsPayload(userId, now = new Date()) {
  return {
    userId,
    appLockEnabled: true,
    appPinHash: "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
    languageCode: "ar",
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read or write notes or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_1")
      .set(validNotePayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("2. Authenticated owner can create, read, list, update, and delete their own note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("notes")
    .doc("note_1");

  const createdTime = new Date(Date.now() - 1000);
  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID, createdTime)));
  await assertSucceeds(noteRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Update note preserving createdAt and userId
  await assertSucceeds(
    noteRef.update({
      title: "Updated Arabic Note ملاحظة",
      isLocked: false,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("3. Cross-user isolation: Alice cannot read, list, update, or delete Bob's notes", async () => {
  const createdTime = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .set(validNotePayload(BOB_UID, createdTime));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobNoteRef = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("notes")
    .doc("bob_note");

  await assertFails(bobNoteRef.get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .where("userId", "==", BOB_UID)
      .get()
  );
  await assertFails(bobNoteRef.update({ title: "Hacked", updatedAt: new Date() }));
  await assertFails(bobNoteRef.delete());
});

test("4. Identity spoofing and shadow fields are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("notes")
    .doc("note_spoof");

  // Spoofed userId
  await assertFails(
    noteRef.set({
      ...validNotePayload(ALICE_UID, new Date(Date.now() - 500)),
      userId: BOB_UID,
    })
  );

  // Shadow field injection
  await assertFails(
    noteRef.set({
      ...validNotePayload(ALICE_UID, new Date(Date.now() - 500)),
      isAdmin: true,
    })
  );
});

test("5. UserSettings validation: owner can create and update valid settings, invalid language rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const settingsRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    settingsRef.set(validSettingsPayload(ALICE_UID, new Date(Date.now() - 500)))
  );
  await assertSucceeds(settingsRef.get());

  // Reject unsupported language code
  await assertFails(
    settingsRef.update({
      languageCode: "fr",
      updatedAt: new Date(),
    })
  );
});

// ---------------------------------------------------------------- schema v2
function v2NotePayload(userId, now = new Date(Date.now() - 1000)) {
  return {
    ...validNotePayload(userId, now),
    isLocked: false,
    schemaVersion: 2,
    isFavorite: true,
    trashedAt: 0,
    folderId: "folderA",
    tags: ["عمل", "ideas"],
    checklist: [{ id: "c1", text: "شراء الخبز", checked: false }],
    reminderAt: 0,
    clientUpdatedAt: now.getTime(),
    deviceId: "device-1",
    blocks: [{ id: "b1", type: "text", len: 5 }, { id: "b2", type: "attachment", attachmentId: "a1" }],
    parentId: "",
    icon: "📒",
  };
}

test("6. v2 note: create, merge-update without createdAt, legacy v1 doc upgraded in place", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const notes = aliceDb.collection("users").doc(ALICE_UID).collection("notes");
  await assertSucceeds(notes.doc("v2note").set(v2NotePayload(ALICE_UID)));
  await assertSucceeds(
    notes.doc("v2note").set({ title: "edited", clientUpdatedAt: Date.now(), updatedAt: new Date() }, { merge: true })
  );
  // A v1 document (written by the previous app version) accepts the new fields.
  await assertSucceeds(notes.doc("v1note").set(validNotePayload(ALICE_UID, new Date(Date.now() - 5000))));
  const { createdAt, ...rest } = v2NotePayload(ALICE_UID);
  await assertSucceeds(notes.doc("v1note").set({ ...rest, updatedAt: new Date() }, { merge: true }));
});

test("7. Invalid v2 values are rejected (color, plaintext checklist on locked note, oversized content, createdAt change)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const notes = aliceDb.collection("users").doc(ALICE_UID).collection("notes");
  await assertFails(notes.doc("bad1").set({ ...v2NotePayload(ALICE_UID), colorIndex: 8 }));
  await assertFails(notes.doc("bad2").set({ ...v2NotePayload(ALICE_UID), isLocked: true }));
  await assertFails(notes.doc("bad3").set({ ...v2NotePayload(ALICE_UID), content: "x".repeat(700001) }));
  await assertFails(notes.doc("bad4").set({ ...v2NotePayload(ALICE_UID), tags: "not-a-list" }));
  // Locked note may not expose its block layout.
  await assertFails(notes.doc("bad5").set({ ...v2NotePayload(ALICE_UID), isLocked: true, checklist: [] }));
  await assertSucceeds(notes.doc("ok").set(v2NotePayload(ALICE_UID)));
  await assertFails(notes.doc("ok").update({ createdAt: new Date(Date.now() - 999999), updatedAt: new Date() }));
});

test("8. Folders are private to their owner", async () => {
  const now = new Date(Date.now() - 1000);
  const folder = { userId: ALICE_UID, name: "العمل", colorIndex: 0, position: 0, createdAt: now, clientUpdatedAt: now.getTime(), updatedAt: now };
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("f1").set(folder));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("folders").doc("f1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("folders").doc("f2").set({ ...folder, userId: BOB_UID }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("f3").set({ ...folder, name: "" }));
});

test("9. Vault envelope: owner only, key id cannot be swapped, cannot be deleted", async () => {
  const env = { userId: ALICE_UID, v: 1, kdf: "pbkdf2-hmac-sha256", iter: 210000, salt: "c2FsdA==", wrapped: "d3JhcHBlZA==", keyId: "abc123", createdAtMillis: Date.now() };
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("key");
  await assertSucceeds(ref.set(env));
  await assertSucceeds(ref.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vault").doc("key").get());
  await assertSucceeds(ref.set({ ...env, salt: "bmV3c2FsdA==", wrapped: "bmV3d3JhcA==" })); // passphrase change, same key
  await assertFails(ref.set({ ...env, keyId: "different" })); // would orphan locked notes
  await assertFails(ref.delete());
  await assertFails(ref.set({ ...env, iter: 1000 })); // weak KDF rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("vault").doc("other").set(env));
});

test("10. Owner may probe a not-yet-existing note (pre-write conflict check); others may not", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("not_yet").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("not_yet").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("folders").doc("nf").get());
});

test("11. sub-notes and icon: valid parentId/icon accepted, wrong types and oversize rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const notes = aliceDb.collection("users").doc(ALICE_UID).collection("notes");
  await assertSucceeds(notes.doc("child1").set({ ...v2NotePayload(ALICE_UID), parentId: "v2note", icon: "📚" }));
  await assertFails(notes.doc("child2").set({ ...v2NotePayload(ALICE_UID), parentId: 42 }));
  await assertFails(notes.doc("child3").set({ ...v2NotePayload(ALICE_UID), parentId: "x".repeat(129) }));
  await assertFails(notes.doc("child4").set({ ...v2NotePayload(ALICE_UID), icon: "x".repeat(65) }));
  await assertFails(notes.doc("child5").set({ ...v2NotePayload(ALICE_UID), icon: true }));
});
