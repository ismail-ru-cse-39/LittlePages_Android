# LittlePages Firebase data contract draft

This is a backend-neutral starting contract for development and review. Machine-readable document shapes are in `contracts/v1.json`, `contracts/configuration.v1.schema.json`, `contracts/storefront.v1.schema.json`, `contracts/publication-manifest.v1.schema.json`, and `contracts/promotions.v1.schema.json`. Public Firestore reads are narrowly allowed in the local rules draft for published storefront records; user/staff data, client writes, and Storage access remain denied. Rules and Functions have not been deployed. Never loosen the rules to make a prototype screen work.

## Admin-managed configuration

- `publicConfiguration/current`: small active manifest pointing to the published configuration and catalogue revisions. Its shape is defined by `contracts/publication-manifest.v1.schema.json`. Clients pin reads to these revision IDs and switch snapshots only after validation.
- `configurationRevisions/{revisionId}`: immutable revision snapshot containing the shared canonical configuration object and its supported product policies, storefronts, product types, localized categories, and page definitions. Android translates this canonical shape into its local `CatalogConfiguration` model when reading; web and iOS can consume the canonical fields directly.
- `catalogRevisions/{revisionId}/products/{productId}`: immutable canonical product documents for the pinned catalogue revision. Android translates product status/type, localized values, variants, typed attributes, and the `bookDetails` extension into its local `ManagedProduct` model; other clients can consume the shared `storefront.v1.schema.json` shape.
- `catalogRevisions/{revisionId}/promotions/{promotionId}`: public immutable automatic discounts and localized banners in the same catalogue revision. Never place discount-code definitions, code digests, assigned account IDs, or redemptions in this public path.
- `discountCodeCampaigns/{campaignId}` and `discountCodes/{codeId}`: trusted-service-only campaign metadata and keyed HMAC code digests. Raw codes are returned once at creation; per-account eligibility and redemption counters remain private and transactionally updated with order creation.
- `configurationDrafts/{draftId}`: staff-only, mutable draft records tagged with base publication and draft revisions. `configurationApprovals/{draftId}` stores the approval actor, reason, approved draft revision/base revision, and content hash. Customer clients have no access to either collection; callable Functions read/write them.
- `publicationAudit/{publicationRevision}`: publisher uid, base and new revision IDs, rollback target when applicable, reason, content hash, product/promotion counts, locale/version metadata, and timestamp. `adminAuditEvents/{eventId}` records actor/action/target/request details for security-sensitive changes. The trusted publishing service records every publish and rollback.
- `adminRoles/{uid}`: server-managed catalog editor, publisher, moderator, or administrator role and status, updated by the administrator-only `setStaffRole` callable after an owner bootstrap. Never allow a client to grant itself a role or publish configuration.
- App and website clients read the manifest, fetch the referenced revisions, and cache the last valid snapshot. Android currently has a read-only Firestore adapter and durable cache; its reads will fall back to the bundled preview until the published documents and reviewed read rules are deployed. Unknown schema versions or invalid values fall back to the last valid revision and are reported; clients never write configuration.
- The staff web admin edits a draft, validates it, previews the impact across platform renderers, then publishes a new monotonically increasing revision. Keep an audit record with admin uid, changed fields, old/new revision, timestamp, and reason. Rollback publishes another revision and retains the history.
- The shared manifest, publish flow, cross-platform refresh behavior, and compatibility approach are documented in `PUBLISHING.md`.
- Callable source for draft/approval/publish/rollback lives in `functions/index.mjs`; server validation and canonical hashing live in `functions/publishing-domain.mjs`. The browser admin invokes these operations when configured, but they have not been deployed or live-verified.
- Publication writes include only products with `status: published` and promotions with `status: active` or `scheduled`; drafts, archived products, and paused/archived promotions remain private in the editable draft.
- Category, storefront, product type, page, and block IDs are stable identifiers. Admins can add, rename, reorder, disable, and archive supported categories and storefront sections; products reference IDs, never display labels. Existing records keep working when labels change. Do not hard-delete IDs referenced by published content.
- Store pages are declarative compositions of allowlisted, versioned blocks (for example hero, category rail, product grid/carousel, and editorial text). Android, iOS, web, and admin preview clients share the block contract but implement their own renderers. Reject unsupported block types and schema versions safely; never evaluate arbitrary HTML, JavaScript, or uploaded code.
- Product type declarations select supported schemas and presentation templates. Adding a toy category/store section, or configuring a supported toy product type, is data work; introducing new fulfillment logic or a new renderer/behavior remains application/backend work.
- Policy configuration is a product control plane, not a security boundary. Cloud Functions must check entitlements, stock, payment state, consent, moderation state, and offline access on every relevant operation. The client can hide unavailable controls, but cannot grant access. Keep Firestore client writes to configuration denied; only the trusted admin service may publish.
- Values in `config/provisional-defaults.v1.json` are generic starting defaults, not legal, billing, or production approval. Admin edits should take effect through a published configuration revision without an Android/iOS/web release for supported settings and categories. Adding a new executable capability or payment provider still requires implementation work.
- Automatic promotions use `contracts/promotions.v1.schema.json`; clients display only currently active offers, while the order service re-evaluates eligibility and applies at most one best discount. Prices have a one-minor-unit floor. Discount-code documents are private and the prototype's browser SHA-256 values are preview artifacts, not production code credentials; production must use server-keyed HMAC digests.

