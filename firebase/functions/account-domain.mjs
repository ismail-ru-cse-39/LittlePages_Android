import { randomBytes } from 'node:crypto';

export const CHILD_AGE_BANDS = Object.freeze(['3 years old', '5 years old', '7 years old']);
export const MAX_CHILD_PROFILES = 6;
const CHILD_ID = /^child-[a-f0-9]{16}$/;
const BOOK_ID = /^[a-z0-9_-]{2,80}$/;
const CATEGORY_ID = /^[a-z0-9_-]{2,60}$/;

export function createChildId() {
  return `child-${randomBytes(8).toString('hex')}`;
}

export function validateChildProfile({ nickname, ageBand, childId = null }) {
  if (typeof nickname !== 'string' || nickname.trim().length < 1 || nickname.trim().length > 30 || /[\u0000-\u001f]/.test(nickname)) {
    return 'Enter a nickname using 1–30 characters.';
  }
  if (!CHILD_AGE_BANDS.includes(ageBand)) return 'Choose one of the supported age bands.';
  if (childId != null && (typeof childId !== 'string' || !CHILD_ID.test(childId))) return 'The child profile ID is invalid.';
  return null;
}

export function validateReadingProgress({ bookId, page, bookmarks }) {
  if (typeof bookId !== 'string' || !BOOK_ID.test(bookId)) return 'Choose a valid book.';
  if (!Number.isSafeInteger(page) || page < 1 || page > 10000) return 'The reading page is invalid.';
  if (!Array.isArray(bookmarks) || bookmarks.length > 100 || new Set(bookmarks).size !== bookmarks.length) return 'Bookmarks are invalid.';
  if (bookmarks.some(mark => !Number.isSafeInteger(mark) || mark < 1 || mark > 10000)) return 'A bookmark page is invalid.';
  return null;
}

export function validateCommunityText(body, label = 'Post') {
  if (typeof body !== 'string') return `${label} text is required.`;
  const trimmed = body.trim();
  if (trimmed.length < 1 || trimmed.length > 2000) return `${label} text must be 1–2000 characters.`;
  return null;
}

export function validateCategoryId(categoryId) {
  if (typeof categoryId !== 'string' || !CATEGORY_ID.test(categoryId)) return 'Choose a supported topic.';
  return null;
}

export function validateReview({ bookId, rating, body }) {
  if (typeof bookId !== 'string' || !BOOK_ID.test(bookId)) return 'Choose a valid book.';
  if (!Number.isSafeInteger(rating) || rating < 1 || rating > 5) return 'Choose a rating from 1 to 5.';
  return validateCommunityText(body, 'Review');
}

export function validateDeletionReason(reason) {
  if (reason == null || reason === '') return null;
  if (typeof reason !== 'string' || reason.trim().length > 1000) return 'The deletion note must be 1000 characters or fewer.';
  return null;
}
