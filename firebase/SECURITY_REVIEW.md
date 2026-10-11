# Firebase security review draft

## Current state

`firestore.rules` and `storage.rules` deny all client reads and writes. This is the safe state while projects, identities, ownership rules, and publication flows are unconfigured. Do not relax these rules to make the local preview work. The deny-all rules are not evidence that the complete backend is secure; they mean no Firebase client integration is available yet.

## Required authorization boundaries

- Public clients may read only published manifests, immutable public catalogue/configuration revisions, and public book metadata.
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

## Before rules can be authored and deployed

Confirm the collection layout, staff identity source, role lifecycle, parent/child ownership model, moderation visibility states, App Check registrations, and Storage upload flow. Then implement least-privilege rules, add Firebase Emulator Suite rule tests, review the rules with the security owner, and deploy to development first. Keep the Phase 3 security task unchecked until those steps pass.
