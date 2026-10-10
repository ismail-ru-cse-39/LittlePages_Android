# LittlePages architecture decisions

## Client direction

- Android is the first client: Kotlin, Jetpack Compose, a single-activity UI, and feature-oriented packages.
- Keep UI independent from Firebase and payment SDKs. Screens depend on feature use cases/repositories; repository implementations own network, persistence, and entitlement checks.
- Keep catalogue, reader access, commerce, and community contracts server-owned so iOS, web, Android, and the staff console share the same rules.
- The current prototype is deliberately transitional: navigation and screens live in `app/src/main/java/com/littlepages/app/ui/LittlePagesApp.kt`, with shared colors in `ui/theme`. Do not add production backend behavior to that screen file.

## Planned package boundaries

`com.littlepages.app` contains app startup and dependency wiring.

`com.littlepages.app.core` contains shared design components, navigation contracts, result/error types, and platform utilities.

`com.littlepages.app.feature.<name>` contains one feature's UI, state holder, and domain-facing interfaces. Initial features: `account`, `catalog`, `reader`, `community`, `family`, `commerce`, and `support`.

`com.littlepages.app.data` contains repository implementations and local/remote data sources. Feature UI must not import Firestore, Storage, or payment SDK types.

Start as packages in the app module. Extract Gradle modules only when build ownership or dependency enforcement justifies the extra setup. This keeps early development quick and lets boundaries evolve without a costly module graph.

## Platform expansion

- Android, iOS, web, and admin clients use shared backend contracts and server authorization. They do not share UI code.
- Store all book metadata with locale-aware fields and a content version. Reader progress/bookmarks are per parent-owned child profile.
- Book files stay private. Clients request authorized reading/offline access; the product decision is that customer PDF export/download is disabled.
- Business and privacy rules still require owner approval before production configuration; see `TASKS.md` and `firebase/SCHEMA.md`.

## Configuration

Do not commit service account keys, payment secrets, or production Firebase configuration. Add environment-specific Firebase app configuration only after the owner provides project IDs and app registrations. Secret values belong in CI/provider secret stores; client apps may contain public Firebase app identifiers, never server credentials.
