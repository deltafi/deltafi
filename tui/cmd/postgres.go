/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package cmd

import (
	"bytes"
	"fmt"
	"os"
	"os/exec"
	"strings"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/spf13/cobra"
)

var postgresCmd = &cobra.Command{
	Use:          "postgres",
	Short:        "Command line access to the running DeltaFi Postgres instance",
	Long:         `Command line access to the running DeltaFi Postgres instance`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var cliCmd = &cobra.Command{
	Use:   "cli",
	Short: "Start an interactive Postgres CLI session",
	Long:  "Start an interactive Postgres CLI session",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, error := app.GetOrchestrator().GetPostgresCmd(args)
		if error != nil {
			return error
		}

		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		if err := c.Run(); err != nil {
			return fmt.Errorf("command execution failed: %w", err)
		}

		return nil
	},
}

var execCmd = &cobra.Command{
	Use:   "eval",
	Short: "Pipe commands to Postgres from stdin",
	Long:  "Opens a non-interactive session with postgres and pipes commands to it from stdin.",
	Example: `
	cat query.sql | deltafi postgres eval
	deltafi postgres eval < query.sql
	deltafi postgres eval -- -e < query.sql`,
	RunE: func(cmd *cobra.Command, args []string) error {
		c, error := app.GetOrchestrator().GetPostgresExecCmd(args)
		if error != nil {
			return error
		}

		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		if err := c.Run(); err != nil {
			return fmt.Errorf("command execution failed: %w", err)
		}

		return nil
	},
}

var postgresStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show Postgres instance status and administrative information",
	Long:  "Display comprehensive information about the Postgres instance including storage usage, table sizes, row counts, connections, and other administrative metrics.",
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		// SQL queries to gather comprehensive status information
		queries := map[string]string{
			"version":         "SELECT version();",
			"database_size":   "SELECT pg_database.datname as database_name, pg_size_pretty(pg_database_size(pg_database.datname)) as size FROM pg_database WHERE pg_database.datname = current_database();",
			"table_sizes":     "SELECT relname AS table, pg_size_pretty(pg_total_relation_size(relid)) AS total_size FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 10;",
			"row_counts":      "SELECT relname AS table, n_live_tup AS live_rows, n_dead_tup AS dead_rows FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10;",
			"connections":     "SELECT state, count(*) as count FROM pg_stat_activity GROUP BY state ORDER BY count DESC;",
			"active_queries":  "SELECT pid, usename, state, query_start, query FROM pg_stat_activity WHERE state = 'active' AND query NOT LIKE '%pg_stat_activity%' ORDER BY query_start LIMIT 5;",
			"index_usage":     "SELECT relname AS table, indexrelname AS index, idx_scan AS scans FROM pg_stat_user_indexes ORDER BY idx_scan DESC LIMIT 10;",
			"cache_hit_ratio": "SELECT round(100.0 * sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)), 2) as cache_hit_ratio FROM pg_statio_user_tables;",
			"locks":           "SELECT mode, count(*) as count FROM pg_locks WHERE granted = true GROUP BY mode ORDER BY count DESC;",
			"flyway_summary":  "SELECT COUNT(*) FILTER (WHERE success = true) AS applied, COUNT(*) FILTER (WHERE success = false) AS failed, MAX(version) AS latest_version FROM flyway_schema_history;",
			"flyway_recent":   "SELECT version, description, type, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;",
		}

		// Execute queries and collect results
		results := make(map[string]string)
		for name, query := range queries {
			output, err := executePostgresQuery(query)
			if err != nil {
				results[name] = fmt.Sprintf("Error: %v", err)
			} else {
				results[name] = output
			}
		}

		// Format and display results
		return displayPostgresStatus(cmd, results)
	},
}

