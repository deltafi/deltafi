---
# ABOUTME: Documents REST endpoints for system event management.
# ABOUTME: Covers creating, querying, acknowledging, and deleting events.
---

# Events REST API

The Events API provides endpoints for managing system events and notifications in DeltaFi. Events appear as notifications in the alarm bell in the upper right corner of the GUI and can be acknowledged by users.

## Event Object

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `severity` | String | `error`, `warn`, `info`, or `success` |
| `summary` | String | Brief summary of the event |
| `content` | String | Detailed description (max 100,000 characters) |
| `source` | String | Source that generated the event |
| `timestamp` | OffsetDateTime | When the event was created |
| `notification` | Boolean | Whether to show as a GUI notification |
| `acknowledged` | Boolean | Whether the event has been acknowledged |

## Get Events

Retrieves events with optional filtering and pagination.

**Endpoint:** `GET /api/v2/events`

**Permission:** `EventRead`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `offset` | Integer | `0` | Records to skip |
| `size` | Integer | `20` | Maximum records to return |
| Additional key-value pairs | | | Filter criteria |

**Response:** Array of Event objects

**Example:**

```bash
curl http://deltafi-host/api/v2/events?offset=0&size=10&severity=error
```

## Get Events with Count

Retrieves events along with pagination metadata including total count.

**Endpoint:** `GET /api/v2/events/with-count`

**Permission:** `EventRead`

**Query Parameters:** Same as [Get Events](#get-events)

**Response:**

```json
{
  "offset": 0,
  "count": 10,
  "totalCount": 25,
  "events": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "severity": "error",
      "summary": "System error occurred",
      "content": "Detailed error description...",
      "source": "core-service",
      "timestamp": "2023-12-01T10:00:00Z",
      "notification": true,
      "acknowledged": false
    }
  ]
}
```

## Get Single Event

**Endpoint:** `GET /api/v2/events/{id}`

**Permission:** `EventRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Event identifier |

**Response:** Event object

**Example:**

```bash
curl http://deltafi-host/api/v2/events/123e4567-e89b-12d3-a456-426614174000
```

## Create Event

**Endpoint:** `POST /api/v2/events`

**Permission:** `EventCreate`

**Request Body:** Event object (`id` and `timestamp` are auto-generated if omitted)

**Response:** Created Event object

**Example:**

```bash
curl -X POST http://deltafi-host/api/v2/events \
  -H "Content-Type: application/json" \
  -d '{
    "severity": "warn",
    "summary": "Configuration updated",
    "content": "System configuration has been updated by admin user",
    "source": "admin-service",
    "notification": true,
    "acknowledged": false
  }'
```

## Acknowledge Event

Marks an event as acknowledged.

**Endpoint:** `PUT /api/v2/events/{id}/acknowledge`

**Permission:** `EventUpdate`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Event identifier |

**Response:** Updated Event object with `acknowledged: true`

**Example:**

```bash
curl -X PUT http://deltafi-host/api/v2/events/123e4567-e89b-12d3-a456-426614174000/acknowledge
```

## Unacknowledge Event

Marks an event as unacknowledged.

**Endpoint:** `PUT /api/v2/events/{id}/unacknowledge`

**Permission:** `EventUpdate`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Event identifier |

**Response:** Updated Event object with `acknowledged: false`

**Example:**

```bash
curl -X PUT http://deltafi-host/api/v2/events/123e4567-e89b-12d3-a456-426614174000/unacknowledge
```

## Delete Event

**Endpoint:** `DELETE /api/v2/events/{id}`

**Permission:** `EventDelete`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Event identifier |

**Response:** The deleted Event object

**Example:**

```bash
curl -X DELETE http://deltafi-host/api/v2/events/123e4567-e89b-12d3-a456-426614174000
```

## Event Severities

| Severity | Color | Aliases |
|----------|-------|---------|
| `error` | Red | `failure`, `red` |
| `warn` | Yellow | `warning`, `yellow` |
| `info` | Blue | All other values |
| `success` | Green | `successful`, `green` |

## GUI Integration

Events with `notification: true` appear in the alarm bell notification area in the upper right corner of the DeltaFi GUI. Users can acknowledge notifications from the GUI, which calls the acknowledge endpoint. Acknowledged events remain accessible through the API and can be unacknowledged if needed.
