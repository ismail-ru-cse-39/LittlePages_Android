import { createHash } from 'node:crypto';
import { getAuth } from 'firebase-admin/auth';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { defineSecret } from 'firebase-functions/params';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import {
  bestAutomaticDiscount,
  chooseBestSingleDiscount,
  digestDiscountCode,
  generateDiscountCode,
  normalizeDiscountCode,
  validateCodeRedemption
} from './discount-domain.mjs';
import {
  contentHash,
  MAX_PRODUCTS,
  MAX_PROMOTIONS,
  publicationApprovalBlockers,
  stableStringify,
  validateMinimumClientVersions,
  validatePublicationDraft
} from './publishing-domain.mjs';

initializeApp();

const firestore = getFirestore();
const discountHmacKey = defineSecret('LITTLEPAGES_DISCOUNT_HMAC_KEY');
const FUNCTION_REGION = 'asia-south1';
const MAX_CODE_BATCH = 100;
const ROLE_CAPABILITIES = Object.freeze({
  catalog_editor: new Set(['edit_configuration', 'validate_configuration', 'create_codes']),
  publisher: new Set(['edit_configuration', 'validate_configuration', 'publish_configuration', 'rollback_configuration', 'create_codes', 'publish_codes']),
  administrator: new Set(['edit_configuration', 'validate_configuration', 'approve_configuration', 'publish_configuration', 'rollback_configuration', 'create_codes', 'approve_codes', 'publish_codes', 'manage_staff'])
});

function requireSignedIn(request) {
  if (!request.auth?.uid) throw new HttpsError('unauthenticated', 'Sign in to continue.');
  if (!request.app) throw new HttpsError('failed-precondition', 'A verified app instance is required.');
  return request.auth.uid;
}

function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }

function assertRoleCapability(roleSnapshot, capability) {
  const role = roleSnapshot.data();
  if (!role || role.status !== 'active' || !ROLE_CAPABILITIES[role.role]?.has(capability)) {
    throw new HttpsError('permission-denied', 'This staff role cannot perform that action.');
  }
  return role.role;
}

function requiredString(value, field, maximum = 120) {
  if (typeof value !== 'string' || !value.trim() || value.length > maximum) {
    throw new HttpsError('invalid-argument', `${field} is invalid.`);
  }
  return value.trim();
}

function currentPublicationRevision(snapshot) {
  if (!snapshot.exists) return 0;
  const revision = snapshot.get('publicationRevision');
  const configurationRevision = snapshot.get('configurationRevision');
  const catalogRevision = snapshot.get('catalogRevision');
  if (!Number.isSafeInteger(revision) || revision < 1 || revision >= Number.MAX_SAFE_INTEGER ||
      !Number.isSafeInteger(configurationRevision) || configurationRevision < 1 || configurationRevision >= Number.MAX_SAFE_INTEGER ||
      !Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || catalogRevision >= Number.MAX_SAFE_INTEGER) {
    throw new HttpsError('failed-precondition', 'The current publication manifest is invalid.');
  }
  return revision;
}

function normalizePublicationDraft(input) {
  if (!object(input) || !object(input.configuration) || !Array.isArray(input.products) || !Array.isArray(input.promotions ?? [])) {
    throw new HttpsError('invalid-argument', 'Include configuration, products, and promotions in the draft snapshot.');
  }
  if (input.products.length > MAX_PRODUCTS || (input.promotions ?? []).length > MAX_PROMOTIONS) {
    throw new HttpsError('invalid-argument', 'The draft exceeds the supported catalogue limits.');
  }
  if (!validateMinimumClientVersions(input.minimumClientVersions ?? {})) throw new HttpsError('invalid-argument', 'Minimum client versions are invalid.');
  const snapshot = {
    configuration: structuredClone(input.configuration),
    products: structuredClone(input.products),
    promotions: structuredClone(input.promotions ?? [])
  };
  const minimumClientVersions = structuredClone(input.minimumClientVersions ?? {});
  if (Buffer.byteLength(stableStringify({ snapshot, minimumClientVersions }), 'utf8') > 700 * 1024) {
    throw new HttpsError('invalid-argument', 'The draft exceeds the 700 KiB limit.');
  }
  return { snapshot, minimumClientVersions };
}

function sortedSnapshot(snapshot) {
  return {
    configuration: snapshot.configuration,
    products: [...snapshot.products].sort((left, right) => left.id.localeCompare(right.id)),
    promotions: [...snapshot.promotions].sort((left, right) => left.id.localeCompare(right.id))
  };
}

function fullPublicationSnapshot(snapshot) {
  return { schemaVersion: 1, ...sortedSnapshot(snapshot) };
}

function publicationManifest({ publicationRevision, configurationRevision, catalogRevision, publishedBy, supportedLocales, minimumClientVersions, hash, now }) {
  return {
    schemaVersion: 1,
    publicationRevision,
    configurationRevision,
    catalogRevision,
    publishedAt: new Date(now).toISOString(),
    publishedBy,
    supportedLocales,
    contentHash: hash,
    minimumClientVersions
  };
}

function validSchedule(schedule) {
  if (typeof schedule?.startsAt !== 'string' || typeof schedule?.endsAt !== 'string') {
    throw new HttpsError('invalid-argument', 'The schedule needs ISO date-time values.');
  }
  const start = Date.parse(schedule?.startsAt);
  const end = Date.parse(schedule?.endsAt);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
    throw new HttpsError('invalid-argument', 'The schedule needs a valid start and later end time.');
  }
  return { startsAt: new Date(start).toISOString(), endsAt: new Date(end).toISOString() };
}

