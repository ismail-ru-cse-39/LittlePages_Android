# LittlePages Android — Delivery Plan

Android is the first client. The app is being built in Kotlin and Jetpack Compose, with boundaries that let future iOS and web clients reuse the backend contracts and product rules.

Use the checkboxes below as the project tracker. Mark a task complete only when its **Done when** condition is true. Add new work under the relevant phase, and record decisions or blockers in the notes at the end.

## Current status

- [x] Android project scaffold and Compose app shell created.
- [x] UI prototype drafted for account entry, library, book details, reader, read-along, parent community, and supporting account/order screens.
- [x] Debug APK build verified successfully.
- [x] User reviewed the responsive Android UI and confirmed it works on their tested device(s).
- [ ] Firestore data, real book content, payments, and moderation connected; Android Firebase Auth client flows are implemented, but provider configuration and QA remain open.

The current screens mostly use sample data and in-memory navigation. Reader page/bookmark state persists locally under a temporary profile key, but it does not sync. The app does not authenticate users, store or play book content, upload community images, or process orders or payments.

## Phase 0 — Confirm product rules

Resolve these decisions before connecting payments, publishing content, or collecting child information.

- [x] Set a provisional catalogue/content policy in `firebase/config/provisional-defaults.v1.json`; owner approval of actual catalogue and distribution rights remains pending.
- [x] Confirm the PDF offer: all customers read PDFs in-app; downloading/exporting is disabled.
- [x] Set editable provisional offline rules (entitlement eligibility, 10-book family cap, 30-day lease, 7-day refresh, encrypted private storage, remove on expiry); owner approval remains pending.
- [x] Set editable provisional access rules (catalogue free flag, active Premium requirement, retain separately purchased access after Premium expiry); billing and entitlement implementation remain pending.
- [x] Record requested Bangladesh methods: bKash and Visa. Digital purchase flow still needs Play policy/provider review in Phase 6.
- [x] Set editable provisional physical-book defaults (Bangladesh/BDT, bKash/Visa, confirmed stock and delivery quote required); fulfillment, cancellation/refunds, and owner approval remain pending.
- [x] Set editable provisional family privacy defaults (parent-owned profiles, nickname and age band only, no personalized ads, parent deletion request); consent and retention approval remain pending.
- [x] Set editable provisional community defaults (parent account, pre-moderation, child privacy, image limits, reports/blocks, admin-owned groups); moderation SLA and owner approval remain pending.
- [x] Confirm launch languages: English and Bangla for app UI and the catalogue.

**Done when:** decisions are written down and product, content, payment, privacy, and fulfillment owners agree on them.

## Phase 1 — Make the Android foundation dependable

- [x] Sync and build the starter project; align AGP 9.2.1 with Gradle 9.4.1 and resolve the Kotlin compile error.
- [x] Choose initial app/core/feature/data package boundaries; see `ARCHITECTURE.md` (migration from prototype is pending).
- [x] Establish route navigation and back behavior, including saving the current route across configuration changes.
- [x] Add reusable empty states for missing search results, offline books, and orders.
- [x] Detect changes to Android's validated default network and show an offline status banner.
- [x] Add reusable loading and service-specific error/retry UI components for remote flows; connect them when remote services are integrated.
- [x] Adapt shared page widths to compact phones, tablets, and expanded windows; use a navigation rail at wide window sizes and compact welcome/reader layouts at short heights.
- [x] Centralize spacing and motion and migrate screen spacing, shapes, sizes, and breakpoints into the shared design system.
- [x] Define shared spacing, shape, size, breakpoint, and page-transition tokens in `ui/theme/Theme.kt`.
- [x] Apply the shared tokens to common scaffolds, reusable controls, and primary screen/form spacing.
- [x] Migrate remaining illustration-specific and one-off component dimensions into design tokens.
- [x] Extract reusable layout, button, card, catalogue, and community primitives into `ui/components`; the palette remains in `ui/theme`.
- [x] Add Android app-locale switching for English/Bangla and translate fixed app UI copy using system resource locales.
- [ ] Review localized copy, dynamic catalogue text, and expansion on supported device sizes. Deferred to final QA; English/Bangla translation is in place.
- [x] Audit the Android literal `AppText` call sites and add Bangla entries for missing fixed preview copy; device-level visual and TalkBack review remains in final QA.
- [x] Add Bangla formatting for dynamic age/page, review/search count, reader mode/voice, profile progress, and order-detail labels; user-entered catalogue/community content retains its authored language.
- [x] Set accessibility behavior for scalable text, screen readers, contrast, touch targets, and reduced motion: retained system font scaling, added meaningful book-cover and control semantics, made primary interactive cards announce as buttons, gave autoplay its combined label/state context, enlarged text-only actions into standard controls, and strengthened the rating color contrast. Device-level TalkBack and font-scaling review remains in release QA.
- [x] Set shared body line heights for Bengali/English text and retain Android system font scaling.
- [x] Use system font fallback for Bengali glyphs and declare English/Bangla app locales; device typography review remains pending.
- [x] Respect Android's animator scale for screen transitions, keep primary controls at 48dp or larger, and darken muted text for readable contrast on the app's main surfaces.
- [x] Add environment selection for local development, staging, and production without committing secrets via local/CI Gradle properties and generated `BuildConfig` values. Firebase SDK wiring and registered project/app values remain open under Phase 3.
- [x] Document the dev/staging/production registration boundaries and ignore project-specific Firebase client files and server credentials; separate Gradle flavors remain open.
- [x] Add Android Gradle environment selection through local or CI Gradle properties and expose the selected public Firebase options/configured flag through `BuildConfig`; Firebase SDK consumption and registered app values remain pending.

**Done when:** the app starts on supported Android devices, navigation is consistent, and feature screens use shared components rather than private styling.

## Phase 2 — Complete the Android screen flows

### Accounts and family setup

- [x] Add guest browsing entry from sign-in, distinguish the guest profile, and gate key purchase, save, Premium, and parent-profile actions in the prototype.
- [x] Finish Google/email/phone sign-in, sign-up, password reset, and phone-code entry screens with local validation and explicit unconfigured-service feedback; Firebase authentication remains in Phase 3.
- [x] Add parent account onboarding and child profile creation/editing in a clearly labeled local preview; real accounts and consent remain unconfigured.
- [x] Add account switching, sign-out, and parent-protected settings/purchase entry in the local preview; real account sessions remain in Phase 6.

### Discovery and reading

- [x] Finish Home, Library, Search/Filters, Book Details, and Reviews screens for the local catalogue and preview flows; live service states depend on backend integration.
- [x] Wire the Library category/age filters and Search query/suggestion chips to the local sample catalogue, including empty results.
- [x] Render unavailable titles, in-app PDF availability, and physical-edition availability from catalogue data in Book Details and checkout; Premium/free access policy remains undecided in Phase 0.
- [x] Add book detail states for unavailable, standard/Premium access, PDF in-app, and physical editions; sample access tiers are placeholders pending Phase 0 entitlement decisions.
- [x] Finish the page-turn and continuous-scroll sample reader controls; actual PDF/ebook rendering remains in Phase 5.
- [x] Add the Reader **Listen** action and reusable gentle Premium upsell dialog (prototype interaction; subscription is not connected).
- [x] Implement sample Read Along controls: Android TTS, word highlighting (engine word timing with estimated fallback), play/pause, seek, voice, speed, audio focus, and Autoplay pages. Production narration/content timing remains in Phase 5.
- [x] Persist reader position and manual bookmarks and resume the selected book in the local preview.
- [x] Associate progress with selected local child profiles and show per-child reading progress; cloud sync remains in Phase 3.
- [x] Finish the Offline Books preview list, storage/sync status, removal confirmation, and empty state; no customer book files are stored until offline policy and encrypted storage are implemented in Phase 5.

### Community and family account

- [x] Finish parent feed filters, post/image selection and rendering, comments, groups, and local discussion screens; moderation and persistence remain in Phase 7.
- [x] Add review submission, report, block, and confirmation states in the local preview; server moderation remains in Phase 6.
- [x] Finish local Parent Profile, child profiles/progress, language selection, Premium preview, Orders, Privacy, and Support screens; service and policy connections remain pending.

