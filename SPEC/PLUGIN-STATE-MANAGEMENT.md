# Plugin State Management

## Overview

DeltaFi uses a declarative plugin management model where plugin configuration represents **desired state**, and a background reconciliation service works to achieve that state. This enables instant snapshot application and provides visibility into plugin health.

### Key Benefits
- **Instant snapshot apply**: Plugin installation is queued, not blocking
- **Clear visibility**: Each plugin shows its installation state
- **Automatic recovery**: Crashed plugins are restarted automatically
- **Data preservation**: Data queues during plugin unavailability instead of being rejected
- **Manual control**: Retry, rollback, disable, and enable operations

---

## Plugin States

```
PENDING     → Plugin desired but install not yet attempted
INSTALLING  → Install in progress
INSTALLED   → Successfully running and registered
FAILED      → Install/start failed (error message stored)
REMOVING    → Uninstall in progress
```

### State Transitions

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    ▼                                     │
┌─────────┐    ┌──────────┐    ┌───────────┐    ┌────────┴─┐
│ PENDING │───▶│INSTALLING│───▶│ INSTALLED │───▶│ REMOVING │───▶ [deleted]
└─────────┘    └──────────┘    └───────────┘    └──────────┘
                    │                │
                    │                │ (crash/stop detected)
                    ▼                ▼
               ┌────────┐      ┌──────────┐
               │ FAILED │◀─────│INSTALLING│ (auto-restart)
               └────────┘      └──────────┘
                    │
                    │ (user retry or rollback)
                    ▼
               ┌─────────┐
               │ PENDING │
               └─────────┘