function validateDiscountDefinition(input) {
  const targeting = input?.targeting;
  if (!targeting || !['all', 'product', 'categories'].includes(targeting.kind)) {
    throw new HttpsError('invalid-argument', 'Choose a valid product, category, or all-products target.');
  }
  if (targeting.kind === 'product' && (typeof targeting.productId !== 'string' || !/^[a-z0-9_-]{2,60}$/.test(targeting.productId))) {
    throw new HttpsError('invalid-argument', 'A product target is required.');
  }
  if (targeting.kind === 'categories' && (!Array.isArray(targeting.categoryIds) || !targeting.categoryIds.length || targeting.categoryIds.length > 100 || targeting.categoryIds.some(id => typeof id !== 'string' || !/^[a-z0-9_-]{2,60}$/.test(id)) || new Set(targeting.categoryIds).size !== targeting.categoryIds.length)) {
    throw new HttpsError('invalid-argument', 'At least one category target is required.');
  }
  const targetingKeys = Object.keys(targeting).sort().join(',');
  const expectedTargetingKeys = targeting.kind === 'all' ? 'kind' : targeting.kind === 'product' ? 'kind,productId' : 'categoryIds,kind';
  if (targetingKeys !== expectedTargetingKeys) throw new HttpsError('invalid-argument', 'The discount target contains unsupported fields.');
  const discount = input.discount;
  if (!discount || !Number.isSafeInteger(discount.value) || discount.value < 1 ||
      !(discount.type === 'percentage' && discount.value <= 99 || discount.type === 'fixed_amount' && discount.currency === 'BDT')) {
    throw new HttpsError('invalid-argument', 'Use a valid percentage or fixed BDT discount; discounts cannot make an item free.');
  }
  const discountKeys = Object.keys(discount).sort().join(',');
  const expectedDiscountKeys = discount.type === 'percentage' ? 'type,value' : 'currency,type,value';
  if (discountKeys !== expectedDiscountKeys) throw new HttpsError('invalid-argument', 'The discount contains unsupported fields.');
  const eligibility = input.eligibility ?? 'any';
  const assignedCustomerIds = input.assignedCustomerIds ?? [];
  if (!['any', 'new_customer', 'assigned_account'].includes(eligibility) ||
      !Array.isArray(assignedCustomerIds) || assignedCustomerIds.length > 10000 ||
      assignedCustomerIds.some(id => typeof id !== 'string' || !id.trim() || id.length > 128) ||
      new Set(assignedCustomerIds).size !== assignedCustomerIds.length ||
      !Number.isSafeInteger(input.perCustomerLimit ?? 1) || (input.perCustomerLimit ?? 1) < 1 ||
      input.totalRedemptionLimit != null && (!Number.isSafeInteger(input.totalRedemptionLimit) || input.totalRedemptionLimit < 1)) {
    throw new HttpsError('invalid-argument', 'Eligibility and redemption limits are invalid.');
  }
  return {
    targeting: structuredClone(targeting),
    discount: structuredClone(discount),
    schedule: validSchedule(input.schedule),
    eligibility,
    assignedCustomerIds,
    perCustomerLimit: input.perCustomerLimit ?? 1,
    totalRedemptionLimit: input.totalRedemptionLimit ?? null
  };
}

function auditRecord({ actorUid, action, targetId, requestId, details = {} }) {
  return {
    actorUid,
    action,
    targetId,
    requestId: requestId ?? null,
    createdAt: FieldValue.serverTimestamp(),
    details
  };
}

export const setStaffRole = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const actorUid = requireSignedIn(request);
    const targetUid = requiredString(request.data?.targetUid, 'targetUid', 128);
    const role = request.data?.role;
    const status = request.data?.status;
    const reason = requiredString(request.data?.reason, 'reason', 500);
    if (targetUid.includes('/') || !['catalog_editor', 'publisher', 'administrator'].includes(role) || !['active', 'disabled'].includes(status)) {
      throw new HttpsError('invalid-argument', 'Choose a valid staff account, role, and active/disabled status.');
    }
    const actorRef = firestore.collection('adminRoles').doc(actorUid);
    assertRoleCapability(await actorRef.get(), 'manage_staff');
    try { await getAuth().getUser(targetUid); }
    catch { throw new HttpsError('not-found', 'The target Firebase Auth account does not exist.'); }

    const targetRef = firestore.collection('adminRoles').doc(targetUid);
    const activeAdminsQuery = firestore.collection('adminRoles').where('role', '==', 'administrator');
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const result = await firestore.runTransaction(async transaction => {
      const [actorSnapshot, targetSnapshot, administratorsSnapshot] = await Promise.all([
        transaction.get(actorRef), transaction.get(targetRef), transaction.get(activeAdminsQuery)
      ]);
      assertRoleCapability(actorSnapshot, 'manage_staff');
      const removingActiveAdministrator = targetSnapshot.exists && targetSnapshot.get('role') === 'administrator' &&
        targetSnapshot.get('status') === 'active' && (role !== 'administrator' || status !== 'active');
      if (removingActiveAdministrator && administratorsSnapshot.docs.filter(snapshot => snapshot.get('status') === 'active').length <= 1) {
        throw new HttpsError('failed-precondition', 'The last active administrator cannot be demoted or disabled.');
      }
      transaction.set(targetRef, {
        role,
        status,
        updatedBy: actorUid,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: targetSnapshot.get('createdAt') ?? FieldValue.serverTimestamp()
      }, { merge: true });
      transaction.create(auditRef, auditRecord({
        actorUid, action: 'set_staff_role', targetId: targetUid,
        requestId: request.rawRequest?.headers?.['x-cloud-trace-context'],
        details: { role, status, previousRole: targetSnapshot.get('role') ?? null, previousStatus: targetSnapshot.get('status') ?? null, reason }
      }));
      return { targetUid, role, status };
    });
    return result;
  }
);

function customerDiscountSummary(appliedDiscount) {
  if (!appliedDiscount) return null;
  if (appliedDiscount.kind === 'code') {
    return { kind: 'code', campaignId: appliedDiscount.campaignId, discountMinor: appliedDiscount.discountMinor };
  }
  return { kind: 'automatic', promotionId: appliedDiscount.promotionId, discountMinor: appliedDiscount.discountMinor };
}

