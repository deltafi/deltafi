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

## Understanding the TUI

### Why It Exists

The TUI is a single command-line tool that:

1. **Abstracts orchestration backends** - The same commands work whether you're running Docker Compose, KinD, or Kubernetes. You don't need to learn `docker-compose`, `kubectl`, or `helm` commands.

2. **Manages the full lifecycle** - Install, upgrade, start, stop, and configure DeltaFi without touching config files directly.

3. **Interacts with running services** - Search DeltaFiles, manage flows, view metrics, and debug issues through a consistent interface.

### Two Types of Commands

The TUI mixes two distinct responsibilities:

| Type | Examples | What They Do |
|------|----------|--------------|
| **Orchestration** | `up`, `down`, `config`, `upgrade` | Control the DeltaFi cluster lifecycle. These work regardless of what version is deployed. |
| **Runtime** | `search`, `ingress`, `data-source`, `properties` | Talk to the running DeltaFi services via API. These interact with the deployed version. |

### Version Model

There are two versions to be aware of:

- **TUI version** (`deltafi --version`) - The binary you're running
- **Deployed version** (`deltafi versions`) - The DeltaFi services actually running

For operators using released versions, these typically match. For developers, the TUI might be built from source while testing against different deployed versions.

To switch deployed versions:
```bash
deltafi upgrade list      # See available versions
deltafi upgrade 2.39.0    # Upgrade to a specific version
```

### Orchestration Modes

The TUI supports multiple deployment backends:

| Mode | Use Case |
|------|----------|
| **Compose** | Docker Compose deployment (simplest, recommended for most users) |
| **KinD** | Kubernetes-in-Docker (for testing K8s features locally) |
| **Kubernetes** | Production Kubernetes clusters |

