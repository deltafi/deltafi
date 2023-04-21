# Changes on branch `input-data-interfaces`
Document any changes on this branch here.
### Added
- 

### Changed
- 

### Fixed
- 

### Removed
- 

### Deprecated
- 

### Security
- 

### Tech-Debt/Refactor
- Hide FormattedData from ValidateInput and EgressInput.  Add input methods:
  - loadFormattedDataStream
  - loadFormattedDataBytes
  - getFilename
  - getFormattedDataSize
  - getMediaType
  - getMetadata
- Add content loading methods to TransformInput, loadInput, EnrichInput, and FormatInput:
  - contentListSize
  - loadContentBytes
  - loadContentBytes(index)
  - loadContentStream
  - loadContentStream(index)

### Upgrade and Migration
- 
