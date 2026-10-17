# Firebase environment setup

LittlePages has three intended Firebase environments: development, staging, and production. They must be separate Firebase projects, each with its own Android app registration and web app registration. Project IDs and app IDs are intentionally not fabricated in this repository.

## Configuration boundaries

- Android client options: this repo initializes Firebase from the selected environment's public Gradle/local properties; no `google-services.json` is required for this setup. If the project later switches to the Google Services Gradle plugin, keep per-environment files under `app/src/{development,staging,production}/google-services.json`; these files are ignored by Git.
- Web public Firebase options: inject the matching web app options at build/deploy time. Do not commit a production web config file or copy a project ID from one environment into another.
- Admin/server credentials: use the deployment platform's secret manager or workload identity. Never put a service-account JSON file in the Android app, website, or repository.
- Local developer setup: keep project selection and local-only values in ignored local configuration. Do not put private keys or payment credentials in `local.properties`, source code, or a client config.

The Android build now accepts public Firebase app options through Gradle properties or the ignored root `local.properties`. Select one environment without editing source code:

```properties
littlepages.environment=development
firebase.development.projectId=
firebase.development.appId=
firebase.development.apiKey=
firebase.development.messagingSenderId=
firebase.staging.projectId=
firebase.staging.appId=
firebase.staging.apiKey=
firebase.staging.messagingSenderId=
firebase.production.projectId=
firebase.production.appId=
firebase.production.apiKey=
firebase.production.messagingSenderId=
```

To select another environment in CI, pass `-Plittlepages.environment=staging` and supply that environment's values as Gradle properties from the CI secret/configuration store. Project ID, Firebase app ID, messaging sender ID, and Firebase client API key are client configuration values, not server secrets; Firestore/Storage rules and App Check still protect the services. If any value is missing, `BuildConfig.FIREBASE_CONFIGURED` is false and the app keeps running in local preview mode. When all four are present, the Android app initializes Firebase using those options. Firebase Authentication and Cloud Firestore SDKs are included; application auth flows and Firestore adapters are not connected yet.

## Required values before wiring the clients

For each environment, record the Firebase project ID, Android package/app registration, web app registration, enabled Authentication providers, Firestore location, Storage bucket, App Check app registrations, and authorized domains in the team's private deployment record. Production values and provider secrets require owner-controlled access.

This document defines the setup boundary. Android selects an environment through Gradle/local properties; Gradle product flavors, web initialization, project registrations, provider setup, Firestore rules, and deployment are not configured yet. Keep project creation and integration tasks in `TASKS.md` open until each environment is registered, secured, and verified against its intended project.