export const saveConfigurationDraft = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const payload = request.data ?? {};
    const basePublicationRevision = payload.basePublicationRevision;
    if (!Number.isSafeInteger(basePublicationRevision) || basePublicationRevision < 0) throw new HttpsError('invalid-argument', 'basePublicationRevision must be a nonnegative integer.');
    const normalized = normalizePublicationDraft(payload.snapshot);
    normalized.snapshot.configuration.status = 'provisional';
    const draftRef = payload.draftId == null
      ? firestore.collection('configurationDrafts').doc()
      : firestore.collection('configurationDrafts').doc(requiredString(payload.draftId, 'draftId', 80));
    if (!/^[A-Za-z0-9_-]{2,80}$/.test(draftRef.id)) throw new HttpsError('invalid-argument', 'draftId has an invalid format.');
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const approvalRef = firestore.collection('configurationApprovals').doc(draftRef.id);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const expectedDraftRevision = payload.expectedDraftRevision;
    const savedRevision = await firestore.runTransaction(async transaction => {
      const [roleSnapshot, manifestSnapshot, draftSnapshot, approvalSnapshot] = await Promise.all([
        transaction.get(roleRef), transaction.get(manifestRef), transaction.get(draftRef), transaction.get(approvalRef)
      ]);
      assertRoleCapability(roleSnapshot, 'edit_configuration');
      const currentRevision = currentPublicationRevision(manifestSnapshot);
      if (basePublicationRevision !== currentRevision) throw new HttpsError('failed-precondition', 'The publication changed. Refresh and rebase this draft before saving.');
      if (draftSnapshot.exists) {
        if (draftSnapshot.get('status') !== 'draft' || !Number.isSafeInteger(expectedDraftRevision) || expectedDraftRevision !== draftSnapshot.get('draftRevision')) {
          throw new HttpsError('aborted', 'This draft changed or is no longer editable. Reload it before saving.');
        }
        if (draftSnapshot.get('basePublicationRevision') !== basePublicationRevision) throw new HttpsError('failed-precondition', 'The draft base revision cannot be changed in place.');
      } else if (expectedDraftRevision != null) {
        throw new HttpsError('not-found', 'The draft does not exist.');
      }
      const nextDraftRevision = (draftSnapshot.get('draftRevision') ?? 0) + 1;
      const record = {
        id: draftRef.id,
        status: 'draft',
        basePublicationRevision,
        draftRevision: nextDraftRevision,
        snapshot: normalized.snapshot,
        minimumClientVersions: normalized.minimumClientVersions,
        createdBy: draftSnapshot.get('createdBy') ?? uid,
        createdAt: draftSnapshot.get('createdAt') ?? FieldValue.serverTimestamp(),
        updatedBy: uid,
        updatedAt: FieldValue.serverTimestamp()
      };
      if (draftSnapshot.exists) transaction.update(draftRef, record);
      else transaction.create(draftRef, record);
      if (approvalSnapshot.exists) transaction.delete(approvalRef);
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'save_configuration_draft', targetId: draftRef.id, details: { basePublicationRevision, draftRevision: nextDraftRevision } }));
      return nextDraftRevision;
    });
    return { draftId: draftRef.id, status: 'draft', basePublicationRevision, draftRevision: savedRevision };
  }
);

export const validateConfigurationDraft = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const roleSnapshot = await firestore.collection('adminRoles').doc(uid).get();
    assertRoleCapability(roleSnapshot, 'validate_configuration');
    const draftId = requiredString(request.data?.draftId, 'draftId', 80);
    const draftSnapshot = await firestore.collection('configurationDrafts').doc(draftId).get();
    if (!draftSnapshot.exists) throw new HttpsError('not-found', 'The configuration draft does not exist.');
    const draft = draftSnapshot.data();
    const errors = validatePublicationDraft({ ...draft.snapshot, minimumClientVersions: draft.minimumClientVersions });
    const approvalBlockers = publicationApprovalBlockers({ ...draft.snapshot.configuration, status: 'approved' });
    return { draftId, draftRevision: draft.draftRevision, valid: errors.length === 0, errors, approvalBlockers };
  }
);

export const getConfigurationDraftPreview = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const roleSnapshot = await firestore.collection('adminRoles').doc(uid).get();
    assertRoleCapability(roleSnapshot, 'validate_configuration');
    const draftId = requiredString(request.data?.draftId, 'draftId', 80);
    const draftSnapshot = await firestore.collection('configurationDrafts').doc(draftId).get();
    if (!draftSnapshot.exists) throw new HttpsError('not-found', 'The configuration draft does not exist.');
    const draft = draftSnapshot.data();
    const validationErrors = validatePublicationDraft({ ...draft.snapshot, minimumClientVersions: draft.minimumClientVersions });
    return {
      draftId,
      draftRevision: draft.draftRevision,
      basePublicationRevision: draft.basePublicationRevision,
      status: draft.status,
      validationErrors,
      snapshot: draft.snapshot,
      minimumClientVersions: draft.minimumClientVersions ?? {}
    };
  }
);

export const approveConfigurationDraft = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const draftId = requiredString(request.data?.draftId, 'draftId', 80);
    const reason = requiredString(request.data?.reason, 'reason', 500);
    const draftRef = firestore.collection('configurationDrafts').doc(draftId);
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const approvalRef = firestore.collection('configurationApprovals').doc(draftId);
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const approvalHash = await firestore.runTransaction(async transaction => {
      const [roleSnapshot, manifestSnapshot, draftSnapshot, approvalSnapshot] = await Promise.all([
        transaction.get(roleRef), transaction.get(manifestRef), transaction.get(draftRef), transaction.get(approvalRef)
      ]);
      assertRoleCapability(roleSnapshot, 'approve_configuration');
      if (!draftSnapshot.exists || draftSnapshot.get('status') !== 'draft') throw new HttpsError('not-found', 'An editable configuration draft is required.');
      const draft = draftSnapshot.data();
      if (draft.basePublicationRevision !== currentPublicationRevision(manifestSnapshot)) throw new HttpsError('failed-precondition', 'The publication changed. Rebase and validate the draft again.');
      if (approvalSnapshot.exists) throw new HttpsError('already-exists', 'This draft already has an approval record.');
      const snapshot = structuredClone(draft.snapshot);
      snapshot.configuration.status = 'approved';
      const errors = validatePublicationDraft({ ...snapshot, minimumClientVersions: draft.minimumClientVersions });
      const blockers = publicationApprovalBlockers(snapshot.configuration);
      if (errors.length || blockers.length) throw new HttpsError('failed-precondition', 'The draft cannot be approved until validation and owner review are complete.', { errors, blockers });
      const hash = contentHash(fullPublicationSnapshot(snapshot));
      const nextRevision = draft.draftRevision + 1;
      transaction.update(draftRef, { snapshot, draftRevision: nextRevision, approvalStatus: 'approved', updatedBy: uid, updatedAt: FieldValue.serverTimestamp() });
      transaction.create(approvalRef, { draftId, draftRevision: nextRevision, basePublicationRevision: draft.basePublicationRevision, contentHash: hash, approvedBy: uid, reason, approvedAt: FieldValue.serverTimestamp() });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'approve_configuration_draft', targetId: draftId, details: { draftRevision: nextRevision, basePublicationRevision: draft.basePublicationRevision, contentHash: hash, reason } }));
      return hash;
    });
    return { draftId, status: 'approved', contentHash: approvalHash };
  }
);

