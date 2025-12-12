# Operator's Guide

This guide is for operators who want to install, run, and manage a DeltaFi system.

## Prerequisites

- Docker Desktop with at least 4 cores and 8GB RAM allocated
- Git

## Setup

### 1. Install DeltaFi

Follow the [Quick Start](/getting-started/quick-start) to install DeltaFi. This installs the **DeltaFi TUI** (Terminal User Interface) - a command-line tool that manages the entire DeltaFi system. See [Understanding the TUI](/operating/TUI#understanding-the-tui) for how it works, or read on for the basics.

When the installation wizard asks about your role, select **Deployment**. When asked about orchestration mode, select **Compose** (recommended for most users).

This will set up a DeltaFi environment using Docker Compose.

### 2. Verify the Installation

After setup completes:

```bash
deltafi status
```

The DeltaFi UI will be available at [http://local.deltafi.org](http://local.deltafi.org).

## Essential TUI Commands

The `deltafi` command-line interface (TUI) is your primary tool for managing DeltaFi. The TUI serves two purposes:

1. **Orchestration**: Installing, upgrading, starting, and stopping DeltaFi
2. **Runtime**: Interacting with the running system (search, ingress, flows)

### System Lifecycle

| Command | Description |
|---------|-------------|
| `deltafi up` | Start DeltaFi |
| `deltafi down` | Stop DeltaFi (keeps data) |
| `deltafi down --destroy` | Stop and remove all data |
| `deltafi status` | Show system health |
| `deltafi status --watch` | Continuous status monitoring |
| `deltafi config` | Interactive configuration wizard |

### Upgrades

```bash
# List available versions
deltafi upgrade list

# View changelog for a version
deltafi upgrade changelog 2.39.0

# Upgrade to a specific version
deltafi upgrade 2.39.0

# Safe upgrade (manages ingress during upgrade)
deltafi upgrade 2.39.0 --safe
```

The `--safe` flag pauses ingress during the upgrade and shows a dashboard for monitoring progress.

### Snapshots & Backup

```bash
# List snapshots
deltafi snapshot list

# Create a snapshot
deltafi snapshot create

# Restore from a snapshot
deltafi snapshot restore <snapshot-id>
```

## Managing Data Flows

### Data Sources, Transforms, and Sinks

```bash
# List flows
deltafi data-source list
deltafi transform list
deltafi data-sink list

# Start/stop flows
deltafi data-source start <name>
deltafi data-source stop <name>

# View flow graph
deltafi graph data-source         # All data sources
deltafi graph <flow-name>         # Specific flow
deltafi graph --all               # Entire system
```

### Ingesting Data

```bash
# Ingress a file
deltafi ingress -d <data-source> <file>

# Ingress with watch mode
deltafi ingress -d <data-source> <file> --watch
```

## Monitoring & Troubleshooting

### Searching DeltaFiles

```bash
# Interactive search
deltafi search

# Filter by data source
deltafi search --data-source <name>

# Filter by time range
deltafi search --from "2024-01-01" --to "2024-01-02"

# Filter by stage
deltafi search --stage ERROR
deltafi search --stage COMPLETE
```

### Error Handling

```bash
# View errored DeltaFiles
deltafi errored view
deltafi errored list --all

# View filtered DeltaFiles
deltafi filtered view
```

### Dashboard

```bash
# Real-time metrics dashboard
deltafi dashboard

# Custom refresh interval
deltafi dashboard --interval 10
```

### System Properties

```bash
# List all properties
deltafi properties list

# Get a specific property
deltafi properties get <name>

# Set a property
deltafi properties set <name> <value>

# Interactive property viewer
deltafi properties view
```

### Database Access

```bash
# PostgreSQL CLI
deltafi postgres cli

# PostgreSQL status (metrics, connections, table sizes)
deltafi postgres status

# View database migrations
deltafi postgres migrations
```

## Topic Analysis

Use topic commands to understand data flow paths:

```bash
# List all topics
deltafi topic list

# See what flows publish/subscribe to a topic
deltafi topic flows <topic-name>

# View downstream flow path
deltafi topic downstream <topic-name>

# View upstream flow path
deltafi topic upstream <topic-name>

# Combined view
deltafi topic graph <topic-name>
```

## Integration Testing

```bash
# Run integration tests
deltafi integration-test run

# Run specific tests
deltafi integration-test run <test-name>

# Run tests matching a pattern
deltafi integration-test run --like "smoke"
```

## Plugin Management

```bash
# List installed plugins
deltafi plugin list

# Install a plugin
deltafi plugin install <image>

# Uninstall a plugin
deltafi plugin uninstall <name>

# Generate a new plugin project
deltafi plugin generate --java my-plugin
deltafi plugin generate --python my-plugin
```

## Reference

- [TUI Reference](/operating/TUI) - Complete command documentation
- [Site Configuration](/operating/site-configuration) - Customizing your deployment
- [Error Handling](/operating/errors) - Resume, replay, and acknowledge
- [Metrics](/operating/metrics) - Grafana dashboards and metrics
- [Configuration](/operating/configuration) - System configuration options
