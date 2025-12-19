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
// ABOUTME: TUI commands for managing provenance data.
// ABOUTME: Includes flush command to trigger provenance compaction.
package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var provenanceCmd = &cobra.Command{
	Use:     "provenance",
	Short:   "Manage provenance data",
	Long:    `Commands for managing provenance data in the analytics system`,
	GroupID: "deltafi",
	RunE: func(cmd *cobra.Command, args []string) error {
		return cmd.Help()
	},
}

var provenanceFlushVerbose bool

var provenanceFlushCmd = &cobra.Command{
	Use:   "flush",
	Short: "Flush and compact provenance data",
	Long: `Triggers the analytics service to flush buffered provenance data and
compact it into parquet files. This is useful before exporting provenance
data or during graceful shutdown.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		dataDir := app.GetDataDir()
		analyticsDir := filepath.Join(dataDir, "analytics")
		flushPath := filepath.Join(analyticsDir, ".flush")

		// Check if analytics directory exists
		if _, err := os.Stat(analyticsDir); os.IsNotExist(err) {
			return fmt.Errorf("analytics directory not found: %s", analyticsDir)
		}

		// Create the .flush file
		if err := os.WriteFile(flushPath, []byte{}, 0644); err != nil {
			return fmt.Errorf("failed to create flush file: %w", err)
		}

		fmt.Print("Flushing...")

		// Poll until the file disappears (max 5 minutes)
		timeout := time.After(5 * time.Minute)
		ticker := time.NewTicker(500 * time.Millisecond)
		defer ticker.Stop()

		for {
			select {
			case <-timeout:
				return fmt.Errorf("timeout waiting for flush to complete")
			case <-ticker.C:
				if _, err := os.Stat(flushPath); os.IsNotExist(err) {
					fmt.Println(" done")

					if provenanceFlushVerbose {
						compactedDir := filepath.Join(analyticsDir, "provenance", "compacted")
						if err := listCompactedFiles(compactedDir); err != nil {
							return err
						}
					}
					return nil
				}
			}
		}
	},
}

func listCompactedFiles(dir string) error {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		fmt.Println("\nNo compacted provenance files found.")
		return nil
	}

	fmt.Println("\nCompacted provenance files:")

	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		if !strings.HasSuffix(info.Name(), ".parquet") {
			return nil
		}

		// Get relative path from compacted dir
		relPath, _ := filepath.Rel(dir, path)
		fmt.Printf("  %s (%s)\n", relPath, humanizeBytes(info.Size()))
		return nil
	})
}

func humanizeBytes(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(bytes)/float64(div), "KMGTPE"[exp])
}

func init() {
	rootCmd.AddCommand(provenanceCmd)
	provenanceCmd.AddCommand(provenanceFlushCmd)

	provenanceFlushCmd.Flags().BoolVarP(&provenanceFlushVerbose, "verbose", "v", false, "List compacted provenance files after flush")
}