export const publishConfigurationDraft = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 3 },
  async request => {
    const uid = requireSignedIn(request);
    const draftId = requiredString(request.data?.draftId, 'draftId', 80);
    const reason = requiredString(request.data?.reason, 'reason', 500);
    const expectedDraftRevision = request.data?.expectedDraftRevision;
    if (!Number.isSafeInteger(expectedDraftRevision) || expectedDraftRevision < 1) throw new HttpsError('invalid-argument', 'expectedDraftRevision must be a positive integer.');
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const draftRef = firestore.collection('configurationDrafts').doc(draftId);
    const approvalRef = firestore.collection('configurationApprovals').doc(draftId);
    const adminAuditRef = firestore.collection('adminAuditEvents').doc();
    const now = Date.now();
    const published = await firestore.runTransaction(async transaction => {
      const [roleSnapshot, manifestSnapshot, draftSnapshot, approvalSnapshot] = await Promise.all([
        transaction.get(roleRef), transaction.get(manifestRef), transaction.get(draftRef), transaction.get(approvalRef)
      ]);
      assertRoleCapability(roleSnapshot, 'publish_configuration');
      if (!draftSnapshot.exists || draftSnapshot.get('status') !== 'draft' || !approvalSnapshot.exists) throw new HttpsError('failed-precondition', 'An approved configuration draft is required.');
      const currentRevision = currentPublicationRevision(manifestSnapshot);
      const draft = draftSnapshot.data();
      if (draft.draftRevision !== expectedDraftRevision || draft.basePublicationRevision !== currentRevision) throw new HttpsError('aborted', 'The draft or publication changed. Reload and revalidate before publishing.');
      const snapshot = draft.snapshot;
      const approval = approvalSnapshot.data();
      if (approval.draftRevision !== draft.draftRevision || approval.basePublicationRevision !== currentRevision || approval.contentHash !== contentHash(fullPublicationSnapshot(snapshot))) throw new HttpsError('failed-precondition', 'The approval no longer matches this draft. Reapproval is required.');
      const errors = validatePublicationDraft({ ...snapshot, minimumClientVersions: draft.minimumClientVersions });
      const blockers = publicationApprovalBlockers(snapshot.configuration);
      if (errors.length || blockers.length) throw new HttpsError('failed-precondition', 'The approved draft no longer passes publication validation.', { errors, blockers });
      if (!validateMinimumClientVersions(draft.minimumClientVersions)) throw new HttpsError('failed-precondition', 'Minimum client versions are invalid.');

      const configurationRevision = (manifestSnapshot.get('configurationRevision') ?? 0) + 1;
      const catalogRevision = (manifestSnapshot.get('catalogRevision') ?? 0) + 1;
      const publicationRevision = currentRevision + 1;
      const configuration = { ...snapshot.configuration, revision: configurationRevision, status: 'approved', updatedAt: new Date(now).toISOString() };
      const publishedSnapshot = fullPublicationSnapshot({
        ...snapshot,
        configuration,
        products: snapshot.products.filter(product => product.status === 'published'),
        promotions: snapshot.promotions.filter(promotion => ['active', 'scheduled'].includes(promotion.status))
      });
      const hash = contentHash(publishedSnapshot);
      const configurationRef = firestore.collection('configurationRevisions').doc(String(configurationRevision));
      const catalogRef = firestore.collection('catalogRevisions').doc(String(catalogRevision));
      const publicationAuditRef = firestore.collection('publicationAudit').doc(String(publicationRevision));
      const [configurationExisting, catalogExisting, auditExisting] = await Promise.all([
        transaction.get(configurationRef), transaction.get(catalogRef), transaction.get(publicationAuditRef)
      ]);
      if (configurationExisting.exists || catalogExisting.exists || auditExisting.exists) throw new HttpsError('already-exists', 'A publication revision already exists; refresh and retry.');

      const manifest = publicationManifest({
        publicationRevision, configurationRevision, catalogRevision, publishedBy: uid,
        supportedLocales: configuration.catalog.supportedLocales,
        minimumClientVersions: draft.minimumClientVersions,
        hash, now
      });
      transaction.create(configurationRef, { configuration });
      transaction.create(catalogRef, { schemaVersion: 1, revision: catalogRevision, productCount: publishedSnapshot.products.length, promotionCount: publishedSnapshot.promotions.length, contentHash: hash, publishedAt: manifest.publishedAt });
      publishedSnapshot.products.forEach(product => transaction.create(catalogRef.collection('products').doc(product.id), product));
      publishedSnapshot.promotions.forEach(promotion => transaction.create(catalogRef.collection('promotions').doc(promotion.id), promotion));
      transaction.create(publicationAuditRef, { action: 'publish', publicationRevision, previousPublicationRevision: currentRevision || null, configurationRevision, catalogRevision, basePublicationRevision: draft.basePublicationRevision, publishedBy: uid, publishedAt: manifest.publishedAt, contentHash: hash, supportedLocales: manifest.supportedLocales, minimumClientVersions: manifest.minimumClientVersions, draftId, reason, productCount: publishedSnapshot.products.length, promotionCount: publishedSnapshot.promotions.length });
      transaction.create(adminAuditRef, auditRecord({ actorUid: uid, action: 'publish_configuration', targetId: String(publicationRevision), details: { draftId, previousPublicationRevision: currentRevision || null, configurationRevision, catalogRevision, contentHash: hash, reason } }));
      if (manifestSnapshot.exists) transaction.update(manifestRef, manifest);
      else transaction.create(manifestRef, manifest);
      transaction.update(draftRef, { status: 'published', publishedRevision: publicationRevision, publishedAt: FieldValue.serverTimestamp(), publishedBy: uid });
      return manifest;
    });
    return { status: 'published', ...published };
  }
);

