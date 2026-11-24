import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { createChildId, validateChildProfile, validateCommunityText, validateReadingProgress, validateReview } from '../account-domain.mjs';
import { describeBookAsset, rightsDeclaration } from '../asset-domain.mjs';
import { planDeliveryQuote, planFulfillmentUpdate, planPhysicalCheckout } from '../fulfillment-domain.mjs';
import { parseNarrationTimings } from '../narration-domain.mjs';
import { operationFailure } from '../operation-log.mjs';
import { planPaymentTransition, planSubscriptionExpiry, verifyPaymentWebhook } from '../payment-domain.mjs';
import { SUPPORTED_BLOCK_TYPES } from '../publishing-domain.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '../../..');
const secret = 'test-payment-webhook-secret-value-32';

function signedEvent(event) {
  const raw = JSON.stringify(event);
  return { raw, signature: createHmac('sha256', secret).update(raw).digest('hex') };
}

const paidOrder = {
  status: 'pending_payment',
  paymentStatus: 'pending',
  currency: 'BDT',
  totalMinor: 45000,
  grantsDigitalAccess: true,
  discountReservationState: 'reserved',
  lineItems: [{ productId: 'brave-little-fox', variantId: 'in-app' }]
};

test('child profile ids and age bands stay parent-safe', () => {
  const childId = createChildId();
  assert.equal(validateChildProfile({ nickname: 'Emma', ageBand: '5 years old', childId }), null);
  assert.equal(validateChildProfile({ nickname: ' ', ageBand: '5 years old' }), 'Enter a nickname using 1–30 characters.');
  assert.match(validateChildProfile({ nickname: 'Emma', ageBand: '4 years old' }), /age bands/);
});

test('reading progress, reviews, and replies reject unsafe values', () => {
  assert.equal(validateReadingProgress({ bookId: 'brave-little-fox', page: 3, bookmarks: [3, 8] }), null);
  assert.match(validateReadingProgress({ bookId: 'brave-little-fox', page: 0, bookmarks: [] }), /page/);
  assert.equal(validateReview({ bookId: 'brave-little-fox', rating: 5, body: 'A gentle story.' }), null);
  assert.match(validateReview({ bookId: 'brave-little-fox', rating: 6, body: 'A gentle story.' }), /rating/);
  assert.equal(validateCommunityText('A kind reply for this discussion.', 'Comment'), null);
  assert.match(validateCommunityText('   ', 'Comment'), /Comment/);
  assert.match(validateCommunityText('x'.repeat(2001), 'Comment'), /2000/);
});

test('book asset paths match the private reader contract', () => {
  const asset = describeBookAsset({
    bookId: 'brave-little-fox', locale: 'en', contentVersion: 2, type: 'reader',
    contentType: 'application/pdf', byteLength: 1200, sha256: 'a'.repeat(64)
  });
  assert.equal(asset.assetId, 'brave-little-fox-en-v2-reader');
  assert.equal(asset.objectPath, 'books/brave-little-fox/v2/en/reader.pdf');
  const rights = rightsDeclaration({ holder: 'LittlePages', licenseReference: 'LIC-1', expiresAt: null });
  assert.equal(rights.declarationOnly, true);
  assert.throws(() => describeBookAsset({ ...asset, type: 'reader', contentType: 'text/plain', byteLength: 10, sha256: 'a'.repeat(64), bookId: 'brave-little-fox', locale: 'en', contentVersion: 1 }), /file type/);
});

