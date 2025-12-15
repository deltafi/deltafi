# Emergency Operations

This guide covers operational procedures for emergency scenarios such as graceful shutdown or halting processing.

## Terminating All In-Flight DeltaFiles

The `terminateAllWithError` mutation marks all in-flight DeltaFiles as ERROR with a custom message. This is useful for:

- **Graceful shutdown**: Before taking a system offline, terminate in-flight work so it can be resumed later
- **Emergency halt**: Stop all processing when issues are detected
- **Migration**: Prepare DeltaFiles for export to another system where they can be resumed

### Usage

```graphql
mutation {
  terminateAllWithError(
    cause: "System shutdown for maintenance"
    context: "Scheduled maintenance window 2024-01-15"
  ) {
    count
    hasMore
  }
}
```

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `cause` | Yes | Error message to set on affected DeltaFiles |
| `context` | No | Additional context for the error |
| `createdBefore` | No | Only terminate DeltaFiles created before this timestamp. Use this to ensure consistent processing across multiple calls. |
| `maxCount` | No | Maximum number of DeltaFiles to process (default: 5000) |

### Response

| Field | Description |
|-------|-------------|
| `count` | Number of DeltaFiles terminated in this call |
| `hasMore` | `true` if more DeltaFiles remain to be processed |

### Batch Processing

The mutation processes DeltaFiles in batches to avoid timeouts. If `hasMore` is `true`, call the mutation again to continue processing.

To ensure you process a consistent set of DeltaFiles (and avoid an endless loop if new files are being ingested), capture the timestamp before your first call and pass it as `createdBefore` on subsequent calls:

```graphql
# First call - capture current time
mutation {
  terminateAllWithError(
    cause: "Graceful shutdown"
    createdBefore: "2024-01-15T10:30:00Z"
  ) {
    count
    hasMore
  }
}

# Subsequent calls - use same timestamp
mutation {
  terminateAllWithError(
    cause: "Graceful shutdown"
    createdBefore: "2024-01-15T10:30:00Z"
  ) {
    count
    hasMore
  }
}
```

### Recovery

DeltaFiles terminated with this mutation enter the ERROR state and can be:

- **Resumed**: Restart processing from where it left off
- **Replayed**: Create a new copy and reprocess from the beginning
- **Acknowledged**: Mark the error as reviewed if no further action is needed

See [Error Handling and Recovery](errors.md) for details on these recovery options.

### Permissions

Requires the `DeltaFileCancel` permission.
