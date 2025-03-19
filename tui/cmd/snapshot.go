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
	"encoding/json"
	"fmt"
	"io"
	"os"
	"sort"
	"time"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

// parseSnapshotID is a helper function to parse and validate snapshot IDs
func parseSnapshotID(id string) (uuid.UUID, error) {
	snapshotId, err := uuid.Parse(id)
	if err != nil {
		return uuid.Nil, fmt.Errorf("invalid snapshot ID: %v", err)
	}
	return snapshotId, nil
}

// validateFormatFlag checks if the format flag value is valid
func validateFormatFlag(cmd *cobra.Command) error {
	format := cmd.Flags().Lookup("format").Value.String()
	if format != "json" && format != "yaml" {
		return fmt.Errorf("invalid format: %s (must be json or yaml)", format)
	}
	return nil
}

var snapshotCmd = &cobra.Command{
	Use:     "snapshot",
	Short:   "Manage system snapshots",
	Long:    `View and manage system snapshots`,
	GroupID: "deltafi",
}

var listSnapshotsCmd = &cobra.Command{
	Use:   "list",
	Short: "List system snapshots",
	Long:  `List all system snapshots with their ID, creation time, and reason`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		resp, err := graphql.GetSystemSnapshots()
		if err != nil {
			return wrapInError("Error fetching system snapshots", err)
		}

		var rows [][]string
		for _, snapshot := range resp.GetSystemSnapshots {
			created := snapshot.GetCreated().Format(time.RFC3339)
			reason := "-"
			if r := snapshot.GetReason(); r != nil {
				reason = *r
			}
			rows = append(rows, []string{
				snapshot.GetId().String(),
				created,
				reason,
			})
		}

		sort.Slice(rows, func(i, j int) bool {
			return rows[i][0] < rows[j][0]
		})

		columns := []string{"ID", "CREATED", "REASON"}
		plain, _ := cmd.Flags().GetBool("plain")
		renderAsSimpleTable(api.NewTable(columns, rows), plain)
		return nil
	},
}

var showSnapshotCmd = &cobra.Command{
	Use:   "show [id]",
	Short: "Show system snapshot details",
	Long:  `Show detailed information about a specific system snapshot`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		if err := validateFormatFlag(cmd); err != nil {
			return err
		}

		snapshotId, err := parseSnapshotID(args[0])
		if err != nil {
			return err
		}

		resp, err := graphql.GetSystemSnapshot(snapshotId)
		if err != nil {
			return wrapInError("Error fetching system snapshot", err)
		}

		return prettyPrint(cmd, resp)
	},
}

var createSnapshotCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new system snapshot",
	Long:  `Create a new system snapshot with an optional reason`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		var reason *string
		if r, _ := cmd.Flags().GetString("reason"); r != "" {
			reason = &r
		}

		resp, err := graphql.SnapshotSystem(reason)
		if err != nil {
			return wrapInError("Error creating system snapshot", err)
		}

		formatFlag := cmd.Flags().Lookup("format")
		if !formatFlag.Changed {
			fmt.Printf("Created snapshot %s\n", resp.SnapshotSystem.GetId())
			return nil
		}

		if err := validateFormatFlag(cmd); err != nil {
			return err
		}

		// Get full snapshot details
		fullResp, err := graphql.GetSystemSnapshot(resp.SnapshotSystem.GetId())
		if err != nil {
			return wrapInError("Error fetching created snapshot details", err)
		}

		return prettyPrint(cmd, fullResp)
	},
}

var deleteSnapshotCmd = &cobra.Command{
	Use:   "delete [id]",
	Short: "Delete a system snapshot",
	Long:  `Delete a specific system snapshot by ID`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		snapshotId, err := parseSnapshotID(args[0])
		if err != nil {
			return err
		}

		resp, err := graphql.DeleteSnapshot(snapshotId)
		if err != nil {
			return wrapInError("Error deleting system snapshot", err)
		}

		if !resp.DeleteSnapshot.Success {
			if len(resp.DeleteSnapshot.Errors) > 0 {
				return fmt.Errorf("failed to delete snapshot: %s", resp.DeleteSnapshot.Errors[0])
			}
			return fmt.Errorf("failed to delete snapshot")
		}

		fmt.Printf("Successfully deleted snapshot %s\n", snapshotId)
		return nil
	},
}

