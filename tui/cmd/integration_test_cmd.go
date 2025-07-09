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
	"fmt"
	"os"
	"sort"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/progress"
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

// Progress bar types for test execution
type testProgressMsg struct {
	completedTests int
	totalTests     int
	results        map[string]*graphql.GetTestResultGetTestResult
	phase          string // "starting" or "running"
}

type testProgressModel struct {
	spinner   spinner.Model
	progress  progress.Model
	completed int
	total     int
	results   map[string]*graphql.GetTestResultGetTestResult
	testNames []string
	done      bool
	phase     string // "starting" or "running"
}

func initialTestProgressModel(testNames []string) testProgressModel {
	s := spinner.New()
	s.Spinner = spinner.Jump
	s.Style = styles.SuccessStyle

	p := progress.New(
		progress.WithGradient(styles.Base.Dark, styles.Blue.Dark),
		progress.WithWidth(40),
		progress.WithoutPercentage(),
	)

	return testProgressModel{
		spinner:   s,
		progress:  p,
		completed: 0,
		total:     0,
		results:   make(map[string]*graphql.GetTestResultGetTestResult),
		testNames: testNames,
		done:      false,
		phase:     "starting",
	}
}

func (m testProgressModel) Init() tea.Cmd {
	return tea.Batch(spinner.Tick)
}

func (m testProgressModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "q":
			return m, tea.Quit
		}
	case testProgressMsg:
		m.completed = msg.completedTests
		m.total = msg.totalTests
		m.results = msg.results
		m.phase = msg.phase
		// Check if all tests are done
		if m.completed >= m.total && m.phase == "running" {
			m.done = true
		}
		return m, nil
	case spinner.TickMsg:
		var cmd tea.Cmd
		m.spinner, cmd = m.spinner.Update(msg)
		return m, cmd
	}
	return m, nil
}

func (m testProgressModel) View() string {
	if m.total == 0 {
		spin := m.spinner.View()
		return fmt.Sprintf("\n%s Initializing...\n\n", spin)
	}

	if m.done {
		// Show final results summary and detailed results
		return m.renderFinalResults()
	}

	// Show progress bar and live status
	spin := m.spinner.View()
	percent := float64(m.completed) / float64(m.total)
	prog := m.progress.ViewAs(percent)

	if percent > 0.999 {
		spin = styles.SuccessStyle.Bold(true).Render("✓")
	}

	var sb strings.Builder
	phaseText := "tests started"
	if m.phase == "running" {
		phaseText = "tests completed"
	}
	sb.WriteString(fmt.Sprintf("\n%s %s %d/%d %s\n\n", spin, prog, m.completed, m.total, phaseText))

	// Add results table only during running phase
	if m.phase == "running" && len(m.testNames) > 0 {
		sb.WriteString("Test Status:\n")
		for _, testName := range m.testNames {
			result, exists := m.results[testName]
			var status string
			if !exists {
				status = styles.InfoStyle.Render("PENDING")
			} else {
				status = formatTestStatus(result.GetStatus())
			}
			sb.WriteString(fmt.Sprintf("  %-7s » %s\n", status, testName))
		}
		sb.WriteString("\n")
	}

	return sb.String()
}

func (m testProgressModel) renderFinalResults() string {
	var sb strings.Builder

	// Calculate summary statistics
	summary := calculateTestSummary(m.results)

	// Show completion message
	sb.WriteString(fmt.Sprintf("\n%s All tests completed!\n\n", styles.SuccessStyle.Bold(true).Render("✓")))

	// Show summary
	sb.WriteString(fmt.Sprintf("  Total Tests:  %d\n", summary.totalTests))
	sb.WriteString(fmt.Sprintf("  Successful:   %s\n", styles.SuccessStyle.Render(fmt.Sprint(summary.successCount))))
	if summary.failureCount > 0 {
		sb.WriteString(fmt.Sprintf("  Failed:       %s\n", styles.ErrorStyle.Render(fmt.Sprint(summary.failureCount))))
	} else {
		sb.WriteString(fmt.Sprintf("  Failed:       %d\n", summary.failureCount))
	}
	sb.WriteString("\n")

	// Show detailed results table
	if len(m.results) > 0 {
		// Create table rows
		var rows []testResultRow
		for _, testName := range m.testNames {
			result := m.results[testName]
			rows = append(rows, createTestResultRow(testName, result))
		}

		// Try to render with terminal width, fallback to plain if too wide
		terminalWidth := getTerminalWidth()
		if terminalWidth < 80 {
			// Use plain text format for narrow terminals
			for _, row := range rows {
				sb.WriteString(fmt.Sprintf("  %s: %s (%s) - %s\n", row.testName, row.status, row.duration, row.errors))
			}
		} else {
			// Convert to string rows for table
			var stringRows [][]string
			for _, row := range rows {
				stringRows = append(stringRows, []string{row.testName, row.status, row.duration, row.errors})
			}

			columns := []string{"Test Name", "Status", "Duration", "Errors"}
			t := api.NewTable(columns, stringRows)
			table := components.NewSimpleTable(&t).Width(terminalWidth)
			sb.WriteString(table.Render())
		}
		sb.WriteString("\n")
	}

	return sb.String()
}

