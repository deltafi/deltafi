# DeltaFi Leader-Member Monitoring Specification

## Overview

This spec defines a multi-site monitoring feature where a DeltaFi instance can be configured as a **leader** that monitors multiple **member** DeltaFi instances. The leader polls member endpoints to collect status, error counts, and in-flight file metrics, displaying them in a unified dashboard.

### Goals
- Enable centralized monitoring of distributed DeltaFi deployments
- Provide real-time visibility into member site health
- Identify issues across member sites quickly (errors, degraded status)
- Compare configurations across the fleet to detect drift
- Foundation for future config sync

---

## Architecture Context

### Current DeltaFi Architecture
- **Backend:** Spring Boot + Netflix DGS GraphQL + PostgreSQL + Valkey + MinIO
- **Frontend:** Vue 3 + PrimeVue + Composition API
- **Deployment:** Kubernetes (multi-node) or Docker Compose (single-node)
- **Monitoring:** StatusCheck system with 5s polling, results stored in Valkey

### Shared State Requirements
- **Storage:** Valkey (not in-memory)
- **Reason:** Core runs main scheduler + workers; workers need to read member status when handling web requests
- **Key Pattern:** Similar to `org.deltafi.monitor.status` used for system status

### Timeout Strategy
The scheduler runs every 30s with `fixedDelay`, meaning it waits for completion before scheduling the next run:
- **Overall member poll timeout:** Configurable (default 25s) - ensures each member poll completes before next cycle
- **Parallel execution:** Members are polled in parallel using a fixed thread pool
- **Graceful degradation:** Failed polls return cached data with STALE indicator

---

## Implemented Features

### Phase 1: Core Monitoring ✓

**Configuration:** `leaderConfig` property in `DeltaFiProperties.java` (JSON format)
```json
{
  "site-east-1": {
    "url": "https://east1.deltafi.example.com",
    "tags": ["east", "production"],
    "credentials": {
      "type": "basic",
      "username": "leader-monitor",
      "passwordEnvVar": "MEMBER_EAST1_PASSWORD"
    }
  }
}
```

**Key Components:**
- `MemberMonitorService.java` - Polls members, caches results in Valkey
- `LeaderMonitorScheduler.java` - 30s polling interval
- `LeaderRest.java` - REST endpoints for dashboard data
- `LeaderDashboardPage.vue` - Card-based member status view

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v2/leader/members` | Get all member statuses |
| GET | `/api/v2/leader/stats` | Aggregated stats across all members |

### Phase 2: Enhanced Monitoring ✓

**Features Implemented:**
- Queue metrics (warm, cold, paused counts)
- System metrics (CPU, memory, disk) via standard node metrics collection
- Flow metrics (ingress, egress, storage, deleted bytes)
- Multiple dashboard views: Summary, Detailed, Ingress, Egress, Storage, Deleted
- Aggregated stats endpoint

**New Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v2/leader/flow-metrics?minutes=60` | Flow metrics for all members |
| GET | `/api/v2/status/report` | Unified member status report (on members) |
| GET | `/api/v2/metrics/flow?minutes=60` | Flow metrics (on members) |

### Phase 3: Config Drift Detection ✓

**Features Implemented:**
- On-demand snapshot assembly without database persistence
- Snapshot fetching from members
- Configuration comparison between leader and members

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v2/system/snapshot/current` | Current config as snapshot (on members) |
| GET | `/api/v2/leader/config/snapshot/{memberName}` | Fetch member's snapshot |
| GET | `/api/v2/leader/config/plugins` | Plugins across all members |

### Phase 4: Fleet Config Page ✓

**Features Implemented:**
- **Members Tab:** Shows reporting status and sync status (diff counts)
- **Plugins Tab:** Matrix view of plugins across members with version comparison
- **Snapshot Tab:** Side-by-side comparison with inline diff highlighting

**Comparison Areas:**
- Plugins (name, version)
- Flows (REST, Timed, OnError data sources, transforms, data sinks)
- Properties
- Links, Users, Roles
- Resume Policies, Delete Policies
- Plugin Variables

**UI Features:**
- localStorage persistence for view mode
- Non-reporting members shown but disabled in dropdowns
- Diff highlighting on value columns only
- Expandable plugin details with per-member version info

---

## Phase 5: Rolling Configuration Sync (Future)

### Overview
Push configurations from leader to members in a controlled, rolling manner that maximizes availability.

### Sync Process

**Goal:** Apply leader's configuration to members while maintaining system availability.

**Process Steps** (per member):
1. **Pause Ingress**: Stop accepting new data at the member
2. **Drain Queue**: Wait for in-flight count to reach 0 (or timeout)
3. **Apply Config**: Push snapshot to member (excluding property overrides)
4. **Resume Ingress**: Re-enable data ingestion
5. **Health Check**: Verify member is healthy before proceeding to next

**Sync Modes:**
1. **Single Member**: Sync one specific member on demand
2. **Rolling All**: Sync all members sequentially
3. **Rolling by Tag**: Sync members with a specific tag (e.g., region)

### Proposed Data Structures

```java
public record SyncRequest(
    List<String> memberNames,      // Empty = all members
    List<String> tags,             // Filter by tags if memberNames empty
    int drainTimeoutMinutes,       // Max time to wait for drain (default 30)
    boolean dryRun                 // If true, compute changes but don't apply
) {}

