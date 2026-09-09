# Production topology requirements

The repository's Compose stack is deliberately single-node development infrastructure. It
is not a production deployment: one PostgreSQL process, one Kafka broker, one Redis, and one
Elasticsearch node are each a failure domain.

Production must supply five independently recoverable PostgreSQL service endpoints (auth,
catalog, order, analytics, recommendation). A managed cluster may host more than one logical
database only when its multi-AZ failover, upgrade, connection, noisy-neighbour, and restore
boundaries are explicitly accepted. Order reads may use a replica only for endpoints whose
staleness contract is documented; checkout/recovery/outbox always use the writer.

Kafka requires at least three brokers across failure domains, topic replication factor 3,
`min.insync.replicas=2`, producer `acks=all`, unclean leader election disabled, TLS, ACLs,
and monitored disk/consumer lag. Partition count must come from measured peak throughput
and consumer parallelism. The application already sets idempotent `acks=all` production and
`read_committed`; the repository does not provision or prove the broker topology.

Orders are the durable financial record and cannot grow without bounds. Before the first
retention window closes, implement time-based archival of terminal orders to encrypted
object storage with checksums and a restore/query procedure. Keep active recovery and outbox
rows on the writer. Partition conversion, cutoff dates, legal retention, read-replica lag
limits, capacity, and cost require measured traffic/data growth and are intentionally not
invented here.
