# Rollback and incident steps

Use this when a development deploy of rules or functions misbehaves. It does not replace owner approval, and it does not create staging or production projects.

## Before the deploy

1. Confirm the Firebase project is the development project and the function region is `asia-south1`.
2. Record the current live functions revision and the current `publicConfiguration/current` publication, configuration, and catalogue revisions.
3. Export a copy of the deployed `firestore.rules` and `storage.rules` if they differ from the repository.

## Functions

1. In Firebase console or the CLI, roll functions back to the last known-good revision. Do not delete the payment webhook secret or the discount HMAC secret while rolling back.
2. Send one signed test webhook with an unknown order ID. The expected result is HTTP 404 and no new entitlement.
3. Confirm a client success screen still cannot mark an order paid.

## Rules

1. Redeploy the last known-good `firestore.rules` and `storage.rules`.
2. Confirm client writes stay denied, including `users`, `orders`, `entitlements`, `posts`, and `comments`.
3. Confirm a signed-in parent can still read only their own child profiles, reading progress, and privacy requests.

## A bad publication

1. Do not edit an immutable revision in place.
2. An active publisher calls `rollbackConfigurationPublication` for the last known-good revision.
3. Confirm `publicConfiguration/current` advances to a new publication revision that points at that snapshot, and that an audit record exists.
4. Open the Android app, resume it, and confirm the storefront shows the restored revision rather than the bad one.

## Payments and stock

1. Disable the provider webhook at the provider before retrying a bad event.
2. A failed payment for a printed order releases the reserved stock unit. A refund does not put stock back; an owner restocks only after checking whether the copy shipped.
3. A `subscription_expired` event revokes subscription entitlements for that product only. It must not revoke a book purchase.

## After recovery

Write down the start time, the project, the revision restored, and what parents could see. Alert destinations are not connected yet, so this note is the incident record until monitoring is chosen.
