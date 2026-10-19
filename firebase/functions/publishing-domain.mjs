import { createHash } from 'node:crypto';
import { assertValidPromotion } from './discount-domain.mjs';

const ID = /^[a-z0-9_-]{2,60}$/;
const TYPE_RENDERERS = new Set(['book_v1', 'physical_good_v1']);
const BLOCK_TYPES = new Set(['hero_v1', 'category_rail_v1', 'product_collection_v1', 'editorial_text_v1']);
const REQUIRED_POLICY_KEYS = ['offlinePolicy', 'accessPolicy', 'privacyPolicy', 'physicalCommercePolicy', 'communityPolicy'];

export const MAX_DRAFT_BYTES = 700 * 1024;
export const MAX_PRODUCTS = 300;
export const MAX_PROMOTIONS = 100;

export function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

export function contentHash(value) {
  return createHash('sha256').update(stableStringify(value), 'utf8').digest('hex');
}

function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function uniqueById(items, label, errors) {
  if (!Array.isArray(items)) { errors.push(`${label} must be an array.`); return new Map(); }
  const map = new Map();
  for (const item of items) {
    if (!object(item) || typeof item.id !== 'string' || !ID.test(item.id)) { errors.push(`${label} contains an invalid ID.`); continue; }
    if (map.has(item.id)) errors.push(`${label} IDs must be unique (${item.id}).`);
    map.set(item.id, item);
  }
  return map;
}
function localized(value, label, errors, required = true, maxLength = 100) {
  if (!object(value)) { if (required) errors.push(`${label} must have localized English and Bangla values.`); return; }
  for (const locale of ['en', 'bn']) {
    if (required && (typeof value[locale] !== 'string' || !value[locale].trim())) errors.push(`${label} needs ${locale}.`);
    if (value[locale] != null && (typeof value[locale] !== 'string' || value[locale].length > maxLength)) errors.push(`${label} has an invalid ${locale} value.`);
  }
}
function validateProduct(product, types, categories, storefronts, errors) {
  if (!object(product) || !ID.test(product.id ?? '')) { errors.push('A product has an invalid ID.'); return; }
  if (!['draft', 'published', 'archived'].includes(product.status)) errors.push(`${product.id} has an invalid publication status.`);
  localized(product.title, `${product.id} title`, errors);
  localized(product.description, `${product.id} description`, errors, false, 5000);
  const type = types.get(product.typeId);
  if (!type || !type.enabled || !TYPE_RENDERERS.has(type.renderer)) errors.push(`${product.id} uses an unsupported or disabled product type.`);
  if (!Array.isArray(product.categoryIds) || new Set(product.categoryIds).size !== (product.categoryIds ?? []).length) errors.push(`${product.id} has invalid or duplicate categories.`);
  else product.categoryIds.forEach(id => {
    const category = categories.get(id);
    if (!category) errors.push(`${product.id} references missing category ${id}.`);
    else if (type && !storefronts.get(category.storefrontId)?.productTypeIds?.includes(product.typeId)) errors.push(`${product.id} uses a category outside its product type storefront.`);
  });
  if (product.imageAssetIds != null && (!Array.isArray(product.imageAssetIds) || product.imageAssetIds.length > 20 || product.imageAssetIds.some(id => typeof id !== 'string' || !ID.test(id)))) errors.push(`${product.id} has invalid image asset references.`);
  if (!Array.isArray(product.variants) || product.variants.length < 1 || product.variants.length > 100) errors.push(`${product.id} must have 1–100 variants.`);
  else {
    const variantIds = new Set();
    product.variants.forEach(variant => {
      if (!object(variant)) { errors.push(`${product.id} has an invalid variant.`); return; }
      if (typeof variant.id !== 'string' || !/^[a-z0-9_-]{1,80}$/.test(variant.id) || variantIds.has(variant.id)) errors.push(`${product.id} has an invalid or duplicate variant ID.`);
      else variantIds.add(variant.id);
      localized(variant.label, `${product.id} variant label`, errors);
      if (!Number.isSafeInteger(variant.priceMinor) || variant.priceMinor < 0 || variant.currency !== 'BDT' || !['in_stock', 'out_of_stock', 'preorder', 'not_applicable'].includes(variant.stockState)) errors.push(`${product.id} has an invalid BDT variant price or stock state.`);
      if (variant.stockQuantity != null && (!Number.isSafeInteger(variant.stockQuantity) || variant.stockQuantity < 0)) errors.push(`${product.id} has an invalid stock quantity.`);
    });
  }
  if (!object(product.attributes)) errors.push(`${product.id} attributes must be an object.`);
  if (object(type) && Array.isArray(type.attributes)) {
    const declaredAttributeIds = new Set();
    for (const definition of type.attributes) {
      if (!object(definition) || typeof definition.id !== 'string' || declaredAttributeIds.has(definition.id)) { errors.push(`${type.id} has invalid or duplicate attribute definitions.`); continue; }
      declaredAttributeIds.add(definition.id);
      localized(definition.label, `${type.id}.${definition.id}`, errors);
      if (!['text', 'number', 'boolean', 'enum', 'text_list'].includes(definition.valueType) || typeof definition.required !== 'boolean') errors.push(`${type.id}.${definition.id} has an unsupported attribute definition.`);
      if (definition.valueType === 'enum' && (!Array.isArray(definition.options) || !definition.options.length || definition.options.some(option => !object(option) || typeof option.value !== 'string') || new Set((definition.options ?? []).filter(object).map(option => option.value)).size !== definition.options.length)) errors.push(`${type.id}.${definition.id} needs unique enum options.`);
      if (definition.valueType === 'enum' && Array.isArray(definition.options)) definition.options.forEach(option => { if (object(option)) localized(option.label, `${type.id}.${definition.id} option`, errors); });
      const value = product.attributes?.[definition.id];
      if (definition.required && (value === undefined || value === null || value === '')) errors.push(`${product.id} is missing required attribute ${definition.id}.`);
      if (value === undefined) continue;
      const valid = definition.valueType === 'text' ? typeof value === 'string' :
        definition.valueType === 'number' ? typeof value === 'number' && Number.isFinite(value) :
          definition.valueType === 'boolean' ? typeof value === 'boolean' :
            definition.valueType === 'enum' ? Array.isArray(definition.options) && definition.options.some(option => object(option) && option.value === value) :
              definition.valueType === 'text_list' ? Array.isArray(value) && value.every(item => typeof item === 'string') : false;
      if (!valid) errors.push(`${product.id} has an invalid ${definition.id} attribute.`);
    }
    if (object(product.attributes) && Object.keys(product.attributes).some(id => !declaredAttributeIds.has(id))) errors.push(`${product.id} has attributes not declared by its product type.`);
  }
  if (type?.renderer === 'book_v1') {
    const details = product.bookDetails;
    if (!object(details) || !['0-2', '3-5', '6-8', '9-12'].includes(details.ageBand) || typeof details.readingLevel !== 'string' || !details.readingLevel.trim() || !Number.isSafeInteger(details.pageCount) || details.pageCount < 1 || !Number.isSafeInteger(details.contentVersion) || details.contentVersion < 1 || !Array.isArray(details.locales) || !details.locales.length || details.locales.some(locale => !['en', 'bn'].includes(locale)) || !['free', 'premium', 'paid'].includes(details.access) || details.pdfInAppEnabled !== true) errors.push(`${product.id} has invalid book-specific metadata.`);
    if (details?.physicalEdition != null && (!object(details.physicalEdition) || typeof details.physicalEdition.available !== 'boolean')) errors.push(`${product.id} has invalid physical edition metadata.`);
    if (details?.physicalEdition?.available && !product.variants?.some(variant => variant.id === details.physicalEdition.variantId)) errors.push(`${product.id} references a missing physical edition variant.`);
  }
}

