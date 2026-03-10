---
# ABOUTME: Documents the DeltaFi API authentication system.
# ABOUTME: Covers auth modes, permission model, request headers, and the full permissions catalog.
---

# Authentication

DeltaFi uses role-based access control (RBAC) to secure API endpoints. Every endpoint requires a specific permission, and users are granted permissions through roles.

For setup and configuration instructions, see [Authentication Configuration](/config/authentication) and [Authorization Configuration](/config/authorization).

## Auth Modes

DeltaFi supports three authentication modes, configured via the `auth.mode` system property:

| Mode | Value | Description |
|------|-------|-------------|
| **Basic** | `basic` | Username/password authentication (default) |
| **Certificate** | `cert` | X.509 client certificate authentication via Distinguished Names |
| **Disabled** | `disabled` | No authentication; all requests treated as admin. For development only. |

### Basic Authentication

The default mode. Users authenticate with username and password via HTTP Basic auth. The default `admin` user's password is set with `deltafi set-admin-password`.

```bash
curl -u admin:password http://deltafi-host/api/v2/status
```

### Certificate Authentication

Uses X.509 client certificates. The Distinguished Name (DN) from the certificate must match a DN configured for a user in the system. Requires a CA certificate chain stored in a Kubernetes secret.

### Disabled Mode

All requests are treated as coming from the `admin` user with full permissions. Intended for local development and testing only.

## Request Headers

After initial authentication, the auth gateway populates these headers on forwarded requests:

| Header | Description |
|--------|-------------|
| `X-DeltaFi-User-Name` | Authenticated username (or DN in cert mode) |
| `X-DeltaFi-User-Id` | User's UUID |
| `X-DeltaFi-Permissions` | Comma-separated list of granted permissions |

## Permission Model

Permissions follow an RBAC hierarchy:

```
User → Roles → Permissions
```

- A **user** is assigned one or more **roles**
- Each **role** contains one or more **permissions**
- Endpoint access requires a specific **permission** (or the `Admin` permission)

### Admin Override

The `Admin` permission grants access to every endpoint. All permission checks include an implicit `OR Admin` clause:

```java
@PreAuthorize("hasAnyAuthority('PermissionName', 'Admin')")
```

### Default Roles

| Role | Permissions | Purpose |
|------|------------|---------|
| Admin | `Admin` | Full system access |
| Ingress Only | `DeltaFileIngress` | Data ingestion service accounts |
| Read Only | Read-only permissions | View-only access |

### Combined Permissions

Some endpoints require multiple permissions simultaneously:

- **DeltaFileReplayAndAcknowledge** — requires both `DeltaFileReplay` and `DeltaFileAcknowledge`
- **SnapshotCreateAndRevert** — requires both `SnapshotCreate` and `SnapshotRevert`

## Permissions Catalog

### Administration

| Permission | Description |
|------------|-------------|
| `Admin` | Full system access |

### General

| Permission | Description |
|------------|-------------|
| `UIAccess` | Access to the UI |
| `StatusView` | View system status |
| `StatusPause` | Pause and resume system status checks |
| `DashboardView` | View the UI dashboard |
| `VersionsView` | View running versions |

### Metrics

| Permission | Description |
|------------|-------------|
| `MetricsAdmin` | Admin role in Grafana |
| `MetricsView` | Viewer role in Grafana |
| `MetricsEdit` | Editor role in Grafana |
| `SurveyCreate` | Post survey metrics into the system |

### DeltaFiles