test('verified payment grants digital access and a mismatched amount does not', () => {
  const event = {
    provider: 'stub', providerEventId: 'event-0001', type: 'payment_succeeded',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date().toISOString()
  };
  const signed = signedEvent(event);
  assert.equal(verifyPaymentWebhook(signed.raw, signed.signature, secret).ok, true);
  assert.equal(verifyPaymentWebhook(signed.raw, 'b'.repeat(64), secret).ok, false);
  assert.equal(verifyPaymentWebhook(signed.raw, signed.signature, 'short').status, 503);
  const plan = planPaymentTransition(paidOrder, event, Date.now());
  assert.equal(plan.entitlement, 'grant');
  assert.equal(plan.reservation, 'settle');
  const mismatch = planPaymentTransition(paidOrder, { ...event, amountMinor: 1 }, Date.now());
  assert.equal(mismatch.action, 'reject');
  const clientEvent = signedEvent({ ...event, provider: 'client' });
  assert.equal(verifyPaymentWebhook(clientEvent.raw, clientEvent.signature, secret).ok, false);
});

test('refunds revoke access and failed payments release a reservation', () => {
  const now = Date.now();
  const failed = planPaymentTransition(paidOrder, {
    provider: 'stub', providerEventId: 'event-0002', type: 'payment_failed',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date(now).toISOString()
  }, now);
  assert.equal(failed.reservation, 'release');
  assert.equal(failed.entitlement, 'none');
  const refund = planPaymentTransition({ ...paidOrder, status: 'paid', paymentStatus: 'succeeded', discountReservationState: 'settled' }, {
    provider: 'stub', providerEventId: 'event-0003', type: 'refunded',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date(now).toISOString()
  }, now);
  assert.equal(refund.entitlement, 'revoke');
  assert.equal(refund.reservation, 'none');
  assert.equal(planPaymentTransition({ ...paidOrder, grantsDigitalAccess: false }, {
    provider: 'stub', providerEventId: 'event-0004', type: 'payment_succeeded',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date(now).toISOString()
  }, now).entitlement, 'none');
});

test('printed stock is reserved only with a current Bangladesh delivery quote', () => {
  const now = Date.now();
  const variant = { stockState: 'in_stock', stockQuantity: 2 };
  const quote = { status: 'active', region: 'BD', currency: 'BDT', feeMinor: 8000, expiresAt: new Date(now + 60_000).toISOString() };
  const reserved = planPhysicalCheckout({ variant, region: 'BD', quote, available: 2, now });
  assert.equal(reserved.ok, true);
  assert.equal(reserved.availableAfter, 1);
  assert.equal(reserved.deliveryFeeMinor, 8000);
  assert.equal(planPhysicalCheckout({ variant, region: 'BD', quote, available: 0, now }).ok, false);
  assert.equal(planPhysicalCheckout({ variant, region: 'US', quote, available: 2, now }).ok, false);
  assert.match(planPhysicalCheckout({ variant, region: 'BD', quote: { ...quote, expiresAt: new Date(now - 1000).toISOString() }, available: 2, now }).reason, /expired/);
  const published = planDeliveryQuote({ region: 'BD', currency: 'BDT', feeMinor: 8000, validForHours: 4 }, now);
  assert.equal(published.ok, true);
  assert.equal(planDeliveryQuote({ region: 'BD', currency: 'BDT', feeMinor: -1, validForHours: 4 }, now).ok, false);
  const paid = { fulfillmentType: 'physical', paymentStatus: 'succeeded', fulfillmentState: 'paid_awaiting_fulfillment' };
  const handed = planFulfillmentUpdate(paid, { status: 'handed_to_carrier', trackingReference: 'BD-100' });
  assert.equal(handed.fulfillmentState, 'handed_to_carrier');
  assert.equal(planFulfillmentUpdate(paid, { status: 'delivered', trackingReference: 'BD-100' }).ok, false);
  assert.equal(planFulfillmentUpdate({ ...paid, paymentStatus: 'pending' }, { status: 'handed_to_carrier', trackingReference: 'BD-100' }).ok, false);
});

