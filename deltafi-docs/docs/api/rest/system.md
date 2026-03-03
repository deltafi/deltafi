---
# ABOUTME: Documents REST endpoints for system status, versions, configuration, and streaming.
# ABOUTME: Covers status checks, version info, UI config, snapshots, SSE, and survey submission.
---

# System REST API

Endpoints for system status, version information, configuration, snapshots, server-sent events, and survey data.

## System Status

Returns the overall system health status.

**Endpoint:** `GET /api/v2/status`

**Permission:** `StatusView`

**Response:** `Status` object with system health information

**Example:**

```bash
curl http://deltafi-host/api/v2/status
```

## Status Report

Returns a comprehensive status report including error counts, statistics, and resource usage.

**Endpoint:** `GET /api/v2/status/report`

**Permission:** `StatusView`

**Response:**

```json
{
  "status": {...},
  "errorCount": 5,
  "stats": {...},
  "cpuUsage": 45.2,
  "memoryUsage": 72.1,
  "diskUsage": 38.5,
  "version": "2.0.0"
}
```

## Pause Status Check

Pauses a specific status check for a given duration.

**Endpoint:** `PUT /api/v2/status/{statusCheckId}/pauseDuration`

**Permission:** `StatusPause`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `statusCheckId` | String | Identifier of the status check |

**Request Body:** ISO-8601 duration string

```json
"PT1H"
```

**Response:** `200 OK`

## Resume Status Check

Resumes a previously paused status check.

**Endpoint:** `DELETE /api/v2/status/{statusCheckId}/pauseDuration`

**Permission:** `StatusPause`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `statusCheckId` | String | Identifier of the status check |

**Response:** `204 No Content`

## Running Versions

Returns version information for all running components.

**Endpoint:** `GET /api/v2/versions`

**Permission:** `VersionsView`

**Response:** `Versions` object with component version details

**Example:**

```bash
curl http://deltafi-host/api/v2/versions
```

## UI Configuration

Returns configuration for the DeltaFi UI, including system properties, links, and feature flags.

**Endpoint:** `GET /api/v2/config`

**Permission:** `UIAccess`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `skip_cache` | Boolean | `false` | Bypass configuration cache |

**Response:**

```json
{
  "title": "DeltaFi",
  "domain": "deltafi.example.com",
  "authMode": "basic",
  "clusterMode": "standalone",
  "properties": {...},
  "links": [...],
  "lookupTablesEnabled": true
}
```

## Current Snapshot

Returns the current system configuration as a snapshot without persisting it.

**Endpoint:** `GET /api/v2/system/snapshot/current`

**Permission:** `SnapshotRead`

**Response:** `Snapshot` object containing the full system configuration

**Example:**

```bash
curl http://deltafi-host/api/v2/system/snapshot/current
```

## Server-Sent Events

Establishes a Server-Sent Events (SSE) connection for real-time system updates.

**Endpoint:** `GET /api/v2/sse`

**Permission:** `UIAccess`

**Response:** `text/event-stream` with `X-Accel-Buffering: no` header

Events are pushed to connected clients as system state changes occur. This is used by the UI for real-time updates.

**Example:**

```bash
curl -N http://deltafi-host/api/v2/sse
```

## Survey

Records survey/analytics events for DeltaFile throughput tracking.

**Endpoint:** `POST /api/v2/survey`

**Permission:** `SurveyCreate`

**Request Body:** Single `SurveyEvent` object or array of `SurveyEvent` objects

```json
[
  {
    "flow": "my-flow",
    "count": 100,
    "bytes": 1048576,
    "timestamp": "2024-01-15T10:00:00Z"
  }
]
```

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Success |
| `400` | Invalid JSON or validation errors |
| `501` | Analytics feature is disabled |