### Purchases and recovery states

- [x] Finish book format selection, physical address capture, bKash/Visa selection, and a local order confirmation/history flow; no payment is taken or order submitted.
- [x] Add local preview states for subscription active, renewal, cancellation, expired, and payment issue; billing remains unconnected.
- [x] Add book-access, offline-sync, and payment recovery UI with retry actions; retries explain that no remote service is connected and do not alter access or payment state.

**Done when:** each screen can be reached through the app flow, primary actions have a defined outcome, and screens cover loading, empty, success, and failure states where applicable.

## Phase 3 — Firebase and trusted backend

- [ ] Create separate Firebase projects/configuration for development, staging, and production.
- [x] Define Firestore collections and versioned data contracts for accounts, profiles, books, access, progress, orders, reviews, and community as a reviewable v1 draft (`firebase/contracts/v1.json`).
- [x] Define proposed Cloud Storage paths and upload limits for books, covers, narration, and community images (`firebase/SCHEMA.md`); production approval remains pending.
- [ ] Implement Firebase Authentication account linking for Google, email, and phone methods.
- [ ] Complete Firebase Console setup for Google and phone authentication: enable both providers, register the app signing SHA fingerprints, refresh the Android Firebase configuration, and provide the OAuth Web client ID for Credential Manager.
- [ ] Implement backend functions for book access, subscription entitlements, order creation, payment verification, and moderation actions.
- [x] Specify trusted backend operation inputs, role checks, idempotency, publication sequencing, and server-owned state in `firebase/BACKEND_OPERATIONS.md`; deployed Functions/API code remains open.
- [x] Define shared multi-product contracts for products, product types, storefronts, page templates/blocks, categories, inventory, and product/variant order lines; keep reader access/progress book-specific (`firebase/contracts/storefront.v1.schema.json`, `firebase/contracts/v1.json`).
- [x] Add versioned storefront/type/category/page fields to the editable configuration schema and provide provisional Books and disabled Toys storefront examples.
- [x] Define the client-side versioned configuration repository boundary, with schema/revision validation and last-valid-cache fallback; add an app-private SharedPreferences cache for complete validated Android snapshots (`SharedPreferencesStorefrontConfigurationCache.kt`). A Firestore remote adapter remains pending.
- [x] Define a cross-platform publication manifest for Android, iOS, web, and admin preview, including pinned configuration/catalogue revisions, locales, content hash, and minimum client versions (`firebase/contracts/publication-manifest.v1.schema.json`).
- [x] Specify shared draft validation, preview, publish, rollback, cache refresh, and atomic snapshot activation behavior (`firebase/PUBLISHING.md`).
- [x] Add Android manifest/content snapshot models and a read-only state-flow source so feature screens can observe a complete validated content revision.
- [ ] Connect the versioned admin-managed storefront configuration and category registry to Firestore, with cached client reads, schema validation, admin-only publishing, revision history, and rollback.
- [ ] Implement the version-pinned content read API/adapters and client refresh notifications; activate a new publication only after the full snapshot validates.
- [ ] Add role-based admin access, Firebase App Check, restrictive Firestore/Storage rules, and audit records.
- [x] Review the existing deny-all Firebase rules and document required ownership, publication, staff, asset, and moderation boundaries in `firebase/SECURITY_REVIEW.md`; rules implementation, emulator tests, and deployment remain open.
- [ ] Add monitoring and alerting for backend failures, storage errors, and payment/order processing.
- [x] Draft backend monitoring signals, alert inputs, and incident response requirements in `firebase/OPERATIONS.md`; dashboards, thresholds, and on-call routing remain open.

**Done when:** app clients cannot grant themselves access, admin actions are permission-checked, and data access follows the approved product rules.

## Phase 4 — Multi-product storefront and admin publishing

