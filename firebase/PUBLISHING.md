# Shared publishing and client update design

## One source, separate renderers

The staff admin edits one shared content system. Android, iOS, and the public website consume the same published records and IDs; each client renders them with its native UI and its own implementation of the versioned page-block registry. There must not be separate app and website copies of products, categories, prices, or store pages.

Use structured, revisioned records and keep document size and query load bounded. The logical content includes:

- A small **published manifest** with the active publication, configuration, and catalogue revisions, supported locales, content hash, and minimum client versions.
- Versioned **configuration** for policies that affect presentation and supported behavior, such as storefront visibility, categories, product-type schemas, page blocks, and non-security business settings.
- **Products and product-specific data** with stable IDs. Reader assets, entitlements, and reading progress stay in book-specific services/models.
- Private drafts and immutable published snapshots, so clients can continue using one pinned revision while administrators prepare the next one.

The manifest is the shared revision pointer. It is validated and published only by the trusted admin service. Admins can preview draft content against Android/iOS/web block registries before publishing. A publish produces an immutable revision, audit entry, content hash, and then advances the manifest. Rollback advances the pointer to a known good snapshot as a new publication revision; it does not erase history.

## Client update behavior

1. Each app/site fetches the manifest and records the revision currently displayed.
2. While foregrounded, the client listens for changes to the small manifest document using Firestore's snapshot listener. It also revalidates on Android/iOS resume, website visibility, and network reconnection in case a notification was missed.
3. After the manifest changes, the client fetches the full content snapshot pinned to those revision IDs.
4. The client validates schema version, block types, product type renderer IDs, and minimum client version before switching its active snapshot.
5. It swaps snapshots atomically after all needed data is available. On network failure or unsupported data, it keeps the last valid cached revision and reports the issue; it never displays a partially applied publish.
6. A newly published change becomes visible across connected clients after they receive and activate that revision. Offline clients update when they reconnect. No remote system can force an already-offline device to refresh.

Public catalogue/content reads may be cached by revision. Account, child data, entitlement, checkout, inventory reservation, payment verification, and moderation actions use authenticated trusted services and are never authorized by the manifest or client cache.

## Admin write API contract

The admin browser must call a trusted service using an authenticated staff identity. The client never writes the published manifest, roles, audit records, payment state, inventory reservation state, or private book files directly.

| Operation | Expected behavior |
|---|---|
| Create/update draft | Save structured admin edits against a base revision; keep draft private |
| Validate draft | Check JSON schema, cross references, locales, product types, block allowlist, content rights/status, product fields, and available client renderers |
| Preview | Return a draft snapshot for the web preview and provide revisioned preview data to app QA builds |
| Publish | Require staff permission and publish approval; create immutable snapshot and audit event, then advance manifest |
| Roll back | Create a new publication revision pointing to a validated known-good snapshot; preserve the audit trail |

Publishing must detect concurrent edits. If the base revision changed while an admin edited, reject or require an explicit rebase; never silently overwrite another administrator's publish.

## Rollout and compatibility

- Keep document schemas and block IDs versioned. Deprecate blocks only after supported mobile versions no longer depend on them.
- Reject unsupported blocks and product renderers during draft validation. Clients still retain a safe fallback for stale caches or server defects.
- Use minimum client versions only for a real incompatible change. Prefer additive, optional fields and stable IDs so content changes do not force an app-store release.
- Keep admin drafts and unpublished assets inaccessible to customer clients. Public clients can read only published revisions and public product metadata.
- The contract in `contracts/publication-manifest.v1.schema.json` defines the shared pointer shape. Firebase project setup and concrete API/security implementation remain tracked in `TASKS.md`.