// Helper functions to reduce duplication

// validateTestResults checks if any tests failed and returns appropriate error
func validateTestResults(summary testSummary) error {
	if summary.failureCount > 0 {
		return fmt.Errorf("%d test(s) failed", summary.failureCount)
	}
	return nil
}

// formatTestStatus formats a test status with appropriate styling
func formatTestStatus(status graphql.TestStatus) string {
	switch status {
	case graphql.TestStatusSuccessful:
		return styles.SuccessStyle.Render("SUCCESS")
	case graphql.TestStatusFailed:
		return styles.ErrorStyle.Render("FAILED")
	case graphql.TestStatusInvalid:
		return styles.WarningStyle.Render("INVALID")
	default:
		return styles.InfoStyle.Render(fmt.Sprintf("%s", status))
	}
}

// calculateTestDuration calculates the duration between start and stop times
func calculateTestDuration(startTime, stopTime *time.Time) string {
	if startTime != nil && stopTime != nil {
		return stopTime.Sub(*startTime).String()
	}
	return "N/A"
}

// formatTestErrors formats test errors into a single string
func formatTestErrors(errors []*string) string {
	if len(errors) == 0 {
		return "None"
	}

	var errorStrings []string
	for _, err := range errors {
		if err != nil {
			errorStrings = append(errorStrings, *err)
		}
	}
	return strings.Join(errorStrings, "; ")
}

// testResultRow represents a single row in a test results table
type testResultRow struct {
	testName string
	status   string
	duration string
	errors   string
}

// createTestResultRow creates a test result row from a test result
func createTestResultRow(testName string, result *graphql.GetTestResultGetTestResult) testResultRow {
	return testResultRow{
		testName: testName,
		status:   formatTestStatus(result.GetStatus()),
		duration: calculateTestDuration(result.GetStart(), result.GetStop()),
		errors:   formatTestErrors(result.GetErrors()),
	}
}

// createTestResultRowFromResult creates a test result row from a GetTestResults result
func createTestResultRowFromResult(result graphql.GetTestResultsGetTestResultsTestResult) testResultRow {
	return testResultRow{
		testName: *result.GetTestName(),
		status:   formatTestStatus(result.GetStatus()),
		duration: calculateTestDuration(result.GetStart(), result.GetStop()),
		errors:   formatTestErrors(result.GetErrors()),
	}
}

// testSummary represents summary statistics for test results
type testSummary struct {
	totalTests   int
	successCount int
	failureCount int
}

// calculateTestSummary calculates summary statistics from test results
func calculateTestSummary(results map[string]*graphql.GetTestResultGetTestResult) testSummary {
	var successCount, failureCount int
	for _, result := range results {
		switch result.GetStatus() {
		case graphql.TestStatusSuccessful:
			successCount++
		case graphql.TestStatusFailed, graphql.TestStatusInvalid:
			failureCount++
		}
	}
	return testSummary{
		totalTests:   len(results),
		successCount: successCount,
		failureCount: failureCount,
	}
}

// calculateTestSummaryFromResults calculates summary statistics from GetTestResults
func calculateTestSummaryFromResults(results []graphql.GetTestResultsGetTestResultsTestResult) testSummary {
	var successCount, failureCount int
	for _, result := range results {
		switch result.GetStatus() {
		case graphql.TestStatusSuccessful:
			successCount++
		case graphql.TestStatusFailed, graphql.TestStatusInvalid:
			failureCount++
		}
	}
	return testSummary{
		totalTests:   len(results),
		successCount: successCount,
		failureCount: failureCount,
	}
}