function validatePolicies(configuration, errors) {
  const offline = configuration.offlinePolicy;
  const access = configuration.accessPolicy;
  const privacy = configuration.privacyPolicy;
  const commerce = configuration.physicalCommercePolicy;
  const community = configuration.communityPolicy;
  if (!['active_book_entitlement', 'active_subscription', 'admin_grant'].includes(offline?.eligibility) || typeof offline?.enabled !== 'boolean' || !Number.isSafeInteger(offline?.maximumBooksPerFamily) || offline.maximumBooksPerFamily < 0 || !Number.isSafeInteger(offline?.accessLeaseDays) || offline.accessLeaseDays < 1 || !Number.isSafeInteger(offline?.onlineRefreshAfterDays) || offline.onlineRefreshAfterDays < 1 || offline.onlineRefreshAfterDays > offline.accessLeaseDays || typeof offline.encryptAppPrivateStorage !== 'boolean' || typeof offline.removeWhenEntitlementExpires !== 'boolean' || offline.allowExport !== false) errors.push('Offline policy is invalid or allows export.');
  if (!['book_catalogue_flag', 'none'].includes(access?.freeAccess) || typeof access.premiumBooksRequireActiveSubscription !== 'boolean' || typeof access.premiumNarrationRequiresActiveSubscription !== 'boolean' || typeof access.purchasedBookAccessSurvivesSubscriptionExpiry !== 'boolean' || access.grantAccessFromClientSuccessScreen !== false) errors.push('Book access policy is invalid or grants access from a client success screen.');
  if (['parentAccountOwnsChildProfiles', 'collectOnlyNicknameAndAgeBandForChildProfile', 'personalizedAdvertising', 'parentCanRequestAccountAndChildDataDeletion', 'requiresOwnerAndLegalReview'].some(key => typeof privacy?.[key] !== 'boolean') || privacy?.deletionRetentionDays != null && (!Number.isSafeInteger(privacy.deletionRetentionDays) || privacy.deletionRetentionDays < 0)) errors.push('Family privacy policy is invalid.');
  if (typeof commerce?.enabled !== 'boolean' || !Array.isArray(commerce.availableRegions) || commerce.availableRegions.some(region => typeof region !== 'string' || region.length < 2) || commerce.currency !== 'BDT' || !Array.isArray(commerce.paymentMethods) || commerce.paymentMethods.some(method => !['bkash', 'visa'].includes(method)) || new Set(commerce.paymentMethods).size !== commerce.paymentMethods.length || typeof commerce.showInAppPdfAndPhysicalOptionsWhenBothAvailable !== 'boolean' || typeof commerce.requireConfirmedStockAndDeliveryQuoteBeforeCheckout !== 'boolean' || commerce.cancellationAndRefundRules != null && typeof commerce.cancellationAndRefundRules !== 'string' || typeof commerce.requiresOwnerReview !== 'boolean') errors.push('Physical commerce policy is invalid.');
  if (['parentAccountRequired', 'moderateBeforePublicVisibility', 'prohibitChildContactAndLocationDetails', 'allowUserCreatedGroups', 'reportAndBlockEnabled', 'requiresOwnerReview'].some(key => typeof community?.[key] !== 'boolean') || !['admin_managed', 'parent_managed'].includes(community?.groupOwnership) || !Array.isArray(community.allowedImageMimeTypes) || community.allowedImageMimeTypes.some(type => !['image/jpeg', 'image/png', 'image/webp'].includes(type)) || !Number.isSafeInteger(community.maximumImageBytes) || community.maximumImageBytes < 0 || !Number.isSafeInteger(community.maximumImagesPerPost) || community.maximumImagesPerPost < 0 || community.moderationResponseTargetHours != null && (!Number.isSafeInteger(community.moderationResponseTargetHours) || community.moderationResponseTargetHours < 0)) errors.push('Community policy is invalid.');
}

