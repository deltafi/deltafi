# DeltaFi TUI (Text User Interface)

DeltaFi TUI is a command-line interface for managing the DeltaFi data transformation and enrichment platform. It provides administrators and developers with control over DeltaFi system operations, from cluster management to flow orchestration and monitoring.

## Overview

DeltaFi TUI serves as the primary management interface for DeltaFi deployments, offering:

- **System Orchestration**: Complete lifecycle management of DeltaFi clusters including startup, shutdown, upgrades, and configuration
- **Flow Management**: Comprehensive control over data sources, transforms, data sinks, and plugins with real-time monitoring
- **Data Operations**: Advanced DeltaFile management including search, filtering, and bulk operations
- **System Administration**: User management, property configuration, and system health monitoring
- **Development Tools**: Plugin development support, debugging capabilities, and testing utilities

This document provides comprehensive documentation for all available commands, subcommands, and their usage, including every flag and option. Whether you're a system administrator managing production deployments or a developer working with DeltaFi flows, this guide covers all aspects of the TUI interface.

## Key Features

- **Interactive TUI Components**: Rich terminal-based interfaces for complex operations like search and dashboard monitoring
- **Comprehensive Command Coverage**: Every DeltaFi operation accessible via command line
- **Advanced Filtering and Search**: Powerful DeltaFile search capabilities with extensive filtering options
- **Real-time Monitoring**: Live system status, metrics, and health monitoring
- **Plugin Ecosystem Support**: Full plugin lifecycle management from installation to development
- **Multi-environment Support**: Works with Compose, Kubernetes, and KinD deployments

