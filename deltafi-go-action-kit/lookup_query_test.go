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
// ABOUTME: Tests for the LookupQuery builder and client-side exclusion filtering.
// ABOUTME: Verifies option construction and ExcludingMatch row removal.

package actionkit

import "testing"

func TestLookupQueryBuilder_Chaining(t *testing.T) {
	q := LookupQuery().
		WithMatch("color", "red", "blue").
		ExcludingMatch("status", "deleted").
		WithResultColumns("score").
		WithSort("name", SortDESC).
		WithLimit(10).
		WithOffset(5)

	if got := q.matchingColumns["color"]; len(got) != 2 || got[0] != "red" || got[1] != "blue" {
		t.Errorf("matchingColumns[color] = %v", got)
	}
	if got := q.excludingColumns["status"]; len(got) != 1 || got[0] != "deleted" {
		t.Errorf("excludingColumns[status] = %v", got)
	}
	if len(q.resultColumns) != 1 || q.resultColumns[0] != "score" {
		t.Errorf("resultColumns = %v", q.resultColumns)
	}
	if q.sortColumn != "name" || q.sortDirection != SortDESC {
		t.Errorf("sort = %q %q", q.sortColumn, q.sortDirection)
	}
	if q.limit == nil || *q.limit != 10 {
		t.Errorf("limit = %v", q.limit)
	}
	if q.offset == nil || *q.offset != 5 {
		t.Errorf("offset = %v", q.offset)
	}
}

func TestLookupQueryBuilder_ToLookupOptions(t *testing.T) {
	// nil builder yields no options.
	var nilBuilder *LookupQueryBuilder
	if opts := nilBuilder.toLookupOptions(); opts != nil {
		t.Errorf("nil builder options = %v, want nil", opts)
	}

	// Each configured facet contributes exactly one option; ExcludingMatch does not.
	q := LookupQuery().
		WithMatch("a", "1").
		WithMatch("b", "2").
		ExcludingMatch("c", "x").
		WithResultColumns("r").
		WithSort("s", SortASC).
		WithOffset(1).
		WithLimit(2)

	// 2 match columns + resultColumns + sort + offset + limit = 6 options
	if got := len(q.toLookupOptions()); got != 6 {
		t.Errorf("option count = %d, want 6", got)
	}
}

func TestLookupQueryBuilder_ApplyExclusions(t *testing.T) {
	rows := []Row{
		NewRow(map[string]string{"id": "1", "status": "active"}),
		NewRow(map[string]string{"id": "2", "status": "deleted"}),
		NewRow(map[string]string{"id": "3", "status": "archived"}),
		NewRow(map[string]string{"id": "4", "status": "deleted"}),
	}
	results := &LookupResults{TotalCount: 4, Rows: rows}

	// Exclude two values across all rows: only the single "active" row survives.
	q := LookupQuery().ExcludingMatch("status", "deleted", "archived")
	filtered := q.applyExclusions(results)

	if filtered.TotalCount != 1 {
		t.Errorf("TotalCount = %d, want 1", filtered.TotalCount)
	}
	if len(filtered.Rows) != 1 {
		t.Fatalf("Rows len = %d, want 1", len(filtered.Rows))
	}
	status, _ := filtered.Rows[0].Get("status")
	if status != "active" {
		t.Errorf("survivor status = %q, want active", status)
	}
}

func TestLookupQueryBuilder_ApplyExclusions_NoOpCases(t *testing.T) {
	results := &LookupResults{TotalCount: 1, Rows: []Row{NewRow(map[string]string{"id": "1"})}}

	// nil builder returns results unchanged.
	var nilBuilder *LookupQueryBuilder
	if got := nilBuilder.applyExclusions(results); got != results {
		t.Error("nil builder should return results unchanged")
	}

	// Builder without exclusions returns results unchanged.
	q := LookupQuery().WithMatch("a", "1")
	if got := q.applyExclusions(results); got != results {
		t.Error("builder without exclusions should return results unchanged")
	}

	// nil results is safe.
	if got := LookupQuery().ExcludingMatch("a", "1").applyExclusions(nil); got != nil {
		t.Error("applyExclusions(nil) should return nil")
	}
}
