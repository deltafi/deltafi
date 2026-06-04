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

# Available tags: debug, operations, flows, ingest, plugins, admin, metrics, generate
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
| `get_flow` | Get detailed flow configuration |
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

System administration, configuration, and lookup tables.

| Tool | Description |
|------|-------------|
| `get_system_properties` | Get system configuration |
| `list_snapshots` | List system snapshots |
| `get_snapshot` | Get snapshot details |
| `create_snapshot` | Create a system snapshot |
| `list_delete_policies` | List content deletion policies |
| `get_ssl_info` | Get SSL/TLS configuration |
| `list_integration_tests` | List integration tests |
| `list_lookup_tables` | List all lookup tables |
| `get_lookup_table` | Get lookup table schema and metadata |
| `query_lookup_table` | Query rows from a lookup table with filters and pagination |
| `create_lookup_table` | Create a new lookup table |
| `delete_lookup_table` | Delete a lookup table |
| `upsert_lookup_table_rows` | Insert or update rows in a lookup table |

**Example:** "Create a snapshot before I make configuration changes"

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

### Generate Tools

Create new plugins and actions.

| Tool | Description |
|------|-------------|
| `generate_plugin` | Create a new Java, Python, or Go plugin (Go requires a `module` path) |
| `generate_action` | Add an action to an existing plugin |
| `list_generated_plugins` | List plugins in the development repo |

**Example:** "Create a new Java plugin called json-normalizer with a transform action that converts JSON to YAML"

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

Use `get_flow` to see examples of existing flow configurations.

### Plugin Development

Scaffold new plugins with AI assistance:

```
User: "Create a Python plugin with an egress action that sends data to Kafka"

AI Assistant uses:
1. generate_plugin - Create the plugin skeleton
2. generate_action - Add the egress action
3. Then reads/edits the generated files to implement the Kafka logic
```

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
3. **Audit Trail**: All operations are logged through standard DeltaFi audit mechanisms

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
