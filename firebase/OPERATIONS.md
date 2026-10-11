# Backend monitoring and incident signals

This is a deployment-independent monitoring plan; no project, dashboard, alert policy, or support rotation is configured yet.

## Signals to collect

- Callable/API request count, latency, timeout, and error rate by operation and environment.
- Firestore read/write failures, quota pressure, rejected schema revisions, manifest-to-snapshot hash mismatches, and stale-client counts.
- Storage upload/download denial counts, upload validation failures, and abnormal private-book access patterns.
- Payment webhook signature failures, duplicate events, processing lag, reconciliation mismatches, and orders stuck in pending states.
- Authentication provider failures, App Check rejects, rate-limit events, and staff authorization denials.
- Community report backlog, oldest unreviewed report, moderation action failures, and report-response target breaches after an owner-approved SLA exists.

## Alert policy inputs

Before creating alerts, the service owner must set per-environment availability/latency targets, error-rate windows, payment reconciliation expectations, moderation response targets, and on-call routing. Use warning and page-level thresholds separately; avoid sending user data or book content to logs. Include operation name, environment, trace ID, and non-sensitive record IDs in structured logs.

## Incident response

Keep a rollback path for published configuration revisions and a runbook for disabling affected payment/order operations without granting or revoking entitlements from client state. Record incident timeline, owner, customer impact, mitigation, and follow-up. Dashboards, alerts, support escalation, and on-call ownership remain unconfigured until the Firebase project and operational owners are supplied.
