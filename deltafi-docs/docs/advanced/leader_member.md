# Fleet Leader-Member Deployment

DeltaFi supports a fleet deployment pattern where a designated **Fleet Leader** instance monitors multiple **Fleet Member** DeltaFi instances. This enables centralized monitoring of distributed deployments across multiple sites, regions, or environments.

## Overview

In a fleet deployment:

- One DeltaFi instance is designated as the **Fleet Leader**
- Other DeltaFi instances are **Fleet Members** that operate independently
- The Fleet Leader polls Fleet Members for status, metrics, and configuration
- Administrators use the Fleet Leader's UI to monitor all sites from a single dashboard

```
                    ┌─────────────┐
                    │Fleet Leader │
                    │  Dashboard  │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │Fleet Member │ │Fleet Member │ │Fleet Member │
    │  (East)     │ │  (West)     │ │  (Dev)      │
    └─────────────┘ └─────────────┘ └─────────────┘
```

## Benefits

- Centralized health monitoring across all sites
- Aggregated metrics (in-flight, errors, queues) at a glance
- Configuration drift detection between Fleet Leader and Fleet Members
- Plugin version comparison across the fleet
- Tag-based filtering for regional or environment groupings

## Configuration

### Enabling Fleet Leader Mode

To designate a DeltaFi instance as a Fleet Leader, configure the `leaderConfig` system property. This can be set via the System Properties page in the UI or through the API.

The property accepts a JSON object mapping Fleet Member names to their configuration:

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

### Fleet Member Configuration Fields

| Field | Required | Description |
|-------|----------|-------------|
| `url` | Yes | Base URL of the Fleet Member DeltaFi instance |
| `tags` | No | List of tags for filtering and grouping |
| `credentials` | No | Authentication credentials (see below) |

### Authentication

If Fleet Members require authentication, configure credentials for each Fleet Member:

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
| `memberPollingInterval` | 30000ms | How often to poll Fleet Members. Request timeout is 80% of this value. |

## Fleet Leader Dashboard

When Fleet Leader mode is enabled, the **Fleet Leader Dashboard** becomes available in the navigation menu.

### Dashboard Views

The view selector at the top of the dashboard switches between different display modes:

- **Summary**: Fleet Member cards showing health status, error counts, and queue metrics
- **Detailed**: Fleet Member cards with additional system metrics (CPU, memory, disk)
- **Ingress**: Bytes ingressed by data source per Fleet Member
- **Egress**: Bytes egressed by data sink per Fleet Member
- **Storage**: Current storage usage per Fleet Member
- **Deleted**: Bytes deleted by policy per Fleet Member

### Aggregate Statistics

The top panel shows totals across all Fleet Members: in-flight count, error count, warm/cold/paused queues, and Fleet Member health summary.

### Filtering

Filter Fleet Members by name substring or by tags using the filter controls.

## Fleet Config

The **Fleet Config** page provides tools for comparing configurations across the fleet.

### Fleet Members Tab

Shows all configured Fleet Members with their reporting status and sync status:
- **Reporting**: Whether the Fleet Member is responding to API requests
- **Sync Status**: Number of configuration differences from Fleet Leader (or "In sync")
- Click **Compare** to view detailed differences

### Plugins Tab

Shows installed plugins across all Fleet Members:
- Fleet Leader version displayed for each plugin
- Fleet Member installation summary (e.g., "3/5 Fleet Members, 1 mismatch")
- Expand a row to see per-Fleet Member version details
- Color coding: green = matches Fleet Leader, yellow = different version

### Snapshot Tab

Side-by-side comparison of Fleet Leader and Fleet Member configuration:
- Select a Fleet Member from the dropdown to compare against Fleet Leader
- Browse configuration sections: Plugins, Flows, Properties, Links, Users, Roles, Resume Policies, Delete Policies, Plugin Variables
- Differences are highlighted with color coding
- Section headers show diff counts
- View raw JSON for detailed analysis

Fleet Members not reporting config data are shown in the dropdown but disabled.

## Fleet Search

The **Fleet Search** page enables searching for DeltaFiles across all Fleet Member systems from a single interface.

### Search Filters

The same filters available on the standard DeltaFile Search page can be applied:
- Time range (modified date)
- Stage (ingress, enrich, egress, complete, error, cancelled)
- Data sources, transforms, and data sinks
- Filename patterns
- Annotations
- And other advanced filters

### Results

Search results show aggregated counts per system:
- **System**: Fleet Member name (click to open that Fleet Member's search page with the same filters applied)
- **Count**: Number of matching DeltaFiles on that Fleet Member
- **Tags**: Fleet Member tags for identification

The summary bar shows the total count across all Fleet Members and search status.

### Filtering Results

Use the search box to filter results by Fleet Member name or tags.

## Fleet Errors

The **Fleet Errors** page enables viewing and managing errors across all Fleet Member systems from a single interface.

### Error Counts

The main view shows error counts per Fleet Member:
- **Member**: Fleet Member name (click to open that Fleet Member's errors page with the same filters applied)
- **Errors**: Count of matching errors on that Fleet Member
- **Tags**: Fleet Member tags for identification
- **Status**: Connection state (Connected, Stale, or Unreachable)

### Summary Views

Three tabs provide different perspectives on fleet-wide errors:

- **By Member**: Error counts grouped by Fleet Member system
- **By Flow**: Error counts aggregated by data source/flow across all Fleet Members
- **By Message**: Error counts aggregated by error message across all Fleet Members

The summary views show:
- **Key**: Flow name or error message
- **Errors**: Total count across all Fleet Members
- **Members**: Number of Fleet Members reporting this error type

### Filters

Filter errors across the fleet using:
- **Members**: Select specific Fleet Members to include
- **Tags**: Filter by Fleet Member tags (e.g., "production", "east")
- **Data Sources**: Filter by data source/flow
- **Error Cause**: Search for errors containing specific text
- **Acknowledgement**: Show all, unacknowledged only, or acknowledged only
- **Time Range**: Filter by modification time

### Bulk Actions

Select Fleet Members (By Member tab) or error groups (By Flow/By Message tabs) to perform bulk actions:

- **Acknowledge**: Mark errors as acknowledged with a reason
- **Resume**: Retry the failed actions
- **Replay**: Create new copies of the errored DeltaFiles

When replaying, an option to acknowledge the original errors is available.

### Action Progress

Actions execute in parallel across up to 5 Fleet Members simultaneously. A progress dialog shows:
- Overall progress across all selected Fleet Members
- Per-Fleet Member progress bars with status indicators
- Stop button to cancel remaining operations

After completion, refresh the page to see updated error counts.

## Connection States

| State | Description |
|-------|-------------|
| **Connected** | Successfully retrieved current data from Fleet Member |
| **Stale** | Using cached data; Fleet Member was recently unreachable |
| **Unreachable** | Cannot connect to Fleet Member; no cached data available |

Stale data is shown with a warning indicator and timestamp of the last successful update.

## Network Requirements

The Fleet Leader must be able to reach each Fleet Member's REST API endpoints (`/api/v2/*`). Ensure:
- Network policies allow Fleet Leader → Fleet Member connections
- Firewalls permit traffic on the configured ports
- TLS certificates are valid if using HTTPS
