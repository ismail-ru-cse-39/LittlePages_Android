import { createHash } from 'node:crypto';
import { getAuth } from 'firebase-admin/auth';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { logger } from 'firebase-functions';
import { defineSecret } from 'firebase-functions/params';
import { HttpsError, onCall, onRequest } from 'firebase-functions/v2/https';
import { createChildId, validateCategoryId, validateChildProfile, validateCommunityText, validateDeletionReason, validateReadingProgress, validateReview, MAX_CHILD_PROFILES } from './account-domain.mjs';
import { describeBookAsset, rightsDeclaration } from './asset-domain.mjs';
import { planDeliveryQuote, planFulfillmentUpdate, planPhysicalCheckout } from './fulfillment-domain.mjs';
import { operationFailure } from './operation-log.mjs';
import { planPaymentTransition, planSubscriptionExpiry, verifyPaymentWebhook } from './payment-domain.mjs';
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
const storage = getStorage();
const discountHmacKey = defineSecret('LITTLEPAGES_DISCOUNT_HMAC_KEY');
const paymentWebhookSecret = defineSecret('LITTLEPAGES_PAYMENT_WEBHOOK_SECRET');
const FUNCTION_REGION = 'asia-south1';
const MAX_CODE_BATCH = 100;
const ROLE_CAPABILITIES = Object.freeze({
  catalog_editor: new Set(['edit_configuration', 'validate_configuration', 'create_codes', 'manage_assets']),
  publisher: new Set(['edit_configuration', 'validate_configuration', 'publish_configuration', 'rollback_configuration', 'create_codes', 'publish_codes', 'manage_assets', 'approve_assets', 'manage_fulfillment']),
  moderator: new Set(['moderate_community']),
  administrator: new Set(['edit_configuration', 'validate_configuration', 'approve_configuration', 'publish_configuration', 'rollback_configuration', 'create_codes', 'approve_codes', 'publish_codes', 'manage_staff', 'moderate_community', 'manage_assets', 'approve_assets', 'manage_fulfillment'])
});

function logFailure(operation, error) {
  logger.error(operationFailure(operation, error));
}

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

function timestampMillis(value) {
  if (value == null) return null;
  if (typeof value?.toMillis === 'function') return value.toMillis();
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export const refreshOfflineBookGrant = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    const childId = requiredString(request.data?.childId, 'childId', 80);
    const locale = request.data?.locale;
    if (!/^[a-z0-9_-]{2,80}$/.test(bookId) || !/^[A-Za-z0-9_-]{1,80}$/.test(childId) || !['en', 'bn'].includes(locale)) {
      throw new HttpsError('invalid-argument', 'Choose a valid book, child profile, and supported locale.');
    }
    const manifestRef = firestore.collection('publicConfiguration').doc('current');
    const userRef = firestore.collection('users').doc(uid);
    const childRef = userRef.collection('children').doc(childId);
    const grantsRef = userRef.collection('offlineAccess');
    const grantRef = grantsRef.doc(bookId);
    const accountLockRef = firestore.collection('offlineAccessControl').doc(uid);
    const now = Date.now();
    const grant = await firestore.runTransaction(async transaction => {
      const accountLockSnapshot = await transaction.get(accountLockRef);
      const manifestSnapshot = await transaction.get(manifestRef);
      if (!manifestSnapshot.exists) throw new HttpsError('failed-precondition', 'No published catalogue is available.');
      const catalogRevision = manifestSnapshot.get('catalogRevision');
      const configurationRevision = manifestSnapshot.get('configurationRevision');
      if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) {
        throw new HttpsError('failed-precondition', 'The published catalogue or configuration revision is invalid.');
      }
      const productRef = firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('products').doc(bookId);
      const configurationRef = firestore.collection('configurationRevisions').doc(String(configurationRevision));
      const [userSnapshot, childSnapshot, productSnapshot, currentGrantSnapshot, configurationSnapshot] = await Promise.all([
        transaction.get(userRef), transaction.get(childRef), transaction.get(productRef), transaction.get(grantRef), transaction.get(configurationRef)
      ]);
      const offlinePolicy = configurationSnapshot.get('configuration')?.offlinePolicy;
      if (!configurationSnapshot.exists || offlinePolicy?.enabled !== true || offlinePolicy?.encryptAppPrivateStorage !== true ||
          offlinePolicy?.removeWhenEntitlementExpires !== true || offlinePolicy?.allowExport !== false) {
        throw new HttpsError('failed-precondition', 'Published offline access is disabled or its policy is invalid.');
      }
      const maximumBooks = offlinePolicy.maximumBooksPerFamily;
      const leaseDays = offlinePolicy.accessLeaseDays;
      const refreshDays = offlinePolicy.onlineRefreshAfterDays;
      if (!Number.isSafeInteger(maximumBooks) || maximumBooks < 1 || maximumBooks > 10 ||
          !Number.isSafeInteger(leaseDays) || leaseDays < 1 || leaseDays > 30 ||
          !Number.isSafeInteger(refreshDays) || refreshDays < 1 || refreshDays > 7) {
        throw new HttpsError('failed-precondition', 'Published offline access limits are invalid.');
      }
      if (!userSnapshot.exists || userSnapshot.get('state') !== 'active') throw new HttpsError('failed-precondition', 'An active parent account is required.');
      if (!childSnapshot.exists) throw new HttpsError('permission-denied', 'The child profile does not belong to this account.');
      if (!productSnapshot.exists || productSnapshot.get('status') !== 'published' || productSnapshot.get('typeId') !== 'book') {
        throw new HttpsError('not-found', 'The book is not in the published catalogue.');
      }
      const book = productSnapshot.data();
      const details = book.bookDetails ?? {};
      const contentVersion = details.contentVersion ?? book.contentVersion;
      if (!Number.isSafeInteger(contentVersion) || contentVersion < 1 || !Array.isArray(details.locales) || !details.locales.includes(locale) || details.pdfInAppEnabled === false) {
        throw new HttpsError('failed-precondition', 'This book has no approved in-app content for the selected locale.');
      }
      const readerAssetId = `${bookId}-${locale}-v${contentVersion}-reader`;
      const readerAssetRef = firestore.collection('books').doc(bookId).collection('assets').doc(readerAssetId);
      const readerAssetSnapshot = await transaction.get(readerAssetRef);
      const readerAsset = readerAssetSnapshot.data();
      const expectedObjectPath = `books/${bookId}/v${contentVersion}/${locale}/reader.pdf`;
      if (!readerAssetSnapshot.exists || readerAsset.type !== 'reader' || readerAsset.reviewStatus !== 'approved' ||
          readerAsset.locale !== locale || readerAsset.contentVersion !== contentVersion || readerAsset.objectPath !== expectedObjectPath ||
          !Number.isSafeInteger(readerAsset.byteLength) || readerAsset.byteLength < 1 || readerAsset.byteLength > 100 * 1024 * 1024 ||
          typeof readerAsset.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(readerAsset.sha256)) {
        throw new HttpsError('failed-precondition', 'The approved private reader file is unavailable for this book and locale.');
      }
      let source = 'free';
      let entitlementExpiry = null;
      if (details.access !== 'free' && details.free !== true) {
        const entitlements = await transaction.get(userRef.collection('entitlements').where('productId', '==', bookId).limit(20));
        const entitlement = entitlements.docs.map(snapshot => snapshot.data()).find(item => {
          const validFrom = timestampMillis(item.validFrom);
          const validUntil = timestampMillis(item.validUntil);
          return item.state === 'active' && (validFrom == null || validFrom <= now) && (validUntil == null || validUntil > now);
        });
        if (!entitlement) throw new HttpsError('permission-denied', 'An active entitlement is required for offline access.');
        source = entitlement.source ?? 'book_purchase';
        if (!['book_purchase', 'subscription', 'admin_grant'].includes(source)) throw new HttpsError('failed-precondition', 'The book entitlement source is invalid.');
        entitlementExpiry = timestampMillis(entitlement.validUntil);
      }
      const trackedBookIds = [...new Set((accountLockSnapshot.data()?.activeBookIds ?? []).filter(id => typeof id === 'string' && /^[a-z0-9_-]{2,80}$/.test(id)))].slice(0, 20);
      const trackedSnapshots = trackedBookIds.length
        ? await transaction.getAll(...trackedBookIds.map(id => grantsRef.doc(id)))
        : [];
      const activeBookIds = trackedSnapshots.filter(snapshot => snapshot.exists && snapshot.get('state') === 'active' && (timestampMillis(snapshot.get('expiresAt')) ?? 0) > now).map(snapshot => snapshot.id);
      const alreadyActive = currentGrantSnapshot.exists && currentGrantSnapshot.get('state') === 'active' && (timestampMillis(currentGrantSnapshot.get('expiresAt')) ?? 0) > now;
      if (!alreadyActive && !activeBookIds.includes(bookId) && activeBookIds.length >= maximumBooks) throw new HttpsError('resource-exhausted', 'The family offline book limit has been reached.');
      const expiresAt = Math.min(now + leaseDays * 24 * 60 * 60 * 1000, entitlementExpiry ?? Number.MAX_SAFE_INTEGER);
      if (expiresAt <= now) throw new HttpsError('permission-denied', 'The book entitlement has expired.');
      const refreshAfter = Math.min(now + refreshDays * 24 * 60 * 60 * 1000, expiresAt - 1000);
      if (refreshAfter <= now) throw new HttpsError('permission-denied', 'The book entitlement expires too soon for an offline grant.');
      const nextGrant = {
        uid, childId, bookId, locale, contentVersion, state: 'active', source,
        issuedAt: FieldValue.serverTimestamp(), refreshAfter: new Date(refreshAfter),
        expiresAt: new Date(expiresAt), entitlementExpiresAt: entitlementExpiry == null ? null : new Date(entitlementExpiry)
      };
      transaction.set(grantRef, nextGrant);
      transaction.set(accountLockRef, { activeBookIds: [...new Set([...activeBookIds, bookId])], updatedAt: FieldValue.serverTimestamp() });
      transaction.create(firestore.collection('offlineAccessEvents').doc(), {
        uid, childId, bookId, locale, contentVersion, action: 'refresh_grant', source,
        createdAt: FieldValue.serverTimestamp(), expiresAt: nextGrant.expiresAt
      });
      return { bookId, childId, locale, contentVersion, source, issuedAt: new Date(now).toISOString(), refreshAfter: nextGrant.refreshAfter.toISOString(), expiresAt: nextGrant.expiresAt.toISOString(), entitlementExpiresAt: nextGrant.entitlementExpiresAt?.toISOString() ?? null };
    });
    return { grant };
  }
);

