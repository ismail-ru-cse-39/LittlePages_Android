# LittlePages Android — Delivery Plan

Android is the first client. The app is being built in Kotlin and Jetpack Compose, with boundaries that let future iOS and web clients reuse the backend contracts and product rules.

Use the checkboxes below as the project tracker. Mark a task complete only when its **Done when** condition is true. Add new work under the relevant phase, and record decisions or blockers in the notes at the end.

## Current status

- [x] Android project scaffold and Compose app shell created.
- [x] UI prototype drafted for account entry, library, book details, reader, read-along, parent community, and supporting account/order screens.
- [ ] Prototype build and device review completed.
- [ ] Backend, authentication, real book content, payments, and moderation connected.

The current screens use sample data and in-memory navigation. They do not authenticate users, store or play books, upload images, or process orders or payments.

## Phase 0 — Confirm product rules

Resolve these decisions before connecting payments, publishing content, or collecting child information.

- [ ] Confirm the launch book catalogue, distribution rights, and who supplies English and Bangla content.
- [ ] Confirm the PDF offer: eligible users can read it in-app; no user can download or export the PDF.
- [ ] Define offline-book rules, including eligibility, storage limits, access expiry, and removal after access ends.
- [ ] Confirm which features are free and which require Premium, including what happens when a subscription expires.
- [ ] Confirm launch payment providers and the purchase flow for digital subscriptions, in-app book access, and physical books.
- [ ] Define physical-book regions, stock handling, delivery fees, fulfillment, cancellation, and refund rules.
- [ ] Decide parent consent, minimum data collection, account/data deletion, and child-profile requirements.
- [ ] Define community rules, image restrictions, reporting, blocking, moderation response, and group ownership.
- [ ] Confirm launch languages: English and Bangla for app UI and the catalogue.

**Done when:** decisions are written down and product, content, payment, privacy, and fulfillment owners agree on them.

## Phase 1 — Make the Android foundation dependable

- [x] Sync and build the starter project; align the Gradle wrapper with AGP 9.4 and resolve the Kotlin compile error.
- [ ] Choose the initial package/module boundaries for app shell, design system, and independent features.
- [ ] Establish navigation, back behavior, loading, empty, error, and offline UI states.
- [ ] Move reusable colors, typography, spacing, buttons, fields, cards, and motion into the shared design system.
- [ ] Add English/Bangla font and layout support, including Bangla line height and text expansion.
- [ ] Set accessibility behavior for scalable text, screen readers, contrast, touch targets, and reduced motion.
- [ ] Add environment configuration for local development, staging, and production without committing secrets.

**Done when:** the app starts on supported Android devices, navigation is consistent, and feature screens use shared components rather than private styling.

## Phase 2 — Complete the Android screen flows

### Accounts and family setup

- [ ] Finish Google sign-in, email sign-in/sign-up, password reset, and phone verification screens.
- [ ] Add parent account onboarding and child profile creation/editing.
- [ ] Add account switching, sign-out, and parent-protected settings/purchase entry.

### Discovery and reading

- [ ] Finish Home, Library, Search/Filters, Book Details, and Reviews screens.
- [ ] Add book detail states for unavailable, free, Premium, PDF in-app, and physical editions.
- [ ] Finish the page-turn and continuous-scroll reader controls.
- [ ] Add the Reader **Listen** action and reusable gentle Premium upsell dialog.
- [ ] Finish Read Along controls: word highlight, play/pause, seek, voice, speed, and Autoplay pages.
- [ ] Add automatic resume, manual bookmarks, and per-child progress UI.
- [ ] Finish Offline Books list, storage status, remove-book flow, and sync status.

### Community and family account

- [ ] Finish parent feed, create-post/image selection, comments, groups, and discussion screens.
- [ ] Add review submission, report, block, and confirmation states.
- [ ] Finish Parent Profile, child profiles, language selection, Premium, Orders, Privacy, and Support screens.

### Purchases and recovery states

- [ ] Finish book format selection, address capture for physical books, payment selection, and order confirmation.
- [ ] Add subscription status, renewal, cancellation, expired, and payment-recovery screens.
- [ ] Add clear recovery UI for network loss, failed book access, interrupted offline saves, and failed payments.

**Done when:** each screen can be reached through the app flow, primary actions have a defined outcome, and screens cover loading, empty, success, and failure states where applicable.

## Phase 3 — Firebase and trusted backend

- [ ] Create separate Firebase projects/configuration for development, staging, and production.
- [ ] Define Firestore collections and versioned data contracts for accounts, profiles, books, access, progress, orders, reviews, and community.
- [ ] Define Cloud Storage paths and upload limits for books, covers, narration, and community images.
- [ ] Implement Firebase Authentication account linking for Google, email, and phone methods.
- [ ] Implement backend functions for book access, subscription entitlements, order creation, payment verification, and moderation actions.
- [ ] Add role-based admin access, Firebase App Check, restrictive Firestore/Storage rules, and audit records.
- [ ] Add monitoring and alerting for backend failures, storage errors, and payment/order processing.

