# Changes on branch `postgres-tuning`
Document any changes on this branch here.
### Added
- PostgreSQL configuration parameters are now configurable via Helm values (Kubernetes) and site values (Compose)
- PostgreSQL tuning documentation at Advanced Topics > PostgreSQL Tuning

### Changed
- PostgreSQL default `random_page_cost` set to `1.1` (SSD-optimized, was PostgreSQL default of `4.0`)
- PostgreSQL default `effective_io_concurrency` set to `200` (SSD-optimized, was PostgreSQL default of `1`)
- PostgreSQL default `log_autovacuum_min_duration` set to `0` (log all autovacuum runs, was disabled)
- Lookup PostgreSQL instance now uses right-sized defaults: `shared_buffers=32MB`, `max_connections=100`, `max_worker_processes=4`

### Fixed
-

### Removed
-

### Deprecated
-

### Security
-

### Tech-Debt/Refactor
-

### Upgrade and Migration
- PostgreSQL now starts with SSD-optimized planner settings and autovacuum logging enabled. Review the [PostgreSQL Tuning](advanced/postgres-tuning.md) documentation for recommended `shared_buffers` sizing based on your host RAM (default remains 128MB).
