const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_1";
const ADMIN_PUB_UID = "admin_pub_uid_2";
const ADMIN_2_UID = "admin_uid_3";
const PUB_UID = "pub_uid_4";
const VIEWER_UID = "viewer_uid_5";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowMinus(ms = 1000) {
  return new Date(Date.now() - ms);
}

async function seedStandardUsers() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = nowMinus(10000);
    await db.collection("users").doc(OWNER_UID).set({
      uid: OWNER_UID,
      email: "owner@notflix.dev",
      displayName: "Platform Owner",
      role: "owner",
      isOwner: true,
      status: "active",
      createdAt: ts,
    });
    await db.collection("users").doc(ADMIN_PUB_UID).set({
      uid: ADMIN_PUB_UID,
      email: "adminpub@notflix.dev",
      displayName: "Admin Publisher",
      role: "admin",
      isAdmin: true,
      publisherStatus: "approved",
      status: "active",
      createdAt: ts,
    });
    await db.collection("users").doc(ADMIN_2_UID).set({
      uid: ADMIN_2_UID,
      email: "admin2@notflix.dev",
      displayName: "Second Admin",
      role: "admin",
      isAdmin: true,
      status: "active",
      createdAt: ts,
    });
    await db.collection("users").doc(PUB_UID).set({
      uid: PUB_UID,
      email: "pub@notflix.dev",
      displayName: "Indie Publisher",
      role: "viewer",
      publisherStatus: "approved",
      status: "active",
      createdAt: ts,
    });
    await db.collection("users").doc(VIEWER_UID).set({
      uid: VIEWER_UID,
      email: "viewer@notflix.dev",
      displayName: "Regular Viewer",
      role: "viewer",
      status: "active",
      createdAt: ts,
    });
  });
}

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

test("Unauthenticated users are denied across all collections", async () => {
  await seedStandardUsers();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(OWNER_UID).get());
  await assertFails(unauth.collection("movies").get());
  await assertFails(unauth.collection("access_applications").get());
  await assertFails(unauth.collection("audit_logs").get());
  await assertFails(unauth.collection("categories").get());
  await assertFails(unauth.collection("live_streams").get());
});

test("Viewer cannot escalate own role, list users, or create movies", async () => {
  await seedStandardUsers();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

  await assertSucceeds(viewerDb.collection("users").doc(VIEWER_UID).get());
  await assertSucceeds(
    viewerDb.collection("users").doc(VIEWER_UID).update({
      displayName: "Updated Viewer",
      updatedAt: nowMinus(500),
    })
  );

  // Role escalation attempts must fail
  await assertFails(
    viewerDb.collection("users").doc(VIEWER_UID).update({ role: "admin" })
  );
  await assertFails(
    viewerDb.collection("users").doc(VIEWER_UID).update({ isPublisher: true })
  );
  await assertFails(viewerDb.collection("users").get());
  await assertFails(viewerDb.collection("users").doc(OWNER_UID).get());

  // Creating a privileged user doc directly fails
  await assertFails(
    viewerDb.collection("users").doc("brand_new_uid").set({
      uid: "brand_new_uid",
      email: "x@y.com",
      role: "admin",
      status: "active",
      createdAt: nowMinus(500),
    })
  );

  // Viewer cannot create a movie
  await assertFails(
    viewerDb.collection("movies").doc("mov_v1").set({
      id: "mov_v1",
      title: "Viewer Film",
      contentType: "movie",
      publisherId: VIEWER_UID,
      status: "draft",
      createdAt: nowMinus(500),
      updatedAt: nowMinus(500),
    })
  );
});

