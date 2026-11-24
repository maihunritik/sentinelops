# Kafka consumer lag

Use when an event-driven service falls behind its Kafka topic and latency grows.

## Triage
1. `kafka-consumer-groups --describe --group <group>` to see lag per partition.
2. If one partition lags, look for a hot key or a poison message being retried.
3. If all partitions lag, consumers are under-provisioned or a downstream write is slow.

## Mitigate
- Scale consumers up to the partition count; beyond that extra replicas sit idle.
- Increase max.poll.records and use batch listeners for cheap per-record work.
- Move poison messages to a dead-letter topic.
