# Leader-Member Deployment

DeltaFi supports a leader-member deployment pattern where a designated **leader** instance monitors multiple **member** DeltaFi instances. This enables centralized monitoring of distributed deployments across multiple sites, regions, or environments.

## Overview

In a leader-member deployment:

- One DeltaFi instance is designated as the **leader**
- Other DeltaFi instances are **members** that operate independently
- The leader polls members for status, metrics, and configuration
- Administrators use the leader's UI to monitor all sites from a single dashboard

```
                    ┌─────────────┐
                    │   Leader    │
                    │  Dashboard  │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │  Member A   │ │  Member B   │ │  Member C   │
    │  (East)     │ │  (West)     │ │  (Dev)      │
    └─────────────┘ └─────────────┘ └─────────────┘
```

## Benefits

- Centralized health monitoring across all sites
- Aggregated metrics (in-flight, errors, queues) at a glance
- Configuration drift detection between leader and members
- Plugin version comparison across the fleet
- Tag-based filtering for regional or environment groupings

## Configuration

### Enabling Leader Mode

To designate a DeltaFi instance as a leader, configure the `leaderConfig` system property. This can be set via the System Properties page in the UI or through the API.

The property accepts a JSON object mapping member names to their configuration:

```json
{
  "site-east-1": {
    "url": "https://east1.deltafi.example.com",
    "tags": ["east", "production"]
  },
  "site-west-1": {
    "url": "https://west1.deltafi.example.com",
    "tags": ["west", "production"]
  },
  "site-dev": {
    "url": "https://dev.deltafi.example.com",
    "tags": ["development"]
  }
}
```

### Member Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `url` | Yes | Base URL of the member DeltaFi instance |
| `tags` | No | List of tags for filtering and grouping |
| `credentials` | No | Authentication credentials (see below) |

### Authentication

If members require authentication, configure credentials for each member:

```json
{
  "secure-site": {
    "url": "https://secure.deltafi.example.com",
    "tags": ["production"],
    "credentials": {
      "type": "basic",
      "username": "leader-monitor",
      "passwordEnvVar": "MEMBER_SECURE_PASSWORD"
    }
  }
}
```

The `passwordEnvVar` field specifies an environment variable name containing the password.

::: warning
Never store passwords directly in the configuration. Always use environment variables for sensitive values.
:::

### Polling Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `memberPollingInterval` | 30000ms | How often to poll members. Request timeout is 80% of this value. |

## Leader Dashboard

When leader mode is enabled, the **Leader Dashboard** becomes available in the navigation menu.

### Dashboard Views

The view selector at the top of the dashboard switches between different display modes:

- **Summary**: Member cards showing health status, error counts, and queue metrics
- **Detailed**: Member cards with additional system metrics (CPU, memory, disk)
- **Ingress**: Bytes ingressed by data source per member
- **Egress**: Bytes egressed by data sink per member
- **Storage**: Current storage usage per member
- **Deleted**: Bytes deleted by policy per member

### Aggregate Statistics

The top panel shows totals across all members: in-flight count, error count, warm/cold/paused queues, and member health summary.

### Filtering

Filter members by name substring or by tags using the filter controls.

## Fleet Config

The **Fleet Config** page provides tools for comparing configurations across the fleet.

### Members Tab

Shows all configured members with their reporting status and sync status:
- **Reporting**: Whether the member is responding to API requests
- **Sync Status**: Number of configuration differences from leader (or "In sync")
- Click **Compare** to view detailed differences

### Plugins Tab

Shows installed plugins across all members:
- Leader version displayed for each plugin
- Member installation summary (e.g., "3/5 members, 1 mismatch")
- Expand a row to see per-member version details
- Color coding: green = matches leader, yellow = different version

### Snapshot Tab

Side-by-side comparison of leader and member configuration:
- Select a member from the dropdown to compare against leader
- Browse configuration sections: Plugins, Flows, Properties, Links, Users, Roles, Resume Policies, Delete Policies, Plugin Variables
- Differences are highlighted with color coding
- Section headers show diff counts
- View raw JSON for detailed analysis

Members not reporting config data are shown in the dropdown but disabled.

## Connection States

| State | Description |
|-------|-------------|
| **Connected** | Successfully retrieved current data from member |
| **Stale** | Using cached data; member was recently unreachable |
| **Unreachable** | Cannot connect to member; no cached data available |

Stale data is shown with a warning indicator and timestamp of the last successful update.

## Network Requirements

The leader must be able to reach each member's REST API endpoints (`/api/v2/*`). Ensure:
- Network policies allow leader → member connections
- Firewalls permit traffic on the configured ports
- TLS certificates are valid if using HTTPS
