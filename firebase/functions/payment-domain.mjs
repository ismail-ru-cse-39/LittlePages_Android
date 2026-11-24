import { createHmac, timingSafeEqual } from 'node:crypto';

const EVENT_TYPES = new Set(['payment_succeeded', 'payment_failed', 'refunded', 'chargeback', 'subscription_expired']);
const EVENT_ID = /^[A-Za-z0-9_-]{8,80}$/;
const ORDER_ID = /^[a-f0-9]{64}$/;
const ACCEPTED_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000;

export function verifyPaymentWebhook(rawBody, signatureHeader, secret) {
  if (typeof secret !== 'string' || secret.length < 32) {
    return { ok: false, status: 503, reason: 'Payment webhook secret is not configured.' };
  }
  if (typeof rawBody !== 'string' || !rawBody || typeof signatureHeader !== 'string' || !/^[a-f0-9]{64}$/.test(signatureHeader)) {
    return { ok: false, status: 401, reason: 'Payment signature is missing or malformed.' };
  }
  const expected = createHmac('sha256', secret).update(rawBody).digest('hex');
  const provided = Buffer.from(signatureHeader);
  const actual = Buffer.from(expected);
  if (provided.length !== actual.length || !timingSafeEqual(provided, actual)) {
    return { ok: false, status: 401, reason: 'Payment signature does not match.' };
  }
  let event;
  try { event = JSON.parse(rawBody); }
  catch { return { ok: false, status: 400, reason: 'Payment event is not valid JSON.' }; }
  const reason = validatePaymentEvent(event);
  if (reason) return { ok: false, status: 400, reason };
  return { ok: true, event };
}

export function validatePaymentEvent(event) {
  if (!event || typeof event !== 'object' || Array.isArray(event)) return 'Payment event is invalid.';
  if (typeof event.provider !== 'string' || !/^[a-z0-9_-]{2,32}$/.test(event.provider)) return 'Payment provider is invalid.';
  if (event.provider === 'client') return 'A client success screen cannot report payment.';
  if (typeof event.providerEventId !== 'string' || !EVENT_ID.test(event.providerEventId)) return 'Payment event ID is invalid.';
  if (!EVENT_TYPES.has(event.type)) return 'Payment event type is unsupported.';
  if (event.type === 'subscription_expired') {
    if (typeof event.subscriptionId !== 'string' || !EVENT_ID.test(event.subscriptionId)) return 'Subscription ID is invalid.';
    if (event.orderId != null || event.amountMinor != null) return 'Subscription expiry cannot carry an order or amount.';
    if (typeof event.occurredAt !== 'string' || !Number.isFinite(Date.parse(event.occurredAt))) return 'Payment time is invalid.';
    return null;
  }
  if (typeof event.orderId !== 'string' || !ORDER_ID.test(event.orderId)) return 'Order ID is invalid.';
  if (!Number.isSafeInteger(event.amountMinor) || event.amountMinor < 1) return 'Payment amount is invalid.';
  if (event.currency !== 'BDT') return 'Payment currency must be BDT.';
  if (typeof event.occurredAt !== 'string' || !Number.isFinite(Date.parse(event.occurredAt))) return 'Payment time is invalid.';
  return null;
}

/** Decide the next order, reservation, and entitlement change. Never grants access by itself. */
export function planPaymentTransition(order, event, now) {
  if (!order) return { action: 'reject', status: 404, reason: 'Order was not found.' };
  if (order.currency !== 'BDT' || !Number.isSafeInteger(order.totalMinor) || order.totalMinor < 1) {
    return { action: 'reject', status: 409, reason: 'Stored order price is invalid.' };
  }
  const occurred = Date.parse(event.occurredAt);
  if (!Number.isFinite(occurred) || occurred > now + 5 * 60 * 1000 || now - occurred > ACCEPTED_WINDOW_MILLIS) {
    return { action: 'reject', status: 400, reason: 'Payment event time is outside the accepted window.' };
  }
  if (event.amountMinor !== order.totalMinor || event.currency !== order.currency) {
    return { action: 'reject', status: 409, reason: 'Payment amount does not match the reserved order.' };
  }
  const eventKey = `${event.provider}:${event.providerEventId}`;
  if (order.settledProviderEventId === eventKey && order.paymentStatus !== 'pending') {
    return { action: 'noop', status: 200, orderStatus: order.status, paymentStatus: order.paymentStatus };
  }
  if (event.type === 'payment_succeeded') {
    if (order.status !== 'pending_payment') return { action: 'reject', status: 409, reason: 'This order is not awaiting payment.' };
    return {
      action: 'apply',
      status: 200,
      orderStatus: 'paid',
      paymentStatus: 'succeeded',
      reservation: order.discountReservationState === 'reserved' ? 'settle' : 'none',
      entitlement: order.grantsDigitalAccess === true ? 'grant' : 'none',
      fulfillment: order.grantsDigitalAccess === true ? 'digital_access_updated' : 'paid_awaiting_fulfillment',
      stock: 'none'
    };
  }
  if (event.type === 'payment_failed') {
    if (order.status !== 'pending_payment') return { action: 'reject', status: 409, reason: 'This order is not awaiting payment.' };
    return {
      action: 'apply',
      status: 200,
      orderStatus: 'payment_failed',
      paymentStatus: 'failed',
      reservation: order.discountReservationState === 'reserved' ? 'release' : 'none',
      entitlement: 'none',
      fulfillment: 'none',
      stock: order.fulfillmentType === 'physical' ? 'release' : 'none'
    };
  }
  if (event.type === 'refunded' || event.type === 'chargeback') {
    if (order.status !== 'paid') return { action: 'reject', status: 409, reason: 'Only a paid order can be refunded or charged back.' };
    const refunded = event.type === 'refunded';
    return {
      action: 'apply',
      status: 200,
      orderStatus: refunded ? 'refunded' : 'chargeback',
      paymentStatus: refunded ? 'refunded' : 'chargeback',
      reservation: 'none',
      entitlement: 'revoke',
      fulfillment: order.grantsDigitalAccess === true ? 'digital_access_revoked' : 'refund_pending_owner',
      stock: 'none'
    };
  }
  return { action: 'reject', status: 400, reason: 'Payment event type is unsupported.' };
}

/** Expire a subscription without touching book-purchase entitlements. */
export function planSubscriptionExpiry(subscription, event, now) {
  if (!subscription) return { action: 'reject', status: 404, reason: 'Subscription was not found.' };
  if (subscription.provider !== event.provider) return { action: 'reject', status: 409, reason: 'Subscription provider does not match the event.' };
  if (typeof subscription.uid !== 'string' || !subscription.uid || subscription.uid.includes('/')) {
    return { action: 'reject', status: 409, reason: 'Stored subscription account is invalid.' };
  }
  if (typeof subscription.productId !== 'string' || !/^[A-Za-z0-9_-]{2,80}$/.test(subscription.productId)) {
    return { action: 'reject', status: 409, reason: 'Stored subscription product is invalid.' };
  }
  const occurred = Date.parse(event.occurredAt);
  if (!Number.isFinite(occurred) || occurred > now + 5 * 60 * 1000 || now - occurred > ACCEPTED_WINDOW_MILLIS) {
    return { action: 'reject', status: 400, reason: 'Subscription event time is outside the accepted window.' };
  }
  const eventKey = `${event.provider}:${event.providerEventId}`;
  if (subscription.state === 'expired' && subscription.settledProviderEventId === eventKey) return { action: 'noop', status: 200 };
  if (subscription.state !== 'active') return { action: 'reject', status: 409, reason: 'Only an active subscription can expire.' };
  return { action: 'expire', status: 200, uid: subscription.uid, productId: subscription.productId };
}
