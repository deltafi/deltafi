# PostgreSQL Configuration Tuning

DeltaFi uses two PostgreSQL instances with different workload profiles. Both are configurable via Helm values (Kubernetes) or site values (Compose).

## Instances

| Instance | Purpose | Default shared_buffers | Default max_connections |
|----------|---------|----------------------|------------------------|
| **deltafi-postgres** | DeltaFile metadata, state, configuration | 128MB | 1000 |
| **deltafi-postgres-lookup** | Lookup tables (key/value store) | 32MB | 100 |

The lookup instance is only deployed when `deltafi.lookup.enabled` is set to `true`.

## Parameters

### Tunable Parameters

| Parameter | Main Default | Lookup Default | Description |
|-----------|-------------|----------------|-------------|
| `shared_buffers` | 128MB | 32MB | PostgreSQL's shared memory buffer pool. Recommend 25% of available RAM for the main instance. |
| `max_connections` | 1000 | 100 | Maximum concurrent database connections. |
| `log_statement` | ddl | ddl | Which SQL statements to log. `ddl` logs schema changes. |
| `log_min_duration_statement` | 1000 | 1000 | Log queries taking longer than this many milliseconds. |
| `log_autovacuum_min_duration` | 0 | 0 | Log all autovacuum runs. Essential for visibility at scale. |
| `random_page_cost` | 1.1 | 1.1 | Query planner cost estimate for random page access. 1.1 is correct for SSD storage. |
| `effective_io_concurrency` | 200 | 200 | Number of concurrent I/O operations for bitmap heap scans. 200 is appropriate for SSD. |
| `wal_compression` | lz4 | lz4 | Compression codec for WAL full-page images. `lz4` is significantly cheaper on CPU than `pglz` and relieves WALWrite contention under high write throughput. |

### Helm-Only Parameters

These additional parameters are set in Kubernetes deployments via the Zalando Postgres Operator:

| Parameter | Main Default | Lookup Default | Description |
|-----------|-------------|----------------|-------------|
| `log_destination` | stderr | stderr | Where to send log output. |
| `logging_collector` | off | off | Whether to use the logging collector subprocess. |
| `log_connections` | off | off | Log each new connection. |
| `log_disconnections` | off | off | Log each disconnection. |
| `max_worker_processes` | 16 | 4 | Maximum background worker processes. |
| `timescaledb.max_background_workers` | 16 | 4 | TimescaleDB background workers. |
| `wal_level` | logical | logical | WAL detail level. `logical` enables logical replication. |
| `max_replication_slots` | 1 | 1 | Maximum replication slots. |

## Overriding Parameters

### Kubernetes (Helm)

Override in your Helm values file:

```yaml
postgres:
  parameters:
    shared_buffers: "2GB"        # 25% of 8GB host RAM
    max_connections: "500"
  lookup:
    parameters:
      shared_buffers: "64MB"
```

Values merge with defaults — you only need to specify what you're changing.

### Compose

Override in `site/values.yaml`:

```yaml
postgres:
  parameters:
    shared_buffers: 2GB          # 25% of 8GB host RAM
  lookup:
    parameters:
      shared_buffers: 64MB
```

Then run `deltafi up` to apply.

## Sizing Recommendations

### shared_buffers

The most impactful parameter. PostgreSQL uses this as its primary cache for table and index data.

| Host RAM | Recommended shared_buffers (main) | Recommended shared_buffers (lookup) |
|----------|----------------------------------|-------------------------------------|
| 4GB | 512MB | 32MB |
| 8GB | 2GB | 32MB |
| 16GB | 4GB | 64MB |
| 32GB | 8GB | 64MB |
| 64GB+ | 16GB | 128MB |

General rule: 25% of available RAM for the main instance. The lookup instance handles a small dataset and rarely needs more than 64MB.

### max_connections

Default of 1000 is sufficient for most deployments. Each connection consumes ~5-10MB of memory. If you reduce `max_connections`, you free memory for `shared_buffers`.

## Verifying Settings

After applying changes, verify they took effect:

```bash
# Kubernetes
kubectl exec -it deltafi-postgres-0 -- psql -U postgres -c "SHOW shared_buffers; SHOW max_connections; SHOW random_page_cost; SHOW effective_io_concurrency; SHOW log_autovacuum_min_duration;"

# Compose
docker exec deltafi-postgres psql -U postgres -c "SHOW shared_buffers; SHOW max_connections; SHOW random_page_cost; SHOW effective_io_concurrency; SHOW log_autovacuum_min_duration;"
```

## Advanced Tuning

For deployments with specific hardware or workload requirements, additional PostgreSQL parameters can be added to the parameters map. Common candidates:

- `effective_cache_size` — Estimate of OS disk cache available to PostgreSQL. Typically 50-75% of total RAM.
- `work_mem` — Memory per sort/hash operation. Higher values speed up complex queries but multiply by `max_connections`.
- `maintenance_work_mem` — Memory for maintenance operations (VACUUM, CREATE INDEX). Can be set higher than `work_mem`.

These are site-specific and should be tuned based on observed query performance. See the [PostgreSQL documentation](https://www.postgresql.org/docs/current/runtime-config-resource.html) for details.