export const revokeOfflineBookGrant = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    if (!/^[a-z0-9_-]{2,80}$/.test(bookId)) throw new HttpsError('invalid-argument', 'Choose a valid book.');
    const grantRef = firestore.collection('users').doc(uid).collection('offlineAccess').doc(bookId);
    const eventRef = firestore.collection('offlineAccessEvents').doc();
    await firestore.runTransaction(async transaction => {
      const grantSnapshot = await transaction.get(grantRef);
      if (!grantSnapshot.exists || grantSnapshot.get('state') !== 'active') return;
      transaction.update(grantRef, { state: 'revoked', revokedAt: FieldValue.serverTimestamp() });
      transaction.create(eventRef, { uid, bookId, childId: grantSnapshot.get('childId'), action: 'revoke_grant', createdAt: FieldValue.serverTimestamp() });
    });
    return { bookId, state: 'revoked' };
  }
);

export const createOfflineBookDownloadUrl = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    const childId = requiredString(request.data?.childId, 'childId', 80);
    const locale = request.data?.locale;
    if (!/^[a-z0-9_-]{2,80}$/.test(bookId) || !/^[A-Za-z0-9_-]{1,80}$/.test(childId) || !['en', 'bn'].includes(locale)) {
      throw new HttpsError('invalid-argument', 'Choose a valid book, child profile, and supported locale.');
    }
    const userRef = firestore.collection('users').doc(uid);
    const grantRef = userRef.collection('offlineAccess').doc(bookId);
    const childRef = userRef.collection('children').doc(childId);
    const userSnapshotPromise = userRef.get();
    const grantSnapshotPromise = grantRef.get();
    const childSnapshotPromise = childRef.get();
    const [userSnapshot, grantSnapshot, childSnapshot] = await Promise.all([userSnapshotPromise, grantSnapshotPromise, childSnapshotPromise]);
    const grant = grantSnapshot.data();
    const now = Date.now();
    if (!userSnapshot.exists || userSnapshot.get('state') !== 'active' || !childSnapshot.exists || !grantSnapshot.exists || grant.state !== 'active' || grant.childId !== childId || grant.locale !== locale ||
        timestampMillis(grant.expiresAt) == null || timestampMillis(grant.expiresAt) <= now || timestampMillis(grant.expiresAt) - now > 30 * 24 * 60 * 60 * 1000) {
      throw new HttpsError('permission-denied', 'A current offline grant for this child, book, and locale is required.');
    }
    const contentVersion = grant.contentVersion;
    const manifest = await firestore.collection('publicConfiguration').doc('current').get();
    const catalogRevision = manifest.get('catalogRevision');
    const configurationRevision = manifest.get('configurationRevision');
    if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) {
      throw new HttpsError('failed-precondition', 'The published catalogue is unavailable.');
    }
    const configurationSnapshot = await firestore.collection('configurationRevisions').doc(String(configurationRevision)).get();
    const offlinePolicy = configurationSnapshot.get('configuration')?.offlinePolicy;
    if (!configurationSnapshot.exists || offlinePolicy?.enabled !== true || offlinePolicy?.encryptAppPrivateStorage !== true ||
        offlinePolicy?.removeWhenEntitlementExpires !== true || offlinePolicy?.allowExport !== false) {
      throw new HttpsError('failed-precondition', 'Published offline access is disabled or its policy is invalid.');
    }
    const productSnapshot = await firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('products').doc(bookId).get();
    const product = productSnapshot.data() ?? {};
    const details = product.bookDetails ?? {};
    const currentContentVersion = details.contentVersion ?? product.contentVersion;
    if (!productSnapshot.exists || product.status !== 'published' || product.typeId !== 'book' || currentContentVersion !== contentVersion ||
        !details.locales?.includes(locale) || details.pdfInAppEnabled === false) {
      throw new HttpsError('failed-precondition', 'The offline grant does not match the current published book version. Refresh access before downloading.');
    }
    if (details.access !== 'free' && details.free !== true) {
      const entitlements = await userRef.collection('entitlements').where('productId', '==', bookId).limit(20).get();
      const activeEntitlement = entitlements.docs.map(snapshot => snapshot.data()).some(item => {
        const validFrom = timestampMillis(item.validFrom);
        const validUntil = timestampMillis(item.validUntil);
        return item.state === 'active' && (validFrom == null || validFrom <= now) && (validUntil == null || validUntil > now);
      });
      if (!activeEntitlement) throw new HttpsError('permission-denied', 'A current entitlement is required to download this book.');
    }
    const assetId = `${bookId}-${locale}-v${contentVersion}-reader`;
    const assetSnapshot = await firestore.collection('books').doc(bookId).collection('assets').doc(assetId).get();
    const asset = assetSnapshot.data();
    const objectPath = `books/${bookId}/v${contentVersion}/${locale}/reader.pdf`;
    if (!Number.isSafeInteger(contentVersion) || contentVersion < 1 || !assetSnapshot.exists || asset.type !== 'reader' || asset.reviewStatus !== 'approved' ||
        asset.locale !== locale || asset.contentVersion !== contentVersion || asset.objectPath !== objectPath || !Number.isSafeInteger(asset.byteLength) ||
        asset.byteLength < 1 || asset.byteLength > 100 * 1024 * 1024 || typeof asset.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(asset.sha256)) {
      throw new HttpsError('failed-precondition', 'The approved reader asset is unavailable.');
    }
    const file = storage.bucket().file(objectPath);
    let metadata;
    try { [metadata] = await file.getMetadata(); }
    catch { throw new HttpsError('not-found', 'The private reader file has not been uploaded.'); }
    if (Number(metadata.size) !== asset.byteLength || metadata.contentType !== 'application/pdf' || metadata.metadata?.sha256 !== asset.sha256) {
      throw new HttpsError('failed-precondition', 'The uploaded reader file does not match its approved asset record.');
    }
    const expiresAt = now + 15 * 60 * 1000;
    const [url] = await file.getSignedUrl({ action: 'read', expires: expiresAt, version: 'v4' });
    return { url, expiresAt: new Date(expiresAt).toISOString(), bookId, childId, locale, contentVersion, sha256: asset.sha256, byteLength: asset.byteLength };
  }
);

