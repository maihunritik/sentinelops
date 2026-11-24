# Latency regression

Use when p95 latency rises well above the learned baseline (LATENCY REGRESSION) without many errors.

## Triage
1. Compare p50 and p95. If only p95/p99 moved, look for slow queries, lock contention or GC pauses.
2. Check database slow query logs and connection pool saturation (HikariCP pending threads).
3. Check JVM GC pause time and heap usage in Prometheus.
4. Check Kafka consumer lag if the service consumes events; lag causes queueing latency.

## Mitigate
- Add or fix a missing index if a slow query is the cause.
- Increase the connection pool only if the database has headroom.
- Scale the deployment horizontally if CPU is saturated (HPA may be at max replicas).
- Enable or warm the Redis cache for hot read paths.