- [x] Add a static local-only interaction prototype for storefront, category, page, and allowlisted block editing with browser draft persistence (`admin/`); storefront/category rename and ordering, category archive/restore, and up to 20 local draft snapshots with restore are supported. This is not a staff-secured or publishing console.
- [x] Add local policy editors for provisional access, offline, family privacy, physical commerce, and community defaults; protected PDF export/access grants remain fixed, and all values still need owner approval and server enforcement.
- [x] Add local product create/edit/archive draft flows with localized content, variants/stock, generated product-type attribute controls, validation, browser draft persistence, and JSON bundle export; image management, server draft/publish, and audit workflow remain pending.
- [x] Add a responsive English/Bangla customer website preview driven by the shared storefront/category/page-block configuration and shared-shape sample products (`website/`); live publication, identity, checkout, and reading services remain pending.
- [x] Implement the web preview renderer for the shared hero, editorial text, category rail, and product collection blocks; Android/iOS production renderer compatibility and versioned fixture checks remain open.
- [x] Give the website preview separate book and physical-item card/detail treatments driven by the declared product renderer; Android/iOS production detail views remain open.
- [x] Add local draft validation for IDs/references and JSON export to the admin preview; production validation, approval, and publishing remain pending.
- [ ] Build the staff-only web admin shell and role management.
- [x] Add a browser-local staff role-matrix panel to the existing admin preview; no authentication, real identities, or permission enforcement is provided.
- [ ] Build the customer website storefront using the same manifest, product IDs, configuration revisions, and allowlisted page blocks as Android/iOS; do not maintain a parallel catalogue.
- [ ] Add an admin settings area for the versioned editable policies in `firebase/config/provisional-defaults.v1.json`, with validation, impact preview, approval status, audit history, and rollback.
- [x] Define supported declarative product type schema metadata for Books and Toys and allowlisted page block types in the shared contract; add schema-generated product attribute controls to the admin preview and attribute definition models on Android.
- [ ] Implement shared production product detail templates for Books and Toys; provide common fields plus validated type-specific attributes. The local website preview has a first book/physical-item split, while native renderers and full template parity remain open.
- [ ] Implement compatible native renderers for the shared product/page contracts on Android, iOS, and web, with cross-platform fixture/contract checks.
- [ ] Add admin workflows to create, rename, reorder, disable, and archive storefronts/sections, product types, and localized categories; app navigation and filters must derive from published configuration.
- [ ] Build an admin page composer using an allowlisted shared block set (hero, category rail, product grid/carousel, and editorial content); publish/reorder/preview/archive pages without an Android/iOS/web release.
- [ ] Add generic product create/edit/archive and draft/publish workflows, with product-specific validation, variants, images, pricing, and stock.
- [ ] Migrate books into the shared product listing contract while retaining book-specific age range, reading level, series, private reader assets, access, and progress models.
- [x] Prototype management of localized English/Bangla product titles, descriptions, categories, and schema-defined product attributes; stable IDs are generated from names in this preview, while production ID/rename rules remain open.
- [ ] Upload and validate book files, covers, narration audio, and word-timing data.
- [ ] Manage book Premium/free access and in-app reading, plus product price, variants, stock, physical delivery, and supported order options.
- [ ] Add preview, edit history, and publishing checks for rights, metadata, file integrity, and narration timing.

**Done when:** an authorized staff member can add a supported product category/store section, configure and publish a page from supported blocks, and publish products without an app release; connected Android, iOS, and website clients activate the same revision safely. New behavior outside supported schemas/templates is explicitly tracked as app/backend work.

## Phase 5 — Reader, narration, and offline access

- [x] Prototype an in-app PDF surface with Android `PdfRenderer`, generated sample pages, loading/error copy, and no export/share controls; approved licensed-book integration remains open.
- [x] Provide sample page-turn, scroll, and PDF modes with persisted page position; production content integration and multi-device sync remain open.
- [x] Persist local reading progress and bookmarks across app restarts; offline-first cloud updates and sync conflict handling remain open.
- [ ] Integrate narration and word-level timing for supported books.
- [ ] Implement Autoplay pages, speed/voice controls, audio focus, pause/resume, and playback recovery.
- [ ] Implement authorized offline book storage in app-private storage, with encryption and explicit removal.
- [x] Add an Android Keystore AES-GCM app-private vault primitive with expiry checks, opaque book filenames, and explicit removal; trusted grant verification, download/sync integration, and device verification remain open.
- [x] Keep the sample PDF reader in-app with no export/share/download actions and enable Android secure-window protection while reading; production access enforcement remains a backend requirement.
- [ ] Define and implement offline access renewal/expiry behavior from Phase 0 decisions.