export const resolveBookAccess = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 15 },
  async request => {
    const uid = requireSignedIn(request);
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    const childId = requiredString(request.data?.childId, 'childId', 80);
    const locale = request.data?.locale;
    if (!/^[a-z0-9_-]{2,80}$/.test(bookId) || !/^[A-Za-z0-9_-]{1,80}$/.test(childId) || !['en', 'bn'].includes(locale)) {
      throw new HttpsError('invalid-argument', 'Choose a valid book, child profile, and supported locale.');
    }
    const userRef = firestore.collection('users').doc(uid);
    const [userSnapshot, childSnapshot, manifestSnapshot] = await Promise.all([
      userRef.get(), userRef.collection('children').doc(childId).get(), firestore.collection('publicConfiguration').doc('current').get()
    ]);
    if (!userSnapshot.exists || userSnapshot.get('state') !== 'active' || !childSnapshot.exists) {
      throw new HttpsError('permission-denied', 'An active parent account and owned child profile are required.');
    }
    const catalogRevision = manifestSnapshot.get('catalogRevision');
    const configurationRevision = manifestSnapshot.get('configurationRevision');
    if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) {
      throw new HttpsError('failed-precondition', 'The published catalogue is unavailable.');
    }
    const [productSnapshot, configurationSnapshot] = await Promise.all([
      firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('products').doc(bookId).get(),
      firestore.collection('configurationRevisions').doc(String(configurationRevision)).get()
    ]);
    if (!configurationSnapshot.exists) throw new HttpsError('failed-precondition', 'The published configuration is unavailable.');
    const product = productSnapshot.data() ?? {};
    const details = product.bookDetails ?? {};
    const contentVersion = details.contentVersion ?? product.contentVersion;
    if (!productSnapshot.exists || product.status !== 'published' || product.typeId !== 'book' ||
        !Number.isSafeInteger(contentVersion) || contentVersion < 1 || !details.locales?.includes(locale) || details.pdfInAppEnabled === false) {
      throw new HttpsError('not-found', 'The published book is not available for the selected locale.');
    }
    const configuration = configurationSnapshot.get('configuration') ?? {};
    const freeByCatalogue = details.access === 'free' || details.free === true;
    const freeAccessAllowed = configuration.accessPolicy?.freeAccess === 'book_catalogue_flag';
    if (!freeByCatalogue || !freeAccessAllowed) {
      const entitlements = await userRef.collection('entitlements').where('productId', '==', bookId).limit(20).get();
      const now = Date.now();
      const entitlement = entitlements.docs.map(snapshot => snapshot.data()).find(item => {
        const validFrom = timestampMillis(item.validFrom);
        const validUntil = timestampMillis(item.validUntil);
        const acceptedSources = details.access === 'premium' && configuration.accessPolicy?.premiumBooksRequireActiveSubscription === true
          ? ['subscription', 'admin_grant']
          : ['book_purchase', 'subscription', 'admin_grant'];
        return item.state === 'active' && acceptedSources.includes(item.source) &&
          (validFrom == null || validFrom <= now) && (validUntil == null || validUntil > now);
      });
      if (!entitlement) throw new HttpsError('permission-denied', 'A current book entitlement is required.');
    }
    const assetId = `${bookId}-${locale}-v${contentVersion}-reader`;
    const assetPath = `books/${bookId}/v${contentVersion}/${locale}/reader.pdf`;
    const assetSnapshot = await firestore.collection('books').doc(bookId).collection('assets').doc(assetId).get();
    const asset = assetSnapshot.data() ?? {};
    if (!assetSnapshot.exists || asset.type !== 'reader' || asset.reviewStatus !== 'approved' || asset.locale !== locale ||
        asset.contentVersion !== contentVersion || asset.objectPath !== assetPath || !Number.isSafeInteger(asset.byteLength) ||
        asset.byteLength < 1 || asset.byteLength > 100 * 1024 * 1024 || typeof asset.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(asset.sha256)) {
      throw new HttpsError('failed-precondition', 'The approved private reader file is unavailable.');
    }
    const file = storage.bucket().file(assetPath);
    let metadata;
    try { [metadata] = await file.getMetadata(); }
    catch { throw new HttpsError('not-found', 'The private reader file has not been uploaded.'); }
    if (Number(metadata.size) !== asset.byteLength || metadata.contentType !== 'application/pdf' || metadata.metadata?.sha256 !== asset.sha256) {
      throw new HttpsError('failed-precondition', 'The uploaded reader file does not match its approved asset record.');
    }
    const now = Date.now();
    const expiresAt = now + 5 * 60 * 1000;
    const [url] = await file.getSignedUrl({ action: 'read', expires: expiresAt, version: 'v4' });
    await firestore.collection('bookAccessEvents').add({
      uid, childId, bookId, locale, contentVersion, action: 'resolve_reader',
      createdAt: FieldValue.serverTimestamp(), expiresAt: new Date(expiresAt)
    });
    return { bookId, childId, locale, contentVersion, url, expiresAt: new Date(expiresAt).toISOString(), sha256: asset.sha256, byteLength: asset.byteLength };
  }
);

const AUDIO_TYPES = new Set(['audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/ogg', 'audio/wav']);

async function signedAssetUrl(bookId, locale, contentVersion, type) {
  const assetId = `${bookId}-${locale}-v${contentVersion}-${type}`;
  const fileName = type === 'timings' ? 'timings.json' : 'narration';
  const objectPath = `books/${bookId}/v${contentVersion}/${locale}/${fileName}`;
  const maxBytes = type === 'timings' ? 5 * 1024 * 1024 : 100 * 1024 * 1024;
  const assetSnapshot = await firestore.collection('books').doc(bookId).collection('assets').doc(assetId).get();
  if (!assetSnapshot.exists) return null;
  const asset = assetSnapshot.data();
  const contentTypeAllowed = type === 'timings' ? asset.contentType === 'application/json' : AUDIO_TYPES.has(asset.contentType);
  if (asset.type !== type || asset.reviewStatus !== 'approved' || asset.locale !== locale || asset.contentVersion !== contentVersion ||
      asset.objectPath !== objectPath || !contentTypeAllowed || !Number.isSafeInteger(asset.byteLength) || asset.byteLength < 1 ||
      asset.byteLength > maxBytes || typeof asset.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(asset.sha256)) {
    throw new HttpsError('failed-precondition', 'The approved narration asset does not match its private file.');
  }
  const file = storage.bucket().file(objectPath);
  let metadata;
  try { [metadata] = await file.getMetadata(); }
  catch { throw new HttpsError('not-found', 'The private narration file has not been uploaded.'); }
  if (Number(metadata.size) !== asset.byteLength || metadata.contentType !== asset.contentType || metadata.metadata?.sha256 !== asset.sha256) {
    throw new HttpsError('failed-precondition', 'The uploaded narration file does not match its approved asset record.');
  }
  const expiresAt = Date.now() + 5 * 60 * 1000;
  const [url] = await file.getSignedUrl({ action: 'read', expires: expiresAt, version: 'v4' });
  return { url, expiresAt: new Date(expiresAt).toISOString(), sha256: asset.sha256, byteLength: asset.byteLength, contentType: asset.contentType };
}

