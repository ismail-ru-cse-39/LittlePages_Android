# LittlePages website preview

This responsive English/Bangla storefront prototype reads a publication manifest and one pinned snapshot from `firebase/config/demo-manifest.v1.json` and `firebase/config/demo-publication.v1.json`. The manifest identifies the configuration and catalogue revisions and carries a SHA-256 hash of the complete snapshot. The browser validates localized fields, supported renderers/blocks, unique IDs, and cross-references before checking the hash and swapping the snapshot into view. It keeps the last valid snapshot in local storage and rechecks the manifest when the tab becomes visible. Navigation, categories, page blocks, and product filtering use the shared IDs and block types used by the Android/admin prototypes.

Serve it from the repository root so browser fetches can reach the shared data files:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/website/`.

The website uses a checked-in demo manifest and sample content by default. To read live published content, set the Firebase Web app's public `apiKey` and `projectId` in `firebase-config.js`; the page then reads `publicConfiguration/current`, its pinned configuration revision, and the pinned product/promotion collections through the Firestore REST API. The rules must be deployed and the API key should be restricted to the website's hosting domains. The website validates the same canonical snapshot hash used by the publisher and keeps its last complete valid snapshot in browser storage. Refresh runs on tab visibility; this is polling, not a live Firestore listener. Checkout, accounts, payments, and production book access are not connected. `firebase/PUBLISHING.md` describes the trusted publisher and revision behavior.