## Table of Contents
- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [Command Groups](#command-groups)
  - [System Orchestration](#system-orchestration)
    - [up](#up)
    - [down](#down)
    - [status](#status)
    - [config](#config)
  - [DeltaFi System Management](#deltafi-system-management)
    - [event](#event)
      - [list](#event-list)
      - [get](#event-get)
      - [create](#event-create)
    - [snapshot](#snapshot)
      - [list](#snapshot-list)
      - [show](#snapshot-show)
      - [create](#snapshot-create)
      - [delete](#snapshot-delete)
      - [import](#snapshot-import)
      - [restore](#snapshot-restore)
    - [freeze](#freeze)
    - [versions](#versions)
    - [upgrade](#upgrade)
      - [list](#upgrade-list)
      - [upgrade](#upgrade-upgrade)
    - [set-admin-password](#set-admin-password)
    - [delete-policies](#delete-policies)
      - [list](#delete-policies-list)
      - [export](#delete-policies-export)
      - [import](#delete-policies-import)
      - [update](#delete-policies-update)
      - [get](#delete-policies-get)
      - [start](#delete-policies-start)
      - [stop](#delete-policies-stop)
      - [delete](#delete-policies-delete)
    - [properties](#properties)
      - [list](#properties-list)
      - [get](#properties-get)
      - [set](#properties-set)
    - [minio](#minio)
      - [cli](#minio-cli)
      - [exec](#minio-exec)
      - [mc](#minio-mc)
      - [watch](#minio-watch)
    - [valkey](#valkey)
      - [cli](#valkey-cli)
      - [latency](#valkey-latency)
      - [stat](#valkey-stat)
      - [monitor](#valkey-monitor)
  - [DeltaFi Flow Management](#deltafi-flow-management)
    - [system-flow-plans](#system-flow-plans)
      - [export](#system-flow-plans-export)
      - [import](#system-flow-plans-import)
    - [data-source](#data-source)
      - [list](#data-source-list)
      - [get](#data-source-get)
      - [load-rest](#data-source-load-rest)
      - [load-timed](#data-source-load-timed)
      - [start](#data-source-start)
      - [stop](#data-source-stop)
      - [pause](#data-source-pause)
      - [test-mode](#data-source-test-mode)
      - [delete](#data-source-delete)
    - [data-sink](#data-sink)
      - [list](#data-sink-list)
      - [get](#data-sink-get)
      - [load](#data-sink-load)
      - [start](#data-sink-start)
      - [stop](#data-sink-stop)
      - [pause](#data-sink-pause)
      - [test-mode](#data-sink-test-mode)
      - [delete](#data-sink-delete)
    - [transform](#transform)
      - [list](#transform-list)
      - [get](#transform-get)
      - [load](#transform-load)
      - [start](#transform-start)
      - [stop](#transform-stop)
      - [pause](#transform-pause)
      - [validate](#transform-validate)
      - [test-mode](#transform-test-mode)
      - [delete](#transform-delete)
    - [plugin](#plugin)
      - [list](#plugin-list)
      - [install](#plugin-install)
      - [uninstall](#plugin-uninstall)
      - [describe](#plugin-describe)
      - [generate](#plugin-generate)
    - [graph](#graph)
  - [DeltaFiles](#deltafiles)
    - [deltafile](#deltafile)
    - [ingress](#ingress)
    - [search](#search)
  - [Metrics](#metrics)
    - [postgres](#postgres)
      - [cli](#postgres-cli)
      - [eval](#postgres-eval)
    - [dashboard](#dashboard)
  - [Legacy Commands](#legacy-commands)
  - [Other Commands](#other-commands)
    - [docs](#docs)
- [Common Flags](#common-flags)
- [Examples](#examples)

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

#### `up`
Start up or update the DeltaFi cluster.

```bash
deltafi up [--force|-f]
```
- `--force, -f`: Force update. Skip upgrade confirmation prompt.

#### `down`
Take down the DeltaFi cluster. This is a destructive operation and can result in the loss of all persistent data.

```bash
deltafi down [--force|-f] [--destroy|-d]
```
- `--force, -f`: Force down without confirmation.
- `--destroy, -d`: Remove data directory contents after successful down.

#### `status`
Display current health and status for the DeltaFi system.

```bash
deltafi status [--watch|-w]
```
- `--watch, -w`: Watch status updates continuously.

#### `config`
Interactive configuration editor for DeltaFi.

```bash
deltafi config
```

### DeltaFi System Management

#### `event`
Manage events in DeltaFi.

- `list`: List all events
  ```bash
  deltafi event list
  ```
- `get [eventId]`: Get details of a specific event
  ```bash
  deltafi event get <eventId> [--format|-o <json|yaml>]
  ```
- `create [summary]`: Create a new event
  ```bash
  deltafi event create "Summary text" [flags]
  ```
  Flags:
  - `--quiet, -q`: Quiet mode, no event output
  - `--source, -s <source>`: Set event source (default 'cli')
  - `--content, -c <content>`: Set event content
  - `--level, -l <level>`: Set event severity (warn, error, info, success)
  - `--severity <level>`: Equivalent to --level
  - `--notification, -n`: Set the notification flag
  - `--warn`: Set severity to warn
  - `--error`: Set severity to error
  - `--success`: Set severity to success

#### `snapshot`
Manage system snapshots.

- `list`: List all system snapshots
  ```bash
  deltafi snapshot list [--plain|-p]
  ```
- `show [id]`: Show detailed information about a specific snapshot
  ```bash
  deltafi snapshot show <id> [--format|-o <json|yaml>]
  ```
- `create`: Create a new system snapshot
  ```bash
  deltafi snapshot create [--reason|-r <reason>] [--format|-o <json|yaml>]
  ```
- `delete [id]`: Delete a specific snapshot
  ```bash
  deltafi snapshot delete <id>
  ```
- `import [file]`: Import a snapshot from file or stdin
  ```bash
  deltafi snapshot import [file] [--reason|-r <reason>] [--format|-o <json|yaml>]
  ```
- `restore [id]`: Restore a snapshot
  ```bash
  deltafi snapshot restore <id> [--hard|-H]
  ```
  - `--hard, -H`: Perform a hard reset (may be more disruptive but more thorough)

#### `freeze`
Interactive wizard to create a preconfigured DeltaFi system distribution.

```bash
deltafi freeze
```

#### `versions`
List version information for all running containers.

```bash
deltafi versions [--brief] [--plain]
```
- `--brief`: Brief output omitting container name
- `--plain`: Plain output without table borders

#### `upgrade`
Upgrade DeltaFi system to a specific version.

- `list`: List available newer versions
  ```bash
  deltafi upgrade list
  ```
- `upgrade [version]`: Upgrade to a specific version
  ```bash
  deltafi upgrade <version>
  ```

#### `set-admin-password`
Set the admin password for the DeltaFi system.

```bash
deltafi set-admin-password [password] [--verbose|-v]
```
- `--verbose, -v`: Show detailed response information

#### `delete-policies`
Manage the delete policies in DeltaFi.

- `list`: List the delete policies
  ```bash
  deltafi delete-policies list [--plain|-p]
  ```
- `export`: Export delete policies from DeltaFi
  ```bash
  deltafi delete-policies export [--format|-o <json|yaml>]
  ```
- `import`: Import delete policies to DeltaFi
  ```bash
  deltafi delete-policies import --file|-f <file> [--replace-all|-r] [--format|-o <json|yaml>]
  ```
- `update`: Update a delete policy
  ```bash
  deltafi delete-policies update --file|-f <file>
  ```
- `get [policyName]`: Get a delete policy
  ```bash
  deltafi delete-policies get <policyName> [--format|-o <json|yaml>]
  ```
- `start [policyName]`: Start a delete policy
  ```bash
  deltafi delete-policies start <policyName>
  ```
- `stop [policyName]`: Stop a delete policy
  ```bash
  deltafi delete-policies stop <policyName>
  ```
- `delete [policyName]`: Remove a delete policy
  ```bash
  deltafi delete-policies delete <policyName>
  ```

#### `properties`
Manage DeltaFi system properties.

- `list`: List system properties
  ```bash
  deltafi properties list [--plain|-p] [--verbose|-v]
  ```
- `get [key]`: Get a system property
  ```bash
  deltafi properties get <key>
  ```
- `set [key] [value]`: Set a system property
  ```bash
  deltafi properties set <key> <value>
  ```

#### `minio`
Command line access to the running DeltaFi MinIO instance.

- `cli`: Start an interactive MinIO TUI session
  ```bash
  deltafi minio cli
  ```
- `exec`: Execute MinIO TUI commands
  ```bash
  deltafi minio exec <commands...>
  ```
- `mc`: Execute a MinIO TUI command
  ```bash
  deltafi minio mc <command>
  ```
- `watch`: Watch a MinIO bucket
  ```bash
  deltafi minio watch [bucket]
  ```

#### `valkey`
Command line access to the running DeltaFi Valkey instance.

- `cli`: Start an interactive Valkey TUI session
  ```bash
  deltafi valkey cli
  ```
- `latency`: Start the Valkey TUI latency command
  ```bash
  deltafi valkey latency
  ```
- `stat`: Start the Valkey TUI stat command
  ```bash
  deltafi valkey stat
  ```
- `monitor`: Start the Valkey TUI monitor command
  ```bash
  deltafi valkey monitor
  ```

### DeltaFi Flow Management

#### `system-flow-plans`
Import and export system flow plans.

- `export`: Export all system flow plans
  ```bash
  deltafi system-flow-plans export [--format|-o <json|yaml>]
  ```
- `import`: Import system flow plans from a file
  ```bash
  deltafi system-flow-plans import --file|-f <file> [--format|-o <json|yaml>]
  ```

#### `data-source`
Manage data sources in DeltaFi.

- `list`: List all data sources
  ```bash
  deltafi data-source list
  ```
- `get [name]`: Get details of a specific data source
  ```bash
  deltafi data-source get <name> [--format|-o <json|yaml>]
  ```
- `load-rest`: Create or update a REST data source
  ```bash
  deltafi data-source load-rest --file|-f <file>
  ```
- `load-timed`: Create or update a timed data source
  ```bash
  deltafi data-source load-timed --file|-f <file>
  ```
- `start [names...]`: Start one or more data sources
  ```bash
  deltafi data-source start <names...> [--all] [--all-actions|-a]
  ```
- `stop [names...]`: Stop one or more data sources
  ```bash
  deltafi data-source stop <names...> [--all] [--all-actions|-a]
  ```
- `pause [name]`: Pause a data source
  ```bash
  deltafi data-source pause <name> [--all-actions|-a]
  ```
- `test-mode [name]`: Enable or disable test mode
  ```bash
  deltafi data-source test-mode <name> --enable|--disable
  ```
- `delete [name]`: Delete a data source
  ```bash
  deltafi data-source delete <name>
  ```

#### `data-sink`
Manage data sinks in DeltaFi.

- `list`: List all data sinks
  ```bash
  deltafi data-sink list
  ```
- `get [name]`: Get details of a specific data sink
  ```bash
  deltafi data-sink get <name>
  ```
- `load`: Create or update a data sink
  ```bash
  deltafi data-sink load --file|-f <file>
  ```
- `start [names...]`: Start one or more data sinks
  ```bash
  deltafi data-sink start <names...> [--all]
  ```
- `stop [names...]`: Stop one or more data sinks
  ```bash
  deltafi data-sink stop <names...> [--all]
  ```
- `pause [name]`: Pause a data sink
  ```bash
  deltafi data-sink pause <name>
  ```
- `test-mode [name]`: Enable or disable test mode
  ```bash
  deltafi data-sink test-mode <name> --enable|--disable
  ```
- `delete [name]`: Delete a data sink
  ```bash
  deltafi data-sink delete <name>
  ```

#### `transform`
Manage transform flows in DeltaFi.

- `list`: List all transform flows
  ```bash
  deltafi transform list [--plain|-p]
  ```
- `get [name]`: Get details of a specific transform
  ```bash
  deltafi transform get <name> [--format|-o <json|yaml>]
  ```
- `load`: Create or update a transform
  ```bash
  deltafi transform load --file|-f <file>
  ```
- `start [names...]`: Start one or more transforms
  ```bash
  deltafi transform start <names...> [--all]
  ```
- `stop [names...]`: Stop one or more transforms
  ```bash
  deltafi transform stop <names...> [--all]
  ```
- `pause [name]`: Pause a transform
  ```bash
  deltafi transform pause <name>
  ```
- `validate [name]`: Validate a transform flow
  ```bash
  deltafi transform validate <name> [--format|-o <json|yaml>]
  ```
- `test-mode [name]`: Enable or disable test mode
  ```bash
  deltafi transform test-mode <name> --enable|-y|--disable|-n
  ```
- `delete [name]`: Delete a transform
  ```bash
  deltafi transform delete <name>
  ```

#### `plugin`
Manage plugins in DeltaFi.

- `list`: List all plugins
  ```bash
  deltafi plugin list [--plain|-p]
  ```
- `install [images...]`: Install one or more plugins
  ```bash
  deltafi plugin install <image> [--secret|-s <secret>]
  ```
- `uninstall [names...]`: Uninstall one or more plugins
  ```bash
  deltafi plugin uninstall <name>
  ```
- `describe [pluginName]`: Describe a plugin and its actions
  ```bash
  deltafi plugin describe <pluginName> [--verbose|-v] [--action|-a <actionName>]
  ```
- `generate`: Generate a new plugin
  ```bash
  deltafi plugin generate [--zip|-z <filename>]
  ```

#### `graph`
Display a graph of data flow paths.

```bash
deltafi graph [flowNames...] [--all]
```
- `--all`: Show all flows

### DeltaFiles

#### `deltafile`
View detailed information about a DeltaFile, including its flows, actions, and status.

```bash
deltafi deltafile <did>
```

#### `ingress`
Ingress files into the DeltaFi system.

```bash
deltafi ingress <files...> --datasource|-d <source> [--content-type|-c <type>] [--plain|-p] [--verbose|-v] [--watch|-w]
```
- `--datasource, -d`: Data source for the ingressed files (required)
- `--content-type, -c`: Content type for the ingressed files (default: application/octet-stream)
- `--plain, -p`: Output only UUIDs, one per line
- `--verbose, -v`: Show detailed deltafile information for each uploaded file
- `--watch, -w`: Wait for each deltafile to complete before showing results

#### `search`
Search and filter DeltaFiles with a rich set of criteria. Opens an interactive TUI for browsing results.

```bash
deltafi search [flags]
```
Flags:
- `--from <time>`: Display DeltaFiles modified after this time (default: today)
- `--to <time>`: Display DeltaFiles modified before this time (default: now)
- `--until <time>`: Alias for --to
- `--creation-time, -C`: Filter by creation time instead of modification time
- `--local`: Display times in local timezone
- `--zulu`: Display times in UTC/Zulu timezone
- `--ascending`: Sort results in ascending order
- `--descending`: Sort results in descending order
- `--sort-by <column>`: Column to sort by (modified, filename, data-source, stage, size)
- `--humanize`: Display timestamps in human-readable format
- `--data-source, -d <name>`: Filter by data source name (can be specified multiple times)
- `--transform, -t <name>`: Filter by transform name (can be specified multiple times)
- `--data-sink, -x <name>`: Filter by data sink name (can be specified multiple times)
- `--annotation <key=value>`: Filter by annotation (can be specified multiple times)
- `--stage, -s <stage>`: Filter by stage (IN_FLIGHT, COMPLETE, ERROR, CANCELLED)
- `--topics <topic>`: Filter by topic name (can be specified multiple times)
- `--name, -n <name>`: Filter by DeltaFile name
- `--filtered-cause <cause>`: Filter by filtered cause
- `--error-cause <cause>`: Filter by error cause
- `--did <id>`: Filter by DeltaFile ID (can be specified multiple times)
- `--requeue-count-min <int>`: Minimum requeue count
- `--ingress-bytes-min <int>`: Minimum ingress bytes
- `--ingress-bytes-max <int>`: Maximum ingress bytes
- `--referenced-bytes-min <int>`: Minimum referenced bytes
- `--referenced-bytes-max <int>`: Maximum referenced bytes
- `--total-bytes-min <int>`: Minimum total bytes
- `--total-bytes-max <int>`: Maximum total bytes
- `--content-deleted <yes|no>`: Filter by content deleted status
- `--egressed <yes|no>`: Filter by egressed status
- `--error-acknowledged <yes|no>`: Filter by error acknowledged status
- `--filtered <yes|no>`: Filter by filtered status
- `--paused <yes|no>`: Filter by paused status
- `--pending-annotations <yes|no>`: Filter by pending annotations status
- `--pinned <yes|no>`: Filter by pinned status
- `--replayable <yes|no>`: Filter by replayable status
- `--replayed <yes|no>`: Filter by replayed status
- `--terminal-stage <yes|no>`: Filter by terminal stage status
- `--test-mode <yes|no>`: Filter by test mode status

Example:
```bash
deltafi search --data-source my-source --stage COMPLETE --from "2024-01-01" --to "2024-02-01"
```

### Metrics

#### `postgres`
Command line access to the running DeltaFi Postgres instance.

- `cli`: Start an interactive Postgres CLI session
  ```bash
  deltafi postgres cli
  ```
- `eval`: Pipe commands to Postgres from stdin
  ```bash
  cat query.sql | deltafi postgres eval
  deltafi postgres eval < query.sql
  ```

#### `dashboard`
Display system metrics dashboard with real-time updates.

```bash
deltafi dashboard [--interval|-n <seconds>]
```
- `--interval, -n`: Refresh interval in seconds (default: 5)

### Legacy Commands

These commands are deprecated but maintained for backward compatibility:

- `cli`: Invoke the original DeltaFi CLI commands
- `cluster`: Invoke the original DeltaFi KinD cluster controls
- `compose`: Invoke the original DeltaFi compose controls

### Other Commands

#### `docs`
DeltaFi documentation browser.

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