export const resolveNarrationAccess = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    try {
      const uid = requireSignedIn(request);
      const bookId = requiredString(request.data?.bookId, 'bookId', 80);
      const childId = requiredString(request.data?.childId, 'childId', 80);
      const locale = request.data?.locale;
      if (!/^[a-z0-9_-]{2,80}$/.test(bookId) || !/^[A-Za-z0-9_-]{1,80}$/.test(childId) || !['en', 'bn'].includes(locale)) {
        throw new HttpsError('invalid-argument', 'Choose a valid book, child profile, and supported locale.');
      }
      const userRef = firestore.collection('users').doc(uid);
      const [userSnapshot, childSnapshot, manifestSnapshot] = await Promise.all([
        userRef.get(), userRef.collection('children').doc(childId).get(), firestore.collection('publicConfiguration').doc('current').get()
      ]);
      if (!userSnapshot.exists || userSnapshot.get('state') !== 'active' || !childSnapshot.exists) {
        throw new HttpsError('permission-denied', 'An active parent account and owned child profile are required.');
      }
      const catalogRevision = manifestSnapshot.get('catalogRevision');
      const configurationRevision = manifestSnapshot.get('configurationRevision');
      if (!Number.isSafeInteger(catalogRevision) || catalogRevision < 1 || !Number.isSafeInteger(configurationRevision) || configurationRevision < 1) {
        throw new HttpsError('failed-precondition', 'The published catalogue is unavailable.');
      }
      const [productSnapshot, configurationSnapshot] = await Promise.all([
        firestore.collection('catalogRevisions').doc(String(catalogRevision)).collection('products').doc(bookId).get(),
        firestore.collection('configurationRevisions').doc(String(configurationRevision)).get()
      ]);
      const product = productSnapshot.data() ?? {};
      const details = product.bookDetails ?? {};
      const contentVersion = details.contentVersion ?? product.contentVersion;
      if (!productSnapshot.exists || product.status !== 'published' || product.typeId !== 'book' || !Number.isSafeInteger(contentVersion) || contentVersion < 1 || !details.locales?.includes(locale)) {
        throw new HttpsError('not-found', 'The published book is not available for the selected locale.');
      }
      const configuration = configurationSnapshot.get('configuration') ?? {};
      const narrationRequiresSubscription = configuration.accessPolicy?.premiumNarrationRequiresActiveSubscription !== false;
      const entitlements = await userRef.collection('entitlements').limit(40).get();
      const now = Date.now();
      const entitlementActive = (item, sources, productIds) => {
        const validFrom = timestampMillis(item.validFrom);
        const validUntil = timestampMillis(item.validUntil);
        return item.state === 'active' && sources.includes(item.source) && productIds.includes(item.productId) &&
          (validFrom == null || validFrom <= now) && (validUntil == null || validUntil > now);
      };
      const freeByCatalogue = details.access === 'free' || details.free === true;
      const freeAccessAllowed = configuration.accessPolicy?.freeAccess === 'book_catalogue_flag';
      if (!freeByCatalogue || !freeAccessAllowed) {
        const accepted = details.access === 'premium' && configuration.accessPolicy?.premiumBooksRequireActiveSubscription === true
          ? ['subscription', 'admin_grant'] : ['book_purchase', 'subscription', 'admin_grant'];
        if (!entitlements.docs.some(snapshot => entitlementActive(snapshot.data(), accepted, [bookId]))) {
          throw new HttpsError('permission-denied', 'A current book entitlement is required.');
        }
      }
      if (narrationRequiresSubscription && !entitlements.docs.some(snapshot => entitlementActive(snapshot.data(), ['subscription', 'admin_grant'], ['premium', bookId]))) {
        throw new HttpsError('permission-denied', 'An active subscription is required for narration.');
      }
      const timings = await signedAssetUrl(bookId, locale, contentVersion, 'timings');
      if (!timings) throw new HttpsError('not-found', 'Approved narration timing is not available for this book.');
      const audio = await signedAssetUrl(bookId, locale, contentVersion, 'narration');
      await firestore.collection('bookAccessEvents').add({
        uid, childId, bookId, locale, contentVersion, action: 'resolve_narration', createdAt: FieldValue.serverTimestamp()
      });
      return {
        bookId, childId, locale, contentVersion,
        timingsUrl: timings.url, timingsExpiresAt: timings.expiresAt, timingsSha256: timings.sha256, timingsByteLength: timings.byteLength,
        audioUrl: audio?.url ?? null, audioExpiresAt: audio?.expiresAt ?? null, audioSha256: audio?.sha256 ?? null,
        audioByteLength: audio?.byteLength ?? null, audioContentType: audio?.contentType ?? null
      };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logFailure('resolveNarrationAccess', error);
      throw new HttpsError('internal', 'Narration access could not be checked.');
    }
  }
);

export const setStaffRole = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const actorUid = requireSignedIn(request);
    const targetUid = requiredString(request.data?.targetUid, 'targetUid', 128);
    const role = request.data?.role;
    const status = request.data?.status;
    const reason = requiredString(request.data?.reason, 'reason', 500);
    if (targetUid.includes('/') || !['catalog_editor', 'publisher', 'moderator', 'administrator'].includes(role) || !['active', 'disabled'].includes(status)) {
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

const MODERATABLE_COLLECTIONS = Object.freeze({ post: 'posts', comment: 'comments', review: 'reviews' });

export const createCommunityReport = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const targetType = request.data?.targetType;
    const targetId = requiredString(request.data?.targetId, 'targetId', 128);
    const reason = requiredString(request.data?.reason, 'reason', 1000);
    if (!Object.hasOwn(MODERATABLE_COLLECTIONS, targetType) || targetId.includes('/') || !/^[A-Za-z0-9_-]{1,128}$/.test(targetId)) {
      throw new HttpsError('invalid-argument', 'Choose a supported community item.');
    }
    const reporterHash = createHash('sha256').update(uid).digest('hex').slice(0, 20);
    const reportId = `${targetType}-${targetId}-${reporterHash}`;
    const reportRef = firestore.collection('reports').doc(reportId);
    const targetRef = firestore.collection(MODERATABLE_COLLECTIONS[targetType]).doc(targetId);
    await firestore.runTransaction(async transaction => {
      const [targetSnapshot, reportSnapshot] = await Promise.all([transaction.get(targetRef), transaction.get(reportRef)]);
      if (!targetSnapshot.exists || targetSnapshot.get('moderationState') !== 'visible') throw new HttpsError('not-found', 'The community item is not available to report.');
      if (targetSnapshot.get('uid') === uid) throw new HttpsError('failed-precondition', 'You cannot report your own content.');
      if (reportSnapshot.exists) throw new HttpsError('already-exists', 'You have already reported this item.');
      transaction.create(reportRef, {
        reporterUid: uid, targetType, targetId, reason, status: 'open',
        assignedModerator: null, createdAt: FieldValue.serverTimestamp(), resolvedAt: null
      });
    });
    return { reportId, status: 'open' };
  }
);

export const listModerationReports = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    assertRoleCapability(await firestore.collection('adminRoles').doc(uid).get(), 'moderate_community');
    const limit = Math.min(Math.max(Number.isSafeInteger(request.data?.limit) ? request.data.limit : 50, 1), 100);
    const reports = await firestore.collection('reports').where('status', '==', 'open').limit(limit).get();
    const records = await Promise.all(reports.docs.map(async snapshot => {
      const report = snapshot.data();
      const collectionName = Object.hasOwn(MODERATABLE_COLLECTIONS, report.targetType) ? MODERATABLE_COLLECTIONS[report.targetType] : null;
      const target = collectionName && typeof report.targetId === 'string' && !report.targetId.includes('/')
        ? await firestore.collection(collectionName).doc(report.targetId).get()
        : null;
      const content = target?.data();
      return {
        ...report,
        id: snapshot.id,
        targetPreview: target?.exists ? {
          body: typeof content.body === 'string' ? content.body.slice(0, 3000) : typeof content.text === 'string' ? content.text.slice(0, 3000) : '',
          moderationState: content.moderationState ?? null,
          createdAt: content.createdAt ?? null
        } : null
      };
    }));
    return { reports: records };
  }
);

export const moderateCommunityItem = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const reportId = requiredString(request.data?.reportId, 'reportId', 128);
    const action = request.data?.action;
    const reason = requiredString(request.data?.reason, 'reason', 1000);
    if (!['keep', 'hide', 'remove'].includes(action) || reportId.includes('/')) {
      throw new HttpsError('invalid-argument', 'Choose a valid report and moderation action.');
    }
    const reportRef = firestore.collection('reports').doc(reportId);
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, reportSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(reportRef)]);
      assertRoleCapability(roleSnapshot, 'moderate_community');
      if (!reportSnapshot.exists || reportSnapshot.get('status') !== 'open') throw new HttpsError('failed-precondition', 'This report is missing or has already been resolved.');
      const report = reportSnapshot.data();
      const collectionName = Object.hasOwn(MODERATABLE_COLLECTIONS, report.targetType) ? MODERATABLE_COLLECTIONS[report.targetType] : null;
      if (!collectionName || typeof report.targetId !== 'string' || !report.targetId || report.targetId.includes('/')) {
        throw new HttpsError('failed-precondition', 'The report target is invalid or unsupported.');
      }
      const itemRef = firestore.collection(collectionName).doc(report.targetId);
      const itemSnapshot = await transaction.get(itemRef);
      if (!itemSnapshot.exists) throw new HttpsError('not-found', 'The reported content no longer exists.');
      const moderationState = action === 'keep' ? 'visible' : action === 'hide' ? 'hidden' : 'removed';
      transaction.update(itemRef, { moderationState, moderatedBy: uid, moderatedAt: FieldValue.serverTimestamp() });
      transaction.update(reportRef, { status: 'resolved', action, reason, assignedModerator: uid, resolvedAt: FieldValue.serverTimestamp() });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'moderate_community_item', targetId: `${report.targetType}:${report.targetId}`, details: { reportId, reportAction: action, moderationState, reason } }));
    });
    return { reportId, action, status: 'resolved' };
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

export const listDiscountCampaigns = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const roleSnapshot = await firestore.collection('adminRoles').doc(uid).get();
    const role = assertRoleCapability(roleSnapshot, 'create_codes');
    if (role === 'catalog_editor') throw new HttpsError('permission-denied', 'Only publishers and administrators can inspect campaign status.');
    const limit = Math.min(Math.max(Number.isSafeInteger(request.data?.limit) ? request.data.limit : 50, 1), 100);
    const campaigns = await firestore.collection('discountCodeCampaigns').limit(limit).get();
    return { campaigns: campaigns.docs.map(snapshot => ({ ...snapshot.data(), id: snapshot.id })) };
  }
);

export const publishDiscountCampaign = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const campaignId = requiredString(request.data?.campaignId, 'campaignId', 80);
    const reason = requiredString(request.data?.reason, 'reason', 500);
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
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'publish_discount_campaign', targetId: campaignId, requestId: request.rawRequest?.headers?.['x-cloud-trace-context'], details: { status, codeCount: codesSnapshot.size, reason } }));
    });
    return { campaignId, status: Date.parse((await campaignRef.get()).data().schedule.startsAt) > now ? 'scheduled' : 'active' };
  }
);

