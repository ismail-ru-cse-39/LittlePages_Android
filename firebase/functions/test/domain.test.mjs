import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { createChildId, validateChildProfile, validateReadingProgress, validateReview } from '../account-domain.mjs';
import { describeBookAsset, rightsDeclaration } from '../asset-domain.mjs';
import { planPaymentTransition, verifyPaymentWebhook } from '../payment-domain.mjs';
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

test('reading progress and reviews reject unsafe values', () => {
  assert.equal(validateReadingProgress({ bookId: 'brave-little-fox', page: 3, bookmarks: [3, 8] }), null);
  assert.match(validateReadingProgress({ bookId: 'brave-little-fox', page: 0, bookmarks: [] }), /page/);
  assert.equal(validateReview({ bookId: 'brave-little-fox', rating: 5, body: 'A gentle story.' }), null);
  assert.match(validateReview({ bookId: 'brave-little-fox', rating: 6, body: 'A gentle story.' }), /rating/);
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
