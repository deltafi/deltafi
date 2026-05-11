---
# ABOUTME: GraphQL API overview for DeltaFi.
# ABOUTME: Covers endpoint usage, custom scalars, common patterns, and conventions.
---

# GraphQL API

DeltaFi uses [Netflix DGS](https://netflix.github.io/dgs/) to expose a GraphQL API for querying and mutating system state. The GraphQL API covers DeltaFile operations, flow management, plugin lifecycle, policies, and system configuration.

## Endpoint

| |                    |
|-|--------------------|
| **URL** | `/api/v2/graphql`  |
| **Method** | `POST`             |
| **Content-Type** | `application/json` |

### GraphiQL IDE

An interactive query editor is available at `/graphiql` in the browser. Use it to explore the schema, build queries, and test mutations.

### Making Requests

```bash
curl -X POST http://deltafi-host/api/v2/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "{ version }"
  }'
```

With variables:

```bash
curl -X POST http://deltafi-host/api/v2/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query GetDeltaFile($did: UUID!) { deltaFile(did: $did) { name stage } }",
    "variables": { "did": "550e8400-e29b-41d4-a716-446655440000" }
  }'
```

## Custom Scalars

| Scalar | Description | Example |
|--------|-------------|---------|
| `DateTime` | ISO 8601 date-time | `"2024-01-15T10:30:00Z"` |
| `UUID` | Universally unique identifier | `"550e8400-e29b-41d4-a716-446655440000"` |
| `Long` | 64-bit integer | `1048576` |
| `JSON` | Arbitrary JSON value | `{"key": "value"}` |
| `Metadata` | Key-value metadata map | `{"env": "prod", "source": "nifi"}` |

## Common Patterns

### Pagination

Paginated queries accept `offset` and `limit` parameters and return a wrapper type:

```graphql
query {
  deltaFiles(offset: 0, limit: 20) {
    offset
    count
    totalCount
    deltaFiles {
      did
      name
      stage
    }
  }
}
```

### Filtering

Many queries accept filter input types. The `DeltaFilesFilter` is the most comprehensive:

```graphql
query {
  deltaFiles(filter: {
    stage: ERROR,
    dataSources: ["my-source"],
    createdAfter: "2024-01-01T00:00:00Z"
  }) {
    totalCount
    deltaFiles { did name }
  }
}
```

### Ordering

Some queries support ordering with a direction and field:

```graphql
query {
  deltaFiles(orderBy: { field: "created", direction: DESC }) {
    deltaFiles { did created }
  }
}
```

### Result Types

Mutations that can partially succeed return per-item result types:

```graphql
mutation {
  resume(dids: ["did-1", "did-2"]) {
    did
    success
    error
  }
}
```

Mutations that succeed or fail atomically return a `Result` type:

```graphql
mutation {
  installPlugin(image: "registry/plugin:1.0") {
    success
    errors
    info
  }
}
```

## Error Handling

GraphQL always returns HTTP `200`. Check the `errors` array in the response:

```json
{
  "data": { "deltaFile": null },
  "errors": [
    {
      "message": "DeltaFile not found: 550e8400-...",
      "path": ["deltaFile"],
      "extensions": { "classification": "DataFetchingException" }
    }
  ]
}
```

Permission errors return a `403`-equivalent message in the errors array when the authenticated user lacks the required permission.

## Key Enums

| Enum | Values | Used For |
|------|--------|----------|
| `DeltaFileStage` | `IN_FLIGHT`, `COMPLETE`, `ERROR`, `CANCELLED` | DeltaFile lifecycle stage |
| `FlowType` | `REST_DATA_SOURCE`, `TIMED_DATA_SOURCE`, `ON_ERROR_DATA_SOURCE`, `TRANSFORM`, `DATA_SINK` | Flow classification |
| `FlowState` | `STOPPED`, `RUNNING`, `PAUSED` | Flow operational state |
| `ActionState` | `COLD_QUEUED`, `QUEUED`, `COMPLETE`, `ERROR`, `RETRIED`, `FILTERED`, `SPLIT`, `CANCELLED`, `JOINING`, `JOINED`, `INHERITED`, `PAUSED` | Action execution state |
| `ActionType` | `INGRESS`, `TIMED_INGRESS`, `TRANSFORM`, `EGRESS`, `PUBLISH`, `UNKNOWN` | Action classification |

## API Sections

- [DeltaFiles](deltafiles.md) — Query, filter, resume, replay, acknowledge DeltaFiles
- [Flows](flows.md) — Flow plans, state management, topics, test mode
- [Plugins](plugins.md) — Plugin queries, installation, variables
- [Policies](policies.md) — Resume and delete policies
- [System](system.md) — Properties, snapshots, SSL, SpEL, links, lookup tables
- [Testing](testing.md) — Integration test management