Switch modes with `deltafi config`. The same commands (`up`, `down`, `status`, etc.) work across all modes.

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
    - [docker-login](#docker-login)
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
    - [auto-resume](#auto-resume)
      - [list](#auto-resume-list)
      - [get](#auto-resume-get)
      - [export](#auto-resume-export)
      - [load](#auto-resume-load)
      - [delete](#auto-resume-delete)
      - [clear](#auto-resume-clear)
      - [apply](#auto-resume-apply)
      - [dry-run](#auto-resume-dry-run)
    - [properties](#properties)
      - [list](#properties-list)
      - [get](#properties-get)
      - [set](#properties-set)
    - [valkey](#valkey)
      - [cli](#valkey-cli)
      - [latency](#valkey-latency)
      - [stat](#valkey-stat)
      - [monitor](#valkey-monitor)
    - [provenance](#provenance)
      - [flush](#provenance-flush)
  - [DeltaFi Flow Management](#deltafi-flow-management)
    - [system-flow-plans](#system-flow-plans)
      - [export](#system-flow-plans-export)
      - [import](#system-flow-plans-import)
    - [data-source](#data-source)
      - [list](#data-source-list)
      - [get](#data-source-get)
      - [load](#data-source-load)
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
    - [flow](#flow)
      - [graph](#flow-graph)
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
      - [export](#errored-export)
      - [resume](#errored-resume)
      - [replay](#errored-replay)
      - [acknowledge](#errored-acknowledge)
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
    - [graph (metrics)](#graph-metrics)
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
deltafi config [--compose|--kind|--kubernetes] [--deployment|--plugin-development|--core-development] [--force] [--no-start]
```

**Flags:**
- `--compose`                Set orchestration mode to Compose
- `--kind`                   Set orchestration mode to KinD
- `--kubernetes`             Set orchestration mode to Kubernetes
- `--deployment`             Set deployment mode to Deployment
- `--plugin-development`     Set deployment mode to PluginDevelopment
- `--core-development`       Set deployment mode to CoreDevelopment
- `--force`                  Force configuration changes without confirmation
- `--no-start`               Do not automatically start DeltaFi if it is not running

If any of these flags are set, the configuration will be updated and saved immediately, and the wizard will not run. You can set both orchestration and deployment mode in a single invocation. If you change orchestration mode while DeltaFi is running, you will be prompted to confirm (unless --force is used), and the system will be stopped automatically.

By default, the config command will automatically start DeltaFi if it is not running after configuration changes. Use the `--no-start` flag to skip this automatic startup behavior.

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

# Configure without automatically starting DeltaFi
deltafi config --compose --no-start
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

#### `docker-login`
Configure Docker registry authentication for pulling private images. This allows DeltaFi to install plugins from image repositories that require authentication.

**⚠️ Important**: This command is only available when the orchestration mode is set to Compose. It is not supported in Kubernetes or KinD deployments.

```bash
deltafi docker-login [registry] --username|-u <username> [--password <password>] [--password-stdin|-p]
```

**Arguments:**
- `registry`: The Docker registry URL (e.g., `registry.example.com`, `123456789012.dkr.ecr.us-east-1.amazonaws.com`)

**Flags:**
- `--username, -u <username>`: Registry username (required)
- `--password <password>`: Registry password (use --password-stdin instead for better security)
- `--password-stdin, -p`: Read password from stdin (mutually exclusive with --password)

**Security Note**: Using `--password-stdin` is recommended over `--password` to avoid exposing credentials in shell history or process listings.

**After Configuration**: The credentials are saved and will be used for plugin installations. You must restart the core services for changes to take effect by running:
```bash
deltafi down && deltafi up
```

**Examples:**
```bash
# Login with password from stdin (recommended for AWS ECR)
aws ecr get-login-password --region us-east-1 | \
  deltafi docker-login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Login with password from environment variable
deltafi docker-login --username myuser --password "$REGISTRY_PASSWORD" \
  registry.example.com

# Standard docker registry login
echo "$DOCKER_PASSWORD" | deltafi docker-login --username myuser \
  --password-stdin docker.io
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
- `changelog [version]`: Show changelog for a specific version or all newer versions
  ```bash
  # Show changelog for a specific version
  deltafi upgrade changelog <version>
  
  # Show changelogs for all newer versions (newest to oldest)
  deltafi upgrade changelog
  ```
- `upgrade [version]`: Upgrade to a specific version
  ```bash
  deltafi upgrade <version> [--safe|-s]
  ```
  - `--safe, -s`: Perform safe upgrade with ingress management and dashboard monitoring

**Safe Upgrade Mode:**

The `--safe` flag enables a safer upgrade process that minimizes downtime and provides monitoring capabilities:

**Features:**
- **Ingress Management**: Automatically disables ingress before upgrade and restores it afterward
- **Dashboard Monitoring**: Shows the system dashboard during upgrade for real-time monitoring
- **Confirmation Prompt**: Requires user confirmation before proceeding with the upgrade
- **Error Recovery**: Automatically restores ingress settings if upgrade fails or is aborted

**Safe Upgrade Workflow:**
1. **DeltaFi Status Check**: If DeltaFi is not running, safe mode has no effect and normal upgrade proceeds
2. **Ingress Assessment**: Checks if `ingressEnabled` property is set to `true`
3. **Ingress Disable**: If enabled, temporarily sets `ingressEnabled` to `false` and logs the action
4. **Dashboard Launch**: Starts the system dashboard for monitoring (user can exit with 'q')
5. **Confirmation**: Shows a Y/n prompt to confirm the upgrade
6. **Upgrade Execution**: Performs the upgrade if confirmed
7. **Ingress Restoration**: Restores `ingressEnabled` to its original value after completion

**Examples:**
```bash
# Normal upgrade
deltafi upgrade 2.25.0

# Safe upgrade with ingress management and monitoring
deltafi upgrade 2.25.0 --safe

# Safe upgrade using short flag
deltafi upgrade 2.25.0 -s
```

**Error Handling:**
- If the upgrade is aborted (N, ctrl+c, q, esc), ingress settings are automatically restored
- If the upgrade fails, ingress settings are automatically restored
- Clear error messages indicate what went wrong and what was restored

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

#### `auto-resume`
Manage auto resume rules that automatically retry errored DeltaFiles. See [Automatic Resume](../advanced/auto_resume.md) for details on how auto resume rules work.

- `list`: List all auto resume rules
  ```bash
  deltafi auto-resume list [--plain|-p]
  ```
- `get [name-or-id]`: Get an auto resume rule by name or UUID
  ```bash
  deltafi auto-resume get <name-or-id> [--format|-o <json|yaml>]
  ```
- `export`: Export all auto resume rules
  ```bash
  deltafi auto-resume export [--format|-o <json|yaml>]
  ```
- `load [files...]`: Load auto resume rules from JSON or YAML files
  ```bash
  deltafi auto-resume load <files...> [--replace-all|-r]
  ```
  Files can contain a single rule or an array of rules.
- `delete [name-or-id]`: Delete an auto resume rule
  ```bash
  deltafi auto-resume delete <name-or-id> [--force|-f]
  ```
- `clear`: Delete all auto resume rules
  ```bash
  deltafi auto-resume clear [--force|-f]
  ```
- `apply [names...]`: Apply auto resume rules to existing errored DeltaFiles
  ```bash
  deltafi auto-resume apply <names...>
  ```
- `dry-run [file]`: Test an auto resume rule against existing errors without saving
  ```bash
  deltafi auto-resume dry-run <file>
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

#### `provenance`
Manage provenance data in the analytics system.

- `flush`: Flush and compact provenance data
  ```bash
  deltafi provenance flush [--verbose|-v]
  ```
  - `--verbose, -v`: List compacted provenance files after flush

  This command signals the analytics collector to flush all buffered provenance data and compact it into parquet files. It waits for the operation to complete before returning.

  **Use cases:**
  - Before exporting provenance data to external storage
  - During graceful shutdown sequences
  - After ingesting a batch of data when immediate export is needed

  **Examples:**
  ```bash
  # Flush and compact provenance data
  deltafi provenance flush

  # Flush with verbose output showing compacted files
  deltafi provenance flush --verbose
  ```

  **Output:**
  ```
  Flushing... done

  # With --verbose:
  Flushing... done

  Compacted provenance files:
    my-system/20251219/14.parquet (125.3 KB)
    my-system/20251219/15.parquet (89.7 KB)
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
- `load [files...]`: Create or update data sources from configuration files
  ```bash
  deltafi data-source load <files...>
  ```
  The type (REST_DATA_SOURCE, TIMED_DATA_SOURCE, or ON_ERROR_DATA_SOURCE) is automatically detected from the `type` field in the file.
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
  deltafi data-sink get <name> [--format|-o <json|yaml>]
  ```
- `load [files...]`: Create or update data sinks from configuration files
  ```bash
  deltafi data-sink load <files...>
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
- `load [files...]`: Create or update transforms from configuration files
  ```bash
  deltafi transform load <files...>
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
  deltafi plugin install <image> [--secret|-s <secret>] [--wait|-w]
  ```
  - `--secret, -s`: Image pull secret for private image repositories (Kubernetes only)
  - `--wait, -w`: Wait for plugin installation to complete before returning. Exits with code 1 if installation fails.
- `uninstall [names...]`: Uninstall one or more plugins
  ```bash
  deltafi plugin uninstall <name>
  ```
- `describe [pluginName]`: Describe a plugin and its actions
  ```bash
  deltafi plugin describe <pluginName> [--verbose|-v] [--action|-a <actionName>]
  ```
- `generate`: Generate a new plugin project
  ```bash
  deltafi plugin generate [--java | --python] <plugin-name> [flags]
  deltafi plugin generate action <plugin-name> <action-name> [flags]
  ```
  
  **Plugin Generation:**
  
  - Generate a new plugin project or update an existing one
    ```bash
    deltafi plugin generate [--java | --python] <plugin-name> [--namespace|-n <namespace>] [--interactive|-i] [--force|-f] [--dry-run|-d]
    ```
    - `--java`: Generate a Java plugin (required, mutually exclusive with --python)
    - `--python`: Generate a Python plugin (required, mutually exclusive with --java)
    - `plugin-name`: Name of the plugin to generate or update (required)
    - `--namespace, -n`: Plugin namespace (default: org.deltafi.&lt;plugin-name&gt;, only used when generating new plugin)
    - `--interactive, -i`: Interactive mode - prompt for each file with diff view (update only)
    - `--force, -f`: Force overwrite all files without prompting (update only)
    - `--dry-run, -d`: Show what would be updated without making changes (update only)
    
    **Java Plugin Behavior:**
    - If the plugin directory does not exist: Generates a complete Java plugin project skeleton
    - If the plugin directory already exists: Updates the plugin with the latest templates
    
    **When generating a new Java plugin**, creates:
    - Project structure following Java/Gradle best practices
    - `build.gradle` configuration with DeltaFi plugin conventions
    - `settings.gradle` with repository configuration
    - `gradle.properties` with DeltaFi SDK version
    - Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle-wrapper.properties`)
    - Dockerfile for containerization (via plugin convention)
    - README.md with project documentation
    - Main Spring Boot application class
    - Empty actions and test directories
    - Flows directory with `data-source.json` and `variables.json`
    
    **When generating a new Python plugin**, creates:
    - Project structure following Python best practices
    - `pyproject.toml` configuration with DeltaFi SDK dependency
    - Dockerfile for containerization
    - Makefile with common development tasks
    - README.md with project documentation
    - Plugin registration file
    - Empty actions and test directories
    - Flows directory with `data-source.json` and `variables.yaml`
    
    **When updating an existing plugin**, updates files with the latest templates while preserving:
    - Java: DeltaFi version in `gradle.properties`, additional dependencies in `build.gradle`
    - Python: Project version in `pyproject.toml`, additional dependencies in `pyproject.toml`
    - User modifications (with interactive merge prompts)
    
    **Update Modes:**
    - **Default**: Shows summary of changes and asks for single confirmation
    - **Interactive (`-i`)**: Shows side-by-side colorized diff for each file and prompts (y/n/d/a/q):
      - `y`: Accept changes
      - `n`: Skip file
      - `d`: Show diff (side-by-side colorized)
      - `a`: Accept all remaining files
      - `q`: Quit without applying changes
    - **Force (`-f`)**: Overwrites all files without prompting
    - **Dry Run (`-d`)**: Shows what would be updated without making changes
    
    **Examples:**
    ```bash
    # Generate a new Java plugin with default namespace
    deltafi plugin generate --java my-plugin
    
    # Generate a Java plugin with custom namespace
    deltafi plugin generate --java my-plugin --namespace com.example.myplugin
    
    # Generate a new Python plugin
    deltafi plugin generate --python my-plugin
    
    # Update an existing plugin (directory already exists)
    deltafi plugin generate --java my-plugin
    
    # Preview what would be updated
    deltafi plugin generate --java my-plugin --dry-run
    
    # Update with interactive merge prompts
    deltafi plugin generate --python my-plugin --interactive
    
    # Force update all files
    deltafi plugin generate --java my-plugin --force
    ```
  
  **Action Generation:**
  
  - `action &lt;plugin-name&gt; &lt;action-name&gt;`: Generate a new action in an existing plugin
    ```bash
    deltafi plugin generate action <plugin-name> <action-name> [--type|-t <type>]
    ```
    - `plugin-name`: Name of the existing plugin (required)
    - `action-name`: Name of the action to generate (required)
    - `--type, -t`: Type of action to generate (default: TransformAction)
    
    **Language Auto-Detection:**
    - Language is automatically detected based on the plugin directory:
      - `build.gradle` file → Java plugin
      - `pyproject.toml` file → Python plugin
    
    **Supported Action Types (Java):**
    - `TransformAction` (default)
    - `EgressAction`
    - `TimedIngressAction`
    - `JoiningTransformAction`
    - `TransformManyAction`
    
    **Supported Action Types (Python):**
    - `TransformAction` (default)
    - `EgressAction`
    - `IngressAction`
    - `TimedIngressAction`
    - `JoiningTransformAction`
    - `TransformManyAction`
    - `TransformExecAction`
    
    **Generates:**
    - Java: Action implementation file in `src/main/java/org/deltafi/&lt;package&gt;/actions/`, test file in `src/test/java/org/deltafi/&lt;package&gt;/actions/`, flow JSON file in `src/main/resources/flows/`
    - Python: Action implementation file in `src/&lt;package&gt;/actions/`, test file in `src/&lt;package&gt;/test/`
    - Initial action with content passthrough behavior, TODO annotations, and logging
    
    **Examples:**
    ```bash
    # Generate a TransformAction (default) - language auto-detected
    deltafi plugin generate action my-plugin my-transform
    
    # Generate an EgressAction
    deltafi plugin generate action my-plugin my-egress --type EgressAction
    
    # Generate action with PascalCase name
    # Java: Creates SuperDuperAction.java with class SuperDuperAction
    # Python: Creates super_duper_action.py with class SuperDuperAction
    deltafi plugin generate action my-plugin SuperDuper
    ```

#### `flow`
Commands for visualizing data flow paths.

##### `graph` {#flow-graph}
Display a graph of data flow paths showing how data moves through data sources, transforms, and data sinks.

```bash
deltafi flow graph [flowNames...] [--all]
```
- `--all`: Show all flows

**Examples:**
```bash
# Show graph for a specific data source
deltafi flow graph my-data-source

# Show all flow paths
deltafi flow graph --all
```

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
deltafi ingress <files...> --datasource|-d <source> [--content-type|-c <type>] [--metadata|-m <json>] [--plain|-p] [--verbose|-v] [--watch|-w]
```
- `--datasource, -d`: Data source for the ingressed files (required)
- `--content-type, -c`: Content type for the ingressed files (default: application/octet-stream)
- `--metadata, -m`: JSON metadata to attach to the ingressed files
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
- `--user-notes <yes|no>`: Filter by user notes status
- `--warnings <yes|no>`: Filter by warnings status

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

##### `export`
Export unacknowledged errored DeltaFiles. By default, exported DeltaFiles are marked as acknowledged.

```bash
deltafi errored export [flags]
```

**Flags:**
- `--output, -o <file>`: Output file name (default: auto-generated)
- `--limit, -l <int>`: Maximum number of errors to export (default: 100)
- `--max-size, -s <size>`: Maximum total size of exported data (default: 100MB)
- `--no-acknowledge, -n`: Do not acknowledge the errors for exported DeltaFiles
- `--reason, -r <reason>`: Acknowledge reason

**Examples:**
```bash
# Export up to 100 errors to auto-named file
deltafi errored export

# Export with custom limit and output file
deltafi errored export --limit 50 --output errors.tar.gz

# Export without acknowledging
deltafi errored export --no-acknowledge

# Export with acknowledge reason
deltafi errored export --reason "Exported for analysis"
```

##### `resume`
Resume errored DeltaFiles matching the filter criteria. Shows count and prompts for confirmation before executing.

```bash
deltafi errored resume [flags]
```

**Flags:**
- `--force, -f`: Skip confirmation prompt
- All search flags are available for filtering

**Examples:**
```bash
# Resume all unacknowledged errors
deltafi errored resume

# Resume errors from a specific data source
deltafi errored resume --data-source my-source

# Resume with specific error cause filter
deltafi errored resume --error-cause "timeout"

# Skip confirmation prompt
deltafi errored resume --force
```

##### `replay`
Replay errored DeltaFiles matching the filter criteria. Creates new child DeltaFiles that restart processing from the beginning.

```bash
deltafi errored replay [flags]
```

**Flags:**
- `--force, -f`: Skip confirmation prompt
- `--acknowledge, -A`: Also acknowledge the original errors after replay
- `--reason, -r <reason>`: Acknowledge reason (required with --acknowledge)
- All search flags are available for filtering (replayable is automatically set)

**Examples:**
```bash
# Replay all replayable unacknowledged errors
deltafi errored replay

# Replay and acknowledge errors
deltafi errored replay --acknowledge --reason "Replayed for retry"

# Replay errors from a specific data source
deltafi errored replay --data-source my-source

# Replay with specific error cause filter
deltafi errored replay --error-cause "connection refused"

# Skip confirmation prompt
deltafi errored replay --force
```

##### `acknowledge`
Acknowledge errors on DeltaFiles matching the filter criteria. Requires a reason to be specified.

```bash
deltafi errored acknowledge --reason <reason> [flags]
```

**Flags:**
- `--reason, -r <reason>`: Acknowledge reason (required)
- `--force, -f`: Skip confirmation prompt
- All search flags are available for filtering

**Examples:**
```bash
# Acknowledge all unacknowledged errors
deltafi errored acknowledge --reason "Known issue, fix deployed"

# Acknowledge errors from a specific data source
deltafi errored acknowledge --reason "Data source deprecated" --data-source old-source

# Acknowledge errors with specific error cause
deltafi errored acknowledge --reason "Transient network issue" --error-cause "timeout"

# Skip confirmation prompt
deltafi errored acknowledge --reason "Bulk acknowledge" --force
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

### Table Lookup Service

#### 'lookup'
Manage lookup tables. Lookup tables provide tabular storage for data that can be queried by plugins.

```bash
deltafi lookup [subcommand] [options] [arguments]
```
##### `postgres`
Start an interactive Postgres CLI session for the table lookup service.

```bash
deltafi lookup postgres
```

##### `list`
List lookup tables.

```bash
deltafi lookup list
```

##### `schema-import`
Import a lookup table schema from a JSON or YAML file. Creates the table structure without data.

```bash
deltafi lookup schema-import [schema-file]
```

Example schema file:
```json
{
  "name": "beatles_songs",
  "columns": ["title", "album", "year", "vocalist"],
  "keyColumns": ["title"],
  "serviceBacked": false,
  "backingServiceActive": false,
  "pullThrough": false
}
```

##### `delete`
Delete a lookup table.

```bash
deltafi lookup delete [name] [--force|-f]
```
- `--force, -f`: Skip confirmation prompt

##### `schema-export`
Export a lookup table schema by name. Outputs the table structure as JSON (without data).

```bash
deltafi lookup schema-export [name]
```

##### `insert`
Insert rows into a lookup table. Rows with key columns matching the input will be updated. The file format is detected by extension (.csv, .json, .yaml, .yml). If no file is specified, reads from stdin.

```bash
deltafi lookup insert [name] [rows-file] [--format|-f <json|yaml|csv>]
```
- `--format, -f`: Input format when reading from stdin (default: json)

Example CSV file:
```csv
title,album,year,vocalist
Strawberry Fields Forever,,1967,John
With A Little Help From My Friends,Sgt. Pepper's Lonely Hearts Club Band,1967,Ringo
```

Example JSON file:
```json
[
  [
    {"column": "title", "value": "Strawberry Fields Forever"},
    {"column": "year", "value": "1967"},
    {"column": "vocalist", "value": "John"}
  ],
  [
    {"column": "title", "value": "With A Little Help From My Friends"},
    {"column": "album", "value": "Sgt. Pepper's Lonely Hearts Club Band"},
    {"column": "year", "value": "1967"},
    {"column": "vocalist", "value": "Ringo"}
  ]
]
```

##### `query`
Show all rows in a lookup table.

```bash
deltafi lookup query [name] [--json|-j] [--yaml|-y] [--csv|-c] [--plain|-p] [--no-header|-n]
```
- `--json, -j`: Output in JSON format
- `--yaml, -y`: Output in YAML format
- `--csv, -c`: Output in CSV format
- `--plain, -p`: Output as plain table (no styling)
- `--no-header, -n`: Omit header row (for table, plain, and csv output)

### Artifact Registry

#### `artifacts`
Manage versioned binary artifacts stored in the DeltaFi artifact registry. Artifacts are scoped to
a plugin (`--owner <plugin-name>`) or shared across the organization (`--owner shared`).

```bash
deltafi artifacts [subcommand] [--owner <owner>] [options] [arguments]
```

The `--owner` flag is required for every subcommand. Use the plugin's artifact ID (e.g.
`org.deltafi.my-plugin`) or `shared` for organization-wide artifacts.

##### `publish`
Publish a new artifact version to the registry. The artifact name defaults to the filename; the
server generates a timestamp-based version if `--version` is omitted.

```bash
deltafi artifacts publish <file> [--owner <owner>] [--name <name>] [--version <version>]
    [--description <text>] [--content-type <mime>] [--label key=value]... [--no-active]
```

- `--name`: Artifact name (defaults to the filename)
- `--version`: Version string (server generates a timestamp version if omitted)
- `--description`: Human-readable description of this version
- `--content-type`: MIME content type (default: `application/octet-stream`)
- `--label key=value`: Freeform label stored on the manifest; may be repeated
- `--no-active`: Do not update the `active` alias after publishing

```bash
# Minimal publish — name and version are inferred
deltafi artifacts publish --owner my-plugin ./routing-rules.json

# Full publish with metadata
deltafi artifacts publish --owner my-plugin ./routing-rules.json \
    --name routing-rules --version 2.0.0 \
    --description "Q2 routing update" \
    --label env=prod --label team=infra

# Publish a canary release without moving active
deltafi artifacts publish --owner my-plugin ./routing-rules-canary.json --no-active
```

##### `list`
List all artifacts for an owner with their active version summary. Pass `--all-owners` instead of
`--owner` to show every artifact across all owners in a single view.

```bash
deltafi artifacts list [--owner <owner>] [--all-owners] [--plain|-p]
```

- `--all-owners`: List artifacts across all owners; mutually exclusive with `--owner`
- `--plain, -p`: Plain output without table borders

```bash
deltafi artifacts list --owner my-plugin
deltafi artifacts list --owner shared
deltafi artifacts list --all-owners
deltafi artifacts list --all-owners --plain
```

##### `versions`
List all published versions of a specific artifact.

```bash
deltafi artifacts versions <artifactName> [--owner <owner>] [--plain|-p]
```

```bash
deltafi artifacts versions --owner my-plugin routing-rules
```

##### `download`
Download an artifact version to a local file. The `version` argument accepts a concrete version
string or any alias (e.g. `active`, `stable`).

```bash
deltafi artifacts download <artifactName> <version> [--owner <owner>] [--output|-o <file>]
```

- `--output, -o`: Output file path (defaults to the artifact name)

```bash
# Download the active version
deltafi artifacts download --owner my-plugin routing-rules active

# Download a specific version to a named file
deltafi artifacts download --owner my-plugin routing-rules 2.0.0 -o rules-v2.json

# Download from the shared namespace by alias
deltafi artifacts download --owner shared geo-regions stable
```

##### `rollback`
Move the `active` alias to a previously published version without re-uploading.

```bash
deltafi artifacts rollback <artifactName> <version> [--owner <owner>]
```

```bash
deltafi artifacts rollback --owner my-plugin routing-rules 1.0.0
```

##### `set-alias`
Set any alias to point to a specific version. This is a generalization of `rollback` — you can
move any alias, not just `active`.

```bash
deltafi artifacts set-alias <artifactName> <alias> <version> [--owner <owner>]
```

```bash
# Point the 'stable' alias at version 2.0.0
deltafi artifacts set-alias --owner my-plugin routing-rules stable 2.0.0

# Roll back active using set-alias
deltafi artifacts set-alias --owner my-plugin routing-rules active 1.0.0
```

##### `remove-alias`
Remove a custom alias from an artifact. The `active` alias cannot be removed.

```bash
deltafi artifacts remove-alias <artifactName> <alias> [--owner <owner>]
```

```bash
deltafi artifacts remove-alias --owner my-plugin routing-rules canary
```

##### `delete`
Delete an artifact version or an entire artifact.

```bash
deltafi artifacts delete <artifactName> [version] [--owner <owner>] [--force|-f]
```

- If `version` is provided, only that version (blob + manifest) is deleted. The version currently
  tagged as `active` cannot be deleted — roll back to another version first.
- If `version` is omitted, the entire artifact is deleted: all versions, aliases, and configuration.
- `--force, -f`: Skip the confirmation prompt

```bash
# Delete a specific version
deltafi artifacts delete --owner my-plugin routing-rules 1.0.0

# Delete the entire artifact (all versions)
deltafi artifacts delete --owner my-plugin routing-rules

# Skip confirmation
deltafi artifacts delete --owner my-plugin routing-rules --force
```

##### `purge-config get`
Get the current retention policy for an artifact.

```bash
deltafi artifacts purge-config get <artifactName> [--owner <owner>]
```

```bash
deltafi artifacts purge-config get --owner my-plugin routing-rules
```

##### `purge-config set`
Set a retention policy to automatically remove old versions. A version is eligible for purge only
when it satisfies **both** criteria and is not the current `active` version.

```bash
deltafi artifacts purge-config set <artifactName> [--owner <owner>]
    [--keep-latest <n>] [--max-age-minutes <minutes>]
```

- `--keep-latest`: Number of most-recent versions to always keep (default: 5)
- `--max-age-minutes`: Versions older than this may be purged; `0` disables age-based purging (default: 43200, i.e. 30 days)

```bash
# Keep 3 versions; purge anything older than 7 days
deltafi artifacts purge-config set --owner my-plugin routing-rules \
    --keep-latest 3 --max-age-minutes 10080
```

##### `purge`
Trigger an immediate purge run for an artifact, applying the configured retention policy now
rather than waiting for the scheduled run.

```bash
deltafi artifacts purge <artifactName> [--owner <owner>]
```

```bash
deltafi artifacts purge --owner my-plugin routing-rules
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
timeout: "PT5M"
plugins:
  - groupId: "org.deltafi"
    artifactId: "deltafi-core-actions"
    version: "ANY"
dataSources:
  - "my-data-source"
transformationFlows:
  - "my-transform"
dataSinks:
  - "my-data-sink"
inputs:
  - dataSource: "my-data-source"
    ingressFileName: "input.json"
    contentType: "application/json"
    data: |
      {
        "key": "value"
      }
expectedDeltaFiles:
  - stage: COMPLETE
    expectedFlows:
      - flow: "my-transform"
        type: TRANSFORM
        actions:
          - "MyTransformAction"
      - flow: "my-data-sink"
        type: DATA_SINK
        actions:
          - "MyEgressAction"
```

For the complete configuration reference, see [Integration Testing](/integration-test).

**Key Features:**

- **Complete Flow Testing**: Test entire data pipelines from source to sink
- **Real-time Progress**: Visual progress bar with live status updates
- **Pattern Matching**: Flexible test selection using `--like` patterns
- **Comprehensive Results**: Detailed test results with timing and error information
- **Batch Operations**: Run multiple tests simultaneously
- **JSON Output**: Machine-readable output for automation
- **Error Handling**: Clear error reporting and failure summaries

### Metrics

#### `graph` {#graph-metrics}
Display interactive time-series graphs from VictoriaMetrics data. Supports predefined metrics and custom MetricsQL queries with interactive time controls and auto-refresh.

```bash
deltafi graph [--metric|-m <metric>] [--query|-q <query>] [--title|-t <title>] [--from|-f <duration>] [--interval|-i <duration>] [--data-source|-d <names>] [--not-data-source|-D <names>] [--line] [--hide-legend]
```

**Flags:**
- `--metric, -m`: Predefined metric to display (see available metrics below)
- `--query, -q`: Custom MetricsQL query (takes precedence over --metric)
- `--title, -t`: Title for a custom query (adds it to the metric selection list)
- `--from, -f`: Time range to display (default: 1h). Supports: 30m, 1h, 4h, 1d, 1w
- `--interval, -i`: Auto-refresh interval (default: 30s). Set to 0 to disable
- `--data-source, -d`: Only show specified data sources (comma-separated)
- `--not-data-source, -D`: Exclude specified data sources (comma-separated)
- `--line`: Use line characters instead of braille for chart rendering
- `--hide-legend`: Start with the legend hidden (toggle with `l`)

**Available Metrics:**
| Metric | Description |
|--------|-------------|
| `bytes` | Bytes ingested by data source |
| `files` | Files ingested by data source |
| `egress-bytes-source` | Bytes egressed by data source |
| `egress-files-source` | Files egressed by data source |
| `egress-bytes-sink` | Bytes egressed by data sink |
| `egress-files-sink` | Files egressed by data sink |
| `bytes-in` | Total bytes in |
| `bytes-out` | Total bytes out |
| `files-in` | Total files in |
| `files-out` | Total files out |
| `files-errored` | Files errored by data source |
| `files-filtered` | Files filtered by data source |
| `files-dropped` | Files dropped by data source |
| `files-retried` | Files retried by data source |
| `total-delta-files` | Total DeltaFiles in system |
| `in-flight-files` | In-flight files count |
| `in-flight-bytes` | In-flight bytes |
| `cold-queued-files` | Cold-queued files count |
| `paused-files` | Paused files count |
| `queue-depth` | Action queue depth |
| `action-exec-count` | Action execution count |
| `action-exec-time` | Action execution time (ms) |
| `exec-time-source` | Execution time by data source (ms) |
| `files-deleted` | Files deleted by policy |
| `bytes-deleted` | Bytes deleted by policy |
| `disk-usage` | Disk usage by node |
| `node-cpu` | Node CPU usage |
| `node-memory` | Node memory usage |
| `app-cpu` | App CPU usage |
| `app-memory` | App memory usage |
| `plugin-cpu` | Plugin CPU usage |
| `plugin-memory` | Plugin memory usage |

**Interactive Controls:**
| Key | Action |
|-----|--------|
| `↑`/`↓` | Cycle through metrics |
| `c` | Change metric (opens selection modal) |
| `+`/`-` | Zoom time range (double/halve) |
| `←`/`→` | Pan time window (back/forward) |
| `x` | Return to now (reset pan) |
| `1-0` | Toggle series 1-10 visibility |
| `f` | Open filter modal |
| `l` | Toggle legend visibility |
| `a`/`n` | Show/hide all series |
| `r` | Force refresh |
| `?` | Show help |
| `q`/`Esc` | Quit |

**Examples:**
```bash
# Open metric selection modal
deltafi graph

# Show bytes ingested per data source (last 1h)
deltafi graph --metric bytes

# Show files ingested with 4 hour range
deltafi graph --metric files --from 4h

# Show custom MetricsQL query
deltafi graph --query '{__name__="gauge.deltafile.totalCount"}'

# Custom query with title (adds it to the metric selection list)
deltafi graph --query '{__name__="gauge.deltafile.totalCount"}' --title "Total DeltaFiles"

# Filter to specific data sources
deltafi graph --metric bytes --data-source source1,source2

# Exclude specific data sources
deltafi graph --metric files --not-data-source test-source

# Use line rendering instead of braille
deltafi graph --metric bytes --line

# Disable auto-refresh
deltafi graph --metric bytes --interval 0

# Start with legend hidden (more chart space)
deltafi graph --metric bytes --hide-legend
```

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

### Performing System Upgrades
```bash
# Normal upgrade
deltafi upgrade 2.25.0

# Safe upgrade with ingress management and monitoring
deltafi upgrade 2.25.0 --safe

# List available upgrade versions
deltafi upgrade list

# View changelog for a specific version
deltafi upgrade changelog 2.25.0
```

### Viewing Flow Graph
```bash
deltafi flow graph my-data-source
```

### Viewing Metrics Graph
```bash
# Open interactive metric selection
deltafi graph

# View bytes ingested by data source
deltafi graph --metric bytes

# View files with custom time range
deltafi graph --metric files --from 4h
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

# Resume all unacknowledged errors
deltafi errored resume

# Replay errors and acknowledge them
deltafi errored replay --acknowledge --reason "Replayed after fix"

# Acknowledge errors with a reason
deltafi errored acknowledge --reason "Known issue resolved"

# Export errors for external analysis
deltafi errored export --limit 50 --output errors.tar.gz

# List filtered DeltaFiles
deltafi filtered list --filtered-cause "validation_failed"

# View filtered DeltaFiles with auto-refresh
deltafi filtered view --auto-refresh 60
```