export const approveDiscountCampaign = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const campaignId = requiredString(request.data?.campaignId, 'campaignId', 80);
    const reason = requiredString(request.data?.reason, 'reason', 500);
    const campaignRef = firestore.collection('discountCodeCampaigns').doc(campaignId);
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, campaignSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(campaignRef)]);
      assertRoleCapability(roleSnapshot, 'approve_codes');
      if (!campaignSnapshot.exists) throw new HttpsError('not-found', 'The discount campaign does not exist.');
      if (campaignSnapshot.get('status') !== 'draft' || campaignSnapshot.get('approvedAt')) throw new HttpsError('failed-precondition', 'Only an unapproved draft can be approved.');
      transaction.update(campaignRef, { approvedAt: FieldValue.serverTimestamp(), approvedBy: uid });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'approve_discount_campaign', targetId: campaignId, details: { reason } }));
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
    const reason = requiredString(request.data?.reason, 'reason', 500);
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
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: `set_discount_campaign_${status}`, targetId: campaignId, details: { codeCount: codesSnapshot.size, reason } }));
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
    const deliveryQuoteId = typeof request.data?.deliveryQuoteId === 'string' ? request.data.deliveryQuoteId.trim() : '';
    const requestFingerprint = createHash('sha256').update(`${productId}:${variantId}:${codeHash ?? ''}:${deliveryQuoteId}`).digest('hex');
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
        if (variant.stockState === 'preorder') throw new HttpsError('failed-precondition', 'Preorder fulfillment is not available.');
        if (!Number.isSafeInteger(variant.priceMinor) || variant.priceMinor < 1 || variant.currency !== 'BDT') throw new HttpsError('failed-precondition', 'The product price is invalid.');
        let physicalPlan = null;
        let inventoryRef = null;
        if (variant.stockState === 'in_stock') {
          if (request.data?.region !== 'BD' || !deliveryQuoteId || deliveryQuoteId.includes('/') || deliveryQuoteId.length > 80 || productId.includes('/') || variantId.includes('/')) {
            throw new HttpsError('failed-precondition', 'A confirmed Bangladesh delivery quote is required before reserving a printed book.');
          }
          const quoteRef = firestore.collection('deliveryQuotes').doc(deliveryQuoteId);
          inventoryRef = firestore.collection('inventory').doc(`${productId}__${variantId}`);
          const [quoteSnapshot, inventorySnapshot] = await Promise.all([transaction.get(quoteRef), transaction.get(inventoryRef)]);
          physicalPlan = planPhysicalCheckout({
            variant,
            region: 'BD',
            quote: quoteSnapshot.exists ? quoteSnapshot.data() : null,
            available: inventorySnapshot.exists ? inventorySnapshot.get('available') : variant.stockQuantity,
            now
          });
          if (!physicalPlan.ok) throw new HttpsError('failed-precondition', physicalPlan.reason);
        }

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
        const deliveryFeeMinor = physicalPlan?.deliveryFeeMinor ?? 0;
        const order = {
          customerUid: uid,
          status: 'pending_payment',
          paymentStatus: 'pending',
          currency: 'BDT',
          subtotalMinor: variant.priceMinor,
          discountMinor,
          deliveryFeeMinor,
          totalMinor: finalPriceMinor + deliveryFeeMinor,
          lineItems: [{ productId, variantId, quantity: 1, unitPriceMinor: variant.priceMinor, currency: 'BDT', fulfillment: physicalPlan ? 'physical' : 'in_app_reading' }],
          grantsDigitalAccess: variantId === 'in-app' && !physicalPlan,
          fulfillmentType: physicalPlan ? 'physical' : 'digital',
          fulfillmentState: physicalPlan ? physicalPlan.fulfillmentState : null,
          deliveryQuoteId: physicalPlan ? deliveryQuoteId : null,
          inventoryId: physicalPlan ? inventoryRef.id : null,
          appliedDiscount: chosenCode ? { kind: 'code', campaignId: codeRecord.campaignId, codeId: codeRecord.id, codeHash, discountMinor } : chosen?.promotion ? { kind: 'automatic', promotionId: chosen.promotion.id, discountMinor } : null,
          discountReservationState: chosenCode ? 'reserved' : null,
          createdAt: FieldValue.serverTimestamp(),
          idempotencyDigest,
          requestFingerprint
        };
        if (physicalPlan) {
          transaction.set(inventoryRef, { productId, variantId, available: physicalPlan.availableAfter, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        }
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
      logFailure('reserveDiscountedOrder', error);
      throw new HttpsError('aborted', 'The order could not be reserved. Retry with the same idempotency key.');
    }
  }
);

async function requireActiveParent(uid) {
  const userRef = firestore.collection('users').doc(uid);
  const snapshot = await userRef.get();
  if (snapshot.exists && snapshot.get('state') !== 'active') {
    throw new HttpsError('failed-precondition', 'This parent account is not active.');
  }
  if (!snapshot.exists) {
    await userRef.set({ state: 'active', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  }
  return userRef;
}

export const saveChildProfile = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const nickname = typeof request.data?.nickname === 'string' ? request.data.nickname.trim() : '';
    const ageBand = request.data?.ageBand;
    const requestedId = request.data?.childId ?? null;
    const validationError = validateChildProfile({ nickname, ageBand, childId: requestedId });
    if (validationError) throw new HttpsError('invalid-argument', validationError);
    const userRef = await requireActiveParent(uid);
    const childId = requestedId ?? createChildId();
    const childRef = userRef.collection('children').doc(childId);
    const saved = await firestore.runTransaction(async transaction => {
      const [userSnapshot, childSnapshot, childrenSnapshot] = await Promise.all([
        transaction.get(userRef), transaction.get(childRef), transaction.get(userRef.collection('children'))
      ]);
      if (userSnapshot.exists && userSnapshot.get('state') !== 'active') throw new HttpsError('failed-precondition', 'This parent account is not active.');
      if (!userSnapshot.exists) transaction.set(userRef, { state: 'active', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
      if (!childSnapshot.exists && childrenSnapshot.size >= MAX_CHILD_PROFILES) {
        throw new HttpsError('failed-precondition', 'This family already has the maximum number of child profiles.');
      }
      transaction.set(childRef, {
        nickname, ageBand,
        createdAt: childSnapshot.get('createdAt') ?? FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });
      return { childId, nickname, ageBand };
    });
    return saved;
  }
);

export const deleteChildProfile = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const childId = request.data?.childId;
    if (validateChildProfile({ nickname: 'Child', ageBand: '5 years old', childId })) {
      throw new HttpsError('invalid-argument', 'The child profile ID is invalid.');
    }
    const userRef = await requireActiveParent(uid);
    const childRef = userRef.collection('children').doc(childId);
    const progress = await childRef.collection('progress').get();
    if (progress.size > 100) throw new HttpsError('failed-precondition', 'This profile has too much reading data to delete in one request.');
    const batch = firestore.batch();
    progress.docs.forEach(snapshot => batch.delete(snapshot.ref));
    batch.delete(childRef);
    await batch.commit();
    return { childId, deleted: true };
  }
);

export const saveReadingProgress = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 20 },
  async request => {
    const uid = requireSignedIn(request);
    const childId = request.data?.childId;
    const bookId = request.data?.bookId;
    const page = request.data?.page;
    const bookmarks = request.data?.bookmarks ?? [];
    if (validateChildProfile({ nickname: 'Child', ageBand: '5 years old', childId })) throw new HttpsError('invalid-argument', 'The child profile ID is invalid.');
    const progressError = validateReadingProgress({ bookId, page, bookmarks });
    if (progressError) throw new HttpsError('invalid-argument', progressError);
    const userRef = await requireActiveParent(uid);
    const childRef = userRef.collection('children').doc(childId);
    const progressRef = childRef.collection('progress').doc(bookId);
    const now = Date.now();
    const clientUpdated = Number(request.data?.updatedAt);
    const updatedAtMillis = Number.isFinite(clientUpdated) && Math.abs(now - clientUpdated) <= 5 * 60 * 1000 ? clientUpdated : now;
    const saved = await firestore.runTransaction(async transaction => {
      const [childSnapshot, progressSnapshot] = await Promise.all([transaction.get(childRef), transaction.get(progressRef)]);
      if (!childSnapshot.exists) throw new HttpsError('permission-denied', 'The child profile does not belong to this account.');
      const existingMillis = timestampMillis(progressSnapshot.get('updatedAt'));
      if (progressSnapshot.exists && existingMillis != null && existingMillis > updatedAtMillis) {
        return { bookId, page: progressSnapshot.get('page'), bookmarks: progressSnapshot.get('bookmarks') ?? [], updatedAt: existingMillis, kept: 'server' };
      }
      transaction.set(progressRef, {
        page, bookmarks, updatedAt: new Date(updatedAtMillis), childId, bookId
      });
      return { bookId, page, bookmarks, updatedAt: updatedAtMillis, kept: 'client' };
    });
    return saved;
  }
);

