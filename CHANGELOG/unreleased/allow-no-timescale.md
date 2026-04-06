# Changes on branch `allow-no-timescale`
Document any changes on this branch here.
### Added
-

### Changed
- Database migrations now conditionally skip TimescaleDB and pg_squeeze operations when the extensions are not installed, allowing DeltaFi to run with plain PostgreSQL

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
- Database migrations now conditionally skip TimescaleDB and pg_squeeze operations when the extensions are not installed
- Flyway repair runs automatically (once) for upgrades from pre-V69 to update checksums for modified migrations
- The `timescaleAnalyticsEnabled` property is automatically disabled when TimescaleDB is not installed

**Note:** Migrating an existing TimescaleDB installation to plain PostgreSQL is not supported. New installations may use plain PostgreSQL. TimescaleDB will be officially removed in 3.0.