**Done when:** app clients cannot grant themselves access, admin actions are permission-checked, and data access follows the approved product rules.

## Phase 4 — Catalogue and admin publishing

- [ ] Build the staff-only web admin shell and role management.
- [ ] Add book create/edit/archive and draft/publish workflows.
- [ ] Manage localized English/Bangla titles, descriptions, categories, age range, reading level, and series.
- [ ] Upload and validate book files, covers, narration audio, and word-timing data.
- [ ] Manage Premium/free access, in-app PDF access, physical edition availability, price, stock, and delivery details.
- [ ] Add preview, edit history, and publishing checks for rights, metadata, file integrity, and narration timing.

**Done when:** an authorized staff member can publish and update a book without an app release, and the Android catalogue reflects the published changes.

## Phase 5 — Reader, narration, and offline access

- [ ] Select and integrate an Android PDF/ebook rendering approach that supports the approved book format.
- [ ] Implement page-turn and scroll modes with stable resume positions.
- [ ] Implement reading progress and bookmark persistence, including offline-first updates and sync conflict handling.
- [ ] Integrate narration and word-level timing for supported books.
- [ ] Implement Autoplay pages, speed/voice controls, audio focus, pause/resume, and playback recovery.
- [ ] Implement authorized offline book storage in app-private storage, with encryption and explicit removal.
- [ ] Enforce in-app-only PDF reading: no customer export, share, or document download action.
- [ ] Define and implement offline access renewal/expiry behavior from Phase 0 decisions.

**Done when:** supported books open online and offline, progress resumes correctly, and access rules are enforced by the backend and app storage flow.

## Phase 6 — Payments and order fulfillment

- [ ] Confirm current Google Play requirements for digital subscriptions/content in each launch market.
- [ ] Implement the approved Android digital billing flow and server-side purchase verification.
- [ ] Integrate the approved Bangladesh payment provider for physical book orders, including bKash and Visa where supported.
- [ ] Add idempotent payment callbacks, order status updates, refunds/cancellations, and reconciliation records.
- [ ] Add physical stock reservation, delivery fee calculation, fulfillment status, and tracking updates.
- [ ] Grant/revoke entitlements from verified transaction state, never from the client’s success screen alone.

**Done when:** successful, pending, failed, refunded, and expired purchase states all produce the correct access and order status.

## Phase 7 — Parent community and moderation

- [ ] Enforce parent-account-only posting and group participation.
- [ ] Implement image upload validation, visibility rules, and removal behavior.
- [ ] Add report/block flows for posts, comments, users, and groups.
- [ ] Build moderator review queues, actions, and audit history in the admin site.
- [ ] Add community guidelines, consent/privacy messaging, and support escalation.

**Done when:** users can report harmful content and authorized moderators can review and act on reports with an audit trail.

## Phase 8 — Release readiness and ongoing operations

- [ ] Complete accessibility and English/Bangla content review on supported device sizes.
- [ ] Review privacy disclosures, consent flow, account deletion, and data retention.
- [ ] Configure production crash/error reporting, dashboards, and support escalation ownership.
- [ ] Prepare store listing, screenshots, subscription descriptions, and customer support documentation.
- [ ] Run release acceptance against the defined product flows and resolve launch-blocking issues.
- [ ] Publish staged rollout and document rollback/incident procedures.

**Done when:** product, operations, support, and privacy owners approve the release checklist and the staged release can be monitored and rolled back.

## Tracking notes

Keep this section current as work proceeds:

- **Current phase:** Phase 0 — Confirm product rules
- **Next task:** Confirm content and payment decisions with the product owner.
- **Blockers:** None recorded.
- **Decision log:** Record date, decision, owner, and affected task IDs here.

### Task update convention

- Check `[x]` only after the task’s completion condition is met.
- If work is blocked, leave it unchecked and add the reason under **Blockers**.
- When scope changes, update the relevant task before implementation so this file remains the shared plan.

## Project structure today

- `app/src/main/java/com/littlepages/app/MainActivity.kt`: Android entry point
- `app/src/main/java/com/littlepages/app/ui/theme/Theme.kt`: shared color palette and Compose theme
- `app/src/main/java/com/littlepages/app/ui/LittlePagesApp.kt`: current app shell, route state, UI prototype screens, and reusable components

As feature implementation grows, split the prototype into feature packages and extract independent Gradle modules when that separation reduces coupling or makes parallel development easier.
