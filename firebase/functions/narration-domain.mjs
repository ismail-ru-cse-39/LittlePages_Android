const MAX_PAGES = 500;
const MAX_WORDS = 2000;
const MAX_TEXT = 20000;
const MAX_PAGE_MS = 60 * 60 * 1000;

/** Approved read-along timing. The client uses this only after the server has signed the private file. */
export function parseNarrationTimings(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Narration timings must be an object.');
  if (value.schemaVersion !== 1) throw new Error('Narration timings schema is unsupported.');
  if (!Array.isArray(value.pages) || value.pages.length < 1 || value.pages.length > MAX_PAGES) {
    throw new Error('Narration timings need 1–500 pages.');
  }
  const pages = value.pages.map(page => {
    if (!page || typeof page !== 'object' || Array.isArray(page)) throw new Error('A narration page is invalid.');
    if (!Number.isSafeInteger(page.page) || page.page < 1) throw new Error('Narration page number is invalid.');
    if (typeof page.text !== 'string' || page.text.length < 1 || page.text.length > MAX_TEXT) throw new Error('Narration page text is invalid.');
    if (!Array.isArray(page.words) || page.words.length < 1 || page.words.length > MAX_WORDS) throw new Error('Narration word timings are invalid.');
    let previousEnd = -1;
    const words = page.words.map(word => {
      if (!word || typeof word !== 'object' || Array.isArray(word)) throw new Error('A narration word timing is invalid.');
      if (!Number.isSafeInteger(word.start) || !Number.isSafeInteger(word.end) || word.start < 0 || word.end <= word.start || word.end > page.text.length) {
        throw new Error('A narration word range does not match the page text.');
      }
      if (!Number.isSafeInteger(word.startMs) || !Number.isSafeInteger(word.endMs) || word.startMs < 0 || word.endMs <= word.startMs || word.startMs < previousEnd || word.endMs > MAX_PAGE_MS) {
        throw new Error('Narration word times must move forward within one hour.');
      }
      previousEnd = word.endMs;
      return { start: word.start, end: word.end, startMs: word.startMs, endMs: word.endMs };
    });
    return { page: page.page, text: page.text, words };
  });
  if (new Set(pages.map(page => page.page)).size !== pages.length) throw new Error('Narration page numbers must be unique.');
  return { schemaVersion: 1, pages };
}
