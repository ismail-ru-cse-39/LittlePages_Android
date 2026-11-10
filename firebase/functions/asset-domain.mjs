const BOOK_ID = /^[a-z0-9_-]{2,80}$/;
const SHA256 = /^[a-f0-9]{64}$/;

const ASSET_RULES = Object.freeze({
  reader: { contentTypes: ['application/pdf'], maxBytes: 100 * 1024 * 1024, fileName: 'reader.pdf' },
  cover: { contentTypes: ['image/jpeg', 'image/png', 'image/webp'], maxBytes: 10 * 1024 * 1024, fileName: 'cover' },
  narration: { contentTypes: ['audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/ogg', 'audio/wav'], maxBytes: 100 * 1024 * 1024, fileName: 'narration' },
  timings: { contentTypes: ['application/json', 'text/json'], maxBytes: 5 * 1024 * 1024, fileName: 'timings.json' }
});

export function describeBookAsset({ bookId, locale, contentVersion, type, contentType, byteLength, sha256 }) {
  if (typeof bookId !== 'string' || !BOOK_ID.test(bookId)) throw new Error('Choose a valid book.');
  if (!['en', 'bn'].includes(locale)) throw new Error('Choose English or Bangla.');
  if (!Number.isSafeInteger(contentVersion) || contentVersion < 1) throw new Error('Content version must be a positive integer.');
  const rule = ASSET_RULES[type];
  if (!rule) throw new Error('Choose a reader, cover, narration, or timing asset.');
  if (!rule.contentTypes.includes(contentType)) throw new Error('The file type is not supported for this asset.');
  if (!Number.isSafeInteger(byteLength) || byteLength < 1 || byteLength > rule.maxBytes) throw new Error('The file size is outside the supported limit.');
  if (typeof sha256 !== 'string' || !SHA256.test(sha256)) throw new Error('A SHA-256 checksum is required.');
  const assetId = `${bookId}-${locale}-v${contentVersion}-${type}`;
  const objectPath = `books/${bookId}/v${contentVersion}/${locale}/${rule.fileName}`;
  return { assetId, objectPath, contentType: type === 'timings' ? 'application/json' : contentType, byteLength, sha256, type, locale, contentVersion, bookId };
}

export function rightsDeclaration(input) {
  const holder = typeof input?.holder === 'string' ? input.holder.trim() : '';
  const licenseReference = typeof input?.licenseReference === 'string' ? input.licenseReference.trim() : '';
  if (holder.length < 2 || holder.length > 120 || licenseReference.length < 2 || licenseReference.length > 120) {
    throw new Error('Enter the rights holder and license reference. This declaration is not legal approval.');
  }
  const expiresAt = input?.expiresAt ?? null;
  if (expiresAt != null && (typeof expiresAt !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(expiresAt))) {
    throw new Error('Rights expiry must be a YYYY-MM-DD date.');
  }
  return { holder, licenseReference, expiresAt, declarationOnly: true };
}
