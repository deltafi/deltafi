/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
// ABOUTME: MCP server command for AI agent integration
// ABOUTME: Starts a Model Context Protocol server exposing DeltaFi capabilities

package cmd

import (
	"github.com/spf13/cobra"

	"github.com/deltafi/tui/internal/mcp"
)

var mcpCmd = &cobra.Command{
	Use:   "mcp",
	Short: "Start MCP server for AI agent integration",
	Long: `Start a Model Context Protocol (MCP) server that exposes DeltaFi
capabilities to AI agents like Claude.

The MCP server communicates over stdio and allows AI agents to:
- Query and trace DeltaFiles
- Manage flows (start, stop, create, delete)
- Handle errors (resume, acknowledge, cancel)
- Install and manage plugins
- Configure system settings

Tools are organized into tag groups that can be selectively enabled:
- debug:      DeltaFile queries, tracing, content inspection
- operations: Resume, acknowledge, cancel, replay
- flows:      Flow CRUD, state control, topics
- generate:   Generate plugins and actions
- ingest:     Data source management
- plugins:    Plugin lifecycle
- admin:      Config, snapshots, policies
- metrics:    Stats, queue depths, system health
- all:        Enable all tools (default)

Examples:
  deltafi mcp                           # Start with all tools
  deltafi mcp --tags debug,operations   # Start with specific tool sets
  deltafi mcp --tags flows,ingest       # Flow management tools only

For Claude Desktop, add to your MCP config:
  {
    "mcpServers": {
      "deltafi": {
        "command": "deltafi",
        "args": ["mcp"]
      }
    }
  }`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		tags, _ := cmd.Flags().GetStringSlice("tags")
		return mcp.Serve(tags)
	},
}

func init() {
	rootCmd.AddCommand(mcpCmd)
	mcpCmd.Flags().StringSlice("tags", []string{"all"},
		"Tool tags to enable (debug,operations,flows,ingest,plugins,admin,metrics,all)")
}