**Done when:** supported books open online and offline, progress resumes correctly, and access rules are enforced by the backend and app storage flow.

## Phase 6 — Payments and order fulfillment

- [x] Confirm current Google Play requirements for digital subscriptions/content in each launch market (Bangladesh review recorded in `firebase/PAYMENTS_POLICY.md`, 2026-10-12; recheck before implementation/release).
- [ ] Implement the approved Android digital billing flow and server-side purchase verification.
- [ ] Integrate the approved Bangladesh payment provider for physical book orders, including bKash and Visa where supported.
- [ ] Add idempotent payment callbacks, order status updates, refunds/cancellations, and reconciliation records.
- [ ] Add physical stock reservation, delivery fee calculation, fulfillment status, and tracking updates.
- [ ] Grant/revoke entitlements from verified transaction state, never from the client’s success screen alone.

**Done when:** successful, pending, failed, refunded, and expired purchase states all produce the correct access and order status.

## Phase 7 — Parent community and moderation

- [x] Gate local posting and group participation to parent preview accounts; server-side enforcement remains required.
- [x] Validate selected preview images by MIME type and reported size before display; upload validation, visibility rules, and removal behavior remain open.
- [x] Add local preview report/block controls for community posts; report/block flows for comments, users, groups, and server moderation remain open.
- [ ] Build moderator review queues, actions, and audit history in the admin site.
- [x] Add a browser-local moderation queue with synthetic reports, keep/hide preview actions, and a local decision timeline; real report ingestion, staff permissions, and tamper-resistant audit records remain open.
- [x] Add community guideline and child-privacy messaging to the local feed; consent records and support escalation remain open.

**Done when:** users can report harmful content and authorized moderators can review and act on reports with an audit trail.

## Phase 8 — Release readiness and ongoing operations

- [ ] Complete accessibility and English/Bangla content review on supported device sizes. Reader controls now reflow on narrow screens; full content/accessibility review remains deferred to final QA.
- [ ] Review privacy disclosures, consent flow, account deletion, and data retention.
- [ ] Configure production crash/error reporting, dashboards, and support escalation ownership.
- [ ] Prepare store listing, screenshots, subscription descriptions, and customer support documentation.
- [x] Draft English/Bangla store listing copy and a customer-support playbook with feature, privacy, pricing, screenshot, contact, and ownership approvals called out (`release/STORE_LISTING_DRAFT.md`, `release/SUPPORT_PLAYBOOK_DRAFT.md`). Final store assets and approved policies remain open.
- [ ] Run release acceptance against the defined product flows and resolve launch-blocking issues.
- [ ] Publish staged rollout and document rollback/incident procedures.

**Done when:** product, operations, support, and privacy owners approve the release checklist and the staged release can be monitored and rolled back.

## Tracking notes

Keep this section current as work proceeds:

