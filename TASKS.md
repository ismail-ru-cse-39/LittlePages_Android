# LittlePages Android — Delivery Plan

Android is the first client. The app is being built in Kotlin and Jetpack Compose, with boundaries that let future iOS and web clients reuse the backend contracts and product rules.

Use the checkboxes below as the project tracker. Mark a task complete only when its **Done when** condition is true. Add new work under the relevant phase, and record decisions or blockers in the notes at the end.

## Current status

- [x] Android project scaffold and Compose app shell created.
- [x] UI prototype drafted for account entry, library, book details, reader, read-along, parent community, and supporting account/order screens.
- [x] Debug APK build verified successfully.
- [ ] Emulator/device review completed.
- [ ] Backend, authentication, real book content, payments, and moderation connected.

The current screens mostly use sample data and in-memory navigation. Reader page/bookmark state persists locally under a temporary profile key, but it does not sync. The app does not authenticate users, store or play book content, upload community images, or process orders or payments.

## Phase 0 — Confirm product rules

Resolve these decisions before connecting payments, publishing content, or collecting child information.

- [ ] Confirm the launch book catalogue, distribution rights, and who supplies English and Bangla content.
- [x] Confirm the PDF offer: all customers read PDFs in-app; downloading/exporting is disabled.
- [ ] Define offline-book rules, including eligibility, storage limits, access expiry, and removal after access ends.
- [ ] Confirm which features are free and which require Premium, including what happens when a subscription expires.
- [x] Record requested Bangladesh methods: bKash and Visa. Digital purchase flow still needs Play policy/provider review in Phase 6.
- [ ] Define physical-book regions, stock handling, delivery fees, fulfillment, cancellation, and refund rules.
- [ ] Decide parent consent, minimum data collection, account/data deletion, and child-profile requirements.
- [ ] Define community rules, image restrictions, reporting, blocking, moderation response, and group ownership.
- [x] Confirm launch languages: English and Bangla for app UI and the catalogue.

**Done when:** decisions are written down and product, content, payment, privacy, and fulfillment owners agree on them.

## Phase 1 — Make the Android foundation dependable

- [x] Sync and build the starter project; align AGP 9.2.1 with Gradle 9.4.1 and resolve the Kotlin compile error.
- [x] Choose initial app/core/feature/data package boundaries; see `ARCHITECTURE.md` (migration from prototype is pending).
- [ ] Establish navigation, back behavior, loading, empty, error, and offline UI states.
- [x] Adapt shared page widths to compact phones, tablets, and expanded windows; use a navigation rail at wide window sizes and compact welcome/reader layouts at short heights.
- [ ] Centralize spacing and motion and finish migrating screen styling into the shared design system.
- [x] Extract reusable layout, button, card, catalogue, and community primitives into `ui/components`; the palette remains in `ui/theme`.
- [x] Add Android app-locale switching for English/Bangla and translate fixed app UI copy using system resource locales.
- [ ] Review localized copy, dynamic catalogue text, and expansion on supported device sizes.
- [ ] Set accessibility behavior for scalable text, screen readers, contrast, touch targets, and reduced motion.
- [x] Set shared body line heights for Bengali/English text and retain Android system font scaling.
- [x] Use system font fallback for Bengali glyphs and declare English/Bangla app locales; device typography review remains pending.
- [x] Respect Android's animator scale for screen transitions, keep primary controls at 48dp or larger, and darken muted text for readable contrast on the app's main surfaces.
- [ ] Add environment configuration for local development, staging, and production without committing secrets. Blocked on Firebase project/app registrations and provider configuration; secret-handling conventions are documented in `ARCHITECTURE.md`.

**Done when:** the app starts on supported Android devices, navigation is consistent, and feature screens use shared components rather than private styling.

## Phase 2 — Complete the Android screen flows

### Accounts and family setup

- [ ] Finish Google sign-in, email sign-in/sign-up, password reset, and phone verification screens.
- [ ] Add parent account onboarding and child profile creation/editing.
- [ ] Add account switching, sign-out, and parent-protected settings/purchase entry.

### Discovery and reading

