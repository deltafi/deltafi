# Changes on branch `streaming-archive-and-compress`
Document any changes on this branch here.
### Added
- Added streaming org.deltafi.core.action.archive.Archive
- Added org.deltafi.core.action.archive.Unarchive
- Added streaming org.deltafi.core.action.compress.Compress
- Added org.deltafi.core.action.compress.Decompress

### Changed
- Moved org.deltafi.core.action.MergeContent to org.deltafi.core.action.merge.Merge

### Removed
- Removed org.deltafi.core.action.ArchiveContent
- Removed org.deltafi.core.action.Decompress

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Replaced deprecated CountingInputStream with BoundedInputStream in all usages

### Upgrade and Migration
- 
