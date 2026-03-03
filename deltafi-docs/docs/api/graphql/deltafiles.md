---
# ABOUTME: Documents GraphQL queries and mutations for DeltaFile operations.
# ABOUTME: Covers searching, filtering, resuming, replaying, acknowledging, annotating, and pinning DeltaFiles.
---

# DeltaFiles GraphQL API

Queries and mutations for managing DeltaFiles — the core data objects that flow through DeltaFi.

## Queries

### deltaFiles

Paginated, filterable list of DeltaFiles.

```graphql
query {
  deltaFiles(
    offset: 0
    limit: 20
    filter: { stage: ERROR, dataSources: ["my-source"] }
    orderBy: { field: "modified", direction: DESC }
  ) {
    offset
    count
    totalCount
    deltaFiles {
      did
      name
      dataSource
      stage
      created
      modified
      ingressBytes
      annotations
    }
  }
}
```

**Permission:** `DeltaFileMetadataView`

#### DeltaFilesFilter Fields

| Field | Type | Description |
|-------|------|-------------|
| `dids` | `[UUID!]` | Filter by specific DeltaFile IDs |
| `nameFilter` | `NameFilter` | Filter by filename (with optional case sensitivity) |
| `parentDid` | `UUID` | Filter by parent DeltaFile |
| `stage` | `DeltaFileStage` | `IN_FLIGHT`, `COMPLETE`, `ERROR`, `CANCELLED` |
| `dataSources` | `[String!]` | Filter by data source names |
| `transforms` | `[String!]` | Filter by transform flow names |
| `dataSinks` | `[String!]` | Filter by data sink names |
| `topics` | `[String!]` | Filter by topic names |
| `actions` | `[String!]` | Filter by action names |
| `annotations` | `[KeyValueInput!]` | Filter by annotation key-value pairs |
| `createdAfter` | `DateTime` | Created after timestamp |
| `createdBefore` | `DateTime` | Created before timestamp |
| `modifiedAfter` | `DateTime` | Modified after timestamp |
| `modifiedBefore` | `DateTime` | Modified before timestamp |
| `errorCause` | `String` | Filter by error message substring |
| `filteredCause` | `String` | Filter by filter cause substring |
| `errorAcknowledged` | `Boolean` | Filter by acknowledgment status |
| `egressed` | `Boolean` | Filter by egress status |
| `filtered` | `Boolean` | Filter by filtered status |
| `testMode` | `Boolean` | Filter by test mode |
| `replayable` | `Boolean` | Filter for replayable DeltaFiles |
| `replayed` | `Boolean` | Filter for replayed DeltaFiles |
| `terminalStage` | `Boolean` | Filter for terminal stages only |
| `pendingAnnotations` | `Boolean` | Has pending annotations |
| `paused` | `Boolean` | Is paused |
| `pinned` | `Boolean` | Is pinned |
| `contentDeleted` | `Boolean` | Content has been deleted |
| `warnings` | `Boolean` | Has warnings |
| `userNotes` | `Boolean` | Has user notes |
| `ingressBytesMin` / `ingressBytesMax` | `Long` | Ingress byte range |
| `referencedBytesMin` / `referencedBytesMax` | `Long` | Referenced byte range |
| `totalBytesMin` / `totalBytesMax` | `Long` | Total byte range |
| `requeueCountMin` | `Int` | Minimum requeue count |

### deltaFile

Get a single DeltaFile by DID.

```graphql
query {
  deltaFile(did: "550e8400-e29b-41d4-a716-446655440000") {
    did
    name
    dataSource
    stage
    flows {
      name
      type
      state
      actions {
        name
        state
        errorCause
      }
    }
  }
}
```

**Permission:** `DeltaFileMetadataView`

### rawDeltaFile

Get the raw JSON representation of a DeltaFile.

```graphql
query {
  rawDeltaFile(did: "550e8400-...", pretty: true)
}
```

**Permission:** `DeltaFileMetadataView`

### lastCreated / lastModified / lastErrored

Get the N most recently created, modified, or errored DeltaFiles.

```graphql
query {
  lastCreated(last: 5) { did name created }
  lastModified(last: 5) { did name modified }
  lastErrored(last: 5) { did name stage }
}
```

**Permission:** `DeltaFileMetadataView`

### lastWithName

Get the most recent DeltaFile with a specific name.

```graphql
query {
  lastWithName(name: "example.txt") { did stage created }
}
```

**Permission:** `DeltaFileMetadataView`

### deltaFileStats

Get system-wide DeltaFile statistics.

```graphql
query {
  deltaFileStats {
    totalCount
    inFlightCount
    inFlightBytes
    warmQueuedCount
    coldQueuedCount
    pausedCount
    oldestInFlightCreated
    oldestInFlightDid
  }
}
```

**Permission:** `DeltaFileMetadataView`

### totalCount / countUnacknowledgedErrors

```graphql
query {
  totalCount
  countUnacknowledgedErrors
}
```

**Permission:** `DeltaFileMetadataView`

### pendingAnnotations

Get pending annotation keys for a specific DeltaFile.

```graphql
query {
  pendingAnnotations(did: "550e8400-...")
}
```

**Permission:** `DeltaFileMetadataView`

### Error and Filter Summaries

Aggregate error or filter counts by flow or by message.

```graphql
query {
  errorSummaryByFlow(
    offset: 0
    limit: 20
    filter: { modifiedAfter: "2024-01-01T00:00:00Z" }
    sortField: COUNT
    direction: DESC
  ) {
    totalCount
    countPerFlow { flow type count }
  }
}
```

