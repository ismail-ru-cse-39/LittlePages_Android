# LittlePages admin preview

This is a static interaction prototype for the planned staff console. It previews storefront, category, page-block, provisional policy, product draft, and staff role-matrix editing using the shared configuration as its initial data. Product attribute inputs are generated from each product type's declared attribute schema, so supported templates can add metadata fields without hard-coding them into this editor.

Run it from the repository root so the page can read the shared JSON defaults:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/admin/`. Storefront/category/page/policy/product edits, product archiving, and block ordering can be saved to this browser's local storage or exported as a JSON draft bundle after local reference and declared product-attribute validation.

This prototype has no staff authentication, Firebase connection, server validation, audit log, or live publish operation. The role matrix is fictional browser-local sample data; changing a role grants no access. The review dialog checks local references, then lets you save a local draft or export the draft JSON. Do not use it to manage production data. Those protections and the publishing service are tracked in `TASKS.md`.