export const rollbackConfigurationPublication = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 3 },
  async request => {
    const uid = requireSignedIn(request);
    const targetPublicationRevision = request.data?.targetPublicationRevision;
    const reason = requiredString(request.data?.reason, 'reason', 500);
    if (!Number.isSafeInteger(targetPublicationRevision) || targetPublicationRevision < 1) throw new HttpsError('invalid-argument', 'targetPublicationRevision must be a positive integer.');
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const targetAuditRef = firestore.collection('publicationAudit').doc(String(targetPublicationRevision));
    const adminAuditRef = firestore.collection('adminAuditEvents').doc();
    const now = Date.now();
    const rolledBack = await firestore.runTransaction(async transaction => {
      const [roleSnapshot, manifestSnapshot, targetAuditSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(manifestRef), transaction.get(targetAuditRef)]);
      assertRoleCapability(roleSnapshot, 'rollback_configuration');
      if (!manifestSnapshot.exists || !targetAuditSnapshot.exists) throw new HttpsError('not-found', 'The current publication or target revision does not exist.');
      const currentRevision = currentPublicationRevision(manifestSnapshot);
      if (targetPublicationRevision >= currentRevision) throw new HttpsError('invalid-argument', 'Choose an earlier known-good publication revision.');
      const target = targetAuditSnapshot.data();
      const configurationRevision = target.configurationRevision;
      const catalogRevision = target.catalogRevision;
      if (!Number.isSafeInteger(configurationRevision) || !Number.isSafeInteger(catalogRevision)) throw new HttpsError('failed-precondition', 'The target audit record has invalid revision pointers.');
      const configurationRef = firestore.collection('configurationRevisions').doc(String(configurationRevision));
      const catalogRef = firestore.collection('catalogRevisions').doc(String(catalogRevision));
      const [configurationSnapshot, catalogSnapshot, productSnapshots, promotionSnapshots] = await Promise.all([
        transaction.get(configurationRef), transaction.get(catalogRef),
        transaction.get(catalogRef.collection('products').limit(MAX_PRODUCTS + 1)),
        transaction.get(catalogRef.collection('promotions').limit(MAX_PROMOTIONS + 1))
      ]);
      if (!configurationSnapshot.exists || !catalogSnapshot.exists || productSnapshots.size > MAX_PRODUCTS || promotionSnapshots.size > MAX_PROMOTIONS) throw new HttpsError('failed-precondition', 'The target snapshot is incomplete or exceeds supported bounds.');
      const configuration = configurationSnapshot.get('configuration');
      const products = productSnapshots.docs.map(doc => {
        const product = doc.data();
        if (product.id != null && product.id !== doc.id) throw new HttpsError('failed-precondition', 'A target product ID does not match its immutable document path.');
        return product.id ? product : { ...product, id: doc.id };
      });
      const promotions = promotionSnapshots.docs.map(doc => {
        const promotion = doc.data();
        if (promotion.id != null && promotion.id !== doc.id) throw new HttpsError('failed-precondition', 'A target promotion ID does not match its immutable document path.');
        return promotion.id ? promotion : { ...promotion, id: doc.id };
      });
      const catalog = catalogSnapshot.data();
      if (catalog.productCount !== products.length || catalog.promotionCount !== promotions.length ||
          !validateMinimumClientVersions(target.minimumClientVersions) ||
          !Array.isArray(target.supportedLocales) || !target.supportedLocales.length ||
          stableStringify(target.supportedLocales) !== stableStringify(configuration?.catalog?.supportedLocales)) {
        throw new HttpsError('failed-precondition', 'The target publication metadata is incomplete or inconsistent.');
      }
      const snapshot = fullPublicationSnapshot({ configuration, products, promotions });
      const hash = contentHash(snapshot);
      if (hash !== target.contentHash || hash !== catalogSnapshot.get('contentHash')) throw new HttpsError('failed-precondition', 'The target snapshot hash does not match its immutable audit record.');
      const validationErrors = validatePublicationDraft({ ...snapshot, minimumClientVersions: target.minimumClientVersions });
      const blockers = publicationApprovalBlockers(configuration);
      if (validationErrors.length || blockers.length) throw new HttpsError('failed-precondition', 'The target snapshot no longer passes validation.', { errors: validationErrors, blockers });
      const publicationRevision = currentRevision + 1;
      const rollbackAuditRef = firestore.collection('publicationAudit').doc(String(publicationRevision));
      const rollbackAuditExisting = await transaction.get(rollbackAuditRef);
      if (rollbackAuditExisting.exists) throw new HttpsError('already-exists', 'The next publication revision already exists.');
      const manifest = publicationManifest({
        publicationRevision, configurationRevision, catalogRevision, publishedBy: uid,
        supportedLocales: target.supportedLocales,
        minimumClientVersions: target.minimumClientVersions,
        hash, now
      });
      transaction.create(rollbackAuditRef, { action: 'rollback', publicationRevision, previousPublicationRevision: currentRevision, rollbackOfPublicationRevision: targetPublicationRevision, configurationRevision, catalogRevision, publishedBy: uid, publishedAt: manifest.publishedAt, contentHash: hash, supportedLocales: manifest.supportedLocales, minimumClientVersions: manifest.minimumClientVersions, reason });
      transaction.create(adminAuditRef, auditRecord({ actorUid: uid, action: 'rollback_configuration', targetId: String(publicationRevision), details: { rollbackOfPublicationRevision: targetPublicationRevision, configurationRevision, catalogRevision, contentHash: hash, reason } }));
      transaction.update(manifestRef, manifest);
      return manifest;
    });
    return { status: 'rolled_back', ...rolledBack };
  }
);

