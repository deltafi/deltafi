# Changes on branch `fix-replay-metadata`
Document any changes on this branch here.
### Added
- GraphQL query `replaySourceMetadata(did: UUID!): [KeyValue!]!` returning the source metadata a replay will seed from for a non-joined DeltaFile. Returns an empty list for joined DeltaFiles.

### Changed
- Replay "Modify Metadata" dialog now fetches its preview via `replaySourceMetadata` instead of inferring metadata from the DeltaFile's flow structure.
- Replay "Modify Metadata" dialog for joined DeltaFiles no longer shows a (misleading) merged preview. Instead it shows an explanatory note: any metadata the user adds will override that key in every parent's contribution before the join runs. Adding/overriding metadata is supported; removing keys is not supported via the dialog for joined replay.
- Joined parent DID retrieval is now ordered deterministically by `join_entry_dids.id`, so join processing no longer depends on unspecified database row order. Independent of the replay-preview changes above.

### Fixed
- Replay "Modify Metadata" dialog for non-joined DeltaFiles now shows accumulated first-flow metadata (what replay actually seeds from), instead of only the flow's input metadata.

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
