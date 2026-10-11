# Firebase environment setup

LittlePages has three intended Firebase environments: development, staging, and production. They must be separate Firebase projects, each with its own Android app registration and web app registration. Project IDs and app IDs are intentionally not fabricated in this repository.

## Configuration boundaries

- Android client files: `app/src/{development,staging,production}/google-services.json` after the matching app registrations exist. These files are ignored by Git.
- Web public Firebase options: inject the matching web app options at build/deploy time. Do not commit a production web config file or copy a project ID from one environment into another.
- Admin/server credentials: use the deployment platform's secret manager or workload identity. Never put a service-account JSON file in the Android app, website, or repository.
- Local developer setup: keep project selection and local-only values in ignored local configuration. Do not put private keys or payment credentials in `local.properties`, source code, or a client config.

The Android build now accepts public Firebase app options through Gradle properties or the ignored root `local.properties`. Select one environment without editing source code:

```properties
littlepages.environment=development
firebase.development.projectId=
firebase.development.appId=
firebase.development.apiKey=
firebase.staging.projectId=
firebase.staging.appId=
firebase.staging.apiKey=
firebase.production.projectId=
firebase.production.appId=
firebase.production.apiKey=
```

To select another environment in CI, pass `-Plittlepages.environment=staging` and supply that environment's values as Gradle properties from the CI secret/configuration store. Project ID, Firebase app ID, and Firebase client API key are client configuration values, not server secrets; Firestore/Storage rules and App Check still protect the services. A missing triplet produces `BuildConfig.FIREBASE_CONFIGURED == false`. The app does not consume these fields yet because the Firebase SDK and adapters are not connected.

## Required values before wiring the clients

For each environment, record the Firebase project ID, Android package/app registration, web app registration, enabled Authentication providers, Firestore location, Storage bucket, App Check app registrations, and authorized domains in the team's private deployment record. Production values and provider secrets require owner-controlled access.

This document defines the setup boundary only. The Android Gradle flavors, Firebase SDK adapters, web initialization, project registrations, provider setup, and deployment are not configured yet. Keep the task in `TASKS.md` open until each environment can be selected without editing application code and the corresponding app can connect to its intended project.