test('subscription expiry revokes only an active subscription and keeps book purchases untouched', () => {
  const now = Date.now();
  const event = {
    provider: 'stub', providerEventId: 'sub-event-1', type: 'subscription_expired',
    subscriptionId: 'sub-premium', occurredAt: new Date(now).toISOString()
  };
  const signed = signedEvent(event);
  assert.equal(verifyPaymentWebhook(signed.raw, signed.signature, secret).ok, true);
  const withOrder = signedEvent({ ...event, orderId: 'a'.repeat(64), amountMinor: 100 });
  assert.equal(verifyPaymentWebhook(withOrder.raw, withOrder.signature, secret).ok, false);
  const plan = planSubscriptionExpiry({ state: 'active', provider: 'stub', uid: 'parent-1', productId: 'premium' }, event, now);
  assert.equal(plan.action, 'expire');
  assert.equal(plan.productId, 'premium');
  assert.equal(planSubscriptionExpiry({ state: 'expired', provider: 'stub', uid: 'parent-1', productId: 'premium', settledProviderEventId: 'stub:sub-event-1' }, event, now).action, 'noop');
  assert.equal(planSubscriptionExpiry({ state: 'active', provider: 'other', uid: 'parent-1', productId: 'premium' }, event, now).action, 'reject');
  const failedPhysical = planPaymentTransition({
    ...paidOrder, grantsDigitalAccess: false, fulfillmentType: 'physical'
  }, {
    provider: 'stub', providerEventId: 'event-0099', type: 'payment_failed',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date(now).toISOString()
  }, now);
  assert.equal(failedPhysical.stock, 'release');
  assert.equal(planPaymentTransition(paidOrder, {
    provider: 'stub', providerEventId: 'event-0098', type: 'payment_failed',
    orderId: 'a'.repeat(64), amountMinor: 45000, currency: 'BDT', occurredAt: new Date(now).toISOString()
  }, now).stock, 'none');
});

test('approved narration timings reject overlapping or out-of-range words', () => {
  const parsed = parseNarrationTimings({
    schemaVersion: 1,
    pages: [{ page: 1, text: 'Hello world', words: [{ start: 0, end: 5, startMs: 0, endMs: 400 }, { start: 6, end: 11, startMs: 400, endMs: 900 }] }]
  });
  assert.equal(parsed.pages[0].words.length, 2);
  assert.throws(() => parseNarrationTimings({ schemaVersion: 1, pages: [{ page: 1, text: 'Hello', words: [{ start: 0, end: 5, startMs: 0, endMs: 10 }, { start: 0, end: 5, startMs: 5, endMs: 20 }] }] }), /move forward/);
  assert.throws(() => parseNarrationTimings({ schemaVersion: 1, pages: [{ page: 1, text: 'Hello', words: [{ start: 0, end: 8, startMs: 0, endMs: 10 }] }] }), /range/);
  assert.throws(() => parseNarrationTimings({ words: [{ startMs: 0, endMs: 10 }] }), /schema/);
  const failure = operationFailure('paymentWebhook', { code: 'aborted', message: 'x'.repeat(400) });
  assert.equal(failure.event, 'littlepages.operation_failed');
  assert.equal(failure.message.length, 300);
  assert.equal(Object.hasOwn(failure, 'stack'), false);
});

test('Android, website, and admin use the same page block contract', () => {
  const sources = [
    ['website/site.js', readFileSync(join(root, 'website/site.js'), 'utf8')],
    ['admin/admin.js', readFileSync(join(root, 'admin/admin.js'), 'utf8')],
    ['app cache', readFileSync(join(root, 'app/src/main/java/com/littlepages/app/data/SharedPreferencesStorefrontConfigurationCache.kt'), 'utf8')]
  ];
  for (const [name, source] of sources) {
    for (const blockType of SUPPORTED_BLOCK_TYPES) assert.equal(source.includes(blockType), true, `${name} is missing ${blockType}`);
  }
  assert.deepEqual([...SUPPORTED_BLOCK_TYPES], ['hero_v1', 'category_rail_v1', 'product_collection_v1', 'editorial_text_v1']);
});