- **Current phase:** Shared cross-platform product/storefront/page and publication contracts, Android configuration/product models, repository policy, a local admin prototype, and a responsive English/Bangla customer website preview are in place. Product editing in the admin preview is generated from shared product attribute definitions. Android and website still use local sample data; Firebase adapters and the secured admin publisher are required for live no-release updates.
- **Next task:** Finish Firebase Console provider setup (Google OAuth client/SHA fingerprints and Phone provider), then build and QA email verification, Google sign-in, and Bangladesh phone OTP. After auth QA, implement the version-pinned Firestore read adapter against the published manifest/snapshot contract; keep admin publishing server-only.
- **Firebase client foundation (2026-10-16):** added Firebase Auth and Firestore Android SDKs, conditional initialization from the selected public environment properties, and Android internet permission. The app remains in local preview mode when configuration is incomplete. App-side auth flows are now implemented; provider setup and Firestore read adapter remain open.
- **Development Firebase config (2026-10-17):** a Firebase Android config for `com.littlepages.app` is present locally for the development project and its public options are mapped into ignored `local.properties`. Email/password sign-in is enabled in Firebase Console. Firestore rules remain deny-all; web app registration, staging/production projects, and live adapter verification remain pending.
- **Android authentication client (2026-10-17):** connected email/password sign-up and sign-in with a required email-verification gate, resend/status check, password reset, Google Credential Manager sign-in, Firebase phone OTP for Bangladesh numbers, and guest entry. Family profiles remain device-local previews. Google and phone providers are not yet configured in Firebase Console; the downloaded Android JSON has no OAuth clients, so Google also needs the Web client ID in ignored `local.properties`. SHA fingerprints, Firebase provider setup, build/device QA, and provider-account linking remain pending; no build or tests were run.
- **Local admin progress (2026-10-16):** expanded the browser page composer with bilingual page titles, reversible enable/disable, editable English/Bangla content for supported blocks, product collection storefront/layout selection, and category rail selection. This improves local draft authoring only; staff authentication, approval, server publication, and durable audit history are still required, so Phase 4 production publishing remains open.
- **Android publication cache progress (2026-10-16):** added an app-private durable cache that serializes and restores the full manifest, storefront configuration, page blocks, products, variants, and typed attributes as one snapshot. The existing repository validates snapshots before saving or activating them. This supports offline last-valid fallback but does not fetch from Firebase or verify a cryptographic content hash; remote adapter and server trust boundaries remain open.
- **Web snapshot validation progress (2026-10-16):** the website demo now rejects malformed or internally inconsistent storefront snapshots (including duplicate IDs, unsupported types/blocks, missing references, incomplete English/Bangla text, and invalid variants) before verifying the manifest hash and activation. It remains a static-file demo; a trusted publication API and live refresh notifications are still open.
- **Local product-type progress (2026-10-16):** the admin preview can add product types by selecting an existing Books or physical-goods renderer, copy that template's declared fields, then rename, reorder, enable, or disable the type. This does not create new renderer behavior; storefront configuration publishing and native client navigation/filter integration remain open.
- **Blockers:** Firebase project IDs/app registrations and provider configuration; final owner/legal approval of provisional policies; content catalogue and distribution rights; physical fulfillment terms; moderation response ownership; applicable digital billing flow. Firebase auth, roles/App Check, Firestore adapters, and the web admin are not connected.
- **Configuration progress:** Added shared product/storefront/page schemas and a cross-platform publication manifest, provisional Books and disabled Toys storefront definitions, plus Android models for products, variants, product types (including attribute definitions), storefronts, and allowlisted blocks. Documented draft validation, revision-pinned reads, atomic client activation, cache refresh, and rollback. The `admin/` prototype edits provisional policies, storefronts/categories/pages/products, generates product fields from declared type metadata, validates local references/attributes, and saves/exports draft bundles. The `website/` preview reads the shared configuration and sample product contract with English/Bangla UI. Android and website still use local preview data; actual Firestore/content API adapters, staff authentication, server publishing, approvals, and audit history remain pending. Arbitrary new business logic/renderers/payment providers still require implementation work.
- **Completed in this pass:** finished the local auth/onboarding screens, child-specific reading progress, offline list/removal preview, parent feed with local image posts and group discussions, profile/privacy/support surfaces, sample checkout/order history, subscription lifecycle previews, and service recovery flows; previous local browsing/detail/review states, preview account switching, report/block confirmation, and reusable loading/error/retry components remain complete. Added an Android `PdfRenderer` preview backed by a generated sample PDF in the app cache; this is placeholder content, not a customer book. No Firebase, account, storage, payment, moderation, or support request was connected, and no build was run.
- **Completed in this pass (2026-10-11):** added a shared product attribute schema for Books and Toys, corresponding Android metadata models, and schema-driven product attribute controls plus local create/edit/archive drafts in the admin preview. Added browser draft persistence, cross-reference/type-attribute validation, JSON bundle export, and separate book/physical-item cards and details in the responsive English/Bangla website preview. Syntax/JSON/diff checks passed; no Gradle build or tests were run. Changes are prototypes only and are not connected to Firebase or live publishing.
- **Completed in this pass (2026-10-12):** added Bangla strings for the fixed Android preview literals identified by the source audit; device-level localization/accessibility review is still pending. Added Android Gradle environment selection and public Firebase `BuildConfig` fields sourced from ignored local properties or CI Gradle properties, with an explicit unconfigured flag. Documented Firebase environment boundaries, trusted backend operation contracts, deny-all rules/security requirements, and monitoring/incident response inputs. Extended the admin preview with an explicitly fictional, browser-local role matrix. Updated `.gitignore` for project-specific Firebase files and server credentials. These steps prepare implementation but do not connect Firebase, secure staff access, or deploy backend/monitoring services. No Gradle build or tests were run.
- **Completed in this pass (2026-10-12):** reviewed the current Google Play digital-goods, subscription, mixed physical/digital SKU, and alternative-billing requirements for Bangladesh and recorded implementation implications and primary sources in `firebase/PAYMENTS_POLICY.md`. This completes the policy research checkbox; billing implementation and server purchase verification remain open.
- **Additional local work (2026-10-12):** the English/Bangla website preview now loads a pinned demo manifest/snapshot, checks the snapshot SHA-256 before activation, caches the last valid copy in browser storage, and rechecks on tab visibility. This advances the web side of versioned publication reads only; Android/iOS adapters, trusted publishing, and live manifest notifications are still required, so the cross-platform publication task remains open. No tests or builds were run.
- **Additional local work (2026-10-12):** extended English/Bangla coverage to Compose accessibility labels and remaining moderation/storage preview messages; added a visible proposed least-privilege role capability matrix to the local admin preview and security review; expanded browser-local catalogue editing with stable-ID storefront/category rename, ordering, reversible category archive/restore, and saved-draft restore history; added a local synthetic moderation queue and decision timeline. Added an Android Keystore AES-GCM private offline vault primitive and drafted English/Bangla store listing and customer-support materials. Marked Gradle environment selection complete; Firebase project registration and SDK wiring remain separate Phase 3 tasks. These are local preparations only: device TalkBack/font checks, verified offline grants/download integration, real staff authorization, Firebase integration/rules, and deployed monitoring remain open. Static JavaScript syntax and diff checks passed; no Gradle build or tests were run.
- **Important limit:** local reading data uses a placeholder profile ID and has no cloud sync. Read-along speaks sample text using an installed English Android TTS voice; exact word timing depends on the engine and falls back to estimated highlighting. No licensed book text/audio/timing is connected. Sample catalogue availability is local metadata. Community images are not uploaded. Authentication, payment, real book content, offline content storage, and Firebase services are not connected. Network status is monitored, but data fetching and service-specific retries are not yet implemented.
- **Decision log (2026-10-11):** English and Bangla are launch languages. PDFs are in-app only for every customer, including Premium. Premium narration/word highlighting is a subscription feature. When a physical edition is available, offer both in-app PDF access and the printed book. Requested Bangladesh payment methods are bKash and Visa. Generic offline, entitlement, privacy, physical-commerce, and community defaults are recorded as editable provisional settings; owner/legal approval and live admin publishing remain open.

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
- `firebase/contracts/storefront.v1.schema.json`, `firebase/contracts/publication-manifest.v1.schema.json`: shared commerce/page and publication contracts for all client platforms
- `firebase/PUBLISHING.md`: shared draft, publish, revision refresh, and rollback behavior
- `admin/`: static local CMS interaction prototype; no staff authentication or live publishing
- `firebase/firestore.rules`, `firebase/storage.rules`: deny-all starter rules until reviewed production rules exist

As feature implementation grows, split the prototype into feature packages and extract independent Gradle modules when that separation reduces coupling or makes parallel development easier.
