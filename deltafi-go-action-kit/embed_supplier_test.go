/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Tests for embedded CSV/JSON lookup table suppliers.
// ABOUTME: Verifies parsing, filtering by column values, and column projection.

package actionkit

import (
	"context"
	"sync"
	"testing"
)

func TestNewEmbeddedCSVSupplier_ParsesAndFilters(t *testing.T) {
	csvData := "name,team\nNorris,McLaren\nVerstappen,RedBull\n"
	def := LookupTableDefinition{Name: "drivers", Columns: []string{"name", "team"}, KeyColumns: []string{"name"}}
	s, err := NewEmbeddedCSVSupplier(def, []byte(csvData))
	if err != nil {
		t.Fatalf("NewEmbeddedCSVSupplier: %v", err)
	}
	rows, err := s.GetRows(context.Background(), nil, map[string][]string{"team": {"McLaren"}}, []string{"name"})
	if err != nil {
		t.Fatalf("GetRows: %v", err)
	}
	name, ok := rows[0].Get("name")
	if len(rows) != 1 || !ok || name != "Norris" {
		t.Fatalf("unexpected rows: %+v", rows)
	}
	if rows[0].Has("team") {
		t.Fatalf("resultColumns projection should drop 'team'")
	}
}

func TestNewEmbeddedJSONSupplier_ParsesRows(t *testing.T) {
	js := `[{"city":"Paris","country":"FR"},{"city":"Berlin","country":"DE"}]`
	def := LookupTableDefinition{Name: "cities", Columns: []string{"city", "country"}, KeyColumns: []string{"city"}}
	s, err := NewEmbeddedJSONSupplier(def, []byte(js))
	if err != nil {
		t.Fatalf("NewEmbeddedJSONSupplier: %v", err)
	}
	rows, err := s.GetRows(context.Background(), nil, nil, nil)
	if err != nil {
		t.Fatalf("GetRows: %v", err)
	}
	if len(rows) != 2 {
		t.Fatalf("got %d rows", len(rows))
	}
}

func TestNewEmbeddedCSVSupplier_RejectsBadData(t *testing.T) {
	if _, err := NewEmbeddedJSONSupplier(LookupTableDefinition{Name: "x"}, []byte("not json")); err == nil {
		t.Fatalf("expected parse error")
	}
}

func TestEmbeddedSupplier_ConcurrentGetRows(t *testing.T) {
	csvData := "name,team\nNorris,McLaren\nVerstappen,RedBull\n"
	def := LookupTableDefinition{Name: "drivers", Columns: []string{"name", "team"}, KeyColumns: []string{"name"}}
	s, err := NewEmbeddedCSVSupplier(def, []byte(csvData))
	if err != nil {
		t.Fatalf("NewEmbeddedCSVSupplier: %v", err)
	}

	const goroutines = 30
	var wg sync.WaitGroup
	wg.Add(goroutines)
	for range goroutines {
		go func() {
			defer wg.Done()
			rows, err := s.GetRows(context.Background(), nil, nil, nil)
			if err != nil {
				t.Errorf("GetRows: %v", err)
				return
			}
			if len(rows) != 2 {
				t.Errorf("got %d rows, want 2", len(rows))
				return
			}
			// Read values via the read-only Row accessors.
			for _, row := range rows {
				_, _ = row.Get("name")
				_, _ = row.Get("team")
			}
		}()
	}
	wg.Wait()
}
