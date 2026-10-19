import { createHmac, randomBytes } from 'node:crypto';

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const VALID_ID = /^[a-z0-9_-]{2,80}$/;

/** Server-only token creation. Return the plaintext once; persist only its keyed digest. */
export function generateDiscountCode() {
  const bytes = randomBytes(16);
  const token = [...bytes].map((byte) => CODE_ALPHABET[byte % CODE_ALPHABET.length]).join('');
  return `LP-${token}`;
}

export function normalizeDiscountCode(code) {
  const normalized = String(code ?? '').trim().toUpperCase();
  if (!/^[A-Z0-9-]{4,32}$/.test(normalized)) throw new Error('Invalid discount code format.');
  return normalized;
}

/** The secret must come from a managed server secret store and must never reach a client. */
export function digestDiscountCode(code, hmacSecret) {
  if (typeof hmacSecret !== 'string' || hmacSecret.length < 32) throw new Error('Discount code HMAC secret is missing or too short.');
  return createHmac('sha256', hmacSecret).update(normalizeDiscountCode(code), 'utf8').digest('hex');
}

export function promotionMatchesProduct(promotion, product) {
  if (!['active', 'scheduled'].includes(promotion?.status) || product?.status !== 'published') return false;
  const targeting = promotion.targeting;
  if (targeting?.kind === 'all') return true;
  if (targeting?.kind === 'product') return targeting.productId === product.id;
  if (targeting?.kind === 'categories' && Array.isArray(targeting.categoryIds)) {
    const productCategories = new Set(product.categoryIds ?? []);
    return targeting.categoryIds.some((categoryId) => productCategories.has(categoryId));
  }
  return false;
}

export function isWithinPromotionSchedule(schedule, now = Date.now()) {
  const startsAt = Date.parse(schedule?.startsAt);
  const endsAt = Date.parse(schedule?.endsAt);
  return Number.isFinite(startsAt) && Number.isFinite(endsAt) && startsAt <= now && now < endsAt;
}

/** Prices are integer minor units; every discount has a one-minor-unit floor. */
export function applyDiscount(priceMinor, discount) {
  if (!Number.isSafeInteger(priceMinor) || priceMinor < 1) throw new Error('Product price must be a positive integer in minor units.');
  if (!Number.isSafeInteger(discount?.value) || discount.value < 1) throw new Error('Discount value must be a positive integer.');
  let requestedSavings;
  if (discount.type === 'percentage' && discount.value <= 99) {
    requestedSavings = Math.floor((priceMinor * discount.value) / 100);
  } else if (discount.type === 'fixed_amount' && discount.currency === 'BDT') {
    requestedSavings = discount.value;
  } else {
    throw new Error('Unsupported discount type or currency.');
  }
  const savingsMinor = Math.min(priceMinor - 1, requestedSavings);
  return { originalPriceMinor: priceMinor, discountMinor: savingsMinor, finalPriceMinor: priceMinor - savingsMinor };
}

/** Apply only the best eligible active automatic offer; stable ID order breaks ties. */
export function bestAutomaticDiscount(product, variant, promotions, now = Date.now()) {
  const priceMinor = variant?.priceMinor;
  if (!Number.isSafeInteger(priceMinor) || priceMinor <= 1) return null;
  const candidates = (promotions ?? [])
    .filter((promotion) => promotionMatchesProduct(promotion, product) && isWithinPromotionSchedule(promotion.schedule, now))
    .map((promotion) => ({ promotion, ...applyDiscount(priceMinor, promotion.discount) }))
    .filter((candidate) => candidate.discountMinor > 0)
    .sort((left, right) => right.discountMinor - left.discountMinor || left.promotion.id.localeCompare(right.promotion.id));
  return candidates[0] ?? null;
}

/** Compare an eligible entered code with automatic campaigns without ever stacking them. */
export function chooseBestSingleDiscount(automaticCandidate, codeCandidate) {
  return [automaticCandidate, codeCandidate]
    .filter((candidate) => candidate && candidate.discountMinor > 0)
    .sort((left, right) => right.discountMinor - left.discountMinor || String(left.promotion?.id ?? left.codeRecord?.id ?? '').localeCompare(String(right.promotion?.id ?? right.codeRecord?.id ?? '')))[0] ?? null;
}

