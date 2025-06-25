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
	"strings"

	"github.com/Khan/genqlient/graphql"
	"github.com/spf13/cobra"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
)

var plainOutput bool
var queryFile string

// JSON syntax highlighting styles
var (
	jsonKeyStyle     = styles.NewStyleBuilder().WithForeground(styles.LightBlue).Bold().Build()
	jsonStringStyle  = styles.NewStyleBuilder().WithForeground(styles.LightGreen).Build()
	jsonNumberStyle  = styles.NewStyleBuilder().WithForeground(styles.Peach).Build()
	jsonBooleanStyle = styles.NewStyleBuilder().WithForeground(styles.Yellow).Build()
	jsonNullStyle    = styles.NewStyleBuilder().WithForeground(styles.Gray).Italic().Build()
	jsonBracketStyle = styles.NewStyleBuilder().WithForeground(styles.Text).Build()
	jsonCommaStyle   = styles.NewStyleBuilder().WithForeground(styles.Text).Build()
)

// graphqlCmd represents the graphql command
var graphqlCmd = &cobra.Command{
	Use:   "graphql [query]",
	Short: "Execute a GraphQL query and return JSON response",
	Long: `Execute a GraphQL query against the DeltaFi GraphQL endpoint and return the JSON response.

The query can be provided in several ways:
- As a command line argument
- In interactive mode (provide no arguments and type or paste to stdin, then press Ctrl+D to submit)
- From stdin (use - for the query argument)
- From a file (use @filename or --file filename for the query argument)

Examples:
  deltafi graphql "query { version }"
  deltafi graphql - < query.graphql
  cat query.graphql | deltafi graphql -
  deltafi graphql @query.graphql
  deltafi graphql --file query.graphql
  echo "query { version }" | deltafi graphql -`,
	GroupID: "deltafi",
	Args:    cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		var queryString string
		var err error

		// Check if --file flag is used
		if queryFile != "" {
			// Read from file specified by --file flag
			bytes, err := os.ReadFile(queryFile)
			if err != nil {
				return fmt.Errorf("error reading file %s: %w", queryFile, err)
			}
			queryString = strings.TrimSpace(string(bytes))
		} else if len(args) == 0 {
			// No arguments provided, read from stdin
			bytes, err := io.ReadAll(os.Stdin)
			if err != nil {
				return fmt.Errorf("error reading from stdin: %w", err)
			}
			queryString = strings.TrimSpace(string(bytes))
		} else {
			query := args[0]

			if query == "-" {
				// Read from stdin
				bytes, err := io.ReadAll(os.Stdin)
				if err != nil {
					return fmt.Errorf("error reading from stdin: %w", err)
				}
				queryString = strings.TrimSpace(string(bytes))
			} else if strings.HasPrefix(query, "@") {
				// Read from file
				filename := query[1:] // Remove the @ prefix
				bytes, err := os.ReadFile(filename)
				if err != nil {
					return fmt.Errorf("error reading file %s: %w", filename, err)
				}
				queryString = strings.TrimSpace(string(bytes))
			} else {
				// Use the query as provided
				queryString = query
			}
		}

		if queryString == "" {
			return fmt.Errorf("empty query")
		}

		// Execute the query
		result, err := executeGraphQLQuery(queryString)
		if err != nil {
			return fmt.Errorf("%w", err)
		}

		if plainOutput {
			fmt.Println(result)
		} else {
			fmt.Println(styles.ColorizeJSON(result))
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(graphqlCmd)
	graphqlCmd.Flags().BoolVar(&plainOutput, "plain", false, "Output plain JSON without syntax coloring")
	graphqlCmd.Flags().StringVarP(&queryFile, "file", "f", "", "Read query from file")
}

// executeGraphQLQuery executes a raw GraphQL query and returns the JSON response
func executeGraphQLQuery(query string) (string, error) {
	// Get the GraphQL client
	client, err := app.GetGraphqlClient()
	if err != nil {
		return "", fmt.Errorf("failed to get GraphQL client: %w", err)
	}

	// Create the GraphQL request
	req := &graphql.Request{
		Query: query,
	}

	// Execute the request
	var resp graphql.Response
	err = client.MakeRequest(nil, req, &resp)
	if err != nil {
		return "", fmt.Errorf("GraphQL request failed: %w", err)
	}

	// Check for GraphQL errors
	if len(resp.Errors) > 0 {
		errorMessages := make([]string, len(resp.Errors))
		for i, err := range resp.Errors {
			errorMessages[i] = err.Message
		}
		return "", fmt.Errorf("GraphQL errors: %s", strings.Join(errorMessages, "; "))
	}

	// Convert response to pretty-printed JSON
	jsonBytes, err := json.MarshalIndent(resp.Data, "", "  ")
	if err != nil {
		return "", fmt.Errorf("failed to marshal response to JSON: %w", err)
	}

	return string(jsonBytes), nil
}