public record SyncStatus(
    UUID syncId,
    SyncState state,               // PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    List<MemberSyncStatus> members,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String initiatedBy
) {}

public record MemberSyncStatus(
    String memberName,
    MemberSyncState state,         // PENDING, PAUSING_INGRESS, DRAINING, APPLYING_CONFIG, etc.
    String statusMessage,
    Long inFlightAtStart,
    Long currentInFlight,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt
) {}
```

### Proposed Endpoints

**Leader-side:**
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v2/leader/sync` | Start a new sync operation |
| GET | `/api/v2/leader/sync/{syncId}` | Get sync status |
| POST | `/api/v2/leader/sync/{syncId}/cancel` | Cancel in-progress sync |
| GET | `/api/v2/leader/sync/history` | List recent sync operations |
| POST | `/api/v2/leader/sync/preview` | Preview changes without applying |

**Member-side (new):**
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v2/system/ingress/pause` | Pause ingress |
| POST | `/api/v2/system/ingress/resume` | Resume ingress |
| POST | `/api/v2/system/config/apply` | Apply snapshot |

### Property Overrides

Properties that should differ between leader and members:
```json
{
  "site-east-1": {
    "url": "https://east1.deltafi.example.com",
    "propertyOverrides": {
      "systemName": "DeltaFi East-1",
      "ui.externalIngressUrl": "https://east1.deltafi.example.com"
    }
  }
}
```

Properties in `propertyOverrides` are excluded from diff comparisons and sync operations.

### Safety Features

1. **Dry Run Mode**: Preview all changes before applying
2. **Drain Timeout**: Configurable max wait time for queue to drain
3. **Health Verification**: Confirm member is healthy before proceeding
4. **Cancellation**: Stop sync at any point (already-synced members stay synced)
5. **Rollback**: Keep previous snapshot on member for manual rollback
6. **Audit Log**: Record who initiated sync and what changed
7. **Version Check**: Warn/prevent sync to incompatible versions

### Error Handling

| Scenario | Behavior |
|----------|----------|
| Member unreachable | Skip member, continue with others, report in status |
| Drain timeout | Fail member, continue with others (configurable) |
| Config apply fails | Keep member paused, alert, require manual intervention |
| Health check fails | Log warning, continue (member will show unhealthy in dashboard) |

---

## Key Issues & Solutions

### Authentication & Credentials
- Basic auth credentials in member config, passwords in env vars
- Never store passwords in properties
- Future: Support client certificate auth

### Network Failures & Timeouts
- Overall member poll timeout (configurable, default 25s)
- Cache last known good data in Valkey
- Display stale data with clear indicators
- Show connection error messages to users

### Version Compatibility
- Members that don't support newer endpoints fall back to legacy 3-call approach
- All new fields in `MemberReport` are nullable for graceful degradation
- Version displayed in dashboard for visibility

### Scalability
- Parallel polling with fixed thread pool (MAX_CONCURRENT_REQUESTS = 50)
- Flow metrics cached for 5 seconds
- Snapshot endpoints cached for 5 seconds

---

## Valkey Key Structure

| Key Pattern | Description | TTL |
|-------------|-------------|-----|
| `org.deltafi.leader.member.{name}` | Full member status JSON | None (overwritten each poll) |

---

## Related Files

### Backend
- `deltafi-core/.../services/MemberMonitorService.java`
- `deltafi-core/.../services/LeaderConfigService.java`
- `deltafi-core/.../schedulers/LeaderMonitorScheduler.java`
- `deltafi-core/.../rest/LeaderRest.java`
- `deltafi-core/.../rest/SystemRest.java`
- `deltafi-core/.../types/leader/*.java`
- `deltafi-core/.../types/MemberReport.java`

### Frontend
- `deltafi-ui/src/pages/LeaderDashboardPage.vue`
- `deltafi-ui/src/pages/LeaderConfigPage.vue`
- `deltafi-ui/src/composables/useLeaderDashboard.ts`
- `deltafi-ui/src/composables/useLeaderConfig.ts`