| Permission | Description |
|------------|-------------|
| `DeltaFileMetadataView` | Query and view DeltaFile metadata |
| `DeltaFileMetadataWrite` | Update DeltaFile metadata |
| `DeltaFileContentView` | View DeltaFile content |
| `DeltaFileReplay` | Replay DeltaFiles |
| `DeltaFileResume` | Resume DeltaFiles in an ERROR stage |
| `DeltaFileAcknowledge` | Acknowledge DeltaFiles in an ERROR stage |
| `DeltaFileCancel` | Cancel processing of a DeltaFile |
| `DeltaFileUserNote` | Add user notes to a DeltaFile |
| `DeltaFilePinning` | Pin or unpin a DeltaFile |
| `DeltaFileIngress` | Ingress DeltaFiles |
| `DeltaFileExport` | Export DeltaFiles |
| `DeltaFileImport` | Import DeltaFiles |

### Flows

| Permission | Description |
|------------|-------------|
| `FlowView` | View flows |
| `FlowValidate` | Validate flows |
| `FlowUpdate` | Change flow state and update flows |
| `FlowPlanDelete` | Remove flow plans from the system plugin |
| `FlowPlanCreate` | Save flow plans in the system plugin |

### Plugins

| Permission | Description |
|------------|-------------|
| `PluginsView` | View installed plugins |
| `PluginInstall` | Install plugins |
| `PluginUninstall` | Uninstall plugins |
| `PluginVariableUpdate` | Edit plugin variables |

### System Properties

| Permission | Description |
|------------|-------------|
| `SystemPropertiesRead` | View System Properties |
| `SystemPropertiesUpdate` | Edit System Properties |

### Delete Policies

| Permission | Description |
|------------|-------------|
| `DeletePolicyCreate` | Create Delete Policies |
| `DeletePolicyRead` | View Delete Policies |
| `DeletePolicyUpdate` | Edit Delete Policies |
| `DeletePolicyDelete` | Delete Delete Policies |

### Snapshots

| Permission | Description |
|------------|-------------|
| `SnapshotCreate` | Create Snapshots |
| `SnapshotRead` | View Snapshots |
| `SnapshotDelete` | Delete Snapshots |
| `SnapshotRevert` | Revert the system to a Snapshot |

### Users

| Permission | Description |
|------------|-------------|
| `UserCreate` | Create Users |
| `UserRead` | View Users |
| `UserUpdate` | Edit Users |
| `UserDelete` | Delete Users |

### Roles

| Permission | Description |
|------------|-------------|
| `RoleCreate` | Create Roles |
| `RoleRead` | View Roles |
| `RoleUpdate` | Edit Roles |
| `RoleDelete` | Delete Roles |

### Events

| Permission | Description |
|------------|-------------|
| `EventCreate` | Create Events |
| `EventRead` | View Events |
| `EventUpdate` | Edit Events |
| `EventDelete` | Delete Events |
| `EventAcknowledge` | Acknowledge Events |

### Stress Tests

| Permission | Description |
|------------|-------------|
| `StressTest` | Execute Stress Tests |

### Integration Tests

| Permission | Description |
|------------|-------------|
| `IntegrationTestUpdate` | Save and start integration tests |
| `IntegrationTestView` | View integration test results |
| `IntegrationTestDelete` | Delete completed integration test results |

### Resume Policies

| Permission | Description |
|------------|-------------|
| `ResumePolicyApply` | Apply Resume Policies to DeltaFiles in an ERROR state |
| `ResumePolicyDryRun` | Preview which DeltaFiles a policy would affect |
| `ResumePolicyCreate` | Create Resume Policies |
| `ResumePolicyRead` | View Resume Policies |
| `ResumePolicyUpdate` | Edit Resume Policies |
| `ResumePolicyDelete` | Delete Resume Policies |

### Artifacts

| Permission | Description |
|------------|-------------|
| `ArtifactRegistryRead` | Read artifacts from the artifact registry |
| `ArtifactRegistryWrite` | Write artifacts to the artifact registry |

## Open Endpoints

The following endpoints do not require authentication:

| Endpoint | Purpose |
|----------|---------|
| `POST /plugins` | Plugin self-registration during startup |
| `GET /api/v2/permissions` | List all available permissions |
| `GET /api/v2/me` | Return current user info from headers |
