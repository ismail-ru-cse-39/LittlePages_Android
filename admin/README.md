# LittlePages admin preview

This is a browser-based staff console and local preview. It supports storefront, category, page-block, provisional policy, product draft, and staff role-matrix editing using the shared configuration as its initial data. Product attribute inputs are generated from each product type's declared attribute schema, so supported templates can add metadata fields without hard-coding them into this editor.

Run it from the repository root so the page can read the shared JSON defaults:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/admin/`. Storefront/category/page/policy/product edits, storefront/category renaming and ordering, reversible category and page disabling, localized page titles, editable English/Bangla supported-block content, product-collection settings, category-rail selection, product-type creation from supported templates with localized rename/reorder/disable controls, multi-category product assignment, product archiving, and page-block ordering can be saved to this browser's local storage or exported as a JSON draft bundle after local reference and declared product-attribute validation. Up to 20 saved local draft revisions can be restored from the revision history.

The Trusted publishing panel can connect a registered Firebase Web app, sign in with Firebase Auth, read the current public manifest, and call App Check protected Functions for draft save/validation, approval, publication, rollback, and staff role changes. The live moderation queue loads reports and submits moderation actions through role-checked Functions. Configure Firebase Web app options and an App Check reCAPTCHA v3 site key in the panel; those are public client settings and are stored only in browser local storage. Do not enter service-account credentials or server secrets. The page must be served from an authorized HTTPS domain (localhost is suitable for development), and App Check, Auth providers, staff roles, Functions, and Firestore rules must already be configured in Firebase. Server role checks and owner review blockers remain authoritative. The local revision history, policy approvals, and sample moderation timeline are previews, not production audit records. The legacy review dialog can save/export local drafts; the live panel sends only public configuration/products/promotions and excludes private discount-code drafts and local asset declarations. No live Firebase project has been configured or verified from this repository.
## Promotions preview

The Promotions section supports local preview drafts and, when Firebase is connected, authenticated server discount-code campaigns. The server generates codes, stores keyed HMAC-SHA-256 digests, returns plaintext once for download, and records approval, publication, pause, and archive audit events. The custom shared-code input and browser SHA-256 are local-preview behavior only. Never distribute local preview codes or enter real customer identifiers while disconnected. Server redemption creates pending orders; payment settlement and reservation release remain unimplemented.

The Book assets section validates local file type/signatures, size limits, word-timing JSON shape, and SHA-256 checksums. It saves only a local metadata manifest; it does not upload or retain file bytes. Rights, malware/content scanning, timing-to-script review, private storage, and signed access remain server/reviewer responsibilities.
