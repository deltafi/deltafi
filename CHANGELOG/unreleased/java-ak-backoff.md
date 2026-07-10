# Changes on branch `java-ak-backoff`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Action kit poll loops now apply jittered, capped exponential backoff (100ms → 5s) when the event queue (Valkey) is unreachable, instead of retrying tightly (previously Java 100ms, Go fixed 5s, Python fixed 1s). This keeps a Valkey outage from becoming a CPU-pegging retry storm that exhausts kernel TCP socket-buffer memory, and spreads reconnect attempts across action threads to avoid a thundering herd. Applied to the Java action loop, the Go action and lookup-supplier poll loops, and the Python action and lookup-supplier loops. The Python lookup-supplier handler additionally gains error handling (an unhandled exception previously killed the thread), and the Java retry path no longer logs a misleading "Shutting down action thread".

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
