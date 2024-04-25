# Changes on branch `streaming-archive-and-compress`
Document any changes on this branch here.
### Added
- Added streaming org.deltafi.core.action.archive.Archive.
- Added org.deltafi.core.action.archive.Unarchive.
- Added streaming org.deltafi.core.action.compress.Compress.
- Added org.deltafi.core.action.compress.Decompress.

### Changed
- Moved org.deltafi.core.action.MergeContent to org.deltafi.core.action.merge.Merge.

### Fixed
- Fixed filenames not populated after split.
- Fixed original file being included as child after split.

### Removed
- Removed org.deltafi.core.action.ArchiveContent.
- Removed org.deltafi.core.action.Decompress. 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Replaced deprecated CountingInputStream with BoundedInputStream.

### Upgrade and Migration
- 
