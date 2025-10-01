# Events API

The Events API provides endpoints for managing system events and notifications in DeltaFi. These events appear as notifications in the alarm bell in the upper right corner of the GUI and can be acknowledged by users.

## Event Object

An Event object contains the following fields:

- `id` (UUID): Unique identifier for the event
- `severity` (String): Event severity level - `error`, `warn`, `info`, or `success`
- `summary` (String): Brief summary of the event
- `content` (String): Detailed content/description of the event (max 100,000 characters)
- `source` (String): Source that generated the event
- `timestamp` (OffsetDateTime): When the event was created
- `notification` (Boolean): Whether this event should be shown as a notification
- `acknowledged` (Boolean): Whether the event has been acknowledged by a user

## API Endpoints

All Event API endpoints are available under `/api/v2/events` and require appropriate permissions.

### Get Events

Retrieves a list of events with optional filtering and pagination.

**Endpoint:** `GET /api/v2/events`

**Required Permission:** `EventRead`

**Query Parameters:**
- `offset` (integer, default: 0): Number of records to skip for pagination
- `size` (integer, default: 20): Maximum number of records to return
- Additional filter parameters can be provided as key-value pairs

**Response:** Array of Event objects

**Example:**
```bash
curl -X GET "http://deltafi-url/api/v2/events?offset=0&size=10&severity=error"
```

### Get Events with Count

Retrieves events along with pagination metadata including total count.

**Endpoint:** `GET /api/v2/events/with-count`

**Required Permission:** `EventRead`

**Query Parameters:**
- `offset` (integer, default: 0): Number of records to skip for pagination
- `size` (integer, default: 20): Maximum number of records to return
- Additional filter parameters can be provided as key-value pairs

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

### Get Single Event

Retrieves a specific event by its ID.

**Endpoint:** `GET /api/v2/events/{id}`

**Required Permission:** `EventRead`

**Path Parameters:**
- `id` (UUID): The unique identifier of the event

**Response:** Event object

**Example:**
```bash
curl -X GET "http://deltafi-url/api/v2/events/123e4567-e89b-12d3-a456-426614174000"
```

### Create Event

Creates a new system event.

**Endpoint:** `POST /api/v2/events`

**Required Permission:** `EventCreate`

**Request Body:** Event object (id and timestamp will be auto-generated if not provided)

**Response:** Created Event object

**Example:**
```bash
curl -X POST "http://deltafi-url/api/v2/events" \
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

### Acknowledge Event

Marks an event as acknowledged.

**Endpoint:** `PUT /api/v2/events/{id}/acknowledge`

**Required Permission:** `EventUpdate`

**Path Parameters:**
- `id` (UUID): The unique identifier of the event to acknowledge

**Response:** Updated Event object with `acknowledged` set to `true`

**Example:**
```bash
curl -X PUT "http://deltafi-url/api/v2/events/123e4567-e89b-12d3-a456-426614174000/acknowledge"
```

### Unacknowledge Event

Marks an event as unacknowledged.

**Endpoint:** `PUT /api/v2/events/{id}/unacknowledge`

**Required Permission:** `EventUpdate`

**Path Parameters:**
- `id` (UUID): The unique identifier of the event to unacknowledge

**Response:** Updated Event object with `acknowledged` set to `false`

**Example:**
```bash
curl -X PUT "http://deltafi-url/api/v2/events/123e4567-e89b-12d3-a456-426614174000/unacknowledge"
```

### Delete Event

Deletes an event from the system.

**Endpoint:** `DELETE /api/v2/events/{id}`

**Required Permission:** `EventDelete`

**Path Parameters:**
- `id` (UUID): The unique identifier of the event to delete

**Response:** The deleted Event object

**Example:**
```bash
curl -X DELETE "http://deltafi-url/api/v2/events/123e4567-e89b-12d3-a456-426614174000"
```

## Event Severities

Events support the following severity levels:

- `error`: Critical issues requiring immediate attention (red)
- `warn`: Warning conditions that should be monitored (yellow)
- `info`: Informational messages (blue)
- `success`: Successful operations or positive status (green)

The system automatically maps similar severity values:
- `failure`, `red` → `error`
- `warning`, `yellow` → `warn`  
- `successful`, `green` → `success`
- All other values → `info`

## Integration with GUI

Events marked with `notification: true` will appear in the alarm bell notification area in the upper right corner of the DeltaFi GUI. Users can acknowledge these notifications directly from the GUI, which calls the acknowledge endpoint described above.

Acknowledged events may be filtered or hidden from the main notification view but remain accessible through the API and can be unacknowledged if needed.