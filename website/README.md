# LittlePages website preview

This responsive English/Bangla storefront prototype reads the shared provisional storefront configuration from `firebase/config/provisional-defaults.v1.json` and sample product documents from `firebase/config/demo-products.v1.json`. Navigation, category labels, page blocks, and product filtering are driven by the same stable IDs and block types used by the Android/admin prototypes.

Serve it from the repository root so browser fetches can reach the shared data files:

```sh
python3 -m http.server 8000
```

Open `http://localhost:8000/website/`.

The website uses sample content and local rendering. It has no account service, live manifest listener, Firebase connection, orders, payments, or production book access. `firebase/PUBLISHING.md` describes the shared revision/API connection that still needs Firebase setup.