export function discountCodeEligibility(codeRecord, { uid, isNewCustomer }) {
  if (!codeRecord || !uid) return false;
  if (codeRecord.eligibility === 'any') return true;
  if (codeRecord.eligibility === 'new_customer') return isNewCustomer === true;
  if (codeRecord.eligibility === 'assigned_account') return codeRecord.assignedCustomerIds?.includes(uid) === true;
  return false;
}

/**
 * Transaction adapters must call this only after reading product, campaign, and per-account
 * redemption counts inside the same database transaction that creates the pending order.
 */
export function validateCodeRedemption(codeRecord, { uid, isNewCustomer, product, variant, now = Date.now(), totalRedemptions = 0, customerRedemptions = 0 }) {
  if (!['active', 'scheduled'].includes(codeRecord?.status) || !isWithinPromotionSchedule(codeRecord.schedule, now)) throw new Error('Discount code is not active.');
  if (codeRecord.stackingPolicy !== 'best_single_discount' || codeRecord.priceFloorMinor !== 1) throw new Error('Unsupported discount-code pricing policy.');
  if (!discountCodeEligibility(codeRecord, { uid, isNewCustomer })) throw new Error('Customer is not eligible for this discount code.');
  if (codeRecord.totalRedemptionLimit != null && totalRedemptions >= codeRecord.totalRedemptionLimit) throw new Error('Discount code redemption limit has been reached.');
  if (!Number.isSafeInteger(codeRecord.perCustomerLimit) || customerRedemptions >= codeRecord.perCustomerLimit) throw new Error('Customer redemption limit has been reached.');
  if (!Number.isSafeInteger(totalRedemptions) || totalRedemptions < 0 || !Number.isSafeInteger(customerRedemptions) || customerRedemptions < 0) throw new Error('Redemption counters are invalid.');
  if (product?.status !== 'published' || !promotionMatchesProduct({ ...codeRecord, status: 'active' }, product)) throw new Error('Product is not eligible for this discount code.');
  return applyDiscount(variant?.priceMinor, codeRecord.discount);
}

export function assertValidPromotion(promotion, { productIds, categoryIds, storefrontIds }) {
  if (!promotion || !VALID_ID.test(promotion.id ?? '') || !['draft', 'scheduled', 'active', 'paused', 'archived'].includes(promotion.status)) throw new Error('Invalid promotion identity or status.');
  const startsAt = Date.parse(promotion.schedule?.startsAt);
  const endsAt = Date.parse(promotion.schedule?.endsAt);
  if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt) || endsAt <= startsAt) throw new Error('Invalid promotion schedule.');
  const targeting = promotion.targeting;
  if (targeting?.kind === 'product' && !productIds.has(targeting.productId)) throw new Error('Promotion references a missing product.');
  if (targeting?.kind === 'categories' && (!targeting.categoryIds?.length || targeting.categoryIds.some((id) => !categoryIds.has(id)))) throw new Error('Promotion references a missing category.');
  if (!['product', 'categories', 'all'].includes(targeting?.kind)) throw new Error('Invalid promotion target.');
  const destination = promotion.banner?.destination;
  const destinationIds = destination?.kind === 'product' ? productIds : destination?.kind === 'category' ? categoryIds : destination?.kind === 'storefront' ? storefrontIds : null;
  if (!destinationIds?.has(destination.targetId)) throw new Error('Promotion banner destination is invalid.');
  if (!promotion.name?.en?.trim() || !promotion.name?.bn?.trim() || !promotion.banner?.text?.en?.trim() || !promotion.banner?.text?.bn?.trim()) throw new Error('Promotion needs English and Bangla labels.');
  if (promotion.stackingPolicy !== 'best_single_discount' || promotion.priceFloorMinor !== 1) throw new Error('Unsupported promotion pricing policy.');
  applyDiscount(100, promotion.discount);
  return true;
}
