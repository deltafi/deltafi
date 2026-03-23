// ABOUTME: Lookup table supplier with pull-through caching that uses action variables.
// ABOUTME: Matches Java's PullThroughLookupTableSupplier with columns column_a, column_b, value.

package suppliers

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"strings"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.RegisterSupplier(&PullThroughLookupTableSupplier{})
}

// PullThroughLookupTableSupplier provides data that incorporates action variables.
type PullThroughLookupTableSupplier struct{}

func (s *PullThroughLookupTableSupplier) Table() actionkit.LookupTableDefinition {
	return actionkit.LookupTableDefinition{
		Name:                 "go_pull_through_lookup_table",
		Columns:              []string{"column_a", "column_b", "value"},
		KeyColumns:           []string{"column_a"},
		ServiceBacked:        true,
		BackingServiceActive: true,
		PullThrough:          true,
	}
}

func (s *PullThroughLookupTableSupplier) GetRows(_ context.Context, variables map[string]string, _ map[string][]string, _ []string) ([]map[string]string, error) {
	log.Println("[pull-through-lookup-supplier] Getting rows for go_pull_through_lookup_table")

	didErrorPrefix := variables["didErrorPrefix"]
	convertToUpper := variables["convertToUpper"]

	return []map[string]string{
		{"column_a": "A1", "column_b": "B1", "value": strings.Join([]string{randomValue(), didErrorPrefix, convertToUpper}, "-")},
		{"column_a": "A2", "column_b": "B2", "value": randomValue()},
		{"column_a": "A3", "column_b": "B3", "value": randomValue()},
	}, nil
}

func randomValue() string {
	return fmt.Sprintf("%d", rand.Intn(100)+1)
}