export function validatePublicationDraft(draft) {
  const errors = [];
  if (!object(draft) || !object(draft.configuration) || !Array.isArray(draft.products) || !Array.isArray(draft.promotions)) return ['Draft must include configuration, products, and promotions.'];
  if (Buffer.byteLength(stableStringify(draft), 'utf8') > MAX_DRAFT_BYTES) errors.push(`Draft exceeds the ${MAX_DRAFT_BYTES} byte limit.`);
  const config = draft.configuration;
  if (config.schemaVersion !== 1 || !Number.isSafeInteger(config.revision) || config.revision < 1 || !['provisional', 'approved'].includes(config.status)) errors.push('Configuration identity, schema, or status is invalid.');
  const catalog = config.catalog;
  if (!object(catalog) || !Array.isArray(catalog.supportedLocales) || !catalog.supportedLocales.length || catalog.supportedLocales.some(locale => !['en', 'bn'].includes(locale)) || new Set(catalog.supportedLocales).size !== catalog.supportedLocales.length) errors.push('Supported catalogue locales are invalid.');
  REQUIRED_POLICY_KEYS.forEach(key => { if (!object(config[key])) errors.push(`Configuration is missing ${key}.`); });
  if (REQUIRED_POLICY_KEYS.every(key => object(config[key]))) validatePolicies(config, errors);
  for (const key of ['bookCategories', 'communityCategories']) {
    const list = catalog?.[key];
    if (!Array.isArray(list)) { errors.push(`Catalogue ${key} must be an array.`); continue; }
    const ids = new Set();
    list.forEach(category => {
      if (!object(category) || !ID.test(category.id ?? '') || ids.has(category.id)) errors.push(`${key} contains an invalid or duplicate category ID.`);
      else ids.add(category.id);
      if (object(category)) { localized(category.label, `${key} label`, errors); if (typeof category.enabled !== 'boolean' || !Number.isSafeInteger(category.sortOrder)) errors.push(`${key} contains invalid category settings.`); }
    });
  }
  const types = uniqueById(catalog?.productTypes, 'Product types', errors);
  types.forEach(type => {
    localized(type.label, `Product type ${type.id}`, errors);
    if (!TYPE_RENDERERS.has(type.renderer) || type.schemaVersion !== 1 || typeof type.enabled !== 'boolean') errors.push(`${type.id} uses an unsupported product renderer or schema.`);
    if (!Array.isArray(type.attributes) || type.attributes.length > 50) errors.push(`${type.id} has invalid attribute definitions.`);
  });
  const storefronts = uniqueById(catalog?.storefronts, 'Storefronts', errors);
  storefronts.forEach(store => {
    localized(store.title, `Storefront ${store.id}`, errors);
    if (!/^[a-z0-9-]{2,80}$/.test(store.slug ?? '') || !Array.isArray(store.productTypeIds) || !store.productTypeIds.length || store.productTypeIds.some(id => !types.has(id)) || new Set(store.productTypeIds).size !== store.productTypeIds.length || !Number.isSafeInteger(store.sortOrder) || typeof store.enabled !== 'boolean' || store.categoryIds != null && (!Array.isArray(store.categoryIds) || new Set(store.categoryIds).size !== store.categoryIds.length) || store.landingPageId != null && typeof store.landingPageId !== 'string') errors.push(`${store.id} has invalid type, slug, or order settings.`);
  });
  const categories = uniqueById(catalog?.productCategories, 'Product categories', errors);
  categories.forEach(category => {
    localized(category.label, `Category ${category.id}`, errors);
    if (!storefronts.has(category.storefrontId) || typeof category.enabled !== 'boolean' || !Number.isSafeInteger(category.sortOrder)) errors.push(`${category.id} has an invalid storefront or order.`);
  });
  const pages = uniqueById(catalog?.storePages, 'Store pages', errors);
  const products = uniqueById(draft.products, 'Products', errors);
  if (products.size > MAX_PRODUCTS) errors.push(`Catalogue exceeds the ${MAX_PRODUCTS} product limit.`);
  const promotions = uniqueById(draft.promotions, 'Promotions', errors);
  if (promotions.size > MAX_PROMOTIONS) errors.push(`Catalogue exceeds the ${MAX_PROMOTIONS} promotion limit.`);
  products.forEach(product => validateProduct(product, types, categories, storefronts, errors));
  storefronts.forEach(store => {
    if (Array.isArray(store.categoryIds) && store.categoryIds.some(id => categories.get(id)?.storefrontId !== store.id)) errors.push(`${store.id} references a category from another storefront.`);
    if (store.landingPageId && !pages.has(store.landingPageId)) errors.push(`${store.id} references a missing landing page.`);
  });
  pages.forEach(page => {
    localized(page.title, `Page ${page.id}`, errors);
    if (page.schemaVersion !== 1 || typeof page.enabled !== 'boolean' || !Array.isArray(page.blocks) || page.blocks.length > 40) errors.push(`${page.id} has an unsupported page schema or too many blocks.`);
    const blockIds = new Set();
    for (const block of Array.isArray(page.blocks) ? page.blocks : []) {
      if (!object(block) || typeof block.id !== 'string' || !block.id || blockIds.has(block.id)) { errors.push(`${page.id} contains an invalid or duplicate block ID.`); continue; }
      blockIds.add(block.id);
      if (!BLOCK_TYPES.has(block.type)) { errors.push(`${page.id} uses unsupported block ${block.type}.`); continue; }
      if (block.type === 'hero_v1') {
        localized(block.title, `${page.id} hero`, errors);
        const action = block.action;
        if (action && !(action.kind === 'product' && products.has(action.targetId) || action.kind === 'category' && categories.has(action.targetId) || action.kind === 'storefront' && storefronts.has(action.targetId))) errors.push(`${page.id} hero has an invalid action.`);
      }
      if (block.type === 'category_rail_v1') {
        localized(block.title, `${page.id} category rail`, errors);
        if (!Array.isArray(block.categoryIds) || block.categoryIds.some(id => !categories.has(id))) errors.push(`${page.id} references an invalid category rail.`);
      }
      if (block.type === 'product_collection_v1') {
        localized(block.title, `${page.id} product collection`, errors);
        const query = block.query ?? {};
        if (!object(query) || query.storefrontId && !storefronts.has(query.storefrontId) || Array.isArray(query.categoryIds) && query.categoryIds.some(id => !categories.has(id)) || Array.isArray(query.productTypeIds) && query.productTypeIds.some(id => !types.has(id)) || Array.isArray(query.productIds) && query.productIds.some(id => !products.has(id)) || ['categoryIds', 'productTypeIds', 'productIds'].some(key => query[key] != null && !Array.isArray(query[key]))) errors.push(`${page.id} product collection references missing content.`);
        if (!['grid', 'carousel'].includes(block.layout)) errors.push(`${page.id} has an invalid product collection layout.`);
      }
      if (block.type === 'editorial_text_v1') localized(block.body, `${page.id} editorial text`, errors, false, 5000);
    }
  });
  const storefrontIds = new Set(storefronts.keys());
  const categoryIds = new Set(categories.keys());
  const productIds = new Set([...products.values()].filter(product => product.status === 'published').map(product => product.id));
  promotions.forEach(promotion => {
    try { assertValidPromotion(promotion, { productIds, categoryIds, storefrontIds }); }
    catch (error) { errors.push(`Promotion ${promotion.id}: ${error.message}`); }
  });
  return [...new Set(errors)].slice(0, 200);
}

export function publicationApprovalBlockers(configuration) {
  const blockers = [];
  if (configuration?.status !== 'approved') blockers.push('Configuration status must be approved.');
  if (configuration?.privacyPolicy?.requiresOwnerAndLegalReview) blockers.push('Privacy policy owner/legal review is still required.');
  if (configuration?.physicalCommercePolicy?.requiresOwnerReview) blockers.push('Physical commerce owner review is still required.');
  if (configuration?.communityPolicy?.requiresOwnerReview) blockers.push('Community policy owner review is still required.');
  if (configuration?.accessPolicy?.grantAccessFromClientSuccessScreen !== false) blockers.push('Client success screens cannot grant access.');
  if (configuration?.offlinePolicy?.allowExport !== false) blockers.push('PDF export must remain disabled.');
  return blockers;
}

export function validateMinimumClientVersions(versions = {}) {
  if (!object(versions)) return false;
  if (Object.keys(versions).some(key => !['android', 'ios', 'web'].includes(key))) return false;
  return Object.entries(versions).every(([platform, value]) => typeof value === 'string' && value.length <= 32 && value.length > 0 && (platform !== 'android' || /^[0-9]+(?:\.[0-9]+){0,2}$/.test(value)));
}
