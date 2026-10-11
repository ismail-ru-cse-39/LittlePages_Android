# LittlePages admin preview

This is a static interaction prototype for the planned staff console. It previews storefront, category, page-block, provisional policy, product draft, and staff role-matrix editing using the shared configuration as its initial data. Product attribute inputs are generated from each product type's declared attribute schema, so supported templates can add metadata fields without hard-coding them into this editor.

Run it from the repository root so the page can read the shared JSON defaults:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/admin/`. Storefront/category/page/policy/product edits, storefront/category renaming and ordering, reversible category archiving, product archiving, and page-block ordering can be saved to this browser's local storage or exported as a JSON draft bundle after local reference and declared product-attribute validation. Up to 20 saved local draft revisions can be restored from the revision history.

This prototype has no staff authentication, Firebase connection, server validation, production audit log, or live publish operation. The role matrix is fictional browser-local sample data; changing a role grants no access. The moderation queue contains synthetic reports only; decisions do not hide live posts or reach a moderation team. The local revision history and moderation timeline are conveniences for previewing flows and are not tamper-resistant audit records. The review dialog checks local references, then lets you save a local draft or export the draft JSON. Do not use it to manage production data. Those protections and the publishing service are tracked in `TASKS.md`.
