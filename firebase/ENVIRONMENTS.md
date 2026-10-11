# Firebase environment setup

LittlePages has three intended Firebase environments: development, staging, and production. They must be separate Firebase projects, each with its own Android app registration and web app registration. Project IDs and app IDs are intentionally not fabricated in this repository.

## Configuration boundaries

- Android client files: `app/src/{development,staging,production}/google-services.json` after the matching app registrations exist. These files are ignored by Git.
- Web public Firebase options: inject the matching web app options at build/deploy time. Do not commit a production web config file or copy a project ID from one environment into another.
- Admin/server credentials: use the deployment platform's secret manager or workload identity. Never put a service-account JSON file in the Android app, website, or repository.
- Local developer setup: keep project selection and local-only values in ignored local configuration. Do not put private keys or payment credentials in `local.properties`, source code, or a client config.

## Required values before wiring the clients

For each environment, record the Firebase project ID, Android package/app registration, web app registration, enabled Authentication providers, Firestore location, Storage bucket, App Check app registrations, and authorized domains in the team's private deployment record. Production values and provider secrets require owner-controlled access.

This document defines the setup boundary only. The Android Gradle flavors, Firebase SDK adapters, web initialization, project registrations, provider setup, and deployment are not configured yet. Keep the task in `TASKS.md` open until each environment can be selected without editing application code and the corresponding app can connect to its intended project.