Available summary queries:
- `errorSummaryByFlow` — errors grouped by flow
- `errorSummaryByMessage` — errors grouped by error message
- `filteredSummaryByFlow` — filtered DeltaFiles grouped by flow
- `filteredSummaryByMessage` — filtered DeltaFiles grouped by filter message

**Permission:** `DeltaFileMetadataView`

### annotationKeys

Get all annotation keys used across the system.

```graphql
query { annotationKeys }
```

## Mutations

### Resume

Retry DeltaFiles that are in an error state.

```graphql
# Resume specific DeltaFiles
mutation {
  resume(
    dids: ["did-1", "did-2"]
    resumeMetadata: [{ flow: "my-flow", action: "my-action", metadata: [{ key: "retry", value: "true" }] }]
  ) {
    did success error
  }
}

# Resume all errors in a flow
mutation {
  resumeByFlow(
    flowType: TRANSFORM
    name: "my-transform"
    includeAcknowledged: false
    limit: 100
  ) {
    did success error
  }
}

# Resume by error message
mutation {
  resumeByErrorCause(errorCause: "Connection timeout") {
    did success error
  }
}

# Resume matching a filter
mutation {
  resumeMatching(
    filter: { stage: ERROR, dataSources: ["my-source"] }
    limit: 50
  ) {
    did success error
  }
}
```

**Permission:** `DeltaFileResume`

### Replay

Re-ingest DeltaFiles from the beginning, optionally modifying metadata.

```graphql
# Replay specific DeltaFiles
mutation {
  replay(
    dids: ["did-1"]
    removeSourceMetadata: ["oldKey"]
    replaceSourceMetadata: [{ key: "newKey", value: "newValue" }]
  ) {
    did success error
  }
}

# Replay matching a filter
mutation {
  replayMatching(filter: { stage: ERROR }, limit: 50) {
    did success error
  }
}

# Replay all errors in a flow
mutation {
  replayErrorsByFlow(flowType: TRANSFORM, flowName: "my-transform") {
    did success error
  }
}

# Replay all errors with a specific message
mutation {
  replayErrorsByMessage(errorCause: "Parse error") {
    did success error
  }
}
```

**Permission:** `DeltaFileReplay`

### Acknowledge

Mark errors as acknowledged.

```graphql
# Acknowledge specific DeltaFiles
mutation {
  acknowledge(dids: ["did-1", "did-2"], reason: "Known issue") {
    did success error
  }
}

# Acknowledge matching a filter
mutation {
  acknowledgeMatching(filter: { errorCause: "timeout" }, reason: "Transient") {
    did success error
  }
}

# Acknowledge by flow
mutation {
  acknowledgeByFlow(flowType: TRANSFORM, flowName: "my-flow", reason: "Batch ack") {
    did success error
  }
}

# Acknowledge by error message
mutation {
  acknowledgeByMessage(errorCause: "Connection refused", reason: "Outage") {
    did success error
  }
}
```

**Permission:** `DeltaFileAcknowledge`

### Replay and Acknowledge

Replay DeltaFiles and acknowledge the original errors in a single operation.

```graphql
mutation {
  replayAndAcknowledge(
    dids: ["did-1"]
    reason: "Replaying with fix"
  ) {
    did replayDid success error
  }
}
```

Variants: `replayAndAcknowledgeErrorsByFlow`, `replayAndAcknowledgeErrorsByMessage`, `replayAndAcknowledgeMatching`

**Permission:** `DeltaFileReplay` and `DeltaFileAcknowledge`

### Cancel

Cancel in-flight DeltaFiles.

```graphql
mutation {
  cancel(dids: ["did-1"]) { did success error }
}

mutation {
  cancelMatching(filter: { dataSources: ["test-source"] }) { did success error }
}
```

**Permission:** `DeltaFileCancel`

### Terminate All with Error

Terminate all in-flight DeltaFiles by marking them with an error.

```graphql
mutation {
  terminateAllWithError(
    cause: "Emergency shutdown"
    context: "System maintenance"
    createdBefore: "2024-01-15T00:00:00Z"
    maxCount: 1000
  ) {
    count
    hasMore
  }
}
```

**Permission:** `DeltaFileCancel`

### Annotations

```graphql
# Add annotations to a single DeltaFile
mutation {
  addAnnotations(
    did: "did-1"
    annotations: [{ key: "status", value: "reviewed" }]
    allowOverwrites: false
  )
}

# Annotate DeltaFiles matching a filter
mutation {
  annotateMatching(
    filter: { dataSources: ["my-source"] }
    annotations: [{ key: "batch", value: "2024-01" }]
    allowOverwrites: true
  )
}
```

Variants: `annotateByFlow`, `annotateByMessage`

**Permission:** `DeltaFileMetadataWrite`

### User Notes

```graphql
mutation {
  userNote(dids: ["did-1", "did-2"], message: "Needs review") {
    success errors
  }
}
```

Variant: `userNoteMatching`

**Permission:** `DeltaFileUserNote`

### Pinning

Pin DeltaFiles to prevent automatic deletion.

```graphql
mutation { pin(dids: ["did-1"]) { success errors } }
mutation { unpin(dids: ["did-1"]) { success errors } }
mutation { pinMatching(filter: { dataSources: ["important"] }) { did success error } }
mutation { unpinMatching(filter: { pinned: true }) { did success error } }
```

**Permission:** `DeltaFilePinning`
