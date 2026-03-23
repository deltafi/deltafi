// ABOUTME: Lookup table supplier providing F1 driver data from embedded CSV content.
// ABOUTME: Matches Java's CsvLookupTableSupplier with columns first, last, team.

package suppliers

import (
	"context"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.RegisterSupplier(&CsvLookupTableSupplier{})
}

// CsvLookupTableSupplier provides F1 driver data as a static lookup table.
type CsvLookupTableSupplier struct{}

func (s *CsvLookupTableSupplier) Table() actionkit.LookupTableDefinition {
	return actionkit.LookupTableDefinition{
		Name:       "go_csv_lookup_table",
		Columns:    []string{"first", "last", "team"},
		KeyColumns: []string{"first", "last"},
	}
}

func (s *CsvLookupTableSupplier) GetRows(_ context.Context, _ map[string]string, _ map[string][]string, _ []string) ([]map[string]string, error) {
	return []map[string]string{
		{"last": "Verstappen", "first": "Max", "team": "Red Bull"},
		{"last": "Piastri", "first": "Oscar", "team": "McLaren"},
		{"last": "Norris", "first": "Lando", "team": "McLaren"},
		{"last": "Leclerc", "first": "Charles", "team": "Ferrari"},
		{"last": "Albon", "first": "Alex", "team": "Williams"},
		{"last": "Hadjar", "first": "Isack", "team": "Racing Bulls"},
	}, nil
}
