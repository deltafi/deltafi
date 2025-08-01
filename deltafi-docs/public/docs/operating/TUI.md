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
    - [kind](#kind)
      - [up](#kind-up)
      - [down](#kind-down)
      - [destroy](#kind-destroy)
      - [shell](#kind-shell)
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
      - [changelog](#upgrade-changelog)
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
      - [load-on-error](#data-source-load-on-error)
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
    - [topic](#topic)
  - [DeltaFiles](#deltafiles)
    - [deltafile](#deltafile)
    - [ingress](#ingress)
    - [search](#search)
    - [deltafiles](#deltafiles)
      - [view](#deltafiles-view)
      - [list](#deltafiles-list)
    - [errored](#errored)
      - [view](#errored-view)
      - [list](#errored-list)
    - [filtered](#filtered)
      - [view](#filtered-view)
      - [list](#filtered-list)
  - [Testing](#testing)
    - [integration-test](#integration-test)
      - [list](#integration-test-list)
      - [load](#integration-test-load)
      - [run](#integration-test-run)
      - [summary](#integration-test-summary)
      - [clear](#integration-test-clear)
      - [remove](#integration-test-remove)
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
Interactive configuration editor for DeltaFi, or set orchestration and deployment modes directly.

```bash
deltafi config [--compose|--kind|--kubernetes] [--deployment|--plugin-development|--core-development] [--force]
```

**Flags:**
- `--compose`                Set orchestration mode to Compose
- `--kind`                   Set orchestration mode to KinD
- `--kubernetes`             Set orchestration mode to Kubernetes
- `--deployment`             Set deployment mode to Deployment
- `--plugin-development`     Set deployment mode to PluginDevelopment
- `--core-development`       Set deployment mode to CoreDevelopment
- `--force`                  Force configuration changes without confirmation

If any of these flags are set, the configuration will be updated and saved immediately, and the wizard will not run. You can set both orchestration and deployment mode in a single invocation. If you change orchestration mode while DeltaFi is running, you will be prompted to confirm (unless --force is used), and the system will be stopped automatically.

**Examples:**
```bash
# Set orchestration mode to Kubernetes and deployment mode to CoreDevelopment
deltafi config --kubernetes --core-development

# Set orchestration mode to Compose only
deltafi config --compose

# Set deployment mode to PluginDevelopment only
deltafi config --plugin-development

# Force orchestration mode change without confirmation
deltafi config --kind --force
```

#### `kind`
Manage the KinD (Kubernetes in Docker) cluster.

**⚠️ Important**: This command is only available when the orchestration mode is set to KinD. It provides subcommands to start and stop the KinD cluster.

```bash
deltafi kind [subcommand]
```

**Subcommands:**

- `up`: Start the KinD cluster
  ```bash
  deltafi kind up
  ```
  This command will:
  - Create and start a new KinD cluster if none exists
  - Configure the cluster for DeltaFi deployment
  - Set up necessary prerequisites

- `down`: Stop and destroy the KinD cluster
  ```bash
  deltafi kind down
  ```
  This is a destructive operation that will:
  - Stop all services running in the KinD cluster
  - Destroy the cluster and all its resources
  - Remove all persistent data
  
  **WARNING**: This will permanently delete all data in the KinD cluster.

- `destroy`: Destroy the KinD cluster
  ```bash
  deltafi kind destroy
  ```
  This is a destructive operation that does everything `down` does plus:
  - Removes images
  - Optionally removes all registries
  - Stop all services running in the KinD cluster
  - Destroy the cluster and all its resources
  - Remove all persistent data
  
  **WARNING**: This will permanently delete all data in the KinD cluster.

- `shell`: Open an interactive shell in the KinD control plane
  ```bash
  deltafi kind shell
  ```
  This command will:
  - Connect to the KinD control plane container
  - Open a tmux session in the `/usr/dev` directory
  - Provide an interactive development environment
  
  Use `Ctrl+B` then `D` to detach from the tmux session.

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
- `changelog [version]`: Show changelog for a specific version or all newer versions
  ```bash
  # Show changelog for a specific version
  deltafi upgrade changelog <version>
  
  # Show changelogs for all newer versions (newest to oldest)
  deltafi upgrade changelog
  ```
- `upgrade [version]`: Upgrade to a specific version
  ```bash
  deltafi upgrade <version>
  ```

**Changelog Command Details:**

The `changelog` command retrieves and displays changelog information from the DeltaFi container registry. It fetches the changelog content from the `org.opencontainers.image.description` annotation in the container manifest and renders it as formatted markdown.

**Features:**
- **Single Version**: Display changelog for a specific version
- **All Newer Versions**: When no version is specified, shows changelogs for all versions newer than the currently installed version
- **Markdown Rendering**: Beautiful terminal formatting with headers, bullet points, and color coding
- **Flexible Version Support**: Works with semantic versions (e.g., "2.25.0") and tags (e.g., "latest")
- **Error Handling**: Graceful handling of missing versions or changelogs

**Examples:**
```bash
# View changelog for a specific version
deltafi upgrade changelog 2.25.0

# View changelogs for all newer versions (newest first)
deltafi upgrade changelog

# View changelog for latest tag
deltafi upgrade changelog latest
```

**Output Format:**
The changelog is rendered with:
- Color-coded headers and sections
- Proper bullet point formatting
- Terminal-optimized width and wrapping
- Clear separation between multiple changelogs when viewing all versions

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
- `view`: Interactive properties viewer and editor
  ```bash
  deltafi properties view
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

#### `graphql`
Execute GraphQL queries against the DeltaFi GraphQL endpoint.

```bash
deltafi graphql [query] [--file|-f <file>] [--plain]
```

The query can be provided in several ways:
- **Command line argument**: Direct query string
- **Interactive mode**: No arguments - type or paste to stdin, then press Ctrl+D to submit
- **From stdin**: Use `-` for the query argument
- **From file**: Use `@filename` or `--file filename` for the query argument

Flags:
- `--file, -f <file>`: Read query from specified file
- `--plain`: Output plain JSON without syntax coloring

Examples:
```bash
# Execute a simple query
deltafi graphql "query { version }"

# Read query from stdin
deltafi graphql - < query.graphql

# Pipe query to command
cat query.graphql | deltafi graphql -

# Read query from file using @ prefix
deltafi graphql @query.graphql

# Read query from file using --file flag
deltafi graphql --file query.graphql

# Interactive mode - type query and press Ctrl+D
deltafi graphql
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
- `load-on-error`: Create or update an on-error data source
  ```bash
  deltafi data-source load-on-error --file|-f <file>
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

#### `topic`
Manage and inspect publish/subscribe topics in DeltaFi.

Topics are used for communication between different components in the DeltaFi system. Publishers send data to topics, and subscribers receive data from topics.

- `list`: List all topics with their participant counts
  ```bash
  deltafi topic list [--json|-j] [--plain|-p]
  ```
  - `--json, -j`: Output in JSON format
  - `--plain, -p`: Plain output format

- `flows [topic-name]`: Show all flows (both publishers and subscribers) that use a specific topic
  ```bash
  deltafi topic flows <topic-name> [--json|-j] [--plain|-p]
  ```
  - `--json, -j`: Output in JSON format
  - `--plain, -p`: Plain output format

- `downstream [topic-name]`: Show downstream flows for a topic in a tree format
  ```bash
  deltafi topic downstream <topic-name>
  ```

- `upstream [topic-name]`: Show all upstream flow paths to a topic (all possible data source paths that can reach the topic)
  ```bash
  deltafi topic upstream <topic-name>
  ```

- `graph [topic-name]`: Show both upstream and downstream flow graphs for a topic, with clear visual separation
  ```bash
  deltafi topic graph <topic-name>
  ```
  This command displays all possible paths from data sources to the specified topic (upstream), and all flows downstream of the topic. Each section is visually separated and uses a bordered frame for clarity.

- `data-sources [topic-name]`: List all data sources that could send data to a topic
  ```bash
  deltafi topic data-sources <topic-name> [--json|-j] [--plain|-p]
  ```
  - `--json, -j`: Output in JSON format
  - `--plain, -p`: Output just the data source names, one per line (no table, no header)

**Examples:**
```bash
# List all topics
deltafi topic list

# Show flows using a specific topic
deltafi topic flows my-topic

# Show downstream flows for a topic
deltafi topic downstream my-topic

# Show all upstream flow paths to a topic
deltafi topic upstream my-topic

# Show both upstream and downstream graphs for a topic
deltafi topic graph my-topic

# List all data sources that could send data to a topic
deltafi topic data-sources my-topic
deltafi topic data-sources my-topic --plain
deltafi topic data-sources my-topic --json
```

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
Search and filter DeltaFiles with a rich set of criteria. Opens an interactive TUI for browsing results with real-time updates and advanced navigation.

```bash
deltafi search [flags]
```

**Interactive Features:**
- **Real-time Navigation**: Use arrow keys, page up/down, or mouse wheel to navigate results
- **Sorting**: Press Tab/Shift+Tab to cycle through sort columns, 'o' to toggle sort direction
- **Auto-refresh**: Use `--auto-refresh` to automatically update results every N seconds
- **Detailed View**: Press Enter to view full DeltaFile details in a modal
- **Help**: Press 'h' to view keyboard shortcuts and navigation help

**Search Flags:**
- `--from <time>`: Display DeltaFiles modified after this time (default: today)
- `--to <time>`: Display DeltaFiles modified before this time (default: now)
- `--until <time>`: Alias for --to
- `--creation-time, -C`: Filter by creation time instead of modification time
- `--local`: Display times in local timezone
- `--zulu`: Display times in UTC/Zulu timezone
- `--ascending`: Sort results in ascending order
- `--descending`: Sort results in descending order
- `--sort-by <column>`: Column to sort by (modified, filename, data-source, stage, size)
- `--auto-refresh <seconds>`: Automatically refresh results every N seconds (0 to disable)
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

**Navigation Keys:**
- `↑/k`: Move selection up
- `↓/j`: Move selection down
- `pgup`: Previous page
- `pgdown`: Next page
- `g`: Go to start
- `tab`: Cycle to next sort column
- `shift+tab`: Cycle to previous sort column
- `o`: Toggle sort direction
- `enter`: View DeltaFile details
- `r`: Refresh results
- `h`: Show help
- `esc/q`: Quit

**Examples:**
```bash
# Basic search with time range
deltafi search --from "yesterday" --to "now"

# Search with auto-refresh every 30 seconds
deltafi search --data-source my-source --auto-refresh 30

# Search with multiple filters
deltafi search --stage COMPLETE --data-source my-source --from "2024-01-01" --to "2024-02-01"

# Search with size filters
deltafi search --total-bytes-min 1000 --total-bytes-max 10000

# Search with annotations
deltafi search --annotation "priority=high" --annotation "environment=production"
```

#### `deltafiles`
Manage and search DeltaFiles with comprehensive filtering and output options.

```bash
deltafi deltafiles [subcommand] [options]
```

**Subcommands:**

##### `view`
Interactive viewer for DeltaFiles with real-time navigation and filtering.

```bash
deltafi deltafiles view [flags]
```

Provides the same interactive TUI experience as `search` with all filtering options available.

**Examples:**
```bash
# View DeltaFiles with time filtering
deltafi deltafiles view --from "today" --to "now"

# View with auto-refresh
deltafi deltafiles view --auto-refresh 60

# View with specific filters
deltafi deltafiles view --stage COMPLETE --data-source my-source
```

##### `list`
List DeltaFiles in various output formats with comprehensive filtering options.

```bash
deltafi deltafiles list [flags]
```

**Output Format Flags:**
- `--plain, -p`: Show plain table output
- `--json`: Show JSON output
- `--yaml`: Show YAML output
- `--verbose, -v`: Show verbose output (JSON and YAML only)
- `--terse`: Hide table header and footer

**Pagination Flags:**
- `--limit <int>`: Limit the number of results (default: 100)
- `--offset <int>`: Offset the results (default: 0)

**All search flags are available** (same as `search` command)

**Examples:**
```bash
# List recent DeltaFiles
deltafi deltafiles list --limit 10

# List in JSON format
deltafi deltafiles list --json --limit 5

# List with specific filters
deltafi deltafiles list --stage COMPLETE --data-source my-source --limit 20

# List with size filtering
deltafi deltafiles list --total-bytes-min 1000 --total-bytes-max 10000

# List with time range and output format
deltafi deltafiles list --from "yesterday" --to "now" --json --verbose
```

#### `errored`
Manage and search DeltaFile errors with specialized filtering for error conditions.

```bash
deltafi errored [subcommand] [options]
```

**Subcommands:**

##### `view`
Interactive viewer for DeltaFile errors. By default shows only unacknowledged errors.

```bash
deltafi errored view [flags]
```

**Error-specific Flags:**
- `--all`: Show all errors (acknowledged and unacknowledged)
- `--acknowledged`: Show only acknowledged errors

**All search flags are available** (same as `search` command, with stage automatically set to ERROR)

**Examples:**
```bash
# View unacknowledged errors (default)
deltafi errored view

# View all errors including acknowledged
deltafi errored view --all

# View only acknowledged errors
deltafi errored view --acknowledged

# View errors with specific filters
deltafi errored view --error-cause "timeout" --data-source my-source

# View errors with auto-refresh
deltafi errored view --auto-refresh 30
```

##### `list`
List DeltaFile errors in various output formats.

```bash
deltafi errored list [flags]
```

**Output Format Flags:**
- `--plain, -p`: Show plain table output
- `--json`: Show JSON output
- `--yaml`: Show YAML output
- `--verbose, -v`: Show verbose output (JSON and YAML only)
- `--terse`: Hide table header and footer

**Pagination Flags:**
- `--limit <int>`: Limit the number of results (default: 100)
- `--offset <int>`: Offset the results (default: 0)

**Error-specific Flags:**
- `--all`: Show all errors (acknowledged and unacknowledged)
- `--acknowledged`: Show only acknowledged errors

**All search flags are available** (same as `search` command, with stage automatically set to ERROR)

**Examples:**
```bash
# List recent errors
deltafi errored list --limit 10

# List all errors including acknowledged
deltafi errored list --all --limit 20

# List errors in JSON format
deltafi errored list --json --verbose

# List errors with specific filters
deltafi errored list --error-cause "timeout" --data-source my-source

# List errors with time range
deltafi errored list --from "last week" --to "now" --json
```

#### `filtered`
Manage and search filtered DeltaFiles with specialized filtering for filtered conditions.

```bash
deltafi filtered [subcommand] [options]
```

**Subcommands:**

##### `view`
Interactive viewer for filtered DeltaFiles.

```bash
deltafi filtered view [flags]
```

**All search flags are available** (same as `search` command, with filtered automatically set to yes)

**Examples:**
```bash
# View filtered DeltaFiles
deltafi filtered view

# View with specific filters
deltafi filtered view --filtered-cause "validation_failed" --data-source my-source

# View with auto-refresh
deltafi filtered view --auto-refresh 60
```

##### `list`
List filtered DeltaFiles in various output formats.

```bash
deltafi filtered list [flags]
```

**Output Format Flags:**
- `--plain, -p`: Show plain table output
- `--json`: Show JSON output
- `--yaml`: Show YAML output
- `--verbose, -v`: Show verbose output (JSON and YAML only)
- `--terse`: Hide table header and footer

**Pagination Flags:**
- `--limit <int>`: Limit the number of results (default: 100)
- `--offset <int>`: Offset the results (default: 0)

**All search flags are available** (same as `search` command, with filtered automatically set to yes)

**Examples:**
```bash
# List filtered DeltaFiles
deltafi filtered list --limit 10

# List in JSON format
deltafi filtered list --json --verbose

# List with specific filters
deltafi filtered list --filtered-cause "validation_failed" --data-source my-source

# List with time range
deltafi filtered list --from "yesterday" --to "now" --json
```

### Testing

#### `integration-test`
Manage integration tests for DeltaFi flows. Integration tests allow you to test complete data flows by providing test inputs and expected outputs, validating that data sources, transformation flows, and data sinks work together correctly.

```bash
deltafi integration-test [subcommand] [options] [arguments]
```

**Subcommands:**

##### `list`
Show integration tests with their details. If no test names are provided, all tests are shown.

```bash
deltafi integration-test list [test-names...] [--short|-s] [--plain|-p] [--json|-j]
```
- `test-names...`: Optional list of specific test names to display
- `--short, -s`: Show only test names
- `--plain, -p`: Plain output format
- `--json, -j`: Output in JSON format

Examples:
```bash
# List all integration tests
deltafi integration-test list

# List specific tests
deltafi integration-test list smoke-test api-test

# Show only test names
deltafi integration-test list --short

# Output in JSON format
deltafi integration-test list --json
```

##### `load`
Load an integration test from a configuration file. The file should contain integration test settings in JSON format. If an integration test with the same name already exists, it will be replaced.

```bash
deltafi integration-test load <file> [--plain|-p]
```
- `file`: Path to the integration test configuration file (JSON format)
- `--plain, -p`: Plain output format

Examples:
```bash
# Load a single test configuration
deltafi integration-test load test-config.json

# Load multiple test configurations
deltafi integration-test load *.json
```

##### `run`
Run integration tests and wait for results. Starts the specified integration tests and polls for completion with a progress bar showing real-time status updates.

```bash
deltafi integration-test run [test-names...] [--like|-l <pattern>] [--timeout|-t <duration>] [--json|-j] [--plain|-p]
```
- `test-names...`: Optional list of specific test names to run. If not provided, all available tests will be run.
- `--like, -l`: Run tests whose names contain this string (case-insensitive, can be specified multiple times)
- `--timeout, -t`: Timeout for test execution (default: 30m)

**Features:**
- **Two-Phase Progress Bar**: 
  - **Startup Phase**: Shows progress as tests are being started (e.g., "3/5 tests started")
  - **Execution Phase**: Shows progress as tests complete (e.g., "2/5 tests completed")
- **Live Status Table**: Displays real-time status of each running test with color-coded results
- **Pattern Matching**: Use `--like` to run tests matching specific patterns
- **Multiple Patterns**: Combine multiple `--like` flags for OR logic matching
- **Intersection Logic**: When both test names and `--like` patterns are specified, only tests that match both criteria are run
- **Comprehensive Results**: Final summary shows success/failure counts and detailed results table
- **Timeout Handling**: Graceful timeout handling with clear indication of incomplete tests

**Filtering Logic:**
- **No arguments + no `--like`**: Runs all tests
- **Specific test names only**: Runs only the specified tests
- **`--like` pattern only**: Runs all tests whose names contain the pattern (case-insensitive)
- **Both test names + `--like`**: Runs the intersection - only tests that are both explicitly named AND match the pattern

Examples:
```bash
# Run all tests
deltafi integration-test run

# Run specific tests
deltafi integration-test run test1 test2

# Run tests containing "smoke" in their name
deltafi integration-test run --like "smoke"

# Run tests containing "test" OR "integration" in their name
deltafi integration-test run --like "test" --like "integration"

# Run specific tests that also contain "api" in their name (intersection)
deltafi integration-test run test1 test2 --like "api"

# Set custom timeout
deltafi integration-test run --timeout 5m

# Output results in JSON format
deltafi integration-test run --json

# Run tests with pattern matching and custom timeout
deltafi integration-test run --like "e2e" --timeout 10m
```

**Output Format:**
The command provides rich terminal output including:
- Real-time progress bar with spinner and percentage
- Live status table during execution
- Final summary with success/failure counts
- Detailed results table with test names, status, duration, and errors
- Color-coded status indicators (green for success, red for failure, yellow for invalid)

##### `summary`
Display a summary of all integration test results. Shows all test execution results with their status, duration, and any errors.

```bash
deltafi integration-test summary [--json|-j] [--plain|-p]
```
- `--json, -j`: Output in JSON format
- `--plain, -p`: Plain text output

Examples:
```bash
# Show all test results
deltafi integration-test summary

# Output in JSON format
deltafi integration-test summary --json

# Plain text output
deltafi integration-test summary --plain
```

##### `clear`
Remove all integration test results from the system.

```bash
deltafi integration-test clear
```

This command will:
- Fetch all existing test results
- Remove each test result individually
- Report the number of results removed
- Show any errors that occurred during removal

Example:
```bash
deltafi integration-test clear
```

##### `remove`
Remove one or more integration tests by name.

```bash
deltafi integration-test remove <test-names...>
```
- `test-names...`: One or more test names to remove (required)

Examples:
```bash
# Remove a single test
deltafi integration-test remove old-test

# Remove multiple tests
deltafi integration-test remove test1 test2 test3
```

**Integration Test Configuration Format**

Integration tests are defined in YAML format with the following structure:

```yaml
name: "test-name"
description: "Test description"
timeout: "5m"
plugins:
  - groupId: "org.deltafi"
    artifactId: "deltafi-core"
    version: "1.0.0"
dataSources:
  - "my-data-source"
transformationFlows:
  - "my-transform"
dataSinks:
  - "my-data-sink"
inputs:
  - name: "input1"
    contentType: "application/json"
    data: |
      {
        "key": "value"
      }
expectedDeltaFiles:
  - flow: "my-data-sink"
    type: "COMPLETE"
    state: "COMPLETE"
    actions:
      - "my-action"
    metadata:
      - key: "expected-key"
        value: "expected-value"
```

**Key Features:**

- **Complete Flow Testing**: Test entire data pipelines from source to sink
- **Real-time Progress**: Visual progress bar with live status updates
- **Pattern Matching**: Flexible test selection using `--like` patterns
- **Comprehensive Results**: Detailed test results with timing and error information
- **Batch Operations**: Run multiple tests simultaneously
- **JSON Output**: Machine-readable output for automation
- **Error Handling**: Clear error reporting and failure summaries

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
  deltafi postgres eval  # Interactive input until Ctrl-D
  ```
- `status`: Show Postgres instance status and administrative information
  ```bash
  deltafi postgres status [--plain|-p]
  ```
  - `--plain, -p`: Plain output, omitting table borders
  
  Displays comprehensive information including:
  - **Version**: PostgreSQL version and compilation details
  - **Database Size**: Current database size and storage usage
  - **Cache Hit Ratio**: Database cache performance metrics
  - **Active Connections**: Current connection states and counts
  - **Table Sizes**: Top 10 tables by total size (including indexes)
  - **Table Statistics**: Live/dead row counts for top tables
  - **Top Index Usage**: Most frequently used indexes with scan counts
  - **Active Queries**: Currently running queries (if any)
  - **Current Locks**: Active lock types and counts
  - **Flyway Migration Summary**: High-level migration statistics
  - **Recent Flyway Migrations**: Last 5 migrations with details

- `migrations`: Show detailed Flyway migration information
  ```bash
  deltafi postgres migrations [--plain|-p]
  ```
  - `--plain, -p`: Plain output, omitting table borders
  
  Displays a comprehensive table of all Flyway migrations including:
  - **Version**: Migration version number
  - **Description**: Human-readable description of what the migration does
  - **Script**: Actual SQL script filename
  - **Installed On**: When the migration was applied
  - **Execution Time**: How long the migration took (in milliseconds)
  - **Success**: Whether the migration succeeded
  
  Also shows a migration summary with:
  - Total number of migrations
  - Number of successful vs failed migrations
  - Latest migration version
  - Last migration timestamp

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

### Managing Topics
```bash
# List all topics in the system
deltafi topic list

# Show all flows that use a specific topic
deltafi topic flows my-topic

# View the complete flow path to a topic
deltafi topic graph my-topic
```

### Running Integration Tests
```bash
# Run all integration tests
deltafi integration-test run

# Run smoke tests only
deltafi integration-test run --like "smoke"

# Run specific tests with custom timeout
deltafi integration-test run test1 test2 --timeout 10m

# View test results summary
deltafi integration-test summary

# Load a new test configuration
deltafi integration-test load my-test.yaml
```

### Managing DeltaFiles
```bash
# Search DeltaFiles with interactive TUI
deltafi search --data-source my-source --stage COMPLETE

# Search with auto-refresh every 30 seconds
deltafi search --auto-refresh 30

# List DeltaFiles in JSON format
deltafi deltafiles list --json --limit 10

# View DeltaFiles with specific filters
deltafi deltafiles view --from "yesterday" --to "now"

# List recent errors
deltafi errored list --limit 5

# View all errors including acknowledged
deltafi errored view --all

# List filtered DeltaFiles
deltafi filtered list --filtered-cause "validation_failed"

# View filtered DeltaFiles with auto-refresh
deltafi filtered view --auto-refresh 60
```