// renderTestResultsTable renders a table of test results
func renderTestResultsTable(rows []testResultRow, plain bool) {
	if len(rows) == 0 {
		return
	}

	// Convert to string rows for table
	var stringRows [][]string
	for _, row := range rows {
		stringRows = append(stringRows, []string{row.testName, row.status, row.duration, row.errors})
	}

	columns := []string{"Test Name", "Status", "Duration", "Errors"}
	t := api.NewTable(columns, stringRows)

	if plain {
		renderAsSimpleTable(t, plain)
	} else {
		renderAsSimpleTableWithWidth(t, getTerminalWidth())
	}
}

// renderTestResultsSummary prints a summary of test results
func renderTestResultsSummary(summary testSummary) {
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("  Total Tests:  %d\n", summary.totalTests))
	sb.WriteString(fmt.Sprintf("  Successful:   %s\n", styles.SuccessStyle.Render(fmt.Sprint(summary.successCount))))
	if summary.failureCount > 0 {
		sb.WriteString(fmt.Sprintf("  Failed:       %s\n", styles.ErrorStyle.Render(fmt.Sprint(summary.failureCount))))
	} else {
		sb.WriteString(fmt.Sprintf("  Failed:       %d\n", summary.failureCount))
	}
	fmt.Println()
	fmt.Println(sb.String())
}

// renderTestResultsSummaryWithTable prints a summary and table of test results
func renderTestResultsSummaryWithTable(rows []testResultRow, summary testSummary, plain bool) {
	renderTestResultsSummary(summary)

	if len(rows) > 0 {
		renderTestResultsTable(rows, plain)
	}
}

var integrationTestCmd = &cobra.Command{
	Use:   "integration-test",
	Short: "Manage integration tests",
	Long: `Configure and manage integration tests for DeltaFi.

Integration tests allow you to test complete data flows by providing
test inputs and expected outputs. They can validate that data sources,
transformation flows, and data sinks work together correctly.

Examples:
  deltafi integration-test list                    # List all tests
  deltafi integration-test list --short            # List test names only
  deltafi integration-test load test-config.yaml   # Load test from file
  deltafi integration-test run                     # Run all tests
  deltafi integration-test run test1 test2         # Run specific tests
  deltafi integration-test summary                 # Show test results summary`,
	GroupID:            "testing",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listIntegrationTests = &cobra.Command{
	Use:          "list [test-names...]",
	Short:        "Show integration tests",
	Long:         `Display integration tests with their details. If no test names are provided, all tests are shown.`,
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		resp, err := graphql.GetIntegrationTests()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		var values []string
		for _, test := range resp.GetIntegrationTests {
			values = append(values, *test.Name)
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listIntegrationTestsByName(cmd, args)
	},
}

var loadIntegrationTest = &cobra.Command{
	Use:   "load [file]",
	Short: "Load integration test configuration",
	Long: `Load an integration test from a configuration file.

The file should contain integration test settings in YAML format.
If an integration test with the same name already exists, it will be replaced.

Examples:
  deltafi integration-test load test-config.yaml
  deltafi integration-test load *.yaml`,
	Args:         cobra.MinimumNArgs(1),
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		var lastErr error
		for _, filename := range args {
			content, err := os.ReadFile(filename)
			if err != nil {
				fmt.Println(styles.FAIL(fmt.Sprintf("Error reading file %s: %v\n", filename, err)))
				lastErr = err
				continue
			}

			// Load the integration test
			resp, err := graphql.LoadIntegrationTest(string(content))
			if err != nil {
				fmt.Println(styles.FAIL(fmt.Sprintf("Error loading integration test from %s: %v\n", filename, err)))
				lastErr = err
				continue
			}

			if resp.LoadIntegrationTest.Success {
				fmt.Println(styles.OK(fmt.Sprintf("Loaded %s", filename)))
			} else {
				fmt.Println(styles.FAIL(fmt.Sprintf("Failed to load %s", filename)))
				if len(resp.LoadIntegrationTest.Errors) > 0 {
					for _, err := range resp.LoadIntegrationTest.Errors {
						fmt.Println("  Error:", err)
					}
				}
				lastErr = fmt.Errorf("failed to load integration test from %s", filename)
			}
		}

		return lastErr
	},
}

