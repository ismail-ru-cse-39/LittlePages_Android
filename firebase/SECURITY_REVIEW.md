# Firebase security review draft

## Current state

`firestore.rules` now permits unauthenticated reads only for `publicConfiguration/current`, numeric immutable `configurationRevisions`, and product and automatic-promotion documents beneath numeric `catalogRevisions`. Client writes and all other reads remain denied. This narrow public read surface supports guest storefront browsing; it is safe only if the trusted publisher places public data in these immutable revision paths and never puts drafts, discount-code digests, customer assignments, redemptions, or private assets there. `storage.rules` still deny all client access. These rules have not been emulator-tested or deployed, and Firestore reads will continue to fail under the currently deployed deny-all rules until you deploy the reviewed file.

## Required authorization boundaries

- Public clients may read only published manifests, immutable public catalogue/configuration revisions, automatic promotion display data, and public book metadata. Discount-code digests and redemption information are never public.
- Clients may not write publication manifests, published snapshots, product publication status, prices, stock reservations, entitlements, payment events, subscription state, moderation outcomes, role assignments, audit events, or private book assets.
- Parent data must be scoped to the authenticated parent UID. Child profiles, reading progress, bookmarks, orders, and privacy requests must be checked against parent ownership in a trusted service.
- Drafts and unpublished assets are staff-only. Role changes and publisher/moderator operations require trusted server authorization and audit records.
- Book and narration assets remain private. The trusted access service issues only short-lived access after entitlement checks; never expose a public Storage path.
- Community writes require a parent identity and server-side content/size/rate checks. Moderated content is not visible until the server sets the approved state. Reports are private to the reporter and authorized moderators.
- Firebase App Check, provider webhook signature checks, and idempotent processing are required in addition to Authentication.

## Proposed staff capability matrix

This is a design target for the trusted admin service, not an implemented grant list. Deny by default and check capabilities on the server for every request; hiding a control in the web page is not authorization.

| Role | Edit drafts | Publish / rollback | Moderate community | Manage staff and policy |
| --- | --- | --- | --- | --- |
| Catalog editor | Yes | No | No | No |
| Publisher | Yes | Yes | No | No |
| Moderator | No | No | Yes | No |
| Administrator | Yes | Yes | Yes | Yes |

Prefer separate publisher and moderator assignments. Staff identity changes, role grants/revocations, publication, rollback, moderation decisions, and policy changes must create immutable audit events with actor UID, action, target, timestamp, request/correlation ID, and before/after revision references where applicable. High-impact operations should require reauthentication or an approval step selected by the security owner.

## Before rules can be deployed

Confirm the collection layout, staff identity source, role lifecycle, parent/child ownership model, moderation visibility states, App Check registrations, and Storage upload flow. Add Firebase Emulator Suite rule tests, review the rules with the security owner, and deploy to development first. The current rules only unlock public published storefront reads; they do not implement the remaining authenticated user, staff, or Storage rules. Keep the Phase 3 security task unchecked until those steps pass.
