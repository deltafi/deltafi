// ABOUTME: Lookup table supplier providing city/state data from embedded JSON content.
// ABOUTME: Matches Java's JsonLookupTableSupplier with columns city, state.

package suppliers

import (
	"context"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func init() {
	actionkit.RegisterSupplier(&JsonLookupTableSupplier{})
}

// JsonLookupTableSupplier provides city data as a static lookup table.
type JsonLookupTableSupplier struct{}

func (s *JsonLookupTableSupplier) Table() actionkit.LookupTableDefinition {
	return actionkit.LookupTableDefinition{
		Name:       "go_json_lookup_table",
		Columns:    []string{"city", "state"},
		KeyColumns: []string{"city", "state"},
	}
}

func (s *JsonLookupTableSupplier) GetRows(_ context.Context, _ map[string]string, _ map[string][]string, _ []string) ([]map[string]string, error) {
	return []map[string]string{
		{"city": "Miami", "state": "FL"},
		{"city": "Austin", "state": "TX"},
		{"city": "Las Vegas", "state": "NV"},
	}, nil
}
