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

## Before rules can be authored and deployed

Confirm the collection layout, staff identity source, role lifecycle, parent/child ownership model, moderation visibility states, App Check registrations, and Storage upload flow. Then implement least-privilege rules, add Firebase Emulator Suite rule tests, review the rules with the security owner, and deploy to development first. Keep the Phase 3 security task unchecked until those steps pass.