var runIntegrationTest = &cobra.Command{
	Use:   "run [test-names...]",
	Short: "Run integration tests",
	Long: `Run integration tests and wait for results.

Starts the specified integration tests and polls for completion.
If no test names are provided, all available tests will be run.
Use --like to run tests that match partial strings (can specify multiple times).

Examples:
  deltafi integration-test run                    # Run all tests
  deltafi integration-test run test1 test2       # Run specific tests
  deltafi integration-test run --like "smoke"    # Run tests containing "smoke"
  deltafi integration-test run --like "test" --like "integration"  # Run tests containing either "test" or "integration"
  deltafi integration-test run --timeout 5m      # Set custom timeout`,
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		resp, err := graphql.GetIntegrationTests()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		var values []string
		for _, test := range resp.GetIntegrationTests {
			values = append(values, *test.Name)
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return runIntegrationTestsByName(cmd, args)
	},
}

var summaryIntegrationTest = &cobra.Command{
	Use:   "summary",
	Short: "Show integration test results summary",
	Long: `Display a summary of all integration test results.

Shows all test execution results with their status, duration, and any errors.
Results are sorted by test name and execution time.

Examples:
  deltafi integration-test summary              # Show all test results
  deltafi integration-test summary --json      # Output in JSON format
  deltafi integration-test summary --plain     # Plain text output`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return showTestResultsSummary(cmd)
	},
}

