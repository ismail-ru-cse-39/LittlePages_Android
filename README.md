# LittlePages Android UI

Android-first Kotlin and Jetpack Compose UI prototype for the LittlePages children’s book reader.

## Run

Open this folder in Android Studio with Android SDK 37 and JDK 17 installed, then sync and run the `app` configuration.

## UI prototype scope

The app currently uses local sample data and in-memory navigation. It demonstrates:

- Parent welcome, email, and phone account screens
- Home, library, search, book details, and reviews
- Page-turn reader, read-along controls, autoplay pages, and Premium prompt
- Parent community feed, group discussions, and post creation
- Offline shelf, child profiles, subscription, checkout, orders, and support

Authentication, narration, Firebase, payments, image uploads, and backend operations are not connected yet. The checkout and subscription screens are visual interaction prototypes and do not process payments.

## Structure

- `MainActivity.kt`: Android entry point
- `ui/theme`: shared palette and Compose theme
- `ui/LittlePagesApp.kt`: app shell, screen routes, reusable UI components, and current screen prototypes

As implementation grows, move each feature into its own package or Gradle module while keeping shared tokens and components in the design system.