- [ ] Finish Home, Library, Search/Filters, Book Details, and Reviews screens.
- [x] Wire the Library category/age filters and Search query/suggestion chips to the local sample catalogue, including empty results.
- [ ] Add book detail states for unavailable, free, Premium, PDF in-app, and physical editions.
- [x] Finish the page-turn and continuous-scroll sample reader controls; actual PDF/ebook rendering remains in Phase 5.
- [x] Add the Reader **Listen** action and reusable gentle Premium upsell dialog (prototype interaction; subscription is not connected).
- [x] Implement sample Read Along controls: Android TTS, word highlighting (engine word timing with estimated fallback), play/pause, seek, voice, speed, audio focus, and Autoplay pages. Production narration/content timing remains in Phase 5.
- [x] Persist reader position and manual bookmarks and resume the selected book in the local preview.
- [ ] Associate progress with real child profiles and show per-child progress UI.
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
- [x] Define Firestore collections and versioned data contracts for accounts, profiles, books, access, progress, orders, reviews, and community as a reviewable v1 draft (`firebase/contracts/v1.json`).
- [x] Define proposed Cloud Storage paths and upload limits for books, covers, narration, and community images (`firebase/SCHEMA.md`); production approval remains pending.
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

- **Current phase:** Phase 1/2 — Prototype flow completion and foundation
- **Next task:** Review responsive layouts on representative phone, tablet, landscape, and foldable emulator configurations; then continue the remaining Android screen states.
- **Blockers:** Firebase project IDs and app registrations; content catalogue and distribution rights; offline eligibility/expiry policy; free/Premium entitlements; privacy/parent-consent/data-retention approval; physical fulfillment terms; approved moderation rules; applicable digital billing flow. Device review is also pending because this environment has no `adb` or connected emulator. These items remain unchecked.
- **Completed in this pass:** extracted shared UI primitives and empty states; added persistent local reader position/bookmarks and Home resume progress; added distinct page-turn and continuous-scroll sample layouts; functional local Library/Search filters; working local book-save and feed-like controls; sign-in input validation without fake authentication; local child-profile and community/review previews with image selection; delivery validation, bKash/Visa selection, and support-form shortcuts; added Android English/Bangla app-locale support and translated fixed app UI copy; added scalable line heights, reduced-motion handling, accessible touch sizing, and stronger muted-text contrast; removed fabricated purchases/orders/offline books; added versioned Firestore contract and proposed Storage paths/limits; implemented device TTS narration, word highlighting, pause/resume, seek, voice/rate controls, audio focus, autoplay, and a one-page sample route; added width-bounded adaptive layouts, a wide-window navigation rail, compact-height welcome/reader layouts, and navigation-stack restoration across configuration changes; built `LittlePageApp_1.25.apk` successfully.
- **Important limit:** local reading data uses a placeholder profile ID and has no cloud sync. Read-along currently speaks sample text using an installed English Android TTS voice; exact word timing depends on the engine and falls back to estimated word highlighting. No licensed book text/audio/timing is connected. Community images are not uploaded. Authentication, payment, real book content, offline content storage, and Firebase services are not connected. Responsive layouts compile, but visual review on device classes and fold posture changes remains pending.
- **Decision log (2026-10-11):** English and Bangla are launch languages. PDFs are in-app only for every customer, including Premium. Premium narration/word highlighting is a subscription feature. When a physical edition is available, offer both in-app PDF access and the printed book. Requested Bangladesh payment methods are bKash and Visa. Offline access is required; entitlement, expiry, and storage policy remain open.

### Task update convention

- Check `[x]` only after the task’s completion condition is met.
- If work is blocked, leave it unchecked and add the reason under **Blockers**.
- When scope changes, update the relevant task before implementation so this file remains the shared plan.

## Project structure today

- `app/src/main/java/com/littlepages/app/MainActivity.kt`: Android entry point
- `app/src/main/java/com/littlepages/app/AppLanguage.kt`: Android app-locale selection and pre-Android 13 configuration wrapper
- `app/src/main/res/values-bn/strings.xml`: initial Bangla shell/account translations
- `app/src/main/java/com/littlepages/app/ui/theme/Theme.kt`: shared color palette and Compose theme
- `app/src/main/java/com/littlepages/app/ui/LittlePagesApp.kt`: current app shell, route state, UI prototype screens, and reusable components
- `app/src/main/java/com/littlepages/app/ui/AppText.kt`: fixed app copy translations for Bangla
- `app/src/main/java/com/littlepages/app/ui/components/SharedComponents.kt`: shared Compose layout and design primitives
- `app/src/main/java/com/littlepages/app/data/ReaderProgressStore.kt`: app-private local reading position and bookmark cache
- `ARCHITECTURE.md`: initial boundaries and platform expansion decisions
- `firebase/SCHEMA.md`: proposed shared backend data contract; no Firebase project is connected
- `firebase/contracts/v1.json`: machine-readable version 1 Firestore document schemas
- `firebase/firestore.rules`, `firebase/storage.rules`: deny-all starter rules until reviewed production rules exist

As feature implementation grows, split the prototype into feature packages and extract independent Gradle modules when that separation reduces coupling or makes parallel development easier.
