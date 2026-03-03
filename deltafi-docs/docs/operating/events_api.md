# Events

::: tip API Reference
For complete events endpoint documentation including all parameters and response formats, see the [Events REST API](/api/rest/events).
:::

DeltaFi uses an events system to surface important system notifications to operators. Events track plugin installations, configuration changes, deployment operations, audit actions, and any custom notifications created by external systems or scripts.

## Viewing Events in the GUI

Events appear in the alarm bell icon in the upper right corner of the DeltaFi GUI. Unacknowledged events with `notification: true` trigger the bell indicator, drawing attention to items that need review.

From the GUI, operators can:
- View event details (summary, content, source, timestamp)
- Acknowledge events to clear notifications
- Filter events by severity

## Event Severity Levels

| Severity | Color | Use For |
|----------|-------|---------|
| `error` | Red | Critical issues requiring immediate attention |
| `warn` | Yellow | Conditions that should be monitored |
| `info` | Blue | Informational messages |
| `success` | Green | Successful operations or positive status updates |

The system normalizes common aliases: `failure` and `red` map to `error`, `warning` and `yellow` map to `warn`, `successful` and `green` map to `success`. All other values map to `info`.

## Common Event Sources

DeltaFi generates events automatically for:

- **Plugin lifecycle** — installation, uninstallation, upgrades, and failures
- **Deployment operations** — container start/stop, health changes
- **Audit logging** — configuration changes made by users
- **Plugin restarts** — when a plugin is restarted due to action execution issues

External systems and scripts can also create events through the REST API.

## Creating Events from Scripts

Events are useful for integrating DeltaFi with external monitoring, CI/CD pipelines, or operational scripts.

```bash
# Post an informational event
curl -X POST http://deltafi-host/api/v2/events \
  -H "Content-Type: application/json" \
  -d '{
    "severity": "info",
    "summary": "Nightly batch processing complete",
    "content": "Processed 15,432 files in 2h 13m",
    "source": "batch-scheduler",
    "notification": false
  }'

# Post an error notification that shows in the alarm bell
curl -X POST http://deltafi-host/api/v2/events \
  -H "Content-Type: application/json" \
  -d '{
    "severity": "error",
    "summary": "Upstream feed offline",
    "content": "The ACME data feed has not sent data in over 30 minutes",
    "source": "feed-monitor",
    "notification": true
  }'
```

## Acknowledging Events

Acknowledged events remain in the system but no longer trigger the notification indicator. This is useful for tracking issues that have been seen but not yet resolved.

```bash
# Acknowledge an event
curl -X PUT http://deltafi-host/api/v2/events/{id}/acknowledge

# Unacknowledge if it needs attention again
curl -X PUT http://deltafi-host/api/v2/events/{id}/unacknowledge
```

## Querying Events

Filter events by severity, source, or other criteria for operational monitoring.

```bash
# Get the 10 most recent error events
curl "http://deltafi-host/api/v2/events?severity=error&size=10"

# Get events with total count for pagination
curl "http://deltafi-host/api/v2/events/with-count?offset=0&size=20"
```

## Permissions

| Action | Permission |
|--------|------------|
| View events | `EventRead` |
| Create events | `EventCreate` |
| Acknowledge/unacknowledge | `EventUpdate` |
| Delete events | `EventDelete` |
