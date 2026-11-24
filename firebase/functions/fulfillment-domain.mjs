const TRACKING_REFERENCE = /^[A-Za-z0-9-]{4,40}$/;
const MAX_DELIVERY_FEE_MINOR = 10_000_000;

/** A printed checkout needs confirmed published stock and an unexpired Bangladesh quote. The fee is never chosen by the client. */
export function planPhysicalCheckout({ variant, region, quote, available, now }) {
  if (region !== 'BD') return { ok: false, reason: 'Printed books can only be reserved for delivery in Bangladesh.' };
  if (!variant || variant.stockState !== 'in_stock') return { ok: false, reason: 'This option does not have confirmed stock.' };
  if (!Number.isSafeInteger(variant.stockQuantity) || variant.stockQuantity < 1) return { ok: false, reason: 'Published stock is not confirmed.' };
  if (!Number.isSafeInteger(available) || available < 1 || available > variant.stockQuantity) {
    return { ok: false, reason: 'This option is no longer in stock.' };
  }
  if (!quote || quote.status !== 'active' || quote.region !== 'BD' || quote.currency !== 'BDT') {
    return { ok: false, reason: 'A confirmed Bangladesh delivery quote is required.' };
  }
  if (!Number.isSafeInteger(quote.feeMinor) || quote.feeMinor < 0 || quote.feeMinor > MAX_DELIVERY_FEE_MINOR) {
    return { ok: false, reason: 'The delivery fee is invalid.' };
  }
  const expiresAt = Date.parse(quote.expiresAt);
  if (!Number.isFinite(expiresAt) || expiresAt <= now) return { ok: false, reason: 'The delivery quote has expired.' };
  return { ok: true, deliveryFeeMinor: quote.feeMinor, availableAfter: available - 1, fulfillmentState: 'stock_reserved' };
}

/** Staff publish the fee. This does not approve a courier contract. */
export function planDeliveryQuote({ region, currency, feeMinor, validForHours }, now) {
  if (region !== 'BD' || currency !== 'BDT') return { ok: false, reason: 'Delivery quotes are only available for Bangladesh in BDT.' };
  if (!Number.isSafeInteger(feeMinor) || feeMinor < 0 || feeMinor > MAX_DELIVERY_FEE_MINOR) return { ok: false, reason: 'The delivery fee is invalid.' };
  const hours = validForHours ?? 4;
  if (!Number.isSafeInteger(hours) || hours < 1 || hours > 24) return { ok: false, reason: 'A delivery quote can last from 1 to 24 hours.' };
  return {
    ok: true,
    region: 'BD',
    currency: 'BDT',
    feeMinor,
    status: 'active',
    expiresAt: new Date(now + hours * 60 * 60 * 1000).toISOString()
  };
}

const NEXT_FULFILLMENT = Object.freeze({
  paid_awaiting_fulfillment: 'handed_to_carrier',
  handed_to_carrier: 'delivered'
});

export function planFulfillmentUpdate(order, update) {
  if (!order || order.fulfillmentType !== 'physical' || order.paymentStatus !== 'succeeded') {
    return { ok: false, reason: 'Only a paid printed order can be updated.' };
  }
  const next = update?.status;
  if (NEXT_FULFILLMENT[order.fulfillmentState] !== next) return { ok: false, reason: 'That fulfillment step is not available.' };
  if (next === 'handed_to_carrier' && (typeof update.trackingReference !== 'string' || !TRACKING_REFERENCE.test(update.trackingReference))) {
    return { ok: false, reason: 'Enter a tracking reference using 4–40 letters, numbers, or hyphens.' };
  }
  return {
    ok: true,
    fulfillmentState: next,
    trackingReference: next === 'handed_to_carrier' ? update.trackingReference : order.trackingReference ?? null
  };
}