export const requestAccountDeletion = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const reasonError = validateDeletionReason(request.data?.reason);
    if (reasonError) throw new HttpsError('invalid-argument', reasonError);
    await requireActiveParent(uid);
    const requestRef = firestore.collection('privacyRequests').doc();
    await requestRef.set({
      uid, type: 'account_deletion', status: 'requested',
      reason: typeof request.data?.reason === 'string' ? request.data.reason.trim() : '',
      createdAt: FieldValue.serverTimestamp()
    });
    return { requestId: requestRef.id, status: 'requested' };
  }
);

export const exportFamilyData = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const userRef = await requireActiveParent(uid);
    const children = await userRef.collection('children').get();
    const profiles = [];
    for (const child of children.docs) {
      const progress = await child.ref.collection('progress').limit(100).get();
      profiles.push({
        id: child.id,
        nickname: child.get('nickname') ?? '',
        ageBand: child.get('ageBand') ?? '',
        progress: progress.docs.map(item => ({ bookId: item.id, page: item.get('page') ?? 1, bookmarks: item.get('bookmarks') ?? [] }))
      });
    }
    return { uid, profiles };
  }
);

function communityId(prefix) {
  return `${prefix}-${createHash('sha256').update(`${Date.now()}:${Math.random()}`).digest('hex').slice(0, 20)}`;
}

export const createCommunityPost = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const bodyError = validateCommunityText(request.data?.body, 'Post');
    const categoryError = validateCategoryId(request.data?.categoryId);
    if (bodyError || categoryError) throw new HttpsError('invalid-argument', bodyError || categoryError);
    await requireActiveParent(uid);
    let imageId = null;
    if (request.data?.imageId != null) {
      imageId = requiredString(request.data.imageId, 'imageId', 80);
      const image = await firestore.collection('communityImages').doc(imageId).get();
      if (!image.exists || image.get('uid') !== uid || image.get('status') !== 'ready') {
        throw new HttpsError('failed-precondition', 'Attach an image only after its upload has been checked.');
      }
    }
    const postRef = firestore.collection('posts').doc(communityId('post'));
    await postRef.set({
      uid, body: request.data.body.trim(), categoryId: request.data.categoryId, imageId,
      moderationState: 'pending', createdAt: FieldValue.serverTimestamp()
    });
    return { id: postRef.id, moderationState: 'pending' };
  }
);

export const createCommunityComment = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    try {
      const uid = requireSignedIn(request);
      const postId = requiredString(request.data?.postId, 'postId', 80);
      const bodyError = validateCommunityText(request.data?.body, 'Comment');
      if (postId.includes('/') || bodyError) throw new HttpsError('invalid-argument', bodyError || 'Choose a valid post.');
      await requireActiveParent(uid);
      const post = await firestore.collection('posts').doc(postId).get();
      if (!post.exists || !['visible', 'pending'].includes(post.get('moderationState')) || (post.get('moderationState') === 'pending' && post.get('uid') !== uid)) {
        throw new HttpsError('not-found', 'That post is not available for replies.');
      }
      const commentRef = firestore.collection('comments').doc(communityId('comment'));
      await commentRef.set({
        uid, postId, body: request.data.body.trim(), moderationState: 'pending', createdAt: FieldValue.serverTimestamp()
      });
      return { id: commentRef.id, moderationState: 'pending' };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logFailure('createCommunityComment', error);
      throw new HttpsError('internal', 'The reply could not be saved.');
    }
  }
);

export const listCommunityPosts = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    await requireActiveParent(uid);
    const categoryId = request.data?.categoryId ?? null;
    if (categoryId != null && validateCategoryId(categoryId)) throw new HttpsError('invalid-argument', 'Choose a supported topic.');
    const [visible, own] = await Promise.all([
      firestore.collection('posts').where('moderationState', '==', 'visible').limit(50).get(),
      firestore.collection('posts').where('uid', '==', uid).limit(50).get()
    ]);
    const posts = new Map();
    [...visible.docs, ...own.docs].forEach(snapshot => {
      if (snapshot.get('moderationState') === 'removed') return;
      if (categoryId && snapshot.get('categoryId') !== categoryId) return;
      posts.set(snapshot.id, {
        id: snapshot.id, body: snapshot.get('body') ?? '', categoryId: snapshot.get('categoryId') ?? '',
        authorUid: snapshot.get('uid'), imageId: snapshot.get('imageId') ?? null,
        moderationState: snapshot.get('moderationState') ?? 'pending'
      });
    });
    return { posts: [...posts.values()] };
  }
);

export const listCommunityComments = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    try {
      const uid = requireSignedIn(request);
      const postId = requiredString(request.data?.postId, 'postId', 80);
      if (postId.includes('/') || !/^[A-Za-z0-9_-]{1,80}$/.test(postId)) throw new HttpsError('invalid-argument', 'Choose a valid post.');
      await requireActiveParent(uid);
      const post = await firestore.collection('posts').doc(postId).get();
      const visibleToCaller = post.exists && post.get('moderationState') !== 'removed' &&
        (post.get('moderationState') === 'visible' || post.get('uid') === uid);
      if (!visibleToCaller) throw new HttpsError('not-found', 'That post is not available for replies.');
      const comments = await firestore.collection('comments').where('postId', '==', postId).limit(50).get();
      return {
        comments: comments.docs.filter(snapshot => {
          const state = snapshot.get('moderationState');
          return state === 'visible' || (snapshot.get('uid') === uid && state !== 'removed');
        }).map(snapshot => ({
          id: snapshot.id, postId, body: snapshot.get('body') ?? '', authorUid: snapshot.get('uid'),
          moderationState: snapshot.get('moderationState') ?? 'pending'
        }))
      };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logFailure('listCommunityComments', error);
      throw new HttpsError('internal', 'Replies could not be loaded.');
    }
  }
);

export const createReview = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const reviewError = validateReview({ bookId: request.data?.bookId, rating: request.data?.rating, body: request.data?.body });
    if (reviewError) throw new HttpsError('invalid-argument', reviewError);
    await requireActiveParent(uid);
    const reviewRef = firestore.collection('reviews').doc(communityId('review'));
    await reviewRef.set({
      uid, bookId: request.data.bookId, rating: request.data.rating, body: request.data.body.trim(),
      moderationState: 'pending', createdAt: FieldValue.serverTimestamp()
    });
    return { id: reviewRef.id, moderationState: 'pending' };
  }
);

export const reviewCommunitySubmission = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const targetType = request.data?.targetType;
    const targetId = requiredString(request.data?.targetId, 'targetId', 128);
    const action = request.data?.action;
    const reason = requiredString(request.data?.reason, 'reason', 1000);
    if (!Object.hasOwn(MODERATABLE_COLLECTIONS, targetType) || targetId.includes('/') || !['approve', 'hide', 'remove'].includes(action)) {
      throw new HttpsError('invalid-argument', 'Choose a supported submission and action.');
    }
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const itemRef = firestore.collection(MODERATABLE_COLLECTIONS[targetType]).doc(targetId);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    const moderationState = action === 'approve' ? 'visible' : action === 'hide' ? 'hidden' : 'removed';
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, itemSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(itemRef)]);
      assertRoleCapability(roleSnapshot, 'moderate_community');
      if (!itemSnapshot.exists) throw new HttpsError('not-found', 'The submission no longer exists.');
      transaction.update(itemRef, { moderationState, moderatedBy: uid, moderatedAt: FieldValue.serverTimestamp() });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'review_community_submission', targetId: `${targetType}:${targetId}`, details: { moderationState, reason } }));
    });
    return { targetType, targetId, moderationState };
  }
);

const COMMUNITY_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_COMMUNITY_IMAGE_BYTES = 5 * 1024 * 1024;

export const createCommunityImageUploadUrl = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const contentType = request.data?.contentType;
    const byteLength = request.data?.byteLength;
    if (!COMMUNITY_IMAGE_TYPES.has(contentType) || !Number.isSafeInteger(byteLength) || byteLength < 1 || byteLength > MAX_COMMUNITY_IMAGE_BYTES) {
      throw new HttpsError('invalid-argument', 'Choose a JPEG, PNG, or WebP image up to 5 MB.');
    }
    await requireActiveParent(uid);
    const imageId = communityId('image');
    const objectPath = `community/${uid}/${imageId}`;
    const imageRef = firestore.collection('communityImages').doc(imageId);
    await imageRef.set({ uid, objectPath, contentType, byteLength, status: 'pending_upload', createdAt: FieldValue.serverTimestamp() });
    const file = storage.bucket().file(objectPath);
    const expiresAt = Date.now() + 15 * 60 * 1000;
    const [url] = await file.getSignedUrl({ action: 'write', expires: expiresAt, version: 'v4', contentType });
    return { imageId, url, expiresAt: new Date(expiresAt).toISOString(), contentType };
  }
);