```

### Plugin Entity Fields

| Field | Description |
|-------|-------------|
| `installState` | Current state (PENDING, INSTALLING, INSTALLED, FAILED, REMOVING) |
| `installError` | Error message when FAILED |
| `lastStateChange` | Timestamp of last state transition |
| `installAttempts` | Number of install attempts |
| `lastSuccessfulVersion` | Version that was last successfully installed |
| `lastSuccessfulImage` | Full image reference for rollback |
| `lastSuccessfulImageTag` | Image tag for rollback |
| `disabled` | Plugin is disabled (stopped but config preserved) |

---

## Reconciliation Service

The `PluginReconciliationService` runs as a scheduled task (every 30 seconds by default) and compares desired state (DB) with actual state (running containers/pods).

### Reconciliation Logic

| Current State | Condition | Action |
|---------------|-----------|--------|
| DISABLED | Container running | Stop container |
| PENDING | - | Start install, set INSTALLING |
| INSTALLING | Container running + registered | Set INSTALLED, save rollback info |
| INSTALLING | Timeout exceeded | Set FAILED with timeout error |
| INSTALLED | Container not running | Set INSTALLING (auto-restart) |
| FAILED | - | Wait for user action (retry or rollback) |
| REMOVING | Container stopped | Delete plugin record |

Orphaned containers (running but not in desired state) are stopped and removed.

### Rollback Support

When a plugin transitions to INSTALLED, its current version/image is saved as the "last successful" state:

- **On successful install**: Save version, image, and tag to `lastSuccessful*` fields
- **On failed upgrade**: User can rollback to the previous version
- **Rollback action**: Restores image/version and sets state to PENDING
- **Fresh install failure**: No rollback available (only retry)

### Disabled Plugins

Plugins can be disabled to stop them without losing configuration:

- **Disable**: Container stopped, DB record preserved with all flow definitions
- **Enable**: State set to PENDING, reconciler reinstalls
- Use case: Temporarily stop a plugin for debugging or resource savings

---

## Flow Behavior

Flow behavior is determined by combining two independent states:

1. **Flow State** (user-controlled): RUNNING, PAUSED, STOPPED
2. **Plugin State** (system-controlled): INSTALLED, PENDING, INSTALLING, FAILED, Disabled

### Design Principles

- **User intent preserved**: Flow state is never changed by plugin state transitions
- **Never lose data silently**: Only explicit user actions reject data (STOPPED flow or Disabled plugin)
- **Temporary unavailability queues data**: Plugin upgrades and failures queue data for later

### Behavior Matrix

| Flow State | Plugin State | Flow Valid? | Effective Behavior |
|------------|--------------|-------------|-------------------|
| RUNNING | INSTALLED, not disabled | Yes | Normal operation |
| RUNNING | PENDING/INSTALLING/FAILED | any | Data queues |
| RUNNING | any | No (invalid) | Data queues |
| RUNNING | Disabled | any | Data rejected |
| PAUSED | any | any | Data queues |
| STOPPED | any | any | Data rejected |

### Data Queueing

When data is queued:
- Stored with `DeltaFileFlowState.PAUSED`
- `RequeueScheduler` checks every 5 seconds
- Data automatically resumes when blocking condition clears
- No data lost during plugin upgrades or temporary failures

---

## User Operations

### Install Plugin
1. Create plugin record with PENDING state
2. Return immediately (non-blocking)
3. Reconciler handles actual installation

### Retry Failed Plugin
1. Set state to PENDING (clears error)
2. Install proceeds on next reconciliation cycle

### Rollback Failed Plugin
Available when a previous successful version exists:
1. Restore image/version to `lastSuccessful*` values
2. Reset state to PENDING, clear error
3. Reconciler reinstalls previous version

### Disable Plugin
1. Set `disabled = true`
2. Reconciler stops container
3. All configuration and flow definitions preserved

### Enable Plugin
1. Set `disabled = false`, state to PENDING
2. Reconciler reinstalls on next cycle

### Uninstall Plugin
1. Set state to REMOVING
2. Reconciler handles container teardown and flow cleanup
3. Record deleted when complete

---

## Snapshot Apply

When applying a snapshot:
1. Compare snapshot plugins to current plugins
2. New plugins: create record with PENDING
3. Removed plugins: set REMOVING
4. Changed plugins (different version): set PENDING
5. Return immediately (no waiting)

Data arriving for flows during plugin installation is automatically queued and processed once the plugin reaches INSTALLED state.

---

## Health Integration

Plugin health contributes to system status:

| Condition | Health |
|-----------|--------|
| All INSTALLED | GREEN |
| Any PENDING or INSTALLING | YELLOW |
| Any FAILED | RED |

The `/api/v2/status` endpoint includes plugin status summary with failed plugin details.

---

## UI Display

### Plugin States

| State | Display |
|-------|---------|
| DISABLED | ⏸ Disabled |
| PENDING | ⏳ Queued |
| INSTALLING | ⟳ Installing |
| INSTALLED | ✓ Installed |
| FAILED | ✗ Failed: {error message} |
| REMOVING | ⟳ Removing |

### Actions by State

| State | Available Actions |
|-------|-------------------|
| INSTALLED | Disable, Uninstall |
| FAILED (fresh) | Retry, Uninstall |
| FAILED (upgrade) | Rollback, Retry, Uninstall |
| DISABLED | Enable, Uninstall |
| PENDING/INSTALLING/REMOVING | None (in progress) |

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Install timeout | INSTALLING → FAILED after configured timeout |
| Container crash loop | Repeated auto-restart attempts, then FAILED |
| Image pull failure | FAILED with pull error (403 auth, 404 not found, etc.) |
| Plugin registration timeout | Container running but not registered → FAILED |
| Failed deployment cleanup | Failed containers/deployments left in place for log inspection |

---

## GraphQL API

### Queries
- `plugins`: List all plugins with state information

### Mutations
- `installPlugin(image: String!)`: Queue plugin for installation
- `uninstallPlugin(pluginCoordinates: PluginCoordinatesInput!)`: Remove plugin
- `retryPluginInstall(pluginCoordinates: PluginCoordinatesInput!)`: Retry failed install
- `rollbackPlugin(pluginCoordinates: PluginCoordinatesInput!)`: Rollback to previous version
- `disablePlugin(pluginCoordinates: PluginCoordinatesInput!)`: Stop plugin, preserve config
- `enablePlugin(pluginCoordinates: PluginCoordinatesInput!)`: Re-enable disabled plugin

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `pluginDeployTimeout` | 60s | Timeout for plugin installation |

Note: The `pluginAutoRollback` property has been removed. Rollback is now always manual via the UI or `deltafi plugin rollback` command.
