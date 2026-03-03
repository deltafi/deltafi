---
# ABOUTME: Documents REST endpoints for system, flow, action, and queue metrics.
# ABOUTME: Covers node metrics, flow throughput, action execution stats, and queue state.
---

# Metrics REST API

Endpoints for querying system metrics, flow throughput, action performance, and queue state.

## System Node Metrics

Returns resource usage and application metrics for all nodes in the cluster.

**Endpoint:** `GET /api/v2/metrics/system/nodes`

**Permission:** `MetricsView`

**Response:**

```json
{
  "nodes": [
    {
      "name": "node-1",
      "apps": [...],
      "resources": {...}
    }
  ],
  "timestamp": "2024-01-15T10:00:00Z"
}
```

**Example:**

```bash
curl http://deltafi-host/api/v2/metrics/system/nodes
```

## Flow Metrics

Queries flow-level throughput metrics (ingress, egress, storage, deleted bytes) over a time window.

**Endpoint:** `GET /api/v2/metrics/flow`

**Permission:** `StatusView`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minutes` | Integer | `60` | Time window in minutes (1–1440) |

**Response:** `FlowMetrics` object with per-flow ingress/egress/storage/deletion statistics

**Example:**

```bash
curl http://deltafi-host/api/v2/metrics/flow?minutes=120
```

## Action Metrics

Queries execution count and timing metrics for specific actions.

**Endpoint:** `POST /api/v2/metrics/action`

**Permission:** `MetricsView`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minutes` | Integer | `60` | Time window in minutes (1–1440) |

**Request Body:** Array of action names

```json
["org.deltafi.core.action.SomeTransform", "org.deltafi.core.action.SomeEgress"]
```

**Response:** Array of `ActionMetrics` objects with execution counts and timing data

## Per-Flow Metrics

Queries per-flow metrics including files and bytes in/out.

**Endpoint:** `POST /api/v2/metrics/perflow`

**Permission:** `MetricsView`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minutes` | Integer | `60` | Time window in minutes (1–1440) |

**Request Body:** Array of `FlowKey` objects

```json
[
  {"flowName": "my-transform", "flowType": "TRANSFORM"},
  {"flowName": "my-sink", "flowType": "DATA_SINK"}
]
```

**Response:** Array of `PerFlowMetrics` objects

## Detailed Queue Metrics

Returns warm and cold queue depths broken down by flow. Results are cached for 5 seconds.

**Endpoint:** `GET /api/v2/metrics/queues/detailed`

**Permission:** `MetricsView`

**Response:**

```json
{
  "warmQueues": {...},
  "coldQueues": {...},
  "timestamp": "2024-01-15T10:00:00Z"
}
```

**Example:**

```bash
curl http://deltafi-host/api/v2/metrics/queues/detailed
```

## Action-Plugin Mapping

Returns a mapping of action class names to their plugin artifact IDs.

**Endpoint:** `GET /api/v2/metrics/queues/action-plugins`

**Permission:** `MetricsView`

**Response:**

```json
{
  "org.deltafi.core.action.SomeTransform": "org.deltafi:deltafi-core-actions",
  "com.example.MyEgress": "com.example:my-plugin"
}
```

## Running Tasks

Returns currently executing actions with their heartbeat timestamps. Results are cached for 5 seconds.

**Endpoint:** `GET /api/v2/metrics/queues/running`

**Permission:** `MetricsView`

**Response:**

```json
{
  "tasks": [...],
  "heartbeatThreshold": 30000
}
```

The `heartbeatThreshold` (in milliseconds) indicates how long since last heartbeat before a task is considered stale.