export const createDiscountCodes = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, secrets: [discountHmacKey], maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const payload = request.data ?? {};
    const campaignId = requiredString(payload.campaignId, 'campaignId', 80);
    if (!/^[a-z0-9_-]{2,80}$/.test(campaignId)) throw new HttpsError('invalid-argument', 'campaignId has an invalid format.');
    const count = payload.mode === 'shared' ? 1 : payload.count;
    if (!['shared', 'unique'].includes(payload.mode) || !Number.isSafeInteger(count) || count < 1 || count > MAX_CODE_BATCH) {
      throw new HttpsError('invalid-argument', `Generate between 1 and ${MAX_CODE_BATCH} codes.`);
    }
    const definition = validateDiscountDefinition(payload);
    if (definition.eligibility === 'assigned_account' && !definition.assignedCustomerIds.length) {
      throw new HttpsError('invalid-argument', 'Assigned-account codes need at least one customer ID.');
    }
    if (!['any', 'new_customer', 'assigned_account'].includes(definition.eligibility) ||
        !Number.isSafeInteger(definition.perCustomerLimit) || definition.perCustomerLimit < 1 ||
        definition.totalRedemptionLimit !== null && (!Number.isSafeInteger(definition.totalRedemptionLimit) || definition.totalRedemptionLimit < 1)) {
      throw new HttpsError('invalid-argument', 'Eligibility and redemption limits are invalid.');
    }
    const hmacSecret = discountHmacKey.value();
    if (!hmacSecret || hmacSecret.length < 32) throw new HttpsError('failed-precondition', 'The server discount key is not configured.');

    const campaignRef = firestore.collection('discountCodeCampaigns').doc(campaignId);
    const codes = Array.from({ length: count }, () => generateDiscountCode());
    const records = codes.map(code => {
      const codeHash = digestDiscountCode(code, hmacSecret);
      return {
        code,
        codeHash,
        ref: firestore.collection('discountCodes').doc(codeHash),
        record: {
          id: codeHash,
          campaignId,
          codeHash,
          mode: payload.mode,
          status: 'draft',
          ...definition,
          totalRedemptionLimit: payload.mode === 'unique' ? 1 : definition.totalRedemptionLimit,
          stackingPolicy: 'best_single_discount',
          priceFloorMinor: 1,
          createdAt: FieldValue.serverTimestamp(),
          createdBy: uid
        }
      };
    });
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    await firestore.runTransaction(async transaction => {
      const manifestSnapshot = await transaction.get(manifestRef);
      if (!manifestSnapshot.exists) throw new HttpsError('failed-precondition', 'A published catalogue is required before creating a discount campaign.');
      const catalogRevision = manifestSnapshot.get('catalogRevision');
      const configurationRevision = manifestSnapshot.get('configurationRevision');
      if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) {
        throw new HttpsError('failed-precondition', 'The published manifest has invalid catalogue pointers.');
      }
      const configurationRef = firestore.collection('configurationRevisions').doc(String(configurationRevision));
      const productRef = definition.targeting.kind === 'product'
        ? firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('products').doc(definition.targeting.productId)
        : null;
      const [roleSnapshot, campaignSnapshot, codeSnapshots, configurationSnapshot, productSnapshot] = await Promise.all([
        transaction.get(roleRef), transaction.get(campaignRef), transaction.getAll(...records.map(record => record.ref)),
        transaction.get(configurationRef), productRef ? transaction.get(productRef) : Promise.resolve(null)
      ]);
      assertRoleCapability(roleSnapshot, 'create_codes');
      if (campaignSnapshot.exists) throw new HttpsError('already-exists', 'That campaign ID already exists.');
      if (codeSnapshots.some(snapshot => snapshot.exists)) throw new HttpsError('aborted', 'A generated code collided. Retry the request.');
      if (!configurationSnapshot.exists) throw new HttpsError('failed-precondition', 'The published configuration revision is missing.');
      if (definition.targeting.kind === 'product' && (!productSnapshot?.exists || productSnapshot.get('status') !== 'published')) {
        throw new HttpsError('invalid-argument', 'Choose a product from the current published catalogue.');
      }
      if (definition.targeting.kind === 'categories') {
        const configuration = configurationSnapshot.get('configuration');
        const categories = configuration?.catalog?.productCategories;
        const stores = configuration?.catalog?.storefronts;
        if (!Array.isArray(categories) || !Array.isArray(stores) || definition.targeting.categoryIds.some(categoryId => {
          const category = categories.find(item => item?.id === categoryId);
          return !category?.enabled || !stores.some(store => store?.id === category.storefrontId && store.enabled);
        })) throw new HttpsError('invalid-argument', 'Choose enabled categories from the current published configuration.');
      }
      transaction.create(campaignRef, {
        id: campaignId,
        status: 'draft',
        mode: payload.mode,
        codeCount: count,
        targeting: definition.targeting,
        discount: definition.discount,
        schedule: definition.schedule,
        eligibility: definition.eligibility,
        perCustomerLimit: definition.perCustomerLimit,
        totalRedemptionLimit: definition.totalRedemptionLimit,
        redemptionCount: 0,
        reservedRedemptionCount: 0,
        createdAt: FieldValue.serverTimestamp(),
        createdBy: uid
      });
      records.forEach(({ ref, record }) => transaction.create(ref, record));
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'create_discount_codes', targetId: campaignId, requestId: request.rawRequest?.headers?.['x-cloud-trace-context'], details: { mode: payload.mode, count, catalogRevision } }));
    });
    return { campaignId, status: 'draft', codes: codes.map((code, index) => ({ id: records[index].record.id, code })) };
  }
);

export const publishDiscountCampaign = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const campaignId = requiredString(request.data?.campaignId, 'campaignId', 80);
    const campaignRef = firestore.collection('discountCodeCampaigns').doc(campaignId);
    const codesQuery = firestore.collection('discountCodes').where('campaignId', '==', campaignId).limit(MAX_CODE_BATCH + 1);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const now = Date.now();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, campaignSnapshot, codesSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(campaignRef), transaction.get(codesQuery)]);
      assertRoleCapability(roleSnapshot, 'publish_codes');
      if (!campaignSnapshot.exists || !codesSnapshot.size) throw new HttpsError('not-found', 'The discount campaign does not exist.');
      const campaign = campaignSnapshot.data();
      if (codesSnapshot.size > MAX_CODE_BATCH || codesSnapshot.size !== campaign.codeCount) throw new HttpsError('failed-precondition', 'Campaign code records do not match the saved batch size.');
      if (!['draft', 'paused'].includes(campaign.status)) throw new HttpsError('failed-precondition', 'Only a draft or paused campaign can be published.');
      if (!campaign.approvedAt || !campaign.approvedBy) throw new HttpsError('failed-precondition', 'An administrator must approve this campaign before publication.');
      const startsAt = Date.parse(campaign.schedule?.startsAt);
      const endsAt = Date.parse(campaign.schedule?.endsAt);
      if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt) || endsAt <= startsAt || endsAt <= now) {
        throw new HttpsError('failed-precondition', 'The campaign schedule is invalid or already expired.');
      }
      const status = startsAt > now ? 'scheduled' : 'active';
      transaction.update(campaignRef, { status, updatedAt: FieldValue.serverTimestamp(), updatedBy: uid });
      codesSnapshot.docs.forEach(snapshot => transaction.update(snapshot.ref, { status }));
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'publish_discount_campaign', targetId: campaignId, requestId: request.rawRequest?.headers?.['x-cloud-trace-context'], details: { status, codeCount: codesSnapshot.size } }));
    });
    return { campaignId, status: Date.parse((await campaignRef.get()).data().schedule.startsAt) > now ? 'scheduled' : 'active' };
  }
);

