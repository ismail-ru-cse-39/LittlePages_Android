# LittlePages website preview

This responsive English/Bangla storefront prototype reads a publication manifest and one pinned snapshot from `firebase/config/demo-manifest.v1.json` and `firebase/config/demo-publication.v1.json`. The manifest identifies the configuration and catalogue revisions and carries a SHA-256 hash of the complete snapshot. The browser validates localized fields, supported renderers/blocks, unique IDs, and cross-references before checking the hash and swapping the snapshot into view. It keeps the last valid snapshot in local storage and rechecks the manifest when the tab becomes visible. Navigation, categories, page blocks, and product filtering use the shared IDs and block types used by the Android/admin prototypes.

Serve it from the repository root so browser fetches can reach the shared data files:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/website/`.

The website uses a checked-in demo manifest and sample content. Its visibility refresh and cache are browser-local; it has no live manifest listener, Firebase connection, accounts, orders, payments, or production book access. The demo hash protects against accidental snapshot mismatch, not a malicious server or a user-editable local cache. `firebase/PUBLISHING.md` describes the trusted publishing API and cross-client update behavior that still need Firebase setup.