var postgresMigrationsCmd = &cobra.Command{
	Use:   "migrations",
	Short: "Show detailed Flyway migration information",
	Long:  "Display a comprehensive table of all Flyway migrations with version, description, type, installation details, and execution information.",
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		// Query to get all Flyway migrations with detailed information
		query := `
			SELECT 
				version,
				description,
				script,
				installed_on,
				execution_time,
				success
			FROM flyway_schema_history 
			ORDER BY installed_rank ASC;
		`

		output, err := executePostgresQuery(query)
		if err != nil {
			return fmt.Errorf("failed to query Flyway migrations: %w", err)
		}

		// Parse the SQL output into structured data
		migrationsTable, err := parseSQLOutput(output)
		if err != nil {
			return fmt.Errorf("failed to parse migration data: %w", err)
		}

		// Display the results using SimpleTable
		plain, _ := cmd.Flags().GetBool("plain")
		renderAsSimpleTable(migrationsTable, plain)

		// Also show a summary
		summaryQuery := `
			SELECT 
				COUNT(*) as total_migrations,
				COUNT(*) FILTER (WHERE success = true) as successful,
				COUNT(*) FILTER (WHERE success = false) as failed,
				MAX(version) as latest_version,
				MAX(installed_on) as last_migration_time
			FROM flyway_schema_history;
		`

		summaryOutput, err := executePostgresQuery(summaryQuery)
		if err != nil {
			return fmt.Errorf("failed to query Flyway summary: %w", err)
		}

		// Parse summary data
		summaryTable, err := parseSQLOutput(summaryOutput)
		if err != nil {
			return fmt.Errorf("failed to parse summary data: %w", err)
		}

		renderAsSimpleTable(summaryTable, plain)

		return nil
	},
}

// executePostgresQuery executes a SQL query and returns the formatted output
func executePostgresQuery(query string) (string, error) {
	orchestrator := app.GetOrchestrator()

	// For non-interactive queries, we need to construct the command differently
	// to avoid TTY allocation issues
	var cmd *exec.Cmd

	switch o := orchestrator.(type) {
	case *orchestration.ComposeOrchestrator:
		// For compose, construct the command manually to avoid TTY issues
		psqlCmd := fmt.Sprintf("psql -v HISTFILE=/tmp/psql_history $POSTGRES_DB -c \"%s\"", query)
		cmd = exec.Command("docker", "exec", "-i", "deltafi-postgres", "bash", "-c", psqlCmd)
	case *orchestration.KubernetesOrchestrator:
		// For Kubernetes, use the existing method but ensure non-interactive
		c, err := o.GetPostgresExecCmd([]string{"-c", query})
		if err != nil {
			return "", err
		}
		cmd = &c
	default:
		return "", fmt.Errorf("unsupported orchestrator type")
	}

	// Capture both stdout and stderr
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		return "", fmt.Errorf("query failed: %w, stderr: %s", err, stderr.String())
	}

	return stdout.String(), nil
}