func ImportSnapshot(reason string, input []byte) (*graphql.ImportSnapshotResponse, error) {
	var rawInput map[string]interface{}
	if err := json.Unmarshal(input, &rawInput); err != nil {
		return nil, fmt.Errorf("error parsing snapshot JSON: %v", err)
	}

	if rawInput["getSystemSnapshot"] != nil {
		rawInput = rawInput["getSystemSnapshot"].(map[string]interface{})
	}

	var snapshotInput graphql.SystemSnapshotInput

	if rawInput["id"] != nil {
		snapshotInput.Id = rawInput["id"].(string)
	}
	if rawInput["reason"] != nil {
		if reasonStr, ok := rawInput["reason"].(string); ok {
			snapshotInput.Reason = &reasonStr
		}
	}
	if rawInput["created"] != nil {
		if createdStr, ok := rawInput["created"].(string); ok {
			created, err := time.Parse(time.RFC3339, createdStr)
			if err == nil {
				snapshotInput.Created = created
			}
		}
	}

	// Check if this is a new format (has "snapshot" field) or old format
	if snapshotData, exists := rawInput["snapshot"]; exists {
		// New format
		schemaVersion, ok := rawInput["schemaVersion"].(float64)
		if !ok {
			return nil, fmt.Errorf("'schemaVersion' must be a number")
		}
		snapshotInput.SchemaVersion = int(schemaVersion)
		snapshotInput.Snapshot = snapshotData.(map[string]interface{})
	} else {
		// Old format - move everything except id, created, and reason into the snapshot field
		snapshotData := make(map[string]interface{})
		for k, v := range rawInput {
			if k != "id" && k != "created" && k != "reason" {
				snapshotData[k] = v
			}
		}
		snapshotInput.SchemaVersion = 1
		snapshotInput.Snapshot = snapshotData
	}
	if reason != "" {
		snapshotInput.Reason = &reason
	}

	resp, err := graphql.ImportSnapshot(snapshotInput)

	return resp, err
}

var importSnapshotCmd = &cobra.Command{
	Use:   "import [file]",
	Short: "Import a system snapshot",
	Long: `Import a system snapshot from a file or stdin.
If no file is specified, reads from stdin.`,
	Args: cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		var input []byte
		var err error

		if len(args) == 0 {
			input, err = io.ReadAll(os.Stdin)
			if err != nil {
				return fmt.Errorf("error reading from stdin: %v", err)
			}
		} else {
			input, err = os.ReadFile(args[0])
			if err != nil {
				return fmt.Errorf("error reading file %s: %v", args[0], err)
			}
		}
		reason, _ := cmd.Flags().GetString("reason")

		resp, err := ImportSnapshot(reason, input)
		if err != nil {
			return wrapInError("Error importing system snapshot", err)
		}

		return prettyPrint(cmd, resp)
	},
}

func RestoreSnapshot(snapshotId uuid.UUID, hardReset bool) (*graphql.ResetFromSnapshotWithIdResponse, error) {
	resp, err := graphql.ResetFromSnapshotWithId(snapshotId, &hardReset)
	if err != nil {
		return resp, wrapInError("Error restoring system from snapshot", err)
	}

	if !resp.ResetFromSnapshotWithId.Success {
		if len(resp.ResetFromSnapshotWithId.Errors) > 0 {
			return resp, fmt.Errorf("failed to restore snapshot: %s", resp.ResetFromSnapshotWithId.Errors[0])
		}
		return resp, fmt.Errorf("failed to restore snapshot")
	}
	return resp, nil
}

var restoreSnapshotCmd = &cobra.Command{
	Use:   "restore [id]",
	Short: "Restore system from a snapshot",
	Long:  `Restore the system state from a specific snapshot`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		snapshotId, err := parseSnapshotID(args[0])
		if err != nil {
			return err
		}

		hardReset, _ := cmd.Flags().GetBool("hard")
		_, err = RestoreSnapshot(snapshotId, hardReset)
		if err != nil {
			return err
		}

		fmt.Printf("Successfully restored system from snapshot %s\n", snapshotId)
		return nil
	},
}

func init() {
	rootCmd.AddCommand(snapshotCmd)
	snapshotCmd.AddCommand(listSnapshotsCmd)
	snapshotCmd.AddCommand(showSnapshotCmd)
	snapshotCmd.AddCommand(createSnapshotCmd)
	snapshotCmd.AddCommand(deleteSnapshotCmd)
	snapshotCmd.AddCommand(importSnapshotCmd)
	snapshotCmd.AddCommand(restoreSnapshotCmd)

	listSnapshotsCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	AddFormatFlag(showSnapshotCmd)
	AddFormatFlag(createSnapshotCmd)
	AddFormatFlag(importSnapshotCmd)
	createSnapshotCmd.Flags().StringP("reason", "r", "", "Reason for creating the snapshot")
	importSnapshotCmd.Flags().StringP("reason", "r", "", "Reason for importing the snapshot")
	restoreSnapshotCmd.Flags().BoolP("hard", "H", false, "Perform a hard reset (may be more disruptive but more thorough)")
}
