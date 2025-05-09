# Deltafi TUI (Text User Interface)

Deltafi TUI is a command-line interface for managing the Deltafi data transformation and enrichment platform. This document provides comprehensive documentation for all available commands and their usage.

## Table of Contents
- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [Command Groups](#command-groups)
  - [System Orchestration](#system-orchestration)
  - [DeltaFi System Management](#deltafi-system-management)
  - [DeltaFi Flow Management](#deltafi-flow-management)
  - [DeltaFiles](#deltafiles)
  - [Metrics](#metrics)
  - [Legacy Commands](#legacy-commands)
  - [Other Commands](#other-commands)

## Installation

[Installation instructions to be added]

## Basic Usage

The basic command structure is:
```bash
deltafi [command] [subcommand] [options] [arguments]
```

To see all available commands:
```bash
deltafi --help
```

To see help for a specific command:
```bash
deltafi [command] --help
```

## Command Groups

### System Orchestration

#### `init`
Interactive wizard to initialize the Deltafi system.

```bash
deltafi init
```

#### `up`
Start up the Deltafi cluster. This command will create or update the DeltaFi cluster with the current configuration.

```bash
deltafi up
```

#### `down`
Take down the Deltafi cluster. This is a destructive operation and will result in the loss of all persistent data.

```bash
deltafi down
# Force down without confirmation
deltafi down --force
# Remove data directory contents after successful down
deltafi down --destroy
```

#### `status`
Display current health and status for the Deltafi system.

```bash
deltafi status
# Watch status updates continuously
deltafi status --watch
```

#### `config`
Interactive configuration editor for Deltafi.

```bash
deltafi config
```

### DeltaFi System Management

#### `event`
Manage events in Deltafi.

Subcommands:
- `list`: List all events
- `get [eventId]`: Get details of a specific event
- `create [summary]`: Create a new event
  - `--source, -s`: Set event source (default 'cli')
  - `--content, -c`: Set event content
  - `--level, -l`: Set event severity (warn, error, info, success)
  - `--notification, -n`: Set the notification flag
  - `--warn`: Set severity to warn
  - `--error`: Set severity to error
  - `--success`: Set severity to success

#### `snapshot`
Manage system snapshots.

Subcommands:
- `list`: List all system snapshots
  - `--plain, -p`: Plain output, omitting table borders
- `show [id]`: Show detailed information about a specific snapshot
  - `--format, -o`: Output format (json|yaml)
- `create`: Create a new system snapshot
  - `--reason, -r`: Reason for creating the snapshot
  - `--format, -o`: Output format (json|yaml)
- `delete [id]`: Delete a specific snapshot
- `import`: Import a snapshot
  - `--reason, -r`: Reason for importing the snapshot
  - `--format, -o`: Output format (json|yaml)
- `restore`: Restore a snapshot
  - `--hard, -H`: Perform a hard reset (may be more disruptive but more thorough)

#### `freeze`
Interactive wizard to create a preconfigured Deltafi system distribution.

```bash
deltafi freeze
```

#### `versions`
List version information for all running containers.

```bash
deltafi versions
# Brief output omitting container name
deltafi versions --brief
# Plain output without table borders
deltafi versions --plain
```

### DeltaFi Flow Management

#### `system-flow-plans`
Import and export system flow plans.

Subcommands:
- `export`: Export all system flow plans
  - `--format, -o`: Output format (json|yaml)
- `import`: Import system flow plans from a file
  - `--file, -f`: Path to configuration file (JSON or YAML)
  - `--format, -o`: Output format (json|yaml)

#### `data-source`
Manage data sources in Deltafi.

Subcommands:
- `list`: List all data sources
- `get [name]`: Get details of a specific data source
- `load-rest`: Create or update a REST data source
  - `--file, -f`: Path to configuration file (JSON or YAML)
- `load-timed`: Create or update a timed data source
  - `--file, -f`: Path to configuration file (JSON or YAML)
- `start [names...]`: Start one or more data sources
  - `--all`: Start all data sources
- `stop [names...]`: Stop one or more data sources
  - `--all`: Stop all data sources
- `pause [name]`: Pause a data source
- `test-mode [name]`: Enable or disable test mode
  - `--enable`: Enable test mode
  - `--disable`: Disable test mode
- `delete [name]`: Delete a data source

#### `data-sink`
Manage data sinks in Deltafi.

Subcommands:
- `list`: List all data sinks
- `get [name]`: Get details of a specific data sink
- `load`: Create or update a data sink
  - `--file, -f`: Path to configuration file (JSON or YAML)
- `start [names...]`: Start one or more data sinks
  - `--all`: Start all data sinks
- `stop [names...]`: Stop one or more data sinks
  - `--all`: Stop all data sinks
- `pause [name]`: Pause a data sink
- `test-mode [name]`: Enable or disable test mode
  - `--enable`: Enable test mode
  - `--disable`: Disable test mode
- `delete [name]`: Delete a data sink

#### `transform`
Manage transform flows in Deltafi.

Subcommands:
- `list`: List all transform flows
  - `--plain, -p`: Plain output, omitting table borders
- `get [name]`: Get details of a specific transform
  - `--format, -o`: Output format (json|yaml)
- `load`: Create or update a transform
  - `--file, -f`: Path to configuration file (JSON or YAML)
- `start [names...]`: Start one or more transforms
  - `--all`: Start all transforms
- `stop [names...]`: Stop one or more transforms
  - `--all`: Stop all transforms
- `pause [name]`: Pause a transform
- `validate [name]`: Validate a transform flow
  - `--format, -o`: Output format (json|yaml)
- `test-mode [name]`: Enable or disable test mode
  - `--enable, -y`: Enable test mode
  - `--disable, -n`: Disable test mode
- `delete [name]`: Delete a transform

#### `plugin`
Manage plugins in Deltafi.

Subcommands:
- `list`: List all plugins
  - `--plain, -p`: Plain output, omitting table borders
- `install [names...]`: Install one or more plugins
  - `--secret, -s`: Image pull secret (optional)
- `uninstall [names...]`: Uninstall one or more plugins

#### `graph`
Display a graph of data flow paths.

```bash
deltafi graph [flowNames...]
# Show all flows
deltafi graph --all
```

### DeltaFiles

#### `deltafile`
View detailed information about a DeltaFile, including its flows, actions, and status.

```bash
deltafi deltafile [did]
```

The command displays:
- Basic Information (DID, Name, Data Source, Stage, Created/Modified timestamps, Bytes)
- Status Flags (Content Deleted, Pinned, Egressed, Filtered, Paused)
- Annotations
- Child and Parent DeltaFiles
- Flow Tree
- Content Tags

#### `ingress`
Ingress files into the Deltafi system.

```bash
deltafi ingress [files...]
# Required flags:
  --datasource, -d: Data source for the ingressed files
# Optional flags:
  --content-type, -c: Content type for the ingressed files (default: application/octet-stream)
  --plain, -p: Output only UUIDs, one per line
  --verbose, -v: Show detailed deltafile information for each uploaded file
  --watch, -w: Wait for each deltafile to complete before showing results
```

### Metrics

#### `postgres`
Command line access to the running Deltafi Postgres instance.

Subcommands:
- `cli`: Start an interactive Postgres CLI session
- `eval`: Pipe commands to Postgres from stdin
  ```bash
  cat query.sql | deltafi postgres eval
  deltafi postgres eval < query.sql
  ```

### Legacy Commands

These commands are deprecated but maintained for backward compatibility:

- `cli`: Invoke the original Deltafi CLI commands
- `cluster`: Invoke the original Deltafi KinD cluster controls
- `compose`: Invoke the original Deltafi compose controls

### Other Commands

#### `docs`
Deltafi documentation browser.

```bash
deltafi docs
```

## Common Flags

Many commands support the following common flags:

- `--format, -o`: Output format (json|yaml)
- `--file, -f`: Path to configuration file (JSON or YAML)
- `--plain, -p`: Plain output, omitting table borders

## Examples

### Creating a New Event
```bash
deltafi event create "System update completed" --level success --notification
```

### Starting All Data Sources
```bash
deltafi data-source start --all
```

### Viewing Transform Details
```bash
deltafi transform get my-transform --format yaml
```

### Creating a System Snapshot
```bash
deltafi snapshot create --reason "Pre-upgrade backup"
```

### Viewing Flow Graph
```bash
deltafi graph my-data-source
``` 