// displayPostgresStatus formats and displays the collected status information
func displayPostgresStatus(cmd *cobra.Command, results map[string]string) error {
	plain, _ := cmd.Flags().GetBool("plain")

	// Version
	if version, ok := results["version"]; ok {
		fmt.Println("📋 Version:")
		versionTable, err := parseSQLOutput(version)
		if err == nil {
			renderAsSimpleTable(versionTable, plain)
		} else {
			fmt.Println(version)
		}
		fmt.Println()
	}

	// Database size
	if dbSize, ok := results["database_size"]; ok {
		fmt.Println("💾 Database Size:")
		dbSizeTable, err := parseSQLOutput(dbSize)
		if err == nil {
			renderAsSimpleTable(dbSizeTable, plain)
		} else {
			fmt.Println(dbSize)
		}
		fmt.Println()
	}

	// Cache hit ratio
	if cacheRatio, ok := results["cache_hit_ratio"]; ok {
		fmt.Println("🎯 Cache Hit Ratio:")
		cacheTable, err := parseSQLOutput(cacheRatio)
		if err == nil {
			renderAsSimpleTable(cacheTable, plain)
		} else {
			fmt.Println(cacheRatio)
		}
		fmt.Println()
	}

	// Connections
	if connections, ok := results["connections"]; ok {
		fmt.Println("🔗 Active Connections:")
		connectionsTable, err := parseSQLOutput(connections)
		if err == nil {
			renderAsSimpleTable(connectionsTable, plain)
		} else {
			fmt.Println(connections)
		}
		fmt.Println()
	}

	// Table sizes
	if tableSizes, ok := results["table_sizes"]; ok {
		fmt.Println("📊 Table Sizes (by total size):")
		tableSizesTable, err := parseSQLOutput(tableSizes)
		if err == nil {
			renderAsSimpleTable(tableSizesTable, plain)
		} else {
			fmt.Println(tableSizes)
		}
		fmt.Println()
	}

	// Row counts and statistics
	if rowCounts, ok := results["row_counts"]; ok {
		fmt.Println("📈 Table Statistics:")
		rowCountsTable, err := parseSQLOutput(rowCounts)
		if err == nil {
			renderAsSimpleTable(rowCountsTable, plain)
		} else {
			fmt.Println(rowCounts)
		}
		fmt.Println()
	}

	// Index usage
	if indexUsage, ok := results["index_usage"]; ok {
		fmt.Println("🔍 Top Index Usage:")
		indexUsageTable, err := parseSQLOutput(indexUsage)
		if err == nil {
			renderAsSimpleTable(indexUsageTable, plain)
		} else {
			fmt.Println(indexUsage)
		}
		fmt.Println()
	}

	// Active queries
	if activeQueries, ok := results["active_queries"]; ok {
		if strings.TrimSpace(activeQueries) != "" {
			fmt.Println("⚡ Active Queries:")
			activeQueriesTable, err := parseSQLOutput(activeQueries)
			if err == nil {
				renderAsSimpleTable(activeQueriesTable, plain)
			} else {
				fmt.Println(activeQueries)
			}
			fmt.Println()
		}
	}

	// Locks
	if locks, ok := results["locks"]; ok {
		fmt.Println("🔒 Current Locks:")
		locksTable, err := parseSQLOutput(locks)
		if err == nil {
			renderAsSimpleTable(locksTable, plain)
		} else {
			fmt.Println(locks)
		}
		fmt.Println()
	}

	// Flyway summary
	if flywaySummary, ok := results["flyway_summary"]; ok {
		fmt.Println("🦋 Flyway Migration Summary:")
		flywaySummaryTable, err := parseSQLOutput(flywaySummary)
		if err == nil {
			renderAsSimpleTable(flywaySummaryTable, plain)
		} else {
			fmt.Println(flywaySummary)
		}
		fmt.Println()
	}

	// Flyway recent migrations
	if flywayRecent, ok := results["flyway_recent"]; ok {
		fmt.Println("🦋 Recent Flyway Migrations:")
		flywayRecentTable, err := parseSQLOutput(flywayRecent)
		if err == nil {
			renderAsSimpleTable(flywayRecentTable, plain)
		} else {
			fmt.Println(flywayRecent)
		}
		fmt.Println()
	}

	return nil
}

// parseSQLOutput parses the output from a SQL query into an api.Table
func parseSQLOutput(output string) (api.Table, error) {
	lines := strings.Split(strings.TrimSpace(output), "\n")
	if len(lines) < 3 {
		return api.Table{}, fmt.Errorf("insufficient data in SQL output")
	}

	// Find the header line (contains column names)
	var headerLine string
	var dataStartIndex int
	for i, line := range lines {
		if strings.Contains(line, "|") && !strings.Contains(line, "---") {
			headerLine = line
			dataStartIndex = i + 1
			break
		}
	}

	if headerLine == "" {
		return api.Table{}, fmt.Errorf("could not find header line")
	}

	// Parse headers
	headers := parseTableRow(headerLine)

	// Parse data rows
	var rows [][]string
	for i := dataStartIndex; i < len(lines); i++ {
		line := strings.TrimSpace(lines[i])
		if line == "" || strings.Contains(line, "(") && strings.Contains(line, "row") {
			break // End of data
		}
		if strings.Contains(line, "|") {
			row := parseTableRow(line)
			if len(row) == len(headers) {
				rows = append(rows, row)
			}
		}
	}

	return api.NewTable(headers, rows), nil
}

// parseTableRow parses a single row from the SQL output
func parseTableRow(line string) []string {
	// Split by | and trim whitespace
	parts := strings.Split(line, "|")
	var result []string
	for _, part := range parts {
		result = append(result, strings.TrimSpace(part))
	}
	return result
}

func init() {
	rootCmd.AddCommand(postgresCmd)
	postgresCmd.AddCommand(cliCmd)
	postgresCmd.AddCommand(execCmd)
	postgresCmd.AddCommand(postgresStatusCmd)
	postgresCmd.AddCommand(postgresMigrationsCmd)

	// Add flags
	postgresStatusCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	postgresMigrationsCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
}
