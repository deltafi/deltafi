# Changes on branch `generic-http-egress`
Document any changes on this branch here.
### Added
- Created a new Egress action, HttpEgress, that will allow for selection of the HTTP request method (POST, PUT, PATCH, or DELETE)

### Changed
- Changed the `passthrough-data-sink` to use the new `HttpEgress` action, with a new plugin variable `passthroughEgressMethod` to choose the HTTP request method
- Mododified the metadata filename created by `deltafi-egress-sink` to include the HTTP request method

### Fixed
- 

### Removed
- 

### Deprecated
- org.deltafi.core.action.egress.RestPostEgress is being deprecated; use org.deltafi.core.action.egress.HttpEgress instead

### Security
- 

### Tech-Debt/Refactor
- 

### Upgrade and Migration
- 
