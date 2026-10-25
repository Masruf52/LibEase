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

function serverTs() {
  return new Date(Date.now() - 500);
}

// --- 1. Unauthenticated Rejections ---

test("Unauthenticated user cannot read books, loans, or user accounts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("books").where("campusId", "==", "main").get());
  await assertFails(unauthDb.collection("loans").where("ownerUid", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

// --- 2. Book Catalog Creation, Query Alignment & Shadow Update Protection ---

test("Authenticated user can catalog a valid book and query with campusId == main", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb.collection("books").doc("book_clean_code").set({
      campusId: "main",
      createdByUid: ALICE_UID,
      title: "Clean Code",
      author: "Robert C. Martin",
      isbn: "978-0132350884",
      category: "Programming",
      publisher: "Prentice Hall",
      publicationYear: 2008,
      description: "A Handbook of Agile Software Craftsmanship",
      totalCopies: 3,
      availableCopies: 3,
      shelfNumber: "Shelf A-1",
      rating: 4.8,
      status: "Available",
      availabilityStatus: "Available",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Query aligned with campusId == 'main' succeeds
  await assertSucceeds(
    aliceDb.collection("books").where("campusId", "==", "main").get()
  );

  // Unfiltered query without campusId fails (rules are not filters)
  await assertFails(aliceDb.collection("books").get());
});

test("Shadow update with unauthorized extra field is rejected on books", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertFails(
    aliceDb.collection("books").doc("book_shadow").set({
      campusId: "main",
      createdByUid: ALICE_UID,
      title: "Shadow Book",
      author: "Test Author",
      isbn: "978-0000000001",
      category: "General",
      totalCopies: 1,
      availableCopies: 1,
      shelfNumber: "Shelf A-1",
      status: "Available",
      availabilityStatus: "Available",
      isVerified: true, // Ghost field!
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

// --- 3. PII Isolation & Email Spoofing Protection ---

test("Cross-user access to private /users/{uid} PII document is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = serverTs(aliceDb);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Rivera",
      email: "alice@university.edu",
      phone: "+1-555-0100",
      preferredRole: "STUDENT",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Spoofed unverified admin email cannot bypass PII isolation", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = serverTs(db);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Rivera",
      email: "alice@university.edu",
      preferredRole: "STUDENT",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_999", {
      email: "masruftaj30@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(spoofedAdminDb.collection("users").doc(ALICE_UID).get());
});

// --- 4. Circulation Loans, Referential Integrity & Terminal State Locking ---

test("Loan requires existing book, enforces owner isolation, and locks Returned terminal state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = serverTs(aliceDb);

  // Orphaned loan referencing non-existent book fails
  await assertFails(
    aliceDb.collection("loans").doc("loan_orphan").set({
      ownerUid: ALICE_UID,
      userId: ALICE_UID,
      userName: "Alice",
      bookId: "non_existent_book",
      bookTitle: "Ghost Book",
      bookAuthor: "Nobody",
      borrowDateMs: 1700000000000,
      dueDateMs: 1701209600000,
      status: "Borrowed",
      fineAmount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Seed valid book first
  await assertSucceeds(
    aliceDb.collection("books").doc("book_1").set({
      campusId: "main",
      createdByUid: ALICE_UID,
      title: "Domain-Driven Design",
      author: "Eric Evans",
      isbn: "978-0321125217",
      category: "Programming",
      totalCopies: 2,
      availableCopies: 2,
      shelfNumber: "Shelf B-2",
      status: "Available",
      availabilityStatus: "Available",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid loan creation succeeds
  await assertSucceeds(
    aliceDb.collection("loans").doc("loan_1").set({
      ownerUid: ALICE_UID,
      userId: ALICE_UID,
      userName: "Alice",
      bookId: "book_1",
      bookTitle: "Domain-Driven Design",
      bookAuthor: "Eric Evans",
      borrowDateMs: 1700000000000,
      dueDateMs: 1701209600000,
      status: "Borrowed",
      fineAmount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read or list Alice's loan
  await assertFails(bobDb.collection("loans").doc("loan_1").get());
  await assertFails(bobDb.collection("loans").where("ownerUid", "==", ALICE_UID).get());

  // Alice returns the book
  await assertSucceeds(
    aliceDb.collection("loans").doc("loan_1").update({
      status: "Returned",
      returnDateMs: 1700500000000,
      updatedAt: serverTs(aliceDb),
    })
  );

  // Terminal state lock: non-librarian cannot mutate loan after Returned
  await assertFails(
    aliceDb.collection("loans").doc("loan_1").update({
      status: "Borrowed",
      updatedAt: serverTs(aliceDb),
    })
  );
});
