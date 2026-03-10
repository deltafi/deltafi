---
# ABOUTME: Top-level API reference overview for DeltaFi.
# ABOUTME: Covers base URLs, authentication summary, error conventions, and links to sub-sections.
---

# API Reference

DeltaFi exposes two APIs: a REST API for data operations, system management, and streaming, and a GraphQL API for querying and mutating system state.

## Base URLs

| API | Base URL | Notes |
|-----|----------|-------|
| REST | `/api/v2/` | All REST endpoints except plugin registration |
| Plugin Registration | `/plugins` | Used by plugins during startup |
| GraphQL | `/graphql` | Single endpoint, POST only |
| GraphiQL IDE | `/graphiql` | Interactive query editor (browser) |

In local development, use `http://local.deltafi.org` as the host. In production, use the configured DeltaFi domain over HTTPS.

## Authentication

All API requests require authentication unless the system is running in `disabled` auth mode. See [Authentication](authentication.md) for details on auth modes, headers, and the permissions model.

For most programmatic access, requests pass through an auth gateway that sets identity headers. Direct API calls should use Basic authentication or certificate-based authentication depending on the configured mode.

## Common Error Responses

### REST Errors

REST endpoints return standard HTTP status codes:

| Status | Meaning |
|--------|---------|
| `200` | Success |
| `204` | Success, no content |
| `400` | Bad request (invalid input, validation failure) |
| `403` | Forbidden (missing required permission) |
| `404` | Resource not found |
| `413` | Payload too large (insufficient storage for ingress) |
| `422` | Unprocessable entity (semantic validation error) |
| `429` | Rate limited |
| `500` | Internal server error |
| `501` | Not implemented (feature disabled) |
| `503` | Service unavailable |

Error responses use this format:

```json
{
  "error": "Description of what went wrong"
}
```

### GraphQL Errors

GraphQL requests always return HTTP `200`. Errors appear in the `errors` array:

```json
{
  "data": null,
  "errors": [
    {
      "message": "Description of the error",
      "locations": [{"line": 1, "column": 1}],
      "path": ["mutationName"],
      "extensions": {
        "classification": "DataFetchingException"
      }
    }
  ]
}
```

Many mutations return a `Result` type with structured success/error information:

```graphql
type Result {
  success: Boolean!
  info: [String]
  errors: [String]
}
```

## API Sections

### REST API

- [DeltaFiles](rest/deltafiles.md) — Ingress, export, import, annotations, content retrieval
- [Events](rest/events.md) — System event notifications
- [Users and Roles](rest/users-and-roles.md) — User, role, and permission management
- [Metrics](rest/metrics.md) — System, flow, action, and queue metrics
- [System](rest/system.md) — Status, versions, configuration, snapshots, SSE
- [Plugins](rest/plugins.md) — Plugin registration and scaffold generation
- [Federation](rest/federation.md) — Fleet leader operations and federated search
- [Artifact Registry](rest/artifact-registry.md) — Versioned artifact publishing, download, aliases, and purge

### GraphQL API

- [GraphQL Overview](graphql/index.md) — Endpoint usage, custom scalars, conventions
- [DeltaFiles](graphql/deltafiles.md) — Query, filter, resume, replay, acknowledge DeltaFiles
- [Flows](graphql/flows.md) — Flow plans, state management, topics, test mode
- [Plugins](graphql/plugins.md) — Plugin queries, installation, variables
- [Policies](graphql/policies.md) — Resume and delete policies
- [System](graphql/system.md) — Properties, snapshots, SSL, SpEL, links, lookup tables
- [Testing](graphql/testing.md) — Integration test management