var clearIntegrationTestResults = &cobra.Command{
	Use:          "clear",
	Short:        "Clear all integration test results",
	Long:         `Remove all integration test results from the system.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		resp, err := graphql.GetTestResults()
		if err != nil {
			return wrapInError("Error getting test results", err)
		}
		results := resp.GetTestResults
		if len(results) == 0 {
			fmt.Println("No test results to remove.")
			return nil
		}
		errors := 0
		for _, result := range results {
			id := result.GetId()
			if id == nil || *id == "" {
				continue
			}
			resp, err := graphql.RemoveTestResult(*id)
			if err != nil || resp == nil || !resp.GetRemoveTestResult() {
				fmt.Printf("Failed to remove test result %s: %v\n", *id, err)
				errors++
			}
		}
		fmt.Printf("Removed %d test result(s).\n", len(results)-errors)
		if errors > 0 {
			return fmt.Errorf("%d test result(s) could not be removed", errors)
		}
		return nil
	},
}

var removeIntegrationTestCmd = &cobra.Command{
	Use:          "remove [test-names...]",
	Short:        "Remove integration tests",
	Long:         `Remove one or more integration tests by name.`,
	Args:         cobra.MinimumNArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		resp, err := graphql.GetIntegrationTests()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		var values []string
		for _, test := range resp.GetIntegrationTests {
			values = append(values, *test.Name)
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		var failed []string
		for _, testName := range args {
			resp, err := graphql.RemoveIntegrationTest(testName)
			if err != nil || resp == nil || !resp.GetRemoveIntegrationTest() {
				failed = append(failed, testName)
				continue
			}
			fmt.Printf("Removed integration test: %s\n", testName)
		}
		if len(failed) > 0 {
			fmt.Printf("Failed to remove %d test(s): %s\n", len(failed), strings.Join(failed, ", "))
			return fmt.Errorf("some tests could not be removed")
		}
		return nil
	},
}

func listIntegrationTestsByName(cmd *cobra.Command, args []string) error {
	resp, err := graphql.GetIntegrationTests()
	if err != nil {
		return wrapInError("Error getting the list of integration tests", err)
	}

	// If no test names provided, show all tests
	if len(args) == 0 {
		return listAllIntegrationTests(cmd, resp)
	}

	// Filter tests by provided names
	var filteredTests []graphql.GetIntegrationTestsGetIntegrationTestsIntegrationTest
	for _, testName := range args {
		found := false
		for _, test := range resp.GetIntegrationTests {
			if *test.Name == testName {
				filteredTests = append(filteredTests, test)
				found = true
				break
			}
		}
		if !found {
			fmt.Printf("Warning: Integration test '%s' not found\n", testName)
		}
	}

	if len(filteredTests) == 0 {
		return fmt.Errorf("no integration tests found matching the provided names")
	}

	return listAllIntegrationTests(cmd, &graphql.GetIntegrationTestsResponse{
		GetIntegrationTests: filteredTests,
	})
}

func listAllIntegrationTests(cmd *cobra.Command, resp *graphql.GetIntegrationTestsResponse) error {

	short, _ := cmd.Flags().GetBool("short")
	json, _ := cmd.Flags().GetBool("json")
	plain, _ := cmd.Flags().GetBool("plain")

	if json {
		// Return JSON output (takes precedence over other formats)
		// If there's only one test, output it directly without the array wrapper
		if len(resp.GetIntegrationTests) == 1 {
			return printJSON(resp.GetIntegrationTests[0], plain)
		}
		return printJSON(resp.GetIntegrationTests, plain)
	}

	if short {
		// Just print the names
		var names []string
		for _, test := range resp.GetIntegrationTests {
			names = append(names, *test.Name)
		}
		sort.Strings(names)
		for _, name := range names {
			fmt.Println(name)
		}
		return nil
	}

	// Create detailed table for each test
	for _, test := range resp.GetIntegrationTests {

		// Format plugins
		var pluginStrs []string
		for _, plugin := range test.Plugins {
			pluginStrs = append(pluginStrs, fmt.Sprintf("%s:%s:%s", plugin.GroupId, plugin.ArtifactId, plugin.Version))
		}
		plugins := strings.Join(pluginStrs, ", ")

		// Format data sources
		var dataSourceStrs []string
		for _, ds := range test.DataSources {
			dataSourceStrs = append(dataSourceStrs, *ds)
		}
		dataSources := strings.Join(dataSourceStrs, ", ")

		// Format transformation flows
		var transformStrs []string
		for _, tf := range test.TransformationFlows {
			transformStrs = append(transformStrs, *tf)
		}
		transformationFlows := strings.Join(transformStrs, ", ")

		// Format data sinks
		var sinkStrs []string
		for _, sink := range test.DataSinks {
			sinkStrs = append(sinkStrs, *sink)
		}
		dataSinks := strings.Join(sinkStrs, ", ")

		// Format inputs count
		inputCount := fmt.Sprintf("%d", len(test.Inputs))

		// Format expected delta files count
		expectedDeltaFilesCount := fmt.Sprintf("%d", len(test.ExpectedDeltaFiles))

		// Create table rows where each row is an attribute
		var rows [][]string
		rows = append(rows, []string{"Description", *test.Description})
		rows = append(rows, []string{"Plugins", plugins})
		rows = append(rows, []string{"Data Sources", dataSources})
		rows = append(rows, []string{"Transformation Flows", transformationFlows})
		rows = append(rows, []string{"Data Sinks", dataSinks})
		rows = append(rows, []string{"Timeout", *test.Timeout})
		rows = append(rows, []string{"Inputs", inputCount})
		rows = append(rows, []string{"Expected DeltaFiles", expectedDeltaFilesCount})

		plain, _ := cmd.Flags().GetBool("plain")

		if plain {
			columns := []string{"Test", *test.Name}
			t := api.NewTable(columns, rows)
			renderAsSimpleTable(t, plain)
		} else {
			columns := []string{"", *test.Name}
			t := api.NewTable(columns, rows)
			renderAsSimpleTableWithWidth(t, getTerminalWidth())
		}
	}
	return nil
}

func runIntegrationTestsByName(cmd *cobra.Command, args []string) error {
	resp, err := graphql.GetIntegrationTests()
	if err != nil {
		return wrapInError("Error getting the list of integration tests", err)
	}

	likePatterns, _ := cmd.Flags().GetStringSlice("like")

	// If no test names provided and no like patterns, run all tests
	if len(args) == 0 && len(likePatterns) == 0 {
		return runAllIntegrationTests(cmd, resp)
	}

	// Filter tests by provided names and/or like patterns
	var filteredTests []graphql.GetIntegrationTestsGetIntegrationTestsIntegrationTest

	// If like patterns are provided, filter by them (OR logic - any pattern can match)
	if len(likePatterns) > 0 {
		for _, test := range resp.GetIntegrationTests {
			testNameLower := strings.ToLower(*test.Name)
			for _, pattern := range likePatterns {
				if strings.Contains(testNameLower, strings.ToLower(pattern)) {
					filteredTests = append(filteredTests, test)
					break // Only add each test once, even if it matches multiple patterns
				}
			}
		}
	}

	// If specific test names are provided, filter by them (and intersect with like if both are used)
	if len(args) > 0 {
		if len(likePatterns) > 0 {
			// Both like patterns and specific names provided - intersect them
			var nameFilteredTests []graphql.GetIntegrationTestsGetIntegrationTestsIntegrationTest
			for _, testName := range args {
				found := false
				for _, test := range filteredTests {
					if *test.Name == testName {
						nameFilteredTests = append(nameFilteredTests, test)
						found = true
						break
					}
				}
				if !found {
					fmt.Printf("Warning: Integration test '%s' not found or doesn't match --like patterns\n", testName)
				}
			}
			filteredTests = nameFilteredTests
		} else {
			// Only specific names provided
			for _, testName := range args {
				found := false
				for _, test := range resp.GetIntegrationTests {
					if *test.Name == testName {
						filteredTests = append(filteredTests, test)
						found = true
						break
					}
				}
				if !found {
					fmt.Printf("Warning: Integration test '%s' not found\n", testName)
				}
			}
		}
	}

	if len(filteredTests) == 0 {
		if len(likePatterns) > 0 {
			return fmt.Errorf("no integration tests found matching patterns: %s", strings.Join(likePatterns, ", "))
		}
		return fmt.Errorf("no integration tests found matching the provided names")
	}

	return runAllIntegrationTests(cmd, &graphql.GetIntegrationTestsResponse{
		GetIntegrationTests: filteredTests,
	})
}

func runAllIntegrationTests(cmd *cobra.Command, resp *graphql.GetIntegrationTestsResponse) error {
	timeout, _ := cmd.Flags().GetDuration("timeout")

	if len(resp.GetIntegrationTests) == 0 {
		return fmt.Errorf("no integration tests available to run")
	}

	// Set default timeout if not specified
	if timeout == 0 {
		timeout = 30 * time.Minute
	}

	// Get test names for progress bar
	var testNames []string
	for _, test := range resp.GetIntegrationTests {
		testNames = append(testNames, *test.Name)
	}
	sort.Strings(testNames)

	// Create and start progress bar
	p := tea.NewProgram(initialTestProgressModel(testNames))
	done := make(chan struct{})
	go func() {
		if _, err := p.Run(); err != nil {
			fmt.Printf("Error running progress bar: %v\n", err)
		}
		close(done)
	}()

	// Send initial progress
	totalTests := len(resp.GetIntegrationTests)
	p.Send(testProgressMsg{
		completedTests: 0,
		totalTests:     totalTests,
		results:        make(map[string]*graphql.GetTestResultGetTestResult),
		phase:          "starting",
	})

	// Start all tests with progress bar
	testExecutions := make(map[string]string) // test name -> execution ID
	var failedStarts []string
	startedTests := 0

	for _, test := range resp.GetIntegrationTests {
		testName := *test.Name

		startResp, err := graphql.StartIntegrationTest(testName)
		if err != nil {
			failedStarts = append(failedStarts, testName)
			continue
		}

		executionID := *startResp.StartIntegrationTest.Id
		testExecutions[testName] = executionID
		startedTests++

		// Update progress bar for test startup
		p.Send(testProgressMsg{
			completedTests: startedTests,
			totalTests:     totalTests,
			results:        make(map[string]*graphql.GetTestResultGetTestResult),
			phase:          "starting",
		})
	}

	// Report any failed starts
	if len(failedStarts) > 0 {
		fmt.Println(styles.ErrorStyle.Render(fmt.Sprintf("\nFailed to start %d test(s):", len(failedStarts))))
		for _, testName := range failedStarts {
			fmt.Printf("  - %s\n", testName)
		}
	}

	if len(testExecutions) == 0 {
		return fmt.Errorf("no tests were successfully started")
	}

	// Switch to running phase
	p.Send(testProgressMsg{
		completedTests: 0,
		totalTests:     len(testExecutions),
		results:        make(map[string]*graphql.GetTestResultGetTestResult),
		phase:          "running",
	})

	// Poll for test completion
	results := make(map[string]*graphql.GetTestResultGetTestResult)
	startTime := time.Now()
	pollInterval := 500 * time.Millisecond
	completedTests := 0

	for len(results) < len(testExecutions) && time.Since(startTime) < timeout {
		for testName, executionID := range testExecutions {
			if _, exists := results[testName]; exists {
				continue // Already completed
			}

			resultResp, err := graphql.GetTestResult(executionID)
			if err != nil {
				continue
			}

			result := resultResp.GetTestResult
			status := result.GetStatus()

			// Check if test is still running
			if status == graphql.TestStatusStarted {
				continue // Still running
			}

			// Test completed
			results[testName] = &result
			completedTests++

			// Update progress bar
			p.Send(testProgressMsg{
				completedTests: completedTests,
				totalTests:     len(testExecutions),
				results:        results,
				phase:          "running",
			})
		}

		if len(results) < len(testExecutions) {
			time.Sleep(pollInterval)
		}
	}

	// Send final progress update and quit
	p.Send(testProgressMsg{
		completedTests: len(testExecutions),
		totalTests:     len(testExecutions),
		results:        results,
		phase:          "running",
	})

	// Give the progress bar time to render the final results
	time.Sleep(500 * time.Millisecond)

	p.Quit()
	<-done // Wait for the progress bar program to finish

	// Check for timeout
	if len(results) < len(testExecutions) {
		fmt.Println(styles.ErrorStyle.Render(fmt.Sprintf("\nTimeout reached (%v). Some tests may still be running.", timeout)))
		for testName := range testExecutions {
			if _, exists := results[testName]; !exists {
				fmt.Printf("Test '%s' did not complete within timeout\n", testName)
			}
		}
	}

	// Check if any tests failed for exit code
	summary := calculateTestSummary(results)
	return validateTestResults(summary)
}

func displayTestResults(cmd *cobra.Command, results map[string]*graphql.GetTestResultGetTestResult, json, plain bool) error {
	if json {
		// Return JSON output
		if len(results) == 1 {
			for _, result := range results {
				return printJSON(result, plain)
			}
		}
		return printJSON(results, plain)
	}

	// Create test result rows
	var rows []testResultRow
	for testName, result := range results {
		rows = append(rows, createTestResultRow(testName, result))
	}

	// Sort by test name
	sort.Slice(rows, func(i, j int) bool {
		return rows[i].testName < rows[j].testName
	})

	// Calculate summary and display results
	summary := calculateTestSummary(results)
	renderTestResultsSummaryWithTable(rows, summary, plain)

	return validateTestResults(summary)
}

func showTestResultsSummary(cmd *cobra.Command) error {
	resp, err := graphql.GetTestResults()
	if err != nil {
		return wrapInError("Error getting all test results", err)
	}

	json, _ := cmd.Flags().GetBool("json")
	plain, _ := cmd.Flags().GetBool("plain")

	if json {
		return printJSON(resp.GetTestResults, plain)
	}

	// Create test result rows
	var rows []testResultRow
	for _, result := range resp.GetTestResults {
		rows = append(rows, createTestResultRowFromResult(result))
	}

	// Sort by test name
	sort.Slice(rows, func(i, j int) bool {
		return rows[i].testName < rows[j].testName
	})

	// Calculate summary and display results
	summary := calculateTestSummaryFromResults(resp.GetTestResults)
	renderTestResultsSummaryWithTable(rows, summary, plain)

	return validateTestResults(summary)
}

func init() {
	rootCmd.AddCommand(integrationTestCmd)
	integrationTestCmd.AddCommand(listIntegrationTests)
	integrationTestCmd.AddCommand(loadIntegrationTest)
	integrationTestCmd.AddCommand(runIntegrationTest)
	integrationTestCmd.AddCommand(summaryIntegrationTest)
	integrationTestCmd.AddCommand(clearIntegrationTestResults)
	integrationTestCmd.AddCommand(removeIntegrationTestCmd)

	listIntegrationTests.Flags().BoolP("short", "s", false, "Show only test names")
	listIntegrationTests.Flags().BoolP("plain", "p", false, "Plain output format")
	listIntegrationTests.Flags().BoolP("json", "j", false, "Output in JSON format")
	loadIntegrationTest.Flags().BoolP("plain", "p", false, "Plain output format")
	runIntegrationTest.Flags().DurationP("timeout", "t", 30*time.Minute, "Timeout for test execution")
	runIntegrationTest.Flags().StringSliceP("like", "l", []string{}, "Run tests whose names contain this string (case-insensitive, can be specified multiple times)")
	summaryIntegrationTest.Flags().BoolP("json", "j", false, "Output in JSON format")
	summaryIntegrationTest.Flags().BoolP("plain", "p", false, "Plain output format")
}
