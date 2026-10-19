# LittlePages admin preview

This is a static interaction prototype for the planned staff console. It previews storefront, category, page-block, provisional policy, product draft, and staff role-matrix editing using the shared configuration as its initial data. Product attribute inputs are generated from each product type's declared attribute schema, so supported templates can add metadata fields without hard-coding them into this editor.

Run it from the repository root so the page can read the shared JSON defaults:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/admin/`. Storefront/category/page/policy/product edits, storefront/category renaming and ordering, reversible category and page disabling, localized page titles, editable English/Bangla supported-block content, product-collection settings, category-rail selection, product-type creation from supported templates with localized rename/reorder/disable controls, multi-category product assignment, product archiving, and page-block ordering can be saved to this browser's local storage or exported as a JSON draft bundle after local reference and declared product-attribute validation. Up to 20 saved local draft revisions can be restored from the revision history.

This prototype has no staff authentication, Firebase connection, server validation, production audit log, or live publish operation. The role matrix is fictional browser-local sample data; changing a role grants no access. The moderation queue contains synthetic reports only; decisions do not hide live posts or reach a moderation team. The local revision history and moderation timeline are conveniences for previewing flows and are not tamper-resistant audit records. The review dialog checks local references, then lets you save a local draft, export the preview JSON, or export a payload shaped for `saveConfigurationDraft`. For the Functions payload, enter the actual current `publicationRevision` from `publicConfiguration/current`; a stale value is rejected by the server. The payload includes public configuration/products/promotions only and excludes private discount-code drafts and local asset declarations. It still must be submitted by an authenticated, App Check verified staff client to the deployed Functions API. Do not use the preview to manage production data.
## Promotions preview

The local Promotions section drafts automatic discounts and shared/unique discount-code campaigns. It validates schedules and targets, hashes code strings in the browser draft, and offers generated plaintext codes for a one-time CSV download. The browser SHA-256 is a preview-only value and is not a production code digest. Never distribute prototype codes or store real customer identifiers here. Production must generate codes on a trusted authenticated service, store keyed HMAC-SHA-256 digests only, enforce limits transactionally with order creation, keep code records private, and write immutable staff audit events.

The Book assets section validates local file type/signatures, size limits, word-timing JSON shape, and SHA-256 checksums. It saves only a local metadata manifest; it does not upload or retain file bytes. Rights, malware/content scanning, timing-to-script review, private storage, and signed access remain server/reviewer responsibilities.
