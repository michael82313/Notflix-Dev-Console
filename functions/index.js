const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const {
  resolveCapabilities,
  authorizeSetUserRole,
  authorizeSetUserStatus,
  authorizeContentReview,
  authorizeApplicationReview,
} = require("./authPolicy");

initializeApp();

const DATABASE_ID = "ai-studio-android-velora-fa049f50-d743-4c93-8b72-1a2b2b3ed613";
const db = getFirestore(DATABASE_ID);
const auth = getAuth();

async function getCallerCapabilities(uid, tokenClaims) {
  const userSnap = await db.collection("users").doc(uid).get();
  const userDoc = userSnap.exists ? userSnap.data() : {};
  return resolveCapabilities(userDoc, tokenClaims || {});
}

async function writeAuditLog(adminUid, adminEmail, action, target) {
  const ref = db.collection("audit_logs").doc();
  await ref.set({
    id: ref.id,
    adminId: adminUid,
    adminEmail: adminEmail || "",
    action,
    target,
    timestamp: FieldValue.serverTimestamp(),
  });
}

exports.setUserRole = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const { targetUid, targetRole, grantPublisher } = request.data || {};

  const [callerCaps, targetSnap] = await Promise.all([
    getCallerCapabilities(callerUid, request.auth.token),
    targetUid ? db.collection("users").doc(String(targetUid)).get() : Promise.resolve(null),
  ]);

  if (!targetSnap || !targetSnap.exists) {
    throw new HttpsError("not-found", "Target user document not found.");
  }

  const decision = authorizeSetUserRole({
    callerUid,
    callerCaps,
    targetUid: String(targetUid),
    targetDoc: targetSnap.data(),
    targetRole,
    grantPublisher,
  });

  if (!decision.allowed) {
    throw new HttpsError(decision.code, decision.reason);
  }

  const targetUserRecord = await auth.getUser(String(targetUid));
  const existingClaims = targetUserRecord.customClaims || {};
  await auth.setCustomUserClaims(String(targetUid), {
    ...existingClaims,
    ...decision.claimsPatch,
  });

  await db.collection("users").doc(String(targetUid)).set(
    {
      ...decision.updateFields,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await writeAuditLog(
    callerUid,
    callerEmail,
    `SET_ROLE_${decision.updateFields.role.toUpperCase()}${decision.updateFields.isPublisher ? "_PUBLISHER" : ""}`,
    String(targetUid)
  );

  return {
    success: true,
    targetUid: String(targetUid),
    ...decision.updateFields,
  };
});

exports.setUserStatus = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const { targetUid, newStatus } = request.data || {};

  const [callerCaps, targetSnap] = await Promise.all([
    getCallerCapabilities(callerUid, request.auth.token),
    targetUid ? db.collection("users").doc(String(targetUid)).get() : Promise.resolve(null),
  ]);

  if (!targetSnap || !targetSnap.exists) {
    throw new HttpsError("not-found", "Target user document not found.");
  }

  const decision = authorizeSetUserStatus({
    callerUid,
    callerCaps,
    targetUid: String(targetUid),
    targetDoc: targetSnap.data(),
    newStatus,
  });

  if (!decision.allowed) {
    throw new HttpsError(decision.code, decision.reason);
  }

  await db.collection("users").doc(String(targetUid)).set(
    {
      status: decision.status,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await writeAuditLog(
    callerUid,
    callerEmail,
    `SET_STATUS_${decision.status.toUpperCase()}`,
    String(targetUid)
  );

  return {
    success: true,
    targetUid: String(targetUid),
    status: decision.status,
  };
});

