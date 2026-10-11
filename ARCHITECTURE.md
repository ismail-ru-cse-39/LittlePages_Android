# LittlePages architecture decisions

## Client direction

- Android is the first client: Kotlin, Jetpack Compose, a single-activity UI, and feature-oriented packages.
- Keep UI independent from Firebase and payment SDKs. Screens depend on feature use cases/repositories; repository implementations own network, persistence, and entitlement checks.
- Keep catalogue, reader access, commerce, and community contracts server-owned so iOS, web, Android, and the staff console share the same rules.
- The current prototype is deliberately transitional: navigation and screens live in `app/src/main/java/com/littlepages/app/ui/LittlePagesApp.kt`, with shared colors in `ui/theme`. Do not add production backend behavior to that screen file.

## Planned package boundaries

`com.littlepages.app` contains app startup and dependency wiring.

`com.littlepages.app.core` contains shared design components, navigation contracts, result/error types, and platform utilities.

`com.littlepages.app.feature.<name>` contains one feature's UI, state holder, and domain-facing interfaces. Initial features: `account`, `storefront`, `catalog`, `reader`, `community`, `family`, `commerce`, and `support`.

`com.littlepages.app.data` contains repository implementations and local/remote data sources. Feature UI must not import Firestore, Storage, or payment SDK types.

Start as packages in the app module. Extract Gradle modules only when build ownership or dependency enforcement justifies the extra setup. This keeps early development quick and lets boundaries evolve without a costly module graph.

## Platform expansion

- Android, iOS, web, and admin clients use shared backend contracts and server authorization. They do not share UI code.
- Model the shop as a multi-product storefront. Books are the first product type; toys and later merchandise use the same product, category, inventory, order, and storefront contracts where possible. Keep reading-specific assets, progress, and entitlements book-specific.
- Store product metadata with locale-aware fields and stable IDs. Reader progress/bookmarks are per parent-owned child profile.
- Book files stay private. Clients request authorized reading/offline access; the product decision is that customer PDF export/download is disabled.
- Android's `OfflineBookVault` stores supplied book bytes only under `noBackupFilesDir`, encrypts with an Android Keystore AES-GCM key, binds the book ID and expiry into the authenticated data, and deletes a cached copy when its local expiry check fails. The caller must obtain and validate an access grant from the trusted service before writing or opening content; this vault does not authenticate a grant, renew access, or make a book downloadable through the customer UI. Server-time/clock rollback protection and account-linked revocation still require the access service.
- Business and privacy rules still require owner approval before production configuration; see `TASKS.md` and `firebase/SCHEMA.md`.

## Configuration

Do not commit service account keys, payment secrets, or production Firebase configuration. Android can select a Firebase environment and receive public app options through ignored local properties or CI Gradle properties; Firebase project IDs and app registrations still need owner setup. Secret values belong in CI/provider secret stores; client apps may contain public Firebase app identifiers, never server credentials. The Firebase SDK is not connected yet.

The environment boundaries and required registrations are listed in `firebase/ENVIRONMENTS.md`. Trusted operation requirements, the fail-closed rules review, and the operations/monitoring plan are in `firebase/BACKEND_OPERATIONS.md`, `firebase/SECURITY_REVIEW.md`, and `firebase/OPERATIONS.md`. These are implementation contracts and review notes; they do not indicate that Firebase is connected.

### Admin-managed product settings

- Treat the publishing service and versioned content model as the single source of truth for Android, iOS, and the website. Admins publish once; the clients implement the same contracts and render with native components. Follow `firebase/PUBLISHING.md` for the manifest, pinned revisions, drafts, validation, rollback, and client refresh contract.
- Keep `publicConfiguration/current` as a small published manifest pointing at immutable configuration and catalogue revisions; do not place the entire catalogue or site in one Firestore document. Provisional config values live in `firebase/config/provisional-defaults.v1.json`.
- Android UI observes a shared storefront/catalogue configuration repository. `VersionedStorefrontConfigurationRepository` now defines revision checks, schema validation, and last-valid-cache fallback behind remote/cache interfaces. The app is still wired to a local preview source; production Firestore and durable-cache adapters remain pending. Screens render stable IDs using localized labels from that source.
- Storefront sections, product categories, and landing pages are admin-managed records. Admins can create, rename, reorder, disable, and archive them without an app release. A store page is assembled from an allowlisted set of cross-platform blocks (for example: hero, category rail, product grid, product carousel, and editorial text); clients render those blocks through a versioned template registry. Do not store executable code, arbitrary HTML, or scripts in configuration.
- Product types use declared schemas and supported presentation templates. Admins can configure a supported type (for example, books or toys), its fields, categories, and page placement without an app release. Adding a new type that requires new behavior, fulfillment rules, or a renderer outside the supported schema still needs an app/backend release.
- Products share fields such as localized title/description, images, status, category IDs, variants, price, and inventory. Type-specific attributes are validated against the product type schema. Book files, reading entitlements, narration, and progress remain in book-specific models.
- Offline eligibility/expiry, access tiers, family privacy defaults, physical-book availability, and community rules are configurable values. A revision publish is audited, validated, and takes effect for supported app behavior without an app release.
- Remote configuration cannot replace authorization. Server functions enforce content access, billing, stock, privacy requests, and moderation. The console cannot enable a capability that has no implemented payment provider or app/backend behavior.
- Keep the configuration contract versioned and backward-compatible across Android, iOS, web, and staff admin. Unknown page blocks or schema versions must fail safely to a supported fallback and be observable; never make an unknown block crash the storefront.
- `admin/` is currently a static interaction prototype that reads the shared provisional JSON and saves browser-local drafts. It is not an authenticated staff tool and has no authority to publish or modify Firebase.
- The team/roles panel in `admin/` is a local role-matrix preview only. Role changes are not authorization and must be replaced by server-checked staff identity/permissions before deployment.
- All values are provisional until product/privacy/content owners approve them. Child privacy, store policy, law, and verified transaction state cannot be overridden by a client-side setting.
