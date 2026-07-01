# Changes on branch `cpp-reconnect`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- C++ action kit now rebuilds its Redis/Valkey connection after a drop instead of spinning on a broken socket. The poller in `ActionWorker` and the `LookupTableSupplierRunner` poll loop reconnect via `connect_with_retry` on exception, and `ActionEventQueue` operations on the shared connection (`put`, `heartbeat`, long-running task tracking) lazily reconnect and retry once on failure.

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