exports.reviewContentSubmission = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const { contentId, status, feedback } = request.data || {};

  if (!contentId || typeof contentId !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(contentId)) {
    throw new HttpsError("invalid-argument", "Invalid contentId.");
  }

  const [callerCaps, movieSnap] = await Promise.all([
    getCallerCapabilities(callerUid, request.auth.token),
    db.collection("movies").doc(contentId).get(),
  ]);

  const decision = authorizeContentReview({
    callerUid,
    callerCaps,
    movieDoc: movieSnap.exists ? movieSnap.data() : null,
    newStatus: status,
    feedback,
  });

  if (!decision.allowed) {
    throw new HttpsError(decision.code, decision.reason);
  }

  // Update the existing catalog document in place (prevents duplicate catalog records)
  await db.collection("movies").doc(contentId).update({
    status: decision.status,
    feedback: decision.feedback,
    reviewedBy: callerUid,
    reviewedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  await writeAuditLog(
    callerUid,
    callerEmail,
    `REVIEW_CONTENT_${decision.status.toUpperCase()}`,
    contentId
  );

  return {
    success: true,
    contentId,
    status: decision.status,
  };
});

exports.reviewAccessApplication = onCall({ region: "us-central1" }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const callerUid = request.auth.uid;
  const callerEmail = request.auth.token.email || "";
  const { applicationId, decision } = request.data || {};

  if (!applicationId || typeof applicationId !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(applicationId)) {
    throw new HttpsError("invalid-argument", "Invalid applicationId.");
  }

  const [callerCaps, appSnap] = await Promise.all([
    getCallerCapabilities(callerUid, request.auth.token),
    db.collection("access_applications").doc(applicationId).get(),
  ]);

  const appDoc = appSnap.exists ? appSnap.data() : null;
  const policyResult = authorizeApplicationReview({
    callerUid,
    callerCaps,
    appDoc,
    decision,
  });

  if (!policyResult.allowed) {
    throw new HttpsError(policyResult.code, policyResult.reason);
  }

  await db.collection("access_applications").doc(applicationId).update({
    status: policyResult.decision,
    reviewedBy: callerUid,
    updatedAt: FieldValue.serverTimestamp(),
  });

  if (policyResult.decision === "approved" && appDoc.uid && appDoc.uid !== "anonymous") {
    const targetSnap = await db.collection("users").doc(appDoc.uid).get();
    const targetDoc = targetSnap.exists ? targetSnap.data() : {};
    const targetCaps = resolveCapabilities(targetDoc, {});

    const nextIsOwner = targetCaps.isOwner;
    const nextIsAdmin = nextIsOwner || targetCaps.isAdmin || policyResult.requestedRole === "admin";
    const nextIsPublisher = nextIsOwner || targetCaps.isPublisher || policyResult.requestedRole === "publisher";

    const nextPrimaryRole = nextIsOwner
      ? "owner"
      : nextIsAdmin
      ? "admin"
      : nextIsPublisher
      ? "publisher"
      : "viewer";

    const roles = [];
    if (nextIsOwner) roles.push("owner");
    if (nextIsAdmin) roles.push("admin");
    if (nextIsPublisher) roles.push("publisher");

    try {
      const targetUserRecord = await auth.getUser(appDoc.uid);
      const existingClaims = targetUserRecord.customClaims || {};
      await auth.setCustomUserClaims(appDoc.uid, {
        ...existingClaims,
        role: nextPrimaryRole,
        roles,
        isOwner: nextIsOwner,
        isAdmin: nextIsAdmin,
        isPublisher: nextIsPublisher,
        isDistributor: nextIsPublisher,
        publisherStatus: nextIsPublisher ? "approved" : targetDoc.publisherStatus || "none",
      });
    } catch (err) {
      console.warn("Unable to update Auth custom claims for applicant:", appDoc.uid, err.message);
    }

    await db.collection("users").doc(appDoc.uid).set(
      {
        uid: appDoc.uid,
        email: appDoc.email || targetDoc.email || "",
        displayName: targetDoc.displayName || appDoc.fullName || "Creator",
        role: nextPrimaryRole,
        roles,
        isOwner: nextIsOwner,
        isAdmin: nextIsAdmin,
        isPublisher: nextIsPublisher,
        isDistributor: nextIsPublisher,
        publisherStatus: nextIsPublisher ? "approved" : targetDoc.publisherStatus || "none",
        status: targetDoc.status || "active",
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
  }

  await writeAuditLog(
    callerUid,
    callerEmail,
    `ACCESS_APP_${policyResult.decision.toUpperCase()}`,
    applicationId
  );

  return {
    success: true,
    applicationId,
    status: policyResult.decision,
  };
});
