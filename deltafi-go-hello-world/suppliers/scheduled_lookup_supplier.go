// ABOUTME: Lookup table supplier providing color data with random values and periodic refresh.
// ABOUTME: Matches Java's ScheduledLookupTableSupplier with columns column_a, column_b, color.

package suppliers

import (
	"context"
	"log"
	"math/rand"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func init() {
	actionkit.RegisterSupplier(&ScheduledLookupTableSupplier{})
}

// ScheduledLookupTableSupplier provides color data with randomized values on each refresh.
type ScheduledLookupTableSupplier struct{}

var rainbowColors = []string{"red", "orange", "yellow", "green", "blue", "indigo", "violet"}

func (s *ScheduledLookupTableSupplier) Table() actionkit.LookupTableDefinition {
	return actionkit.LookupTableDefinition{
		Name:                 "go_scheduled_lookup_table",
		Columns:              []string{"column_a", "column_b", "color"},
		KeyColumns:           []string{"column_a"},
		ServiceBacked:        true,
		BackingServiceActive: true,
		RefreshDuration:      "PT1M",
	}
}

func (s *ScheduledLookupTableSupplier) GetRows(_ context.Context, _ map[string]string, _ map[string][]string, _ []string) ([]map[string]string, error) {
	log.Println("[scheduled-lookup-supplier] Getting rows for go_scheduled_lookup_table")
	return []map[string]string{
		{"column_a": "A1", "column_b": "B1", "color": randomColor()},
		{"column_a": "A2", "column_b": "B2", "color": randomColor()},
		{"column_a": "A3", "column_b": "B3", "color": "red"}, // always red for integration tests
	}, nil
}

func randomColor() string {
	return rainbowColors[rand.Intn(len(rainbowColors))]
}
