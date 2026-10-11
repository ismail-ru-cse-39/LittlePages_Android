# LittlePages admin preview

This is a static interaction prototype for the planned staff console. It previews the multi-product store, category, and page-block workflows using the provisional configuration as its initial data.

Run it from the repository root so the page can read the shared JSON defaults:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/admin/`. Storefront/category/page edits and block ordering can be saved to this browser's local storage.

This prototype has no staff authentication, Firebase connection, server validation, audit log, or live publish operation. The review button only saves a local draft. Do not use it to manage production data. Those protections and the publishing service are tracked in `TASKS.md`.
