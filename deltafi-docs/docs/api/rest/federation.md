---
# ABOUTME: Documents REST endpoints for fleet leader operations and federated search.
# ABOUTME: Covers member management, aggregated stats, config sync, and cross-fleet error handling.
---

# Federation REST API

Endpoints available on the fleet leader instance for managing and querying across fleet members. These endpoints are only available when DeltaFi is running in cluster (leader/member) mode.

For background on the leader/member architecture, see [Leader Member](/advanced/leader_member).

## Member Management

### Get Members

Returns status and dashboard data for all fleet members.

**Endpoint:** `GET /api/v2/leader/members`

**Permission:** `StatusView`

**Response:** `LeaderDashboardData` with member statuses

### Get Aggregated Stats

Returns statistics aggregated across all fleet members.

**Endpoint:** `GET /api/v2/leader/stats`

**Permission:** `StatusView`

**Response:** `AggregatedStats` object

## Metrics

### All Member Flow Metrics

Returns flow metrics for all fleet members.

**Endpoint:** `GET /api/v2/leader/metrics/flow`

**Permission:** `StatusView`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minutes` | Integer | `60` | Time window in minutes (max 1440) |

**Response:** Map of member name to `FlowMetrics`

### Single Member Flow Metrics

Returns flow metrics for a specific fleet member.

**Endpoint:** `GET /api/v2/leader/metrics/flow/{memberName}`

**Permission:** `StatusView`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `memberName` | String | Name of the fleet member |

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minutes` | Integer | `60` | Time window in minutes (max 1440) |

**Response:** `FlowMetrics` for the specified member

## Configuration

### Get All Member Plugins

Returns plugins installed on all fleet members.

**Endpoint:** `GET /api/v2/leader/config/plugins`

**Permission:** `SnapshotRead`

**Response:** `PluginsResponse` with per-member plugin lists

### Get Member Snapshot

Returns the configuration snapshot for a specific fleet member.

**Endpoint:** `GET /api/v2/leader/config/snapshot/{memberName}`

**Permission:** `SnapshotRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `memberName` | String | Member name (use `"Leader"` for the leader's own snapshot) |

**Response:** `Snapshot` object

### Get Config Diff

Computes the configuration difference between the leader and a member.

**Endpoint:** `GET /api/v2/leader/config/diff/{memberName}`

**Permission:** `SnapshotRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `memberName` | String | Name of the fleet member to compare |

**Response:** `ConfigDiff` showing configuration differences

## Federated Search

### Search DeltaFiles

Executes a federated search across all fleet members, returning match counts per member.

**Endpoint:** `POST /api/v2/leader/search/federated`

**Permission:** `DeltaFileMetadataView`

**Request Body:** `DeltaFilesFilter` (same filter format as the GraphQL `deltaFiles` query)

**Response:** `FederatedSearchResponse` with per-member match counts

### Get Search Options

Returns combined search filter options (flows, topics, annotation keys) aggregated from all members.

**Endpoint:** `GET /api/v2/leader/search/options`

**Permission:** `DeltaFileMetadataView`

**Response:** `AggregatedSearchOptions` object

## Federated Error Management

### Search Errors

Searches for errors across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/search`

**Permission:** `DeltaFileMetadataView`

**Request Body:** `FederatedErrorFilter`

**Response:** `FederatedErrorResponse` with errors from all members

### Acknowledge Errors

Acknowledges errors matching a filter across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/acknowledge`

**Permission:** `DeltaFileAcknowledge`

**Request Body:** `FederatedActionRequest`

**Response:** `FederatedActionResponse` with per-member results

### Resume Errors

Resumes errors matching a filter across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/resume`

**Permission:** `DeltaFileResume`

**Request Body:** `FederatedActionRequest`

**Response:** `FederatedActionResponse` with per-member results

### Replay Errors

Replays errors matching a filter across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/replay`

**Permission:** `DeltaFileReplay`

**Request Body:** `FederatedActionRequest`

**Response:** `FederatedActionResponse` with per-member results

### Replay and Acknowledge Errors

Replays and acknowledges errors in a single operation across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/replay-and-acknowledge`

**Permission:** `DeltaFileReplay` and `DeltaFileAcknowledge`

**Request Body:** `FederatedActionRequest`

**Response:** `FederatedActionResponse` with per-member results

### Error Summary by Data Source

Returns error counts grouped by data source across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/summary/by-data-source`

**Permission:** `DeltaFileMetadataView`

**Request Body:** `FederatedErrorFilter`

**Response:** `FederatedErrorSummaryResponse`

### Error Summary by Message

Returns error counts grouped by error message across all fleet members.

**Endpoint:** `POST /api/v2/leader/errors/summary/by-message`

**Permission:** `DeltaFileMetadataView`

**Request Body:** `FederatedErrorFilter`

**Response:** `FederatedErrorSummaryResponse`
