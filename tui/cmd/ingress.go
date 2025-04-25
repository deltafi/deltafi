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
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

var (
	datasource  string
	contentType string
	plain       bool
	verbose     bool
	watch       bool
)

type IngressResponse struct {
	Did string `json:"did"`
}

// pollDeltaFileState polls the deltafile state until it reaches a terminal state
func pollDeltaFileState(did uuid.UUID) error {
	for {
		resp, err := graphql.DeltaFile(did)
		if err != nil {
			return fmt.Errorf("failed to fetch deltafile: %v", err)
		}

		if resp == nil || resp.DeltaFile == nil {
			return fmt.Errorf("no deltafile found with ID: %s", did)
		}

		state := resp.DeltaFile.Stage
		if state == "COMPLETE" || state == "ERROR" {
			return nil
		}

		time.Sleep(time.Second)
	}
}

var ingressCmd = &cobra.Command{
	Use:   "ingress",
	Short: "Ingress files into DeltaFi",
	Long:  `Ingress one or more files into DeltaFi with the specified data source.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if datasource == "" {
			return fmt.Errorf("data source is required (use -d or --datasource)")
		}

		if len(args) == 0 {
			return fmt.Errorf("at least one file must be specified")
		}

		client := app.GetInstance().GetAPIClient()
		var rows [][]string
		var uuids []string

		for _, filePath := range args {
			// Open and read the file content
			file, err := os.Open(filePath)
			if err != nil {
				if plain || verbose {
					uuids = append(uuids, fmt.Sprintf("error: %v", err))
				} else {
					rows = append(rows, []string{
						styles.ErrorStyle.Render("✗"),
						filepath.Base(filePath),
						fmt.Sprintf("%v", err),
					})
				}
				continue
			}

			// Read the file content
			fileContent, err := io.ReadAll(file)
			file.Close()
			if err != nil {
				if plain || verbose {
					uuids = append(uuids, fmt.Sprintf("error: %v", err))
				} else {
					rows = append(rows, []string{
						styles.ErrorStyle.Render("✗"),
						filepath.Base(filePath),
						fmt.Sprintf("%v", err),
					})
				}
				continue
			}

			// Create request options with headers
			opts := &api.RequestOpts{
				Headers: map[string]string{
					"Filename":     filepath.Base(filePath),
					"DataSource":   datasource,
					"Content-Type": contentType,
				},
			}

			// Send the request and get the response
			var result string
			err = client.Post("/api/v2/deltafile/ingress", bytes.NewBuffer(fileContent), &result, opts)
			if err != nil {
				if plain || verbose {
					uuids = append(uuids, fmt.Sprintf("error: %v", err))
				} else {
					rows = append(rows, []string{
						styles.ErrorStyle.Render("✗"),
						filepath.Base(filePath),
						fmt.Sprintf("%v", err),
					})
				}
				continue
			}

			if plain || verbose {
				uuids = append(uuids, result)
			} else {
				rows = append(rows, []string{
					styles.SuccessStyle.Render("✓"),
					filepath.Base(filePath),
					result,
				})
			}
		}

		if watch {
			// Wait for all deltafiles to complete
			for _, uuidStr := range uuids {
				if uuidStr[:6] == "error:" {
					continue
				}
				did, err := uuid.Parse(uuidStr)
				if err != nil {
					fmt.Printf("error parsing UUID %s: %v\n", uuidStr, err)
					continue
				}
				fmt.Printf("Waiting for %s to complete...\n", uuidStr)
				if err := pollDeltaFileState(did); err != nil {
					fmt.Printf("error polling deltafile state: %v\n", err)
				}
			}
		}

		if plain {
			for _, uuid := range uuids {
				fmt.Println(uuid)
			}
		} else if verbose {
			for _, uuidStr := range uuids {
				if uuidStr[:6] == "error:" {
					fmt.Println(uuidStr)
					continue
				}
				did, err := uuid.Parse(uuidStr)
				if err != nil {
					fmt.Printf("error parsing UUID %s: %v\n", uuidStr, err)
					continue
				}
				fmt.Printf("\nDetails for %s:\n", uuidStr)
				if err := viewDeltaFile(did); err != nil {
					fmt.Printf("error viewing deltafile: %v\n", err)
				}
			}
		} else {
			columns := []string{"Status", "File", "Result"}
			renderAsSimpleTable(api.NewTable(columns, rows), false)
		}
		return nil
	},
}

func init() {
	rootCmd.AddCommand(ingressCmd)
	ingressCmd.Flags().StringVarP(&datasource, "datasource", "d", "", "Data source for the ingressed files (required)")
	ingressCmd.Flags().StringVarP(&contentType, "content-type", "c", "application/octet-stream", "Content type for the ingressed files")
	ingressCmd.Flags().BoolVarP(&plain, "plain", "p", false, "Output only UUIDs, one per line")
	ingressCmd.Flags().BoolVarP(&verbose, "verbose", "v", false, "Show detailed deltafile information for each uploaded file")
	ingressCmd.Flags().BoolVarP(&watch, "watch", "w", false, "Wait for each deltafile to complete before showing results")
	ingressCmd.MarkFlagRequired("datasource")

	// Register completion function for the datasource flag
	ingressCmd.RegisterFlagCompletionFunc("datasource", getDataSourceNames)
}