export const approveDiscountCampaign = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const campaignId = requiredString(request.data?.campaignId, 'campaignId', 80);
    const campaignRef = firestore.collection('discountCodeCampaigns').doc(campaignId);
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, campaignSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(campaignRef)]);
      assertRoleCapability(roleSnapshot, 'approve_codes');
      if (!campaignSnapshot.exists) throw new HttpsError('not-found', 'The discount campaign does not exist.');
      if (campaignSnapshot.get('status') !== 'draft' || campaignSnapshot.get('approvedAt')) throw new HttpsError('failed-precondition', 'Only an unapproved draft can be approved.');
      transaction.update(campaignRef, { approvedAt: FieldValue.serverTimestamp(), approvedBy: uid });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'approve_discount_campaign', targetId: campaignId }));
    });
    return { campaignId, status: 'approved' };
  }
);

export const setDiscountCampaignStatus = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const campaignId = requiredString(request.data?.campaignId, 'campaignId', 80);
    const status = request.data?.status;
    if (!['paused', 'archived'].includes(status)) throw new HttpsError('invalid-argument', 'Campaign status must be paused or archived.');
    const campaignRef = firestore.collection('discountCodeCampaigns').doc(campaignId);
    const codesQuery = firestore.collection('discountCodes').where('campaignId', '==', campaignId).limit(MAX_CODE_BATCH + 1);
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, campaignSnapshot, codesSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(campaignRef), transaction.get(codesQuery)]);
      assertRoleCapability(roleSnapshot, 'publish_codes');
      if (!campaignSnapshot.exists || !codesSnapshot.size) throw new HttpsError('not-found', 'The discount campaign does not exist.');
      if (codesSnapshot.size > MAX_CODE_BATCH || codesSnapshot.size !== campaignSnapshot.get('codeCount')) throw new HttpsError('failed-precondition', 'Campaign code records do not match the saved batch size.');
      if (campaignSnapshot.get('status') === 'archived') throw new HttpsError('failed-precondition', 'An archived campaign cannot be changed.');
      transaction.update(campaignRef, { status, updatedAt: FieldValue.serverTimestamp(), updatedBy: uid });
      codesSnapshot.docs.forEach(snapshot => transaction.update(snapshot.ref, { status }));
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: `set_discount_campaign_${status}`, targetId: campaignId, details: { codeCount: codesSnapshot.size } }));
    });
    return { campaignId, status };
  }
);

