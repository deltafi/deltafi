# Changes on branch `reduce-jedis-startup-noise`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- Reduced `RateLimitService` startup log noise — previously dumped the full `JedisPool.toString()` (including an embedded Java stack trace) as the log message; now logs just the configured pool size.
- Reduced plugin registration log noise — the full `PluginRegistration` payload (action descriptors, flow plans, lookup tables, etc.) is no longer logged on every successful registration. It is now logged only when registration fails, where it's useful for diagnosis.

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
