const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  resolveCapabilities,
  authorizeSetUserRole,
  authorizeSetUserStatus,
  authorizeContentReview,
  authorizeApplicationReview,
} = require("../authPolicy");

test("resolveCapabilities preserves Admin + Publisher dual permissions", () => {
  const caps = resolveCapabilities(
    { role: "admin", isAdmin: true, publisherStatus: "approved", status: "active" },
    {}
  );
  assert.equal(caps.isOwner, false);
  assert.equal(caps.isAdmin, true);
  assert.equal(caps.isPublisher, true);
  assert.equal(caps.isSuspended, false);
});

test("setUserRole blocks non-owner Admins from appointing or removing Admins", () => {
  const adminCaps = resolveCapabilities({ role: "admin", isAdmin: true });
  const promoteToAdmin = authorizeSetUserRole({
    callerUid: "admin_1",
    callerCaps: adminCaps,
    targetUid: "pub_1",
    targetDoc: { role: "publisher", isPublisher: true },
    targetRole: "admin",
  });
  assert.equal(promoteToAdmin.allowed, false);
  assert.equal(promoteToAdmin.code, "permission-denied");

  const demoteAdmin = authorizeSetUserRole({
    callerUid: "admin_1",
    callerCaps: adminCaps,
    targetUid: "admin_2",
    targetDoc: { role: "admin", isAdmin: true },
    targetRole: "publisher",
  });
  assert.equal(demoteAdmin.allowed, false);
  assert.equal(demoteAdmin.code, "permission-denied");
});

test("setUserRole allows Owner to appoint Admin while preserving Publisher inheritance", () => {
  const ownerCaps = resolveCapabilities({ role: "owner", isOwner: true });
  const res = authorizeSetUserRole({
    callerUid: "owner_1",
    callerCaps: ownerCaps,
    targetUid: "pub_1",
    targetDoc: { role: "publisher", isPublisher: true },
    targetRole: "admin",
  });
  assert.equal(res.allowed, true);
  assert.equal(res.updateFields.isAdmin, true);
  assert.equal(res.updateFields.isPublisher, true);
  assert.deepEqual(res.updateFields.roles, ["admin", "publisher"]);
});

test("setUserRole and setUserStatus forbid self-modification", () => {
  const ownerCaps = resolveCapabilities({ role: "owner", isOwner: true });
  const selfRole = authorizeSetUserRole({
    callerUid: "owner_1",
    callerCaps: ownerCaps,
    targetUid: "owner_1",
    targetDoc: { role: "owner", isOwner: true },
    targetRole: "viewer",
  });
  assert.equal(selfRole.allowed, false);

  const selfSuspend = authorizeSetUserStatus({
    callerUid: "owner_1",
    callerCaps: ownerCaps,
    targetUid: "owner_1",
    targetDoc: { role: "owner", isOwner: true },
    newStatus: "suspended",
  });
  assert.equal(selfSuspend.allowed, false);
});

test("reviewContentSubmission strictly blocks Admin+Publisher from approving own submission", () => {
  const adminPubCaps = resolveCapabilities({ role: "admin", isAdmin: true, isPublisher: true });
  const selfApprove = authorizeContentReview({
    callerUid: "admin_pub_1",
    callerCaps: adminPubCaps,
    movieDoc: { publisherId: "admin_pub_1", status: "pending" },
    newStatus: "approved",
    feedback: "LGTM",
  });
  assert.equal(selfApprove.allowed, false);
  assert.equal(selfApprove.code, "permission-denied");

  const approveOther = authorizeContentReview({
    callerUid: "admin_pub_1",
    callerCaps: adminPubCaps,
    movieDoc: { publisherId: "other_pub", status: "pending" },
    newStatus: "approved",
    feedback: "Great film",
  });
  assert.equal(approveOther.allowed, true);
  assert.equal(approveOther.status, "approved");
});

test("reviewAccessApplication blocks non-owner Admins from approving Admin access requests", () => {
  const adminCaps = resolveCapabilities({ role: "admin", isAdmin: true });
  const denied = authorizeApplicationReview({
    callerUid: "admin_1",
    callerCaps: adminCaps,
    appDoc: { uid: "user_9", requestedRole: "admin" },
    decision: "approved",
  });
  assert.equal(denied.allowed, false);
  assert.equal(denied.code, "permission-denied");
});
