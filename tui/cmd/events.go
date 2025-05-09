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
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

var eventCmd = &cobra.Command{
	Use:          "event",
	Short:        "Manage events in DeltaFi",
	Long:         `Manage events in DeltaFi.`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listEvents = &cobra.Command{
	Use:   "list",
	Short: "List the events",
	Long:  "List the events",
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAllEvents(cmd)
	},
}

var getEvent = &cobra.Command{
	Use:   "get",
	Short: "Get an event",
	Long:  `Get the details of the specified event.`,
	Args:  cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return doGetEvent(cmd, args[0])
	},
}

var (
	quiet        bool
	source       = "cli"
	content      string
	severity     = "info"
	severities   []string
	notification bool
)

var warnFlag, errorFlag, successFlag bool

var createEventCmd = &cobra.Command{
	Use:   "create [summary]",
	Short: "Create a new event",
	Long: `Create an event with a required summary text. 
The event can include optional source, content, severity level, and notification flags.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) == 0 {
			cmd.Help()
			return fmt.Errorf("summary argument is required")
		}
		RequireRunningDeltaFi()
		summary := args[0]

		if warnFlag {
			severity = "warn"
		} else if errorFlag {
			severity = "error"
		} else if successFlag {
			severity = "success"
		}

		if len(severities) > 1 {
			return newError("The level (severity) flag can only be set once", "The level flag can only be set once but was set to "+strings.Join(severities, ", "))
		} else if len(severities) == 1 {
			severity = severities[0]
		}

		validSeverities := map[string]bool{"info": true, "warn": true, "error": true, "success": true}
		if !validSeverities[severity] {
			return newError("Invalid severity level "+severity, "The severity flag must be one of: info, warn, error, success")
		}

		event := api.Event{
			ID:           uuid.New(),
			Severity:     severity,
			Summary:      summary,
			Content:      content,
			Source:       source,
			Timestamp:    time.Now(),
			Notification: notification,
			Acknowledged: false,
		}

		newEvent, err := app.GetInstance().GetAPIClient().CreatEvent(event)

		if err != nil {
			return wrapInError("Error posting the new event", err)
		}

		if !quiet {
			return prettyPrint(cmd, newEvent)
		}

		return nil
	},
}

func doGetEvent(cmd *cobra.Command, uuid string) error {
	client := app.GetInstance().GetAPIClient()
	var resp, err = client.Event(uuid)

	if err != nil {
		return wrapInError("Error getting event with id "+uuid, err)
	}

	return prettyPrint(cmd, resp)
}

func listAllEvents(cmd *cobra.Command) error {
	client := app.GetInstance().GetAPIClient()
	var events, err = client.Events()

	if err != nil {
		return wrapInError("Error getting list of events", err)
	}

	var rows [][]string

	for _, event := range events {
		rows = append(rows, []string{
			event.ID.String(),
			event.Timestamp.String(),
			event.Severity,
			event.Summary,
			event.Source,
			strconv.FormatBool(event.Acknowledged),
			strconv.FormatBool(event.Notification),
		})
	}
	sort.Slice(rows, func(i, j int) bool {
		timeI, _ := time.Parse(time.RFC3339, rows[i][1])
		timeJ, _ := time.Parse(time.RFC3339, rows[j][1])
		return timeI.After(timeJ)
	})
	columns := []string{"ID", "Timestamp", "Level", "Summary", "Source", "Ack", "Notify"}

	plain, _ := cmd.Flags().GetBool("plain")
	renderAsSimpleTable(api.NewTable(columns, rows), plain)
	return nil
}

func init() {
	rootCmd.AddCommand(eventCmd)
	eventCmd.AddCommand(listEvents)
	eventCmd.AddCommand(getEvent)
	eventCmd.AddCommand(createEventCmd)

	createEventCmd.Flags().BoolVarP(&quiet, "quiet", "q", false, "Quiet mode, no event output")
	createEventCmd.Flags().StringVarP(&source, "source", "s", "cli", "Set event source (default 'cli')")
	createEventCmd.Flags().StringVarP(&content, "content", "c", "", "Set event content (default null)")
	createEventCmd.Flags().StringSliceVarP(&severities, "level", "l", nil, "Set event severity (warn, error, info, success)")
	createEventCmd.Flags().StringSliceVar(&severities, "severity", nil, "Equivalent to --level")
	createEventCmd.Flags().BoolVarP(&notification, "notification", "n", false, "Set the notification flag")
	createEventCmd.Flags().BoolVar(&warnFlag, "warn", false, "Set severity to warn")
	createEventCmd.Flags().BoolVar(&errorFlag, "error", false, "Set severity to error")
	createEventCmd.Flags().BoolVar(&successFlag, "success", false, "Set severity to success")

	createEventCmd.MarkFlagsMutuallyExclusive("level", "severity", "warn", "error", "success")
	_ = createEventCmd.RegisterFlagCompletionFunc("level", severityArgs)
	_ = createEventCmd.RegisterFlagCompletionFunc("severity", severityArgs)

	AddFormatFlag(createEventCmd)
	AddFormatFlag(getEvent)
}

func severityArgs(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	return []string{"warn", "error", "info", "success"}, cobra.ShellCompDirectiveNoFileComp
}
