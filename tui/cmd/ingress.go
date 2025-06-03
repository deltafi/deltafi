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
	"strings"
	"sync"
	"time"

	"github.com/charmbracelet/bubbles/progress"
	tea "github.com/charmbracelet/bubbletea"
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
	concurrency int
)

type IngressResponse struct {
	Did string `json:"did"`
}

type IngressResult struct {
	FilePath string
	Result   string
	Error    error
}

type progressMsg struct {
	filePath string
	progress float64
}

type completeMsg struct {
	filePath string
	result   IngressResult
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

func processFile(client *api.Client, filePath string, datasource string, contentType string, progressChan chan<- progressMsg) IngressResult {
	// Open and read the file content
	file, err := os.Open(filePath)
	if err != nil {
		return IngressResult{
			FilePath: filePath,
			Error:    err,
		}
	}

	// Read the file content
	fileContent, err := io.ReadAll(file)
	file.Close()
	if err != nil {
		return IngressResult{
			FilePath: filePath,
			Error:    err,
		}
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
		return IngressResult{
			FilePath: filePath,
			Error:    err,
		}
	}

	// Send progress update
	progressChan <- progressMsg{filePath: filePath, progress: 1.0}

	return IngressResult{
		FilePath: filePath,
		Result:   result,
	}
}

type IngressCommand struct {
	BaseCommand
	progress    progress.Model
	completed   int
	totalFiles  int
	results     []IngressResult
	width       int
	height      int
	ready       bool
	currentFile string
}

func NewIngressCommand(totalFiles int) *IngressCommand {
	p := progress.New(
		progress.WithDefaultGradient(),
		progress.WithWidth(40),
	)

	return &IngressCommand{
		BaseCommand: NewBaseCommand(),
		progress:    p,
		completed:   0,
		totalFiles:  totalFiles,
		results:     make([]IngressResult, 0),
	}
}

func (c *IngressCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.progress.IncrPercent(0.0),
	)
}

func (c *IngressCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.ready = true
		c.progress.Width = msg.Width - 4

	case progressMsg:
		c.completed++
		c.currentFile = filepath.Base(msg.filePath)
		return c, nil

	case completeMsg:
		c.results = append(c.results, msg.result)
		if len(c.results) == c.totalFiles {
			return c, tea.Quit
		}
		return c, nil

	case tea.KeyMsg:
		if msg.Type == tea.KeyCtrlC {
			return c, tea.Quit
		}
	}

	return c, nil
}

func (c *IngressCommand) View() string {
	if !c.ready {
		return "Initializing..."
	}

	var s strings.Builder
	s.WriteString("\nUploading files...\n\n")

	progress := float64(c.completed) / float64(c.totalFiles)
	s.WriteString(fmt.Sprintf("Progress: %d/%d files\n", c.completed, c.totalFiles))
	if c.currentFile != "" {
		s.WriteString(fmt.Sprintf("Current: %s\n", c.currentFile))
	}
	s.WriteString(c.progress.ViewAs(progress))
	s.WriteString("\n")

	return s.String()
}

var ingressCmd = &cobra.Command{
	Use:     "ingress",
	Short:   "Ingress files into DeltaFi",
	Long:    `Ingress one or more files into DeltaFi with the specified data source.`,
	GroupID: "deltafile",
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

		// Create channels for work distribution and results
		jobs := make(chan string, len(args))
		results := make(chan IngressResult, len(args))
		progressChan := make(chan progressMsg, len(args))

		// Start worker pool
		var wg sync.WaitGroup
		for i := 0; i < concurrency; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				for filePath := range jobs {
					result := processFile(client, filePath, datasource, contentType, progressChan)
					results <- result
				}
			}()
		}

		// Send jobs to workers
		go func() {
			for _, filePath := range args {
				jobs <- filePath
			}
			close(jobs)
		}()

		// Create and run the progress UI
		model := NewIngressCommand(len(args))
		p := tea.NewProgram(model)

		// Start progress updates
		go func() {
			for msg := range progressChan {
				p.Send(msg)
			}
		}()

		// Start result processing
		go func() {
			for result := range results {
				p.Send(completeMsg{result: result})
			}
		}()

		// Wait for all workers to finish
		go func() {
			wg.Wait()
			close(results)
			close(progressChan)
		}()

		// Run the program
		if _, err := p.Run(); err != nil {
			return fmt.Errorf("error running progress UI: %v", err)
		}

		// Process results
		for _, result := range model.results {
			if result.Error != nil {
				if plain || verbose {
					uuids = append(uuids, fmt.Sprintf("error: %v", result.Error))
				} else {
					rows = append(rows, []string{
						styles.ErrorStyle.Render("✗"),
						filepath.Base(result.FilePath),
						fmt.Sprintf("%v", result.Error),
					})
				}
				continue
			}

			if plain || verbose {
				uuids = append(uuids, result.Result)
			} else {
				rows = append(rows, []string{
					styles.SuccessStyle.Render("✓"),
					filepath.Base(result.FilePath),
					result.Result,
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
				if result, err := RenderDeltaFile(did); err != nil {
					fmt.Printf("error viewing deltafile: %v\n", err)
				} else {
					fmt.Println(result)
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
	ingressCmd.Flags().IntVarP(&concurrency, "jobs", "j", 8, "Number of concurrent jobs to run")
	ingressCmd.MarkFlagRequired("datasource")

	// Register completion function for the datasource flag
	ingressCmd.RegisterFlagCompletionFunc("datasource", getRunningDataSourceNames)
}

func getRunningDataSourceNames(_ *cobra.Command, _ []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchDataSourceNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchRunningDataSourceNames() ([]string, error) {
	var resp, err = graphql.ListDataSources()
	if err != nil {
		return nil, err
	}

	var names []string

	for _, obj := range resp.GetAllFlows.GetRestDataSource() {
		if obj.GetFlowStatus().State == "RUNNING" {
			names = append(names, obj.GetName())
		}
	}

	return names, nil
}