export const finalizeCommunityImage = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 10 },
  async request => {
    const uid = requireSignedIn(request);
    const imageId = requiredString(request.data?.imageId, 'imageId', 80);
    const imageRef = firestore.collection('communityImages').doc(imageId);
    const image = await imageRef.get();
    if (!image.exists || image.get('uid') !== uid || image.get('status') === 'ready') {
      throw new HttpsError(image.get('status') === 'ready' ? 'already-exists' : 'permission-denied', 'This image upload is not available.');
    }
    const file = storage.bucket().file(image.get('objectPath'));
    let metadata;
    try { [metadata] = await file.getMetadata(); }
    catch { throw new HttpsError('not-found', 'The image has not been uploaded.'); }
    if (metadata.contentType !== image.get('contentType') || Number(metadata.size) !== image.get('byteLength') || Number(metadata.size) > MAX_COMMUNITY_IMAGE_BYTES) {
      throw new HttpsError('failed-precondition', 'The uploaded image does not match the declared type or size.');
    }
    await imageRef.set({ status: 'ready', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { imageId, status: 'ready' };
  }
);

export const createBookAssetUploadUrl = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    assertRoleCapability(await firestore.collection('adminRoles').doc(uid).get(), 'manage_assets');
    let described;
    let rights;
    try {
      described = describeBookAsset(request.data ?? {});
      rights = rightsDeclaration(request.data?.rights ?? {});
    } catch (error) { throw new HttpsError('invalid-argument', error.message); }
    const assetRef = firestore.collection('books').doc(described.bookId).collection('assets').doc(described.assetId);
    const existing = await assetRef.get();
    if (existing.exists && existing.get('reviewStatus') === 'approved') {
      throw new HttpsError('failed-precondition', 'An approved asset cannot be replaced. Publish a new content version.');
    }
    await assetRef.set({
      ...described, rights, reviewStatus: 'pending_upload', uploadedBy: uid, updatedAt: FieldValue.serverTimestamp(),
      createdAt: existing.get('createdAt') ?? FieldValue.serverTimestamp()
    });
    const file = storage.bucket().file(described.objectPath);
    const expiresAt = Date.now() + 15 * 60 * 1000;
    const [url] = await file.getSignedUrl({ action: 'write', expires: expiresAt, version: 'v4', contentType: described.contentType });
    return { ...described, url, expiresAt: new Date(expiresAt).toISOString(), reviewStatus: 'pending_upload' };
  }
);

export const finalizeBookAsset = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5, memory: '512MiB' },
  async request => {
    const uid = requireSignedIn(request);
    assertRoleCapability(await firestore.collection('adminRoles').doc(uid).get(), 'manage_assets');
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    const assetId = requiredString(request.data?.assetId, 'assetId', 160);
    if (bookId.includes('/') || assetId.includes('/')) throw new HttpsError('invalid-argument', 'The asset reference is invalid.');
    const assetRef = firestore.collection('books').doc(bookId).collection('assets').doc(assetId);
    const assetSnapshot = await assetRef.get();
    const asset = assetSnapshot.data();
    if (!assetSnapshot.exists || asset.reviewStatus === 'approved') throw new HttpsError('failed-precondition', 'This asset is not waiting for an upload check.');
    const file = storage.bucket().file(asset.objectPath);
    let metadata;
    try { [metadata] = await file.getMetadata(); }
    catch { throw new HttpsError('not-found', 'The private file has not been uploaded.'); }
    if (Number(metadata.size) !== asset.byteLength || metadata.contentType !== asset.contentType) {
      throw new HttpsError('failed-precondition', 'The uploaded file does not match its declared size or type.');
    }
    const digest = createHash('sha256');
    await new Promise((resolve, reject) => {
      file.createReadStream().on('data', chunk => digest.update(chunk)).on('end', resolve).on('error', reject);
    });
    if (digest.digest('hex') !== asset.sha256) throw new HttpsError('failed-precondition', 'The uploaded file failed its checksum.');
    await file.setMetadata({ metadata: { sha256: asset.sha256 } });
    await assetRef.set({ reviewStatus: 'pending_review', finalizedBy: uid, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return { assetId, reviewStatus: 'pending_review' };
  }
);

export const reviewBookAsset = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    const uid = requireSignedIn(request);
    const bookId = requiredString(request.data?.bookId, 'bookId', 80);
    const assetId = requiredString(request.data?.assetId, 'assetId', 160);
    const decision = request.data?.decision;
    const reason = requiredString(request.data?.reason, 'reason', 1000);
    if (!['approved', 'rejected'].includes(decision) || bookId.includes('/') || assetId.includes('/')) {
      throw new HttpsError('invalid-argument', 'Choose a valid asset and approval decision.');
    }
    const roleRef = firestore.collection('adminRoles').doc(uid);
    const assetRef = firestore.collection('books').doc(bookId).collection('assets').doc(assetId);
    const auditRef = firestore.collection('adminAuditEvents').doc();
    await firestore.runTransaction(async transaction => {
      const [roleSnapshot, assetSnapshot] = await Promise.all([transaction.get(roleRef), transaction.get(assetRef)]);
      assertRoleCapability(roleSnapshot, 'approve_assets');
      if (!assetSnapshot.exists || assetSnapshot.get('reviewStatus') !== 'pending_review') {
        throw new HttpsError('failed-precondition', 'Only an uploaded asset that is waiting for review can be approved or rejected.');
      }
      transaction.set(assetRef, { reviewStatus: decision, reviewedBy: uid, reviewReason: reason, reviewedAt: FieldValue.serverTimestamp() }, { merge: true });
      transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'review_book_asset', targetId: assetId, details: { bookId, decision, reason } }));
    });
    return { assetId, reviewStatus: decision };
  }
);

