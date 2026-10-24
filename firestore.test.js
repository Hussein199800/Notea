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
