# LittlePages Firebase data contract draft

This is a backend-neutral starting contract for development and review. Machine-readable document shapes are in `contracts/v1.json`. No Firebase project is connected and the default rules deny every client request. Never loosen the rules to make a prototype screen work.

## Identity and family

- `users/{uid}`: parent account display name, email/phone references, locale, created/updated timestamps, account state.
- `users/{uid}/children/{childId}`: parent-managed nickname, age band, preferred languages, avatar key, created/updated timestamps. Do not store a child's legal name or birth date by default.
- `users/{uid}/entitlements/{entitlementId}`: server-written product, state, source transaction reference, valid-from/to timestamps.

## Catalogue and reading

- `books/{bookId}`: publication state, localized title/description/author/category, age range, page count, cover asset key, edition availability, prices, rights metadata, content version.
- `books/{bookId}/assets/{assetId}`: private Storage object key, asset type (`cover`, `reader`, `narration`, `timings`), checksum, locale, content version. Reader assets are served only after server entitlement checks and remain app-private on device.
- `users/{uid}/profiles/{childId}/progress/{bookId}`: current page/position, completion, last-read timestamp, content version.
- `users/{uid}/profiles/{childId}/bookmarks/{bookmarkId}`: book, page/position, created timestamp.
- `users/{uid}/offlineAccess/{bookId}`: server-issued access expiry and content version; never a client entitlement grant.

## Commerce

- `orders/{orderId}`: owning uid, line items, physical/digital fulfillment type, price/currency snapshot, status, payment reference, delivery snapshot where needed, timestamps.
- `paymentEvents/{eventId}`: provider event ID, order/subscription reference, verified result, received/processed timestamps. Server-only and idempotent by provider event ID.
- `subscriptions/{subscriptionId}`: uid, provider, verified product/state/renewal dates, provider transaction references. Server-only writes.

## Parent community

- `posts/{postId}`, `groups/{groupId}`, `groups/{groupId}/members/{uid}`, `comments/{commentId}`, and `reviews/{reviewId}`: parent-authored content with author uid and moderation state.
- `reports/{reportId}`: reporter uid, target type/id, reason, status, assigned moderator, action and audit timestamps. Reporter cannot read other users' reports; moderation writes are server/admin only.
- `blocks/{uid}/users/{blockedUid}`: parent block relationship, enforced in feed queries and server actions.

## Required server checks before launch

1. Verify Firebase Auth identity and App Check on callable/API requests.
2. Resolve a parent-owned child profile; clients cannot write entitlements, payment events, moderation outcomes, or book publication state.
3. Verify the book is published, the requested locale/version exists, and access is valid before issuing a short-lived reader/offline token.
4. Keep original books and narration out of public buckets. Enforce upload size/type limits and image safety review before community publication.
5. Make payment webhooks idempotent and grant/revoke access only from verified provider state.
6. Add retention/deletion handling after product, privacy, and legal owners approve the policy.

## Proposed Storage object paths and limits

These are implementation defaults for review, not production authorization:

- `books/{bookId}/v{contentVersion}/{locale}/reader.pdf`: max 100 MiB, `application/pdf` only.
- `books/{bookId}/v{contentVersion}/{locale}/cover.webp`: max 10 MiB, JPEG/PNG/WebP accepted on upload and normalized to WebP.
- `books/{bookId}/v{contentVersion}/{locale}/narration/{trackId}.mp3`: max 100 MiB per track.
- `books/{bookId}/v{contentVersion}/{locale}/timings/{trackId}.json`: max 5 MiB; validate word offsets against the narration and script before publish.
- `community/{uid}/{assetId}`: max 5 MiB per image, JPEG/PNG/WebP only, maximum four images per post, pending moderation before parent-feed visibility.

Reader assets are private. Admin uploads and parent image uploads must go through authenticated, size/type-limited flows; storage rules remain deny-all until the owner approves roles, moderation, retention, and access expiry.
