# High 5xx error rate

Use when a service returns a high share of 5xx responses (ERROR RATE SPIKE).

## Triage
1. Check whether a deployment went out in the last 30 minutes: `kubectl rollout history deploy/<service>`.
2. Look at the error breakdown by endpoint. If a single endpoint dominates, the cause is usually a dependency of that endpoint.
3. Check downstream dependencies (database, cache, third-party APIs) for timeouts or connection refusals.

## Mitigate
- If a recent deploy is the cause, roll back: `kubectl rollout undo deploy/<service>`.
- If a dependency is failing, enable the circuit breaker or fallback for that dependency.
- Scale out only if pods are crash-looping because of resource exhaustion (OOMKilled).

## Verify
The error rate should drop below threshold within two evaluation windows; SentinelOps resolves the incident automatically.
