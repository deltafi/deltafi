# Flow States and Data Behavior

This guide explains how flow states affect data processing in DeltaFi, including the relationship between flow state, flow validity, and plugin state.

## Flow States

Every flow has a state that controls whether it processes data:

| State | Description | Data Behavior |
|-------|-------------|---------------|
| **RUNNING** | Flow is active and ready to process | Normal processing (if valid and plugin ready) |
| **PAUSED** | Flow is temporarily suspended | Data queues for later processing |
| **STOPPED** | Flow is intentionally halted | Data is rejected |

### RUNNING

A running flow actively processes incoming data. Data sources accept ingress, transforms process published data, and data sinks egress data to external systems.

### PAUSED

A paused flow queues data instead of processing it. Use pause when you need to:

- Temporarily halt processing for maintenance
- Buffer data during downstream system outages
- Control data flow rates

Queued data automatically resumes processing when the flow returns to RUNNING.

### STOPPED

A stopped flow rejects data entirely. Use stop when:

- The flow should not receive any data
- You want to prevent accidental data ingress
- The flow is deprecated but not yet removed

## Flow Validity

Flows can become invalid due to configuration errors. Invalid flows behave differently from valid flows:

| Validity | Data Behavior |
|----------|---------------|
| **Valid** | Processes based on flow state |
| **Invalid** | Data queues (regardless of flow state) |

### Common Causes of Invalid Flows

- **Unresolved variables**: Flow references a variable that doesn't exist
- **Unregistered action**: Flow uses an action type not registered by any plugin
- **Inactive action**: Action exists but is not currently active
- **Invalid action parameters**: Action parameters fail validation
- **Invalid configuration**: Other configuration errors

### How Invalid Flows Handle Data

Invalid flows queue data rather than rejecting it. This prevents data loss when:

- A plugin is temporarily unavailable
- Configuration changes cause transient validation errors
- Variables are being updated

Once the flow becomes valid again, queued data automatically resumes processing.

## Complete Behavior Matrix

The effective behavior of a flow depends on three factors:

1. **Flow State**: RUNNING, PAUSED, or STOPPED (user-controlled)
2. **Flow Validity**: Valid or Invalid (configuration-dependent)
3. **Plugin State**: INSTALLED, PENDING, FAILED, etc. (system-controlled)

| Flow State | Valid? | Plugin Ready? | Result |
|------------|--------|---------------|--------|
| RUNNING | Yes | Yes | **Normal processing** |
| RUNNING | Yes | No | Data queues |
| RUNNING | No | Any | Data queues |
| RUNNING | Any | Disabled | Data rejected |
| PAUSED | Any | Any | Data queues |
| STOPPED | Any | Any | Data rejected |

### Key Principles

1. **Data queues by default**: Unless explicitly stopped or disabled, data queues rather than being rejected
2. **User intent preserved**: Only STOPPED flows and disabled plugins reject data
3. **Automatic recovery**: Once conditions improve, queued data processes automatically

## Queued Data Processing

When data is queued (due to pause, invalid flow, or plugin not ready):

1. The DeltaFile is marked as paused with state PAUSED
2. Data remains in the queue until conditions change
3. A background scheduler checks every 5 seconds for data that can be unpaused
4. When the flow becomes processable, data automatically resumes

### What Triggers Resume

Queued data resumes when ALL of these are true:

- Flow state is RUNNING (not PAUSED or STOPPED)
- Flow is valid (no configuration errors)
- Plugin is ready (INSTALLED and not disabled)

### Monitoring Queued Data

View paused DeltaFiles in the UI or query via API:

```graphql
query {
  deltaFiles(filter: { paused: true }) {
    deltaFiles {
      did
      name
      flows {
        name
        state
      }
    }
  }
}
```

## Operational Scenarios

### Plugin Upgrade

During a plugin upgrade:
1. Plugin enters INSTALLING state
2. Data arriving at plugin's flows queues automatically
3. Plugin completes upgrade, enters INSTALLED state
4. Queued data resumes processing

No manual intervention required.

### Configuration Change

When updating flow configuration:
1. Changes may temporarily invalidate the flow
2. In-flight data queues
3. Once configuration validates, data resumes

### Downstream System Outage

If an egress destination is unavailable:
1. Pause the data sink to prevent error accumulation
2. Data queues at the paused sink
3. When the destination recovers, unpause
4. Queued data egresses automatically

### Deprecating a Flow

To safely deprecate a flow:
1. Stop the flow to prevent new data
2. Allow existing in-flight data to complete
3. Remove the flow definition

Stopped flows reject new data, signaling to upstream systems that the flow is no longer available.
