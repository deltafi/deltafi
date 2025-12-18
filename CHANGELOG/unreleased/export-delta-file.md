# Changes on branch `export-delta-file`
Document any changes on this branch here.
### Added
- Added an endpoint, `api/v2/deltafile/export/{did}`, that exports the DeltaFile as a tar file. The archive includes a `<did>.json` file which holds the DeltaFile metadata and content directory which holds all the referenced content.
- Added an endpoint, `api/v2/deltafile/import`, that accepts tar files. Each DeltaFile JSON file is mapped to an entity which is inserted into the database. Each content file is loaded into the content storage system.
- Added an endpoint, `api/v2/deltafile/export/errors`, that exports a batch of errored DeltaFiles and optionally marks each one that is exported as acknowledged. The endpoint acknowledges by default, to disable use the `acknowledge` query param with a value of false. By default, up to 100 DeltaFiles will be exported, to adjust the amount use the `limit` query param with the desired max value.
- Added an `Export DeltaFile` option to the DeltaFile Viewer menu
- Added new permissions, `DeltaFileExport` and `DeltaFileImport`, to control access to the new export and import feature
- Added commands for importing and exporting DeltaFiles
   ```bash
   deltafi deltafile export <did>
   deltafi deltafile import <did>.tar
   deltafi errored export
   ```

### Changed
- Changed the `deltafi deltafile` command to require subcommands. The original view functionality is now under `deltafi deltafile view <did>`

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
- 
