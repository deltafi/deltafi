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
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
	"sort"
	"strconv"
	"strings"
	"time"
)

var GetEvents = &cobra.Command{
	Use:   "event",
	Short: "Get events",
	Long: `Get the list of events when no arguments are given.
When an id is given get the details of the specified event.`,
	Aliases: []string{"events"},
	RunE: func(cmd *cobra.Command, args []string) error {
		client := app.GetInstance().GetAPIClient()
		if len(args) == 0 {
			return listAllEvents(cmd, client)
		} else {
			return getEvent(cmd, args[0], client)
		}
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

var CreateEventCmd = &cobra.Command{
	Use:   "event",
	Short: "Create a new event",
	Long: `Create an event with a required summary text. 
The event can include optional source, content, severity level, and notification flags.`,
	Args: cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
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

func getEvent(cmd *cobra.Command, uuid string, client *api.Client) error {
	var resp, err = client.Event(uuid)

	if err != nil {
		return wrapInError("Error getting event with id "+uuid, err)
	}

	return prettyPrint(cmd, resp)
}

func listAllEvents(cmd *cobra.Command, client *api.Client) error {
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
		sort.Slice(rows, func(i, j int) bool {
			return rows[i][0] < rows[j][0]
		})
	}
	columns := []string{"ID", "Timestamp", "Level", "Summary", "Source", "Ack", "Notify"}

	plain, _ := cmd.Flags().GetBool("plain")
	renderAsSimpleTable(api.NewTable(columns, rows), plain)
	return nil
}

func init() {
	GetCmd.AddCommand(GetEvents)
	CreateCmd.AddCommand(CreateEventCmd)

	CreateEventCmd.Flags().BoolVarP(&quiet, "quiet", "q", false, "Quiet mode, no event output")
	CreateEventCmd.Flags().StringVarP(&source, "source", "s", "cli", "Set event source (default 'cli')")
	CreateEventCmd.Flags().StringVarP(&content, "content", "c", "", "Set event content (default null)")
	CreateEventCmd.Flags().StringSliceVarP(&severities, "level", "l", nil, "Set event severity (warn, error, info, success)")
	CreateEventCmd.Flags().StringSliceVar(&severities, "severity", nil, "Equivalent to --level")
	CreateEventCmd.Flags().BoolVarP(&notification, "notification", "n", false, "Set the notification flag")
	CreateEventCmd.Flags().BoolVar(&warnFlag, "warn", false, "Set severity to warn")
	CreateEventCmd.Flags().BoolVar(&errorFlag, "error", false, "Set severity to error")
	CreateEventCmd.Flags().BoolVar(&successFlag, "success", false, "Set severity to success")

	CreateEventCmd.Flags().StringP("format", "o", "json", "output format (json or yaml)")
	_ = GetCmd.RegisterFlagCompletionFunc("format", formatCompletion)

	CreateEventCmd.MarkFlagsMutuallyExclusive("level", "severity", "warn", "error", "success")
	_ = CreateEventCmd.RegisterFlagCompletionFunc("level", severityArgs)
	_ = CreateEventCmd.RegisterFlagCompletionFunc("severity", severityArgs)
}

func severityArgs(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	return []string{"warn", "error", "info", "success"}, cobra.ShellCompDirectiveNoFileComp
}
