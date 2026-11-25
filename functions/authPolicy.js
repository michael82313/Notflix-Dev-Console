const VALID_ROLES = new Set(["owner", "admin", "publisher", "viewer"]);
const VALID_STATUSES = new Set(["active", "suspended"]);
const VALID_REVIEW_STATUSES = new Set(["approved", "rejected", "changes_requested"]);
const VALID_APP_DECISIONS = new Set(["approved", "rejected"]);

function normalizeStringList(raw) {
  if (Array.isArray(raw)) {
    return raw.map((v) => String(v || "").trim().toLowerCase()).filter(Boolean);
  }
  if (raw && typeof raw === "object") {
    return Object.entries(raw)
      .filter(([, enabled]) => Boolean(enabled))
      .map(([k]) => String(k).trim().toLowerCase());
  }
  return [];
}

function resolveCapabilities(userDoc = {}, claims = {}) {
  const roleStr = String(
    userDoc.role || userDoc.userRole || userDoc.type || userDoc.access || claims.role || claims.access || ""
  ).toLowerCase();

  const rolesList = [
    ...normalizeStringList(userDoc.roles),
    ...normalizeStringList(claims.roles),
  ];

  const accountStatus = String(userDoc.status || userDoc.accountStatus || "active").toLowerCase();
  const publisherStatus = String(userDoc.publisherStatus || claims.publisherStatus || "").toLowerCase();
  const isSuspended = accountStatus === "suspended";

  const isOwner =
    !isSuspended &&
    (Boolean(userDoc.isOwner) ||
      Boolean(userDoc.owner) ||
      Boolean(claims.isOwner) ||
      Boolean(claims.owner) ||
      roleStr.includes("owner") ||
      rolesList.some((r) => r.includes("owner")));

  const isAdmin =
    !isSuspended &&
    (isOwner ||
      Boolean(userDoc.isAdmin) ||
      Boolean(userDoc.admin) ||
      Boolean(claims.isAdmin) ||
      Boolean(claims.admin) ||
      roleStr.includes("admin") ||
      rolesList.some((r) => r.includes("admin")));

  const isPublisher =
    !isSuspended &&
    (isOwner ||
      Boolean(userDoc.isPublisher) ||
      Boolean(userDoc.publisher) ||
      Boolean(userDoc.isDistributor) ||
      Boolean(userDoc.distributor) ||
      Boolean(claims.isPublisher) ||
      Boolean(claims.publisher) ||
      Boolean(claims.isDistributor) ||
      Boolean(claims.distributor) ||
      publisherStatus === "approved" ||
      publisherStatus === "active" ||
      roleStr.includes("publisher") ||
      roleStr.includes("distributor") ||
      roleStr.includes("creator") ||
      rolesList.some(
        (r) => r.includes("publisher") || r.includes("distributor") || r.includes("creator")
      ));

  return {
    isSuspended,
    isOwner,
    isAdmin,
    isPublisher,
    accountStatus,
    publisherStatus,
  };
}

function authorizeSetUserRole({ callerUid, callerCaps, targetUid, targetDoc, targetRole, grantPublisher }) {
  if (!callerUid || typeof callerUid !== "string") {
    return { allowed: false, code: "unauthenticated", reason: "Authentication required." };
  }
  if (callerCaps.isSuspended || (!callerCaps.isOwner && !callerCaps.isAdmin)) {
    return { allowed: false, code: "permission-denied", reason: "Admin or Owner privileges required." };
  }
  if (!targetUid || typeof targetUid !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(targetUid)) {
    return { allowed: false, code: "invalid-argument", reason: "Invalid targetUid." };
  }
  if (targetUid === callerUid) {
    return { allowed: false, code: "permission-denied", reason: "Users cannot modify their own roles." };
  }

  const normalizedRole = String(targetRole || "").trim().toLowerCase();
  if (!VALID_ROLES.has(normalizedRole) || normalizedRole === "owner") {
    return { allowed: false, code: "invalid-argument", reason: "Target role must be admin, publisher, or viewer." };
  }

  const targetCaps = resolveCapabilities(targetDoc || {}, {});
  if (targetCaps.isOwner) {
    return { allowed: false, code: "permission-denied", reason: "Owner account permissions cannot be modified." };
  }

  const involvesAdminChange = normalizedRole === "admin" || targetCaps.isAdmin;
  if (involvesAdminChange && !callerCaps.isOwner) {
    return {
      allowed: false,
      code: "permission-denied",
      reason: "Only the Owner can appoint, modify, or remove Admin accounts.",
    };
  }

  const nextIsAdmin = normalizedRole === "admin";
  const nextIsPublisher =
    typeof grantPublisher === "boolean"
      ? grantPublisher || normalizedRole === "publisher"
      : normalizedRole === "publisher" || (nextIsAdmin && targetCaps.isPublisher);

  const roles = [];
  if (nextIsAdmin) roles.push("admin");
  if (nextIsPublisher) roles.push("publisher");
  if (roles.length === 0) roles.push("viewer");

  const nextPublisherStatus = nextIsPublisher ? "approved" : "none";

  return {
    allowed: true,
    updateFields: {
      role: normalizedRole,
      roles,
      isOwner: false,
      isAdmin: nextIsAdmin,
      isPublisher: nextIsPublisher,
      isDistributor: nextIsPublisher,
      publisherStatus: nextPublisherStatus,
    },
    claimsPatch: {
      role: normalizedRole,
      roles,
      isOwner: false,
      isAdmin: nextIsAdmin,
      isPublisher: nextIsPublisher,
      isDistributor: nextIsPublisher,
      publisherStatus: nextPublisherStatus,
    },
  };
}

