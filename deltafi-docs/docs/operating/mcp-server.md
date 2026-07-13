# MCP Server for AI Integration

DeltaFi includes a built-in [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server that enables AI assistants to interact with your DeltaFi system. This allows AI tools like Claude, ChatGPT, Cursor, and others to query system status, manage flows, debug issues, and even generate new plugins.

## Overview

The MCP server exposes DeltaFi capabilities as tools that AI assistants can invoke. Instead of manually running CLI commands or navigating the UI, you can describe what you want in natural language and let an AI assistant handle the details.

**Example interactions:**
- "Show me all flows that are currently stopped"
- "What errors occurred in the last hour?"
- "Resume all errored DeltaFiles in the passthrough-transform flow"
- "Create a new Java plugin called data-normalizer with a transform action"
- "Why is the s3-egress data sink invalid?"

## Quick Start

### Starting the MCP Server

```bash
deltafi mcp
```

This starts an MCP server on stdio, which is the standard transport for local AI tool integration.

### Configuring Claude Code

For [Claude Code](https://claude.com/claude-code) integration, create a `.mcp.json` file in your project root:

```json
{
  "mcpServers": {
    "deltafi": {
      "type": "stdio",
      "command": "deltafi",
      "args": ["mcp"]
    }
  }
}
```

Claude Code automatically discovers this file and makes DeltaFi tools available in your conversations.

### Configuring Other AI Tools

The `.mcp.json` format is supported by multiple AI tools with slight path variations:

| Tool | Project-level Config | Global Config |
|------|---------------------|---------------|
| Claude Code | `.mcp.json` | `~/.claude/mcp.json` |
| Cursor | `.cursor/mcp.json` | `~/.cursor/mcp.json` |
| JetBrains AI | `.junie/mcp/mcp.json` | `~/.junie/mcp/mcp.json` |

## Available Tool Groups

Tools are organized into groups. You can enable specific groups using the `--tags` flag:

```bash
# Enable all tools (default)
deltafi mcp

# Enable specific tool groups
deltafi mcp --tags debug,flows

# Available tags: debug, operations, flows, ingest, ingress, plugins, admin, metrics,
# testing, auto_resume, delete_policies, events, annotations, generate
```

### Debug Tools

Query and inspect DeltaFiles for debugging and troubleshooting.

| Tool | Description |
|------|-------------|
| `get_deltafile` | Get detailed information about a DeltaFile by DID |
| `search_deltafiles` | Search DeltaFiles with filters (stage, flow, time range, etc.) |
| `get_error_summary` | Get summary of errors grouped by flow or message |
| `count_errors` | Count unacknowledged errors |
| `get_annotation_keys` | List all annotation keys used across DeltaFiles |
| `export_config` | Export system configuration as YAML |

**Example:** "Search for all errored DeltaFiles from the last 2 hours in the passthrough flow"

### Operations Tools

List, search, resume, acknowledge, and replay errored DeltaFiles.

| Tool | Description |
|------|-------------|
| `list_errors` | List DeltaFiles in ERROR state with optional filters |
| `search_errored_deltafiles` | Search errored DeltaFiles with flow/action detail |
| `resume_errored_deltafiles` | Resume errored DeltaFiles matching a filter |
| `acknowledge_errored_deltafiles` | Acknowledge errors with a reason |
| `replay_errored_deltafiles` | Replay errored DeltaFiles (re-ingest original data) |
| `replay_and_acknowledge_errored_deltafiles` | Replay and acknowledge in one operation |

**Example:** "Resume all errored DeltaFiles in the xml-transform flow from the last hour"

### Flows Tools

Manage flows, topics, and flow configurations.

| Tool | Description |
|------|-------------|
| `list_flows` | List all flows with status |
| `get_flow` | Get detailed flow configuration (runtime state) |
| `get_flow_plan` | Get a flow's plan (the load_flow input, placeholders intact, no runtime state) |
| `start_flow` | Start a stopped or paused flow |
| `stop_flow` | Stop a running flow |
| `pause_flow` | Pause a flow (completes in-flight, stops accepting new data) |
| `list_topics` | List all pub/sub topics with publishers and subscribers |
| `get_action_descriptors` | List available action types from plugins |
| `get_action_schema` | Get parameter schema for an action type |
| `enable_test_mode` | Enable test mode on a flow |
| `disable_test_mode` | Disable test mode |
| `load_flow` | Create or update a flow from JSON |
| `validate_flow` | Validate flow configuration and action parameters |
| `delete_flow` | Delete a system flow by name and type (stop it first; plugin flows are removed with their plugin) |
| `export_system_plans` | Export all system flow plans as an `import_system_plans`-compatible payload |
| `import_system_plans` | Bulk import a full set of flow plans (pair with `export_system_plans` for migration) |

**Example:** "Show me why the s3-egress data sink is invalid"

### Ingest Tools

Manage data sources and ingestion settings.

| Tool | Description |
|------|-------------|
| `list_data_sources` | List all data sources (REST, timed, on-error) |
| `get_data_source` | Get data source details |
| `get_timed_source_status` | Get schedule and status for timed sources |
| `delete_data_source` | Delete a data source |
| `set_timed_source_schedule` | Update cron schedule for timed sources |

**Example:** "Change the cron schedule for my-timed-source to run every 10 minutes"

### Plugins Tools

Manage plugin installation and lifecycle.

| Tool | Description |
|------|-------------|
| `list_plugins` | List installed plugins with status |
| `get_plugin_details` | Get plugin actions, variables, and configuration |
| `install_plugin` | Install a plugin from image (optionally wait for completion) |
| `uninstall_plugin` | Remove a plugin |
| `enable_plugin` | Enable a disabled plugin |
| `disable_plugin` | Disable a plugin without removing it |
| `retry_plugin_install` | Retry a failed installation |
| `rollback_plugin` | Rollback a failed upgrade to the previous version |

**Example:** "Install the deltafi/deltafi-stix plugin version 2.0 and wait for it to complete"

### Admin Tools

System administration, configuration, snapshots, and lookup tables.

| Tool | Description |
|------|-------------|
| `get_system_properties` | Get system configuration |
| `update_system_properties` | Update one or more system properties (set a value to `null` to clear it) |
| `get_status_report` | Aggregated health summary (status, error count, queue depths, CPU/memory/disk, version) |
| `list_snapshots` | List system snapshots |
| `get_snapshot` | Get snapshot details |
| `create_snapshot` | Create a system snapshot |
| `delete_snapshot` | Delete a snapshot by id |
| `restore_snapshot` | Reset the system to the state captured in a snapshot (optional `hard_reset`) |
| `import_snapshot` | Import a snapshot from JSON, optionally resetting the system after import |
| `list_delete_policies` | List content deletion policies |
| `get_ssl_info` | Get SSL/TLS configuration |
| `list_lookup_tables` | List all lookup tables |
| `get_lookup_table` | Get lookup table schema and metadata |
| `query_lookup_table` | Query rows from a lookup table with filters and pagination |
| `create_lookup_table` | Create a new lookup table |
| `delete_lookup_table` | Delete a lookup table |
| `upsert_lookup_table_rows` | Insert or update rows in a lookup table |

**Example:** "Create a snapshot, raise the requeue interval to 60 seconds, and report the new status"

### Metrics Tools

Monitor system health, performance, and time-series data.

| Tool | Description |
|------|-------------|
| `get_system_status` | Get overall system health |
| `get_version` | Get DeltaFi version info |
| `get_deltafile_stats` | Get DeltaFile processing statistics |
| `list_running_flows` | List currently running flows |
| `get_node_metrics` | Get node resource usage (CPU, memory, disk) |
| `list_available_metrics` | List predefined metric keys for time-series queries |
| `query_metrics` | Query time-series metrics (ingestion rates, errors, queue depths, etc.) |
| `query_custom_metrics` | Execute custom MetricsQL/PromQL queries |

**Example:** "Show me the ingestion rate by data source over the last 6 hours"

### Ingress Tools

Send data into DeltaFi from the MCP server.

| Tool | Description |
|------|-------------|
| `ingress_file` | Ingress a file from the local filesystem. Absolute, relative, and `~`-prefixed paths are all accepted; the file is read by the host running the MCP server. |
| `ingress_content` | Ingress inline content (text or base64-encoded bytes) into a named data source |

**Example:** "Ingress ~/samples/order-42.json into the orders-rest data source with content type application/json"

### Testing Tools

Manage integration tests and inspect their results.

| Tool | Description |
|------|-------------|
| `list_integration_tests` | List configured integration tests |
| `get_integration_test` | Get a single integration test definition |
| `load_integration_test` | Load (create or update) an integration test from inline `config_yaml` or a YAML file at `config_path` |
| `start_integration_test` | Start an integration test by name and return the run id |
| `remove_integration_test` | Delete an integration test definition |
| `get_test_results` | List all integration test run results |
| `get_test_result` | Get a single integration test result by id |
| `remove_test_result` | Delete a recorded integration test result |

**Example:** "Start the smoke-test integration test and tell me when it finishes"

### Auto-Resume Tools

Configure and exercise the automatic error-recovery system.

| Tool | Description |
|------|-------------|
| `list_resume_policies` | List all resume policies |
| `get_resume_policy` | Get a single resume policy by id |
| `load_resume_policies` | Load a batch of resume policies (optional `replace_all` to swap the full set) |
| `update_resume_policy` | Update an existing resume policy by id |
| `remove_resume_policy` | Delete a resume policy by id |
| `toggle_resume_policy` | Enable or disable a resume policy |
| `apply_resume_policies` | Apply named policies to any matching errored DeltaFiles |
| `resume_policy_dry_run` | Evaluate a policy against existing errors without applying it |

Each policy expects `name`, `maxAttempts`, and a `backOff` object (`delay` required; `maxDelay`, `multiplier`, `random` optional). Optional scoping fields: `errorSubstring`, `dataSource`, `action`, `priority`, `enabled`.

**Example:** "Dry-run a resume policy that retries 503 errors three times with a one-minute back-off"

### Delete Policy Tools

Manage timed delete policies for DeltaFile retention.

| Tool | Description |
|------|-------------|
| `load_timed_delete_policy` | Create or update a single timed delete policy |
| `import_delete_policies` | Bulk import (optional `replace_all` to swap the full set; an empty set clears all policies) |
| `remove_delete_policy` | Delete a policy by id |
| `toggle_delete_policy` | Enable or disable a policy |
| `export_delete_policies` | Export all delete policies in a form suitable for re-import |

Each policy requires `name`, `enabled`, `deleteMetadata`, and at least one of `afterCreate` or `afterComplete` (ISO-8601 duration strings such as `PT24H` or `P7D`). Optional `flow` and `minBytes` narrow the scope.

**Example:** "Import a delete policy that purges files older than seven days in the bulk-ingest flow"

### Events Tools

Read and write the DeltaFi event log over the REST API.

| Tool | Description |
|------|-------------|
| `list_events` | List events with optional filters and paging, ordered newest first |
| `list_events_with_count` | Same as `list_events` but includes total-count metadata for paging UIs |
| `get_event` | Get a single event by id |
| `create_event` | Create an event (defaults: source `MCP`, severity `info`) |
| `acknowledge_event` | Set or clear the acknowledged flag on an event |
| `delete_event` | Delete an event from the event log |

Supported `filters` keys: `id`, `severity`, `summary`, `content`, `source`, `acknowledged`, `notification`, `start`, and `end`.

**Example:** "Record an info event summarizing the rollout, then list all unacknowledged warnings from the last six hours"

### Annotations Tools

Add or inspect DeltaFile annotations.

| Tool | Description |
|------|-------------|
| `annotate_deltafile` | Add one or more key/value annotations to a DeltaFile. Set `allow_overwrites` to replace existing values. |

Annotation values are coerced to strings; pass `null` to send an empty value. `get_annotation_keys` (in the `debug` group) lists every key already in use across the system.

**Example:** "Annotate DeltaFile <did> with reviewed=true and tier=gold"

### Plugin Generation Tools

Scaffold new plugins, add actions and suppliers to existing ones, and refresh an existing plugin's scaffolding to the latest templates. These tools wrap the same generators used by `deltafi plugin generate` and write files into the configured `repoPath`.

| Tool | Description |
|------|-------------|
| `generate_plugin` | Create a new Java, Python, or Go plugin in the repos directory. Go plugins require a `module` path. |
| `generate_plugin_action` | Add a new action to an existing plugin. Language is auto-detected. Supported action types: `TransformAction` (default), `EgressAction`, `TimedIngressAction`, `JoiningTransformAction`. |
| `generate_plugin_supplier` | Add a lookup table supplier to an existing Go plugin. |
| `update_plugin_scaffolding` | Refresh an existing plugin's scaffolding (build files, Dockerfile, Makefile, flow files, README) to the current templates. Pass `dry_run=true` to preview; pass `include_source=true` to also refresh user-owned source files. |

For `generate_plugin_action`, `generate_plugin_supplier`, and `update_plugin_scaffolding`, the `plugin` argument can be either a name (resolved against `repoPath` in the development config) or an absolute path to a plugin directory.

**Example:** "Generate a new Go plugin called metrics-collector with module github.com/acme/metrics-collector, then add a TimedIngressAction called PollPrometheus."

## Use Cases

### Debugging Data Flow Issues

When data isn't flowing as expected, an AI assistant can help investigate:

```
User: "Data from the rest-ingest source isn't reaching the s3-egress sink"

AI Assistant uses:
1. list_flows - Check if all flows in the path are running
2. list_topics - Verify topic subscriptions are correct
3. search_deltafiles - Find recent DeltaFiles and check their stage
4. get_deltafile - Inspect a specific DeltaFile's action history
5. get_error_summary - Check for errors in the pipeline
```

### Bulk Error Recovery

After fixing an upstream issue, recover affected DeltaFiles:

```
User: "The external API was down for an hour. Resume all the failures."

AI Assistant uses:
1. get_error_summary - Identify affected flows and error counts
2. resume_errored_deltafiles - Resume errors with appropriate filters
3. search_deltafiles - Verify DeltaFiles are processing
```

### Flow Configuration

Create and configure new data flows:

```
User: "Set up a transform flow that subscribes to raw-data and publishes to processed-data"

AI Assistant uses:
1. get_action_descriptors - Find available transform actions
2. get_action_schema - Get parameter requirements
3. load_flow - Create the transform flow configuration
4. validate_flow - Check for configuration errors
5. start_flow - Start the new flow
```

The `load_flow` tool takes a `config` object that must include:
- `name`: Flow name
- `type`: Flow type (`TRANSFORM`, `REST_DATA_SOURCE`, `TIMED_DATA_SOURCE`, `ON_ERROR_DATA_SOURCE`, `DATA_SINK`)

Example transform flow config:
```json
{
  "name": "my-transform",
  "type": "TRANSFORM",
  "description": "Transforms data from raw to processed",
  "subscribe": [{"topic": "raw-data"}],
  "transformActions": [
    {
      "name": "ProcessData",
      "type": "org.deltafi.core.action.delay.Delay",
      "parameters": {"minDelayMS": 0, "maxDelayMS": 0}
    }
  ],
  "publish": {
    "matchingPolicy": "FIRST_MATCHING",
    "defaultRule": {"defaultBehavior": "ERROR"},
    "rules": [{"topic": "processed-data"}]
  }
}
```

Use `get_flow_plan` to see an example of an existing flow's configuration; its output decodes straight back into `load_flow`. (`get_flow` returns runtime state that `load_flow` rejects.)

### System Health Monitoring

Quick system health checks:

```
User: "Is the system healthy? Any issues I should know about?"

AI Assistant uses:
1. get_system_status - Overall health
2. list_flows - Check for stopped/invalid flows
3. query_metrics - Check queue depths and ingestion rates
4. get_error_summary - Recent error trends
```

## Security Considerations

The MCP server inherits authentication from the DeltaFi CLI configuration (`~/.deltafi/config.yaml`). Ensure:

1. **Access Control**: Only authorized users should have CLI access to systems with sensitive data
2. **Network Security**: The MCP server uses stdio transport (local only) by default
3. **Audit Trail**: DeltaFi API mutations are subject to the standard DeltaFi audit mechanisms. Local filesystem reads and writes, generated files, and Git operations performed by MCP tools are not recorded in the DeltaFi audit log.
4. **Host File Access**: `ingress_file` and `load_integration_test` can read any file accessible to the user running the MCP server
5. **Host File Changes**: Plugin generation tools create or modify local directories; action, supplier, and scaffolding tools accept absolute plugin paths

Only connect trusted MCP clients and review filesystem-related tool calls before approval. Run the server as a user whose filesystem permissions are limited to the files and development repositories the client should access.

## Troubleshooting

### MCP Server Won't Start

Ensure DeltaFi is running:
```bash
deltafi status
```

The MCP server requires a running DeltaFi instance to connect to.

### Tools Not Appearing in AI Assistant

1. Verify the `.mcp.json` file is in the correct location
2. Restart your AI tool to pick up configuration changes
3. Check that `deltafi` is in your PATH

### Tool Invocations Failing

Check DeltaFi connectivity:
```bash
deltafi status
deltafi versions
```

If these commands fail, the MCP server will also fail to execute tools.

## Further Reading

- [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [DeltaFi TUI Documentation](/operating/TUI)
- [Plugin Development Guide](/getting-started/simple-plugin)