test("Only Owner can appoint/remove Admins; Admins cannot appoint Admins or modify Owner", async () => {
  await seedStandardUsers();
  const adminDb = testEnv.authenticatedContext(ADMIN_PUB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  // Admin cannot appoint viewer or publisher as admin
  await assertFails(
    adminDb.collection("users").doc(PUB_UID).update({
      role: "admin",
      isAdmin: true,
      updatedAt: nowMinus(500),
    })
  );
  // Admin cannot modify another Admin
  await assertFails(
    adminDb.collection("users").doc(ADMIN_2_UID).update({
      status: "suspended",
      updatedAt: nowMinus(500),
    })
  );
  // Admin cannot modify Owner
  await assertFails(
    adminDb.collection("users").doc(OWNER_UID).update({
      status: "suspended",
      updatedAt: nowMinus(500),
    })
  );
  // Admin cannot modify own role
  await assertFails(
    adminDb.collection("users").doc(ADMIN_PUB_UID).update({
      role: "owner",
      updatedAt: nowMinus(500),
    })
  );

  // Owner CAN appoint Admin
  await assertSucceeds(
    ownerDb.collection("users").doc(PUB_UID).update({
      role: "admin",
      isAdmin: true,
      isPublisher: true,
      updatedAt: nowMinus(500),
    })
  );
});

test("Publisher can query only own submissions, edit draft/rejected/changes_requested, and cannot self-approve", async () => {
  await seedStandardUsers();
  const created = nowMinus(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("movies").doc("pub_draft").set({
      id: "pub_draft",
      title: "Draft Movie",
      description: "Initial",
      contentType: "movie",
      genre: "Drama",
      releaseYear: 2026,
      publisherId: PUB_UID,
      publisherName: "Indie Publisher",
      status: "rejected",
      feedback: "Needs better synopsis",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("movies").doc("other_pending").set({
      id: "other_pending",
      title: "Other Pending",
      contentType: "movie",
      publisherId: ADMIN_PUB_UID,
      status: "pending",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("movies").doc("already_approved").set({
      id: "already_approved",
      title: "Published Hit",
      contentType: "movie",
      publisherId: PUB_UID,
      status: "approved",
      createdAt: created,
      updatedAt: created,
    });
  });

  const pubDb = testEnv.authenticatedContext(PUB_UID).firestore();

  // Unfiltered list fails for publisher; scoped query succeeds
  await assertFails(pubDb.collection("movies").get());
  await assertSucceeds(pubDb.collection("movies").where("publisherId", "==", PUB_UID).get());

  // Cross-publisher read/edit fails
  await assertFails(pubDb.collection("movies").doc("other_pending").get());
  await assertFails(
    pubDb.collection("movies").doc("other_pending").update({
      title: "Hijack",
      updatedAt: nowMinus(500),
    })
  );

  // Publisher CAN edit & resubmit their own rejected submission (without duplicating ID)
  await assertSucceeds(
    pubDb.collection("movies").doc("pub_draft").update({
      title: "Draft Movie Revised",
      description: "Better synopsis",
      status: "pending",
      updatedAt: nowMinus(500),
    })
  );

  // Publisher CANNOT self-approve on create or update
  await assertFails(
    pubDb.collection("movies").doc("pub_new_hack").set({
      id: "pub_new_hack",
      title: "Self Approved",
      contentType: "movie",
      publisherId: PUB_UID,
      status: "approved",
      createdAt: nowMinus(500),
      updatedAt: nowMinus(500),
    })
  );
  await assertFails(
    pubDb.collection("movies").doc("pub_draft").update({
      status: "approved",
      updatedAt: nowMinus(500),
    })
  );

  // Publisher CANNOT modify an already-approved catalog item
  await assertFails(
    pubDb.collection("movies").doc("already_approved").update({
      title: "Tampered",
      updatedAt: nowMinus(500),
    })
  );
});

test("Admin + Publisher can submit own movie and review others, but CANNOT approve own submission", async () => {
  await seedStandardUsers();
  const created = nowMinus(5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("movies").doc("admin_own_sub").set({
      id: "admin_own_sub",
      title: "Admin Own Movie",
      contentType: "movie",
      publisherId: ADMIN_PUB_UID,
      publisherName: "Admin Publisher",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("movies").doc("pub_sub").set({
      id: "pub_sub",
      title: "Indie Submission",
      contentType: "movie",
      publisherId: PUB_UID,
      publisherName: "Indie Publisher",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    });
  });

  const adminPubDb = testEnv.authenticatedContext(ADMIN_PUB_UID).firestore();
  const secondAdminDb = testEnv.authenticatedContext(ADMIN_2_UID).firestore();

  // Admin+Publisher CAN create new submission under own UID
  await assertSucceeds(
    adminPubDb.collection("movies").doc("admin_new_film").set({
      id: "admin_new_film",
      title: "Director Cut",
      contentType: "movie",
      publisherId: ADMIN_PUB_UID,
      status: "pending",
      createdAt: nowMinus(500),
      updatedAt: nowMinus(500),
    })
  );

  // Admin+Publisher CANNOT approve their own submission!
  await assertFails(
    adminPubDb.collection("movies").doc("admin_own_sub").update({
      status: "approved",
      feedback: "Self approved",
      reviewedBy: ADMIN_PUB_UID,
      updatedAt: nowMinus(500),
    })
  );

  // Admin+Publisher CAN approve another publisher's submission
  await assertSucceeds(
    adminPubDb.collection("movies").doc("pub_sub").update({
      status: "approved",
      feedback: "Approved for catalog",
      reviewedBy: ADMIN_PUB_UID,
      updatedAt: nowMinus(500),
    })
  );

  // Second Admin CAN approve Admin+Publisher's submission
  await assertSucceeds(
    secondAdminDb.collection("movies").doc("admin_own_sub").update({
      status: "approved",
      feedback: "Verified by peer admin",
      reviewedBy: ADMIN_2_UID,
      updatedAt: nowMinus(500),
    })
  );
});

test("Audit logs are append-only and immutable", async () => {
  await seedStandardUsers();
  const adminDb = testEnv.authenticatedContext(ADMIN_PUB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  await assertSucceeds(
    adminDb.collection("audit_logs").doc("log_1").set({
      id: "log_1",
      adminId: ADMIN_PUB_UID,
      adminEmail: "adminpub@notflix.dev",
      action: "REVIEW_CONTENT_APPROVED",
      target: "pub_sub",
      timestamp: nowMinus(500),
    })
  );

  await assertFails(
    ownerDb.collection("audit_logs").doc("log_1").update({ action: "TAMPERED" })
  );
  await assertFails(ownerDb.collection("audit_logs").doc("log_1").delete());
});

test("Access applications enforce applicant ownership and Owner-only Admin approvals", async () => {
  await seedStandardUsers();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_PUB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

  // Viewer can submit own pending application
  await assertSucceeds(
    viewerDb.collection("access_applications").doc("app_pub").set({
      id: "app_pub",
      uid: VIEWER_UID,
      email: "viewer@notflix.dev",
      fullName: "Viewer Creator",
      studioName: "Studio V",
      requestedRole: "publisher",
      reason: "Releasing indie short film",
      status: "pending",
      createdAt: nowMinus(1000),
    })
  );
  await assertSucceeds(
    viewerDb.collection("access_applications").doc("app_adm").set({
      id: "app_adm",
      uid: VIEWER_UID,
      email: "viewer@notflix.dev",
      fullName: "Viewer AdminReq",
      studioName: "Ops",
      requestedRole: "admin",
      reason: "Ops help",
      status: "pending",
      createdAt: nowMinus(1000),
    })
  );

  // Viewer cannot self-approve
  await assertFails(
    viewerDb.collection("access_applications").doc("app_pub").update({
      status: "approved",
      updatedAt: nowMinus(500),
    })
  );

  // Admin can approve publisher application, CANNOT approve admin application
  await assertSucceeds(
    adminDb.collection("access_applications").doc("app_pub").update({
      status: "approved",
      reviewedBy: ADMIN_PUB_UID,
      updatedAt: nowMinus(500),
    })
  );
  await assertFails(
    adminDb.collection("access_applications").doc("app_adm").update({
      status: "approved",
      reviewedBy: ADMIN_PUB_UID,
      updatedAt: nowMinus(500),
    })
  );

  // Owner CAN approve admin application
  await assertSucceeds(
    ownerDb.collection("access_applications").doc("app_adm").update({
      status: "approved",
      reviewedBy: OWNER_UID,
      updatedAt: nowMinus(500),
    })
  );
});

test("Categories and live_streams enforce role and creator ownership bounds", async () => {
  await seedStandardUsers();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const pubDb = testEnv.authenticatedContext(PUB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_PUB_UID).firestore();

  // Categories: Admin can write, Publisher can read only, Viewer denied
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_scifi").set({
      id: "cat_scifi",
      name: "Sci-Fi",
      slug: "sci-fi",
    })
  );
  await assertSucceeds(pubDb.collection("categories").get());
  await assertFails(pubDb.collection("categories").doc("cat_hack").set({ id: "cat_hack", name: "H", slug: "h" }));
  await assertFails(viewerDb.collection("categories").get());

  // Live streams: Publisher can create/update own stream; cannot spoof another creator
  await assertSucceeds(
    pubDb.collection("live_streams").doc("stream_1").set({
      id: "stream_1",
      title: "Premiere Q&A",
      streamUrl: "https://example.com/live.m3u8",
      scheduleTime: "2026-12-01T20:00Z",
      isLive: true,
      creatorId: PUB_UID,
    })
  );
  await assertFails(
    pubDb.collection("live_streams").doc("stream_spoof").set({
      id: "stream_spoof",
      title: "Spoof",
      streamUrl: "https://example.com/live.m3u8",
      creatorId: ADMIN_PUB_UID,
    })
  );
});

