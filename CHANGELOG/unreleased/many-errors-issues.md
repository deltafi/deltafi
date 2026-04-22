# Changes on branch `many-errors-issues`
Document any changes on this branch here.
### Added
- `resumeByErrorCause` now accepts an optional `modifiedBefore` argument, matching `resumeByFlow`.

### Changed
- Resume-by-flow and resume-by-error-cause now clamp a missing or future `modifiedBefore` to the time the request is received, so concurrent error growth cannot widen the set of affected DeltaFiles mid-call.

### Fixed
- Reduced contention when resuming large numbers of errored DeltaFiles. Manual resume now skips locked rows instead of waiting behind background auto-resume work.
- Reduced database contention during large auto-resume backlogs. Each scheduled auto-resume pass now processes one bounded batch of retries at a time instead of trying to drain the full backlog in one run.
- When an auto-resume batch fills completely, DeltaFi schedules a quick follow-up pass in 5 seconds so large backlogs continue to drain without making the retry worker monopolize normal processing.

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
