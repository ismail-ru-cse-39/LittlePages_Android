# Trusted backend operation contract

This is the implementation boundary for the future Firebase Functions/API layer. It is not deployed code. Every operation below must verify Firebase Auth, App Check, request shape, current product state, and authorization on the server. Client UI state and payment success callbacks are never trusted evidence.

| Operation | Caller | Required server checks | Result |
|---|---|---|---|
| `getPublishedSnapshot` | Public app/site | Read only the current manifest and immutable public revisions; reject unsupported schema/minimum client version | Manifest and complete pinned configuration/catalogue snapshot |
| `saveConfigurationDraft` | Staff | Active staff role; editor or higher; validate base revision; enforce size/schema bounds | Private draft ID and validation state |
| `validateConfigurationDraft` | Staff | Active staff role; validate all localized labels, IDs, references, renderer/block allowlists, product attributes, rights metadata, and client compatibility | Structured validation report; no publication side effect |
| `publishConfiguration` | Staff publisher | Publisher role; approved draft; compare-and-swap base revision; recompute hash; write immutable snapshot and audit entry before advancing manifest | New publication revision |
| `rollbackPublication` | Staff publisher | Publisher role; target revision is known and valid; create a new audit record and publication revision | New manifest revision pointing to the selected snapshot |
| `resolveBookAccess` | Signed-in parent | Parent owns child profile; book is published; entitlement or free rule is valid; locale/content version is supported | Short-lived reader access grant |
| `createPhysicalOrder` | Signed-in parent | Validate product/variant, current price, stock, region, delivery quote, and idempotency key | Pending order with immutable price/address snapshot |
| `verifyPaymentEvent` | Provider webhook | Verify provider signature and event uniqueness; reconcile with pending order/subscription | Idempotent transaction result and server-owned entitlement/order update |
| `createCommunityReport` | Signed-in parent | Validate parent actor, allowed target/reason, rate limits, and report visibility | Private report reference |
| `moderateCommunityItem` | Staff moderator | Moderator role; valid moderation state transition; capture actor, reason, target, and timestamp | Moderated item state plus audit entry |

## Cross-cutting requirements

- Use idempotency keys for client order creation and provider event IDs for webhooks.
- Use server timestamps and server-owned price, inventory, entitlement, publication, and moderation state.
- Apply least privilege to staff roles. Administrator, publisher, catalog editor, and moderator capabilities must be checked per operation, not just hidden in the UI.
- Keep published snapshots immutable. Reject stale draft base revisions instead of overwriting newer work.
- Avoid returning private book object paths or reusable long-lived download URLs. Issue short-lived access only after entitlement checks.
- Record security-sensitive actions in append-only audit records. Restrict audit reads to authorized staff.
- Set explicit request limits, timeouts, structured error codes, rate limits, and monitoring before production rollout.

Project registration, provider choices, Firebase SDK/runtime versions, implementation, and deployment remain open. Do not treat this contract as a substitute for a reviewed implementation.