async function applyVerifiedPayment(event) {
  const orderRef = firestore.collection('orders').doc(event.orderId);
  const eventRef = firestore.collection('paymentEvents').doc(createHash('sha256').update(`${event.provider}:${event.providerEventId}`).digest('hex'));
  const now = Date.now();
  return firestore.runTransaction(async transaction => {
    const existingEvent = await transaction.get(eventRef);
    const orderSnapshot = await transaction.get(orderRef);
    const order = orderSnapshot.exists ? orderSnapshot.data() : null;
    if (existingEvent.exists) {
      return { status: 200, body: { orderId: event.orderId, status: order?.status ?? 'unknown', duplicate: true } };
    }
    const plan = planPaymentTransition(order, event, now);
    if (plan.action === 'reject') return { status: plan.status, body: { error: plan.reason } };
    if (plan.action === 'noop') return { status: 200, body: { orderId: event.orderId, status: plan.orderStatus, paymentStatus: plan.paymentStatus, duplicate: true } };
    const purchaseRef = firestore.collection('customerPurchaseState').doc(order.customerUid);
    const purchaseSnapshot = await transaction.get(purchaseRef);
    const discount = order.appliedDiscount?.kind === 'code' ? order.appliedDiscount : null;
    const codeRefs = discount && plan.reservation !== 'none' ? {
      campaign: firestore.collection('discountCodeCampaigns').doc(discount.campaignId),
      usage: firestore.collection('discountCodeUsage').doc(discount.codeHash),
      customer: firestore.collection('discountCampaignUsage').doc(discount.campaignId).collection('customers').doc(createHash('sha256').update(order.customerUid).digest('hex'))
    } : null;
    if (codeRefs) {
      await transaction.get(codeRefs.campaign);
      await transaction.get(codeRefs.usage);
      await transaction.get(codeRefs.customer);
    }
    const entitlementRef = firestore.collection('users').doc(order.customerUid).collection('entitlements').doc(orderRef.id);
    if (plan.entitlement === 'revoke') await transaction.get(entitlementRef);
    const inventorySnapshot = plan.stock === 'release' && typeof order.inventoryId === 'string' && order.inventoryId && !order.inventoryId.includes('/')
      ? await transaction.get(firestore.collection('inventory').doc(order.inventoryId))
      : null;
    const releasedStock = inventorySnapshot?.exists && Number.isSafeInteger(inventorySnapshot.get('available'));
    transaction.create(eventRef, { ...event, orderId: orderRef.id, appliedAt: FieldValue.serverTimestamp(), plan: plan.orderStatus });
    transaction.set(orderRef, {
      status: plan.orderStatus,
      paymentStatus: plan.paymentStatus,
      settledProviderEventId: `${event.provider}:${event.providerEventId}`,
      discountReservationState: plan.reservation === 'settle' ? 'settled' : plan.reservation === 'release' ? 'released' : order.discountReservationState ?? null,
      fulfillmentState: plan.stock === 'release' && !releasedStock ? 'stock_release_missing' : plan.fulfillment,
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
    if (releasedStock) {
      transaction.set(inventorySnapshot.ref, { available: inventorySnapshot.get('available') + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    }
    if (codeRefs && plan.reservation === 'release') {
      transaction.set(codeRefs.campaign, { reservedRedemptionCount: FieldValue.increment(-1) }, { merge: true });
      transaction.set(codeRefs.usage, { reservedCount: FieldValue.increment(-1) }, { merge: true });
      transaction.set(codeRefs.customer, { reservedCount: FieldValue.increment(-1) }, { merge: true });
    }
    if (codeRefs && plan.reservation === 'settle') {
      transaction.set(codeRefs.campaign, { reservedRedemptionCount: FieldValue.increment(-1), redemptionCount: FieldValue.increment(1) }, { merge: true });
      transaction.set(codeRefs.usage, { reservedCount: FieldValue.increment(-1), redemptionCount: FieldValue.increment(1) }, { merge: true });
      transaction.set(codeRefs.customer, { reservedCount: FieldValue.increment(-1), redemptionCount: FieldValue.increment(1) }, { merge: true });
    }
    const productId = order.lineItems?.[0]?.productId;
    if (plan.entitlement === 'grant' && typeof productId === 'string') {
      transaction.set(entitlementRef, {
        productId, state: 'active', source: 'book_purchase', validFrom: new Date(now), validUntil: null,
        orderId: orderRef.id, provider: event.provider, providerEventId: event.providerEventId, updatedAt: FieldValue.serverTimestamp()
      });
      transaction.set(purchaseRef, { verifiedPurchaseCount: FieldValue.increment(1) }, { merge: true });
    }
    if (plan.entitlement === 'revoke') {
      transaction.set(entitlementRef, { state: 'revoked', updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (Number(purchaseSnapshot.get('verifiedPurchaseCount') ?? 0) > 0) {
        transaction.set(purchaseRef, { verifiedPurchaseCount: FieldValue.increment(-1) }, { merge: true });
      }
    }
    return { status: 200, body: { orderId: orderRef.id, status: plan.orderStatus, paymentStatus: plan.paymentStatus, fulfillment: plan.fulfillment } };
  });
}

async function applySubscriptionExpiry(event) {
  const subscriptionRef = firestore.collection('subscriptions').doc(event.subscriptionId);
  const eventRef = firestore.collection('paymentEvents').doc(createHash('sha256').update(`${event.provider}:${event.providerEventId}`).digest('hex'));
  const now = Date.now();
  return firestore.runTransaction(async transaction => {
    const existingEvent = await transaction.get(eventRef);
    const subscriptionSnapshot = await transaction.get(subscriptionRef);
    const subscription = subscriptionSnapshot.exists ? subscriptionSnapshot.data() : null;
    if (existingEvent.exists) return { status: 200, body: { subscriptionId: event.subscriptionId, duplicate: true } };
    const plan = planSubscriptionExpiry(subscription, event, now);
    if (plan.action === 'reject') return { status: plan.status, body: { error: plan.reason } };
    if (plan.action === 'noop') return { status: 200, body: { subscriptionId: event.subscriptionId, duplicate: true } };
    const entitlements = await transaction.get(
      firestore.collection('users').doc(plan.uid).collection('entitlements').where('source', '==', 'subscription').limit(41)
    );
    if (entitlements.size > 40) return { status: 409, body: { error: 'Too many subscription entitlements to expire in one event.' } };
    const matching = entitlements.docs.filter(snapshot => snapshot.get('productId') === plan.productId);
    transaction.create(eventRef, { provider: event.provider, providerEventId: event.providerEventId, type: event.type, subscriptionId: event.subscriptionId, occurredAt: event.occurredAt, appliedAt: FieldValue.serverTimestamp() });
    transaction.set(subscriptionRef, {
      state: 'expired', settledProviderEventId: `${event.provider}:${event.providerEventId}`, expiredAt: event.occurredAt, updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
    matching.forEach(snapshot => transaction.set(snapshot.ref, { state: 'revoked', updatedAt: FieldValue.serverTimestamp() }, { merge: true }));
    return { status: 200, body: { subscriptionId: event.subscriptionId, status: 'expired', revokedEntitlements: matching.length } };
  });
}

export const publishDeliveryQuote = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    try {
      const uid = requireSignedIn(request);
      assertRoleCapability(await firestore.collection('adminRoles').doc(uid).get(), 'manage_fulfillment');
      const now = Date.now();
      const planned = planDeliveryQuote({
        region: request.data?.region, currency: request.data?.currency, feeMinor: request.data?.feeMinor, validForHours: request.data?.validForHours
      }, now);
      if (!planned.ok) throw new HttpsError('invalid-argument', planned.reason);
      const { ok: _accepted, ...quote } = planned;
      const quoteRef = firestore.collection('deliveryQuotes').doc();
      const auditRef = firestore.collection('adminAuditEvents').doc();
      await firestore.runTransaction(async transaction => {
        assertRoleCapability(await transaction.get(firestore.collection('adminRoles').doc(uid)), 'manage_fulfillment');
        transaction.create(quoteRef, { ...quote, createdBy: uid, createdAt: FieldValue.serverTimestamp() });
        transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'publish_delivery_quote', targetId: quoteRef.id, details: { feeMinor: planned.feeMinor, expiresAt: planned.expiresAt } }));
      });
      return { quoteId: quoteRef.id, feeMinor: planned.feeMinor, currency: 'BDT', expiresAt: planned.expiresAt };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logFailure('publishDeliveryQuote', error);
      throw new HttpsError('internal', 'The delivery quote could not be published.');
    }
  }
);

export const recordFulfillmentUpdate = onCall(
  { region: FUNCTION_REGION, enforceAppCheck: true, maxInstances: 5 },
  async request => {
    try {
      const uid = requireSignedIn(request);
      const orderId = requiredString(request.data?.orderId, 'orderId', 64);
      if (!/^[a-f0-9]{64}$/.test(orderId)) throw new HttpsError('invalid-argument', 'The order ID is invalid.');
      const orderRef = firestore.collection('orders').doc(orderId);
      const auditRef = firestore.collection('adminAuditEvents').doc();
      const result = await firestore.runTransaction(async transaction => {
        const [roleSnapshot, orderSnapshot] = await Promise.all([
          transaction.get(firestore.collection('adminRoles').doc(uid)), transaction.get(orderRef)
        ]);
        assertRoleCapability(roleSnapshot, 'manage_fulfillment');
        const planned = planFulfillmentUpdate(orderSnapshot.exists ? orderSnapshot.data() : null, request.data ?? {});
        if (!planned.ok) throw new HttpsError('failed-precondition', planned.reason);
        transaction.set(orderRef, {
          fulfillmentState: planned.fulfillmentState, trackingReference: planned.trackingReference, updatedAt: FieldValue.serverTimestamp()
        }, { merge: true });
        transaction.create(auditRef, auditRecord({ actorUid: uid, action: 'record_fulfillment_update', targetId: orderId, details: { fulfillmentState: planned.fulfillmentState, trackingReference: planned.trackingReference } }));
        return { fulfillmentState: planned.fulfillmentState, trackingReference: planned.trackingReference };
      });
      return { orderId, fulfillmentState: result.fulfillmentState, trackingReference: result.trackingReference };
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      logFailure('recordFulfillmentUpdate', error);
      throw new HttpsError('internal', 'The fulfillment update could not be saved.');
    }
  }
);

export const paymentWebhook = onRequest(
  { region: FUNCTION_REGION, secrets: [paymentWebhookSecret], maxInstances: 10 },
  async (request, response) => {
    if (request.method !== 'POST') { response.status(405).send('POST only'); return; }
    const verdict = verifyPaymentWebhook(request.rawBody?.toString('utf8') ?? '', request.get('x-littlepages-signature') ?? '', paymentWebhookSecret.value());
    if (!verdict.ok) { response.status(verdict.status).json({ error: verdict.reason }); return; }
    try {
      const result = verdict.event.type === 'subscription_expired'
        ? await applySubscriptionExpiry(verdict.event)
        : await applyVerifiedPayment(verdict.event);
      response.status(result.status).json(result.body);
    } catch (error) {
      logFailure('paymentWebhook', error);
      response.status(500).json({ error: 'The payment event could not be applied.' });
    }
  }
);