function authorizeSetUserStatus({ callerUid, callerCaps, targetUid, targetDoc, newStatus }) {
  if (!callerUid) {
    return { allowed: false, code: "unauthenticated", reason: "Authentication required." };
  }
  if (callerCaps.isSuspended || (!callerCaps.isOwner && !callerCaps.isAdmin)) {
    return { allowed: false, code: "permission-denied", reason: "Admin or Owner privileges required." };
  }
  if (!targetUid || typeof targetUid !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(targetUid)) {
    return { allowed: false, code: "invalid-argument", reason: "Invalid targetUid." };
  }
  if (targetUid === callerUid) {
    return { allowed: false, code: "permission-denied", reason: "You cannot suspend or activate your own account." };
  }

  const normalizedStatus = String(newStatus || "").trim().toLowerCase();
  if (!VALID_STATUSES.has(normalizedStatus)) {
    return { allowed: false, code: "invalid-argument", reason: "Status must be active or suspended." };
  }

  const targetCaps = resolveCapabilities(targetDoc || {}, {});
  if (targetCaps.isOwner) {
    return { allowed: false, code: "permission-denied", reason: "Owner account status cannot be modified." };
  }
  if (targetCaps.isAdmin && !callerCaps.isOwner) {
    return { allowed: false, code: "permission-denied", reason: "Only the Owner can change an Admin account status." };
  }

  return {
    allowed: true,
    status: normalizedStatus,
  };
}

function authorizeContentReview({ callerUid, callerCaps, movieDoc, newStatus, feedback }) {
  if (!callerUid) {
    return { allowed: false, code: "unauthenticated", reason: "Authentication required." };
  }
  if (callerCaps.isSuspended || (!callerCaps.isOwner && !callerCaps.isAdmin)) {
    return { allowed: false, code: "permission-denied", reason: "Only active Admins or Owner can review submissions." };
  }
  if (!movieDoc) {
    return { allowed: false, code: "not-found", reason: "Submission not found." };
  }
  if (movieDoc.publisherId && movieDoc.publisherId === callerUid) {
    return {
      allowed: false,
      code: "permission-denied",
      reason: "Self-approval is strictly prohibited. Another Admin or the Owner must review your submission.",
    };
  }

  const normalizedStatus = String(newStatus || "").trim().toLowerCase();
  if (!VALID_REVIEW_STATUSES.has(normalizedStatus)) {
    return {
      allowed: false,
      code: "invalid-argument",
      reason: "Review status must be approved, rejected, or changes_requested.",
    };
  }

  const safeFeedback = typeof feedback === "string" ? feedback.trim().slice(0, 1000) : "";

  return {
    allowed: true,
    status: normalizedStatus,
    feedback: safeFeedback,
  };
}

function authorizeApplicationReview({ callerUid, callerCaps, appDoc, decision }) {
  if (!callerUid) {
    return { allowed: false, code: "unauthenticated", reason: "Authentication required." };
  }
  if (callerCaps.isSuspended || (!callerCaps.isOwner && !callerCaps.isAdmin)) {
    return { allowed: false, code: "permission-denied", reason: "Only active Admins or Owner can review applications." };
  }
  if (!appDoc) {
    return { allowed: false, code: "not-found", reason: "Access application not found." };
  }
  const normalizedDecision = String(decision || "").trim().toLowerCase();
  if (!VALID_APP_DECISIONS.has(normalizedDecision)) {
    return { allowed: false, code: "invalid-argument", reason: "Decision must be approved or rejected." };
  }

  const requestedRole = String(appDoc.requestedRole || "publisher").trim().toLowerCase();
  if (requestedRole === "owner") {
    return { allowed: false, code: "permission-denied", reason: "Owner role cannot be granted via access applications." };
  }
  if (normalizedDecision === "approved" && requestedRole === "admin" && !callerCaps.isOwner) {
    return { allowed: false, code: "permission-denied", reason: "Only the Owner can approve Admin access applications." };
  }

  return {
    allowed: true,
    decision: normalizedDecision,
    requestedRole: requestedRole === "admin" ? "admin" : "publisher",
  };
}

module.exports = {
  resolveCapabilities,
  authorizeSetUserRole,
  authorizeSetUserStatus,
  authorizeContentReview,
  authorizeApplicationReview,
};