## Identity and family

- `users/{uid}`: parent account display name, email/phone references, locale, created/updated timestamps, account state.
- `users/{uid}/children/{childId}`: parent-managed nickname, age band, preferred languages, avatar key, created/updated timestamps. Do not store a child's legal name or birth date by default.
- `users/{uid}/entitlements/{entitlementId}`: server-written product, state, source transaction reference, valid-from/to timestamps.

## Catalogue and reading

- `products/{productId}`: common listing data including product type ID, publication state, localized title/description, category IDs, image keys, variants, price/currency, inventory/availability, and validated type-specific attributes. Shape is defined in `contracts/storefront.v1.schema.json`. Physical books and toys use the same listing/order surface where applicable.
- `storefronts/{storefrontId}`: localized name, route/slug, product type/category scope, enabled/published state, navigation placement, supported regions, and optional landing page ID.
- `storePages/{pageId}`: localized page metadata and ordered allowlisted blocks such as hero, category rail, product grid/carousel, and editorial content. Blocks reference product/category IDs or controlled content; they contain no executable markup or script.
- `productTypes/{typeId}`: admin-configurable schema and supported template selection for types the clients/backend know how to render/validate (initially books and toys). Contract shapes for storefronts, products, product types, and page blocks are in `contracts/storefront.v1.schema.json`. Adding unsupported behavior requires a client/backend release.
- Products store `categoryIds: ["animals", "bedtime"]`; display labels and filter ordering come from the managed category registry, not client literals.
- A book product uses the shared listing plus `bookDetails` for age band, reading level, series, page count, locales, content version, free/Premium/paid access, in-app PDF availability, and an optional physical-edition variant link. Reader files, grants, and progress remain in private book-specific records and never in the public product document.
- `books/{bookId}` (or a book-specific extension keyed to the corresponding product): reader-specific age range, reading level, series, page count, rights metadata, content version, and physical/digital book edition information. Final normalization/migration is a tracked Phase 3 task.
- `books/{bookId}/assets/{assetId}`: private Storage object key, asset type (`cover`, `reader`, `narration`, `timings`), checksum, locale, content version, byte length, and trusted review state. Reader assets are served only after server entitlement checks and remain app-private on device. `createOfflineBookDownloadUrl` returns a 15-minute signed URL only when the private object's metadata matches the approved record.
- `users/{uid}/profiles/{childId}/progress/{bookId}`: current page/position, completion, last-read timestamp, content version.
- `users/{uid}/profiles/{childId}/bookmarks/{bookmarkId}`: book, page/position, created timestamp.
- `users/{uid}/offlineAccess/{bookId}`: server-issued access expiry, child, locale, content version, entitlement source, and refresh time; never a client entitlement grant. `refreshOfflineBookGrant` validates the signed-in parent, owned child, published book, and free/entitled access before issuing a maximum 30-day grant. It enforces a 10-book active limit and records refresh/revocation events. `revokeOfflineBookGrant` lets the same parent revoke the server grant.
- Android's local `OfflineBookAccessPolicy` reflects the provisional 10-book family cap, 30-day maximum lease, 7-day online refresh interval, and removal-on-entitlement-expiry setting. `OfflineBookVault` scopes encrypted files and authentication data to the signed-in account and rejects overlong grants. The Android callable client and server grant endpoints are implemented, but the UI download lifecycle, trusted asset upload/review, and device QA remain open; the local helper cannot establish or renew access by itself.

## Commerce

- `orders/{orderId}`: owning uid, product/variant line items with quantity and price/currency snapshots, physical/in-app-reading fulfillment type, status, payment reference, delivery snapshot where needed, timestamps. Line item shape is in `contracts/v1.json`.
- `discountCodeUsage/{codeHash}` and `discountCampaignUsage/{campaignId}/customers/{uidHash}`: server-only reserved/redeemed counts, respectively scoped to a code and to a customer within a campaign. Never make these documents client-readable. Pending payment settlement must convert reservations to redemptions or release them idempotently.
- `paymentEvents/{eventId}`: provider event ID, order/subscription reference, verified result, received/processed timestamps. Server-only and idempotent by provider event ID.
- `subscriptions/{subscriptionId}`: uid, provider, verified product/state/renewal dates, provider transaction references. Server-only writes.

## Parent community

- `posts/{postId}`, `groups/{groupId}`, `groups/{groupId}/members/{uid}`, `comments/{commentId}`, and `reviews/{reviewId}`: parent-authored content with author uid and moderation state.
- `reports/{reportId}`: reporter uid, target type/id, reason, status, assigned moderator, action and audit timestamps. `createCommunityReport` prevents self-reports and duplicate reports by the same account; reporter cannot read reports, and resolution is server/admin only.
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
