# Trusted backend operation contract

This document describes the Firebase Functions/API boundary. Initial source exists under `firebase/functions/`, but it is not deployed code. Every operation must verify Firebase Auth, App Check, request shape, current product state, and authorization on the server. Client UI state and payment success callbacks are never trusted evidence.

| Operation | Caller | Required server checks | Result |
|---|---|---|---|
| `getPublishedSnapshot` | Public app/site | Read only the current manifest and immutable public configuration, product, and automatic-promotion revisions; reject unsupported schema/minimum client version | Manifest and complete pinned configuration/catalogue/promotion snapshot; never include private code records |
| `saveConfigurationDraft` | Staff | Active staff role; editor or higher; validate base revision; enforce size/schema bounds | Private draft ID and validation state |
| `validateConfigurationDraft` | Staff | Active staff role; validate all localized labels, IDs, references, renderer/block allowlists, product attributes, rights metadata, and client compatibility | Structured validation report; no publication side effect |
| `publishConfiguration` | Staff publisher | Publisher role; approved draft; compare-and-swap base revision; recompute hash; write immutable snapshot and audit entry before advancing manifest | New publication revision |
| `rollbackPublication` | Staff publisher | Publisher role; target revision is known and valid; create a new audit record and publication revision | New manifest revision pointing to the selected snapshot |
| `setStaffRole` | Administrator | Active administrator role; target Firebase Auth user exists; valid role/status; prevent removal of the last active administrator; append audit event | Updated staff role; initial administrator remains an owner-bootstrap operation |
| `savePromotionDraft` | Staff catalog editor | Active staff role; validate product/category scope, percentage or BDT amount, nonzero price floor, destinations, and UTC schedule; keep drafts private | Private promotion draft and validation state |
| `publishPromotion` | Staff publisher | Publisher role; approved promotion; compare-and-swap catalogue revision; include automatic promotions in immutable public catalogue snapshot; write audit entry | New catalogue/publication revision |
| `createDiscountCodes` | Staff catalog editor | Active staff role; enforce batch limits and scope/schedule; generate high-entropy unique tokens on the server; store only keyed HMAC hashes; return raw codes once over the authenticated response | Private code campaign metadata and one-time plaintext code response |
| `approveDiscountCampaign` / `publishDiscountCampaign` | Administrator / publisher | Approval and publication are separate audited role-gated transactions; only approved drafts publish; schedule status is enforced at redemption | Approved, active or scheduled private campaign |
| `redeemDiscountCode` | Signed-in parent | App Check/Auth; normalize code; keyed HMAC lookup; verify active time window, account eligibility, products/categories, and remaining limits; compare its savings with eligible automatic campaigns, apply only the best single discount with a positive-price floor, and reserve a chosen code atomically with order creation | Order total and immutable applied discount record; reject duplicate/concurrent redemptions |
| `resolveBookAccess` | Signed-in parent | Parent owns child profile; book is published; entitlement or free rule is valid; locale/content version is supported | Short-lived reader access grant |
| `refreshOfflineBookGrant` / `revokeOfflineBookGrant` | Signed-in parent | App Check/Auth; account owns child profile; published book/locale/content version and approved private reader asset are valid; free or current entitlement; 30-day maximum, 7-day refresh, 10-book account cap | Private server-owned offline grant and issuance/revocation event |
| `createOfflineBookDownloadUrl` | Signed-in parent | App Check/Auth; matching active grant and child; approved asset record matches the private PDF object's size, MIME type, and SHA-256 metadata | 15-minute signed private download URL plus expected checksum |
| `createPhysicalOrder` | Signed-in parent | Validate product/variant, current price, stock, region, delivery quote, and idempotency key | Pending order with immutable price/address snapshot |
| `verifyPaymentEvent` | Provider webhook | Verify provider signature and event uniqueness; reconcile with pending order/subscription | Idempotent transaction result and server-owned entitlement/order update |
| `createCommunityReport` | Signed-in parent | App Check/Auth; supported visible target; no self-report; one report per reporter/target | Private report reference |
| `moderateCommunityItem` | Staff moderator | Moderator role; valid moderation state transition; capture actor, reason, target, and timestamp | Moderated item state plus audit entry |

## Cross-cutting requirements

- Use idempotency keys for client order creation and provider event IDs for webhooks.
- Use server timestamps and server-owned price, inventory, entitlement, publication, and moderation state.
- Apply least privilege to staff roles. Administrator, publisher, catalog editor, and moderator capabilities must be checked per operation, not just hidden in the UI.
- Keep published snapshots immutable. Reject stale draft base revisions instead of overwriting newer work.
- Avoid returning private book object paths or reusable long-lived download URLs. Issue short-lived access only after entitlement checks.
- Record security-sensitive actions in append-only audit records. Restrict audit reads to authorized staff.
- Keep discount-code definitions, keyed hashes, assigned account IDs, and redemption records private. Public immutable snapshots may contain automatic discount rules and banners but never code hashes or unique code tokens. Use server-side HMAC with a protected secret for code lookup; do not use a plain client-generated hash as a production credential store.
- Set explicit request limits, timeouts, structured error codes, rate limits, and monitoring before production rollout.

Initial implementation exists in `firebase/functions/index.mjs` for configuration draft save/validation/preview, administrator approval, immutable publish, rollback, private discount campaign creation/approval/publication/pause/archive, and pending discounted-order reservation. `publishing-domain.mjs` holds snapshot validation and hashing. The implementation does not verify or settle payment, release reservations, update the trusted paid-purchase summary, grant access, or implement the other operations above. The Firebase project, region match, secret, App Check, staff-role provisioning, browser admin integration, emulator review, and deployment remain open. Do not treat source presence as production approval.