export const reserveDiscountedOrder = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, secrets: [discountHmacKey], maxInstances: 20 },
  async request => {
    const uid = requireSignedIn(request);
    const productId = requiredString(request.data?.productId, 'productId', 80);
    const variantId = requiredString(request.data?.variantId, 'variantId', 80);
    const idempotencyKey = requiredString(request.data?.idempotencyKey, 'idempotencyKey', 128);
    const idempotencyDigest = createHash('sha256').update(`${uid}:${idempotencyKey}`).digest('hex');
    const orderRef = firestore.collection('orders').doc(idempotencyDigest);
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const productRef = firestore.collection('catalogRevisions');
    let codeInput = null;
    if (request.data?.discountCode != null && typeof request.data.discountCode !== 'string') throw new HttpsError('invalid-argument', 'The discount code must be text.');
    if (typeof request.data?.discountCode === 'string' && request.data.discountCode.trim()) {
      try { codeInput = normalizeDiscountCode(request.data.discountCode); }
      catch { throw new HttpsError('invalid-argument', 'The discount code format is invalid.'); }
    }
    const hmacSecret = discountHmacKey.value();
    if (codeInput && (!hmacSecret || hmacSecret.length < 32)) throw new HttpsError('failed-precondition', 'The server discount key is not configured.');
    const codeHash = codeInput ? digestDiscountCode(codeInput, hmacSecret) : null;
    const requestFingerprint = createHash('sha256').update(`${productId}:${variantId}:${codeHash ?? ''}`).digest('hex');
    const codeRef = codeHash ? firestore.collection('discountCodes').doc(codeHash) : null;
    const usageRef = codeHash ? firestore.collection('discountCodeUsage').doc(codeHash) : null;
    const customerHash = createHash('sha256').update(uid).digest('hex');
    const customerPurchaseStateRef = firestore.collection('customerPurchaseState').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const now = Date.now();

    try {
      return await firestore.runTransaction(async transaction => {
        const existingOrder = await transaction.get(orderRef);
        if (existingOrder.exists) {
          const order = existingOrder.data();
          if (order.customerUid !== uid) throw new HttpsError('permission-denied', 'Order does not belong to this account.');
          if (order.requestFingerprint !== requestFingerprint) throw new HttpsError('already-exists', 'This idempotency key was already used for a different order request.');
          return { orderId: orderRef.id, status: order.status, totalMinor: order.totalMinor, currency: order.currency, discount: customerDiscountSummary(order.appliedDiscount) };
        }
        const manifestSnapshot = await transaction.get(manifestRef);
        if (!manifestSnapshot.exists) throw new HttpsError('failed-precondition', 'No published catalogue is available.');
        const catalogRevision = manifestSnapshot.get('catalogRevision');
        const configurationRevision = manifestSnapshot.get('configurationRevision');
        if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) throw new HttpsError('failed-precondition', 'The published catalogue or configuration revision is invalid.');
        const pinnedProductRef = productRef.doc(String(catalogRevision)).collection('products').doc(productId);
        const pinnedConfigurationRef = firestore.collection('configurationRevisions').doc(String(configurationRevision));
        const [productSnapshot, promotionsSnapshot, codeSnapshot, usageSnapshot, purchaseStateSnapshot, codeConfigurationSnapshot] = await Promise.all([
          transaction.get(pinnedProductRef),
          transaction.get(firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('promotions').limit(101)),
          codeRef ? transaction.get(codeRef) : Promise.resolve(null),
          usageRef ? transaction.get(usageRef) : Promise.resolve(null),
          transaction.get(customerPurchaseStateRef),
          codeRef ? transaction.get(pinnedConfigurationRef) : Promise.resolve(null)
        ]);
        if (!productSnapshot.exists) throw new HttpsError('not-found', 'The product is not published.');
        const product = productSnapshot.data();
        const variant = product.variants?.find(item => item.id === variantId);
        if (product.status !== 'published' || !variant) throw new HttpsError('failed-precondition', 'The selected product option is not available.');
        if (variant.stockState === 'out_of_stock') throw new HttpsError('failed-precondition', 'The selected option is out of stock.');
        if (!Number.isSafeInteger(variant.priceMinor) || variant.priceMinor < 1 || variant.currency !== 'BDT') throw new HttpsError('failed-precondition', 'The product price is invalid.');

        const promotions = promotionsSnapshot.docs.map(snapshot => snapshot.data());
        if (promotions.length > 100) throw new HttpsError('failed-precondition', 'The published catalogue has too many simultaneous promotion records to price safely.');
        const automaticCandidate = bestAutomaticDiscount({ ...product, status: 'published' }, variant, promotions, now);
        let codeCandidate = null;
        let codeRecord = null;
        let campaignRef = null;
        let customerUsageRef = null;
        let campaignSnapshot = null;
        let customerUsageSnapshot = null;
        let isNewCustomer = false;
        let codeUsageTotal = 0;
        let campaignUsageTotal = 0;
        let customerUsageTotal = 0;
        if (codeHash) {
          if (!codeSnapshot?.exists) throw new HttpsError('invalid-argument', 'This discount code is invalid or unavailable.');
          if (!codeConfigurationSnapshot?.exists) throw new HttpsError('failed-precondition', 'The current published configuration is missing.');
          codeRecord = codeSnapshot.data();
          if (codeRecord.targeting?.kind === 'categories') {
            const configuration = codeConfigurationSnapshot.get('configuration');
            const categories = configuration?.catalog?.productCategories;
            const stores = configuration?.catalog?.storefronts;
            if (!Array.isArray(categories) || !Array.isArray(stores) || codeRecord.targeting.categoryIds?.some(categoryId => {
              const category = categories.find(item => item?.id === categoryId);
              return !category?.enabled || !stores.some(store => store?.id === category.storefrontId && store.enabled);
            })) throw new HttpsError('failed-precondition', 'The discount code targets a category that is no longer active.');
          }
          campaignRef = firestore.collection('discountCodeCampaigns').doc(codeRecord.campaignId);
          customerUsageRef = firestore.collection('discountCampaignUsage').doc(codeRecord.campaignId).collection('customers').doc(customerHash);
          [campaignSnapshot, customerUsageSnapshot] = await Promise.all([transaction.get(campaignRef), transaction.get(customerUsageRef)]);
          if (!campaignSnapshot.exists || !['active', 'scheduled'].includes(campaignSnapshot.get('status'))) throw new HttpsError('failed-precondition', 'This discount campaign is not active.');
          codeUsageTotal = Number(usageSnapshot?.get('redemptionCount') ?? 0) + Number(usageSnapshot?.get('reservedCount') ?? 0);
          campaignUsageTotal = Number(campaignSnapshot.get('redemptionCount') ?? 0) + Number(campaignSnapshot.get('reservedRedemptionCount') ?? 0);
          if (!Number.isSafeInteger(campaignUsageTotal) || campaignUsageTotal < 0) throw new HttpsError('failed-precondition', 'Campaign redemption counters are invalid.');
          if (campaignSnapshot.get('totalRedemptionLimit') != null && campaignUsageTotal >= campaignSnapshot.get('totalRedemptionLimit')) {
            throw new HttpsError('failed-precondition', 'This discount campaign has reached its redemption limit.');
          }
          customerUsageTotal = Number(customerUsageSnapshot?.get('redemptionCount') ?? 0) + Number(customerUsageSnapshot?.get('reservedCount') ?? 0);
          if (codeRecord.eligibility === 'new_customer') {
            isNewCustomer = Number(purchaseStateSnapshot.get('verifiedPurchaseCount') ?? 0) === 0;
          }
          let discounted;
          try {
            discounted = validateCodeRedemption(codeRecord, {
              uid, isNewCustomer, product: { ...product, status: 'published' }, variant, now,
              totalRedemptions: codeUsageTotal,
              customerRedemptions: customerUsageTotal
            });
          } catch (error) {
            throw new HttpsError('failed-precondition', error.message);
          }
          if (codeRecord.mode === 'unique' && codeUsageTotal > 0) throw new HttpsError('failed-precondition', 'This unique discount code has already been reserved or redeemed.');
          codeCandidate = { codeRecord, ...discounted };
        }
        const chosen = chooseBestSingleDiscount(automaticCandidate, codeCandidate);
        const chosenCode = Boolean(codeCandidate && chosen?.codeRecord?.id === codeRecord?.id);
        const finalPriceMinor = chosen?.finalPriceMinor ?? variant.priceMinor;
        const discountMinor = chosen?.discountMinor ?? 0;
        const order = {
          customerUid: uid,
          status: 'pending_payment',
          paymentStatus: 'pending',
          currency: 'BDT',
          subtotalMinor: variant.priceMinor,
          discountMinor,
          totalMinor: finalPriceMinor,
          lineItems: [{ productId, variantId, quantity: 1, unitPriceMinor: variant.priceMinor, currency: 'BDT' }],
          appliedDiscount: chosenCode ? { kind: 'code', campaignId: codeRecord.campaignId, codeId: codeRecord.id, codeHash, discountMinor } : chosen?.promotion ? { kind: 'automatic', promotionId: chosen.promotion.id, discountMinor } : null,
          discountReservationState: chosenCode ? 'reserved' : null,
          createdAt: FieldValue.serverTimestamp(),
          idempotencyDigest,
          requestFingerprint
        };
        transaction.create(orderRef, order);
        if (chosenCode) {
          transaction.set(campaignRef, { reservedRedemptionCount: FieldValue.increment(1) }, { merge: true });
          transaction.set(usageRef, { reservedCount: FieldValue.increment(1), campaignId: codeRecord.campaignId }, { merge: true });
          transaction.set(customerUsageRef, { reservedCount: FieldValue.increment(1), campaignId: codeRecord.campaignId }, { merge: true });
        }
        transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'reserve_discounted_order', targetId: orderRef.id, requestId: idempotencyDigest, details: { productId, variantId, discountKind: chosenCode ? 'code' : chosen?.promotion ? 'automatic' : 'none', totalMinor: finalPriceMinor } }));
        return { orderId: orderRef.id, status: 'pending_payment', totalMinor: finalPriceMinor, currency: 'BDT', discount: customerDiscountSummary(order.appliedDiscount) };
      });
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      throw new HttpsError('aborted', 'The order could not be reserved. Retry with the same idempotency key.', { cause: error?.message });
    }
  }
);
