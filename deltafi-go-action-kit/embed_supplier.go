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
// ABOUTME: Lookup table suppliers backed by embedded CSV/JSON resources.
// ABOUTME: Parses bytes once at construction; GetRows filters and projects in memory.

package actionkit

import (
	"bytes"
	"context"
	"encoding/csv"
	"encoding/json"
	"fmt"
	"slices"
)

type embeddedSupplier struct {
	def  LookupTableDefinition
	rows []map[string]string
}

// NewEmbeddedCSVSupplier builds a LookupTableSupplier from CSV bytes whose first
// row is the header. Rows are parsed once; the supplier is safe for concurrent use.
func NewEmbeddedCSVSupplier(def LookupTableDefinition, data []byte) (LookupTableSupplier, error) {
	records, err := csv.NewReader(bytes.NewReader(data)).ReadAll()
	if err != nil {
		return nil, fmt.Errorf("parse CSV for %q: %w", def.Name, err)
	}
	if len(records) == 0 {
		return &embeddedSupplier{def: def}, nil
	}
	headers := records[0]
	rows := make([]map[string]string, 0, len(records)-1)
	for _, rec := range records[1:] {
		row := make(map[string]string, len(headers))
		for i, h := range headers {
			if i < len(rec) {
				row[h] = rec[i]
			}
		}
		rows = append(rows, row)
	}
	return &embeddedSupplier{def: def, rows: rows}, nil
}

// NewEmbeddedJSONSupplier builds a LookupTableSupplier from a JSON array of objects.
func NewEmbeddedJSONSupplier(def LookupTableDefinition, data []byte) (LookupTableSupplier, error) {
	var rows []map[string]string
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, fmt.Errorf("parse JSON for %q: %w", def.Name, err)
	}
	return &embeddedSupplier{def: def, rows: rows}, nil
}

func (s *embeddedSupplier) Table() LookupTableDefinition { return s.def }

// GetRows returns rows matching the given criteria. When no projection is applied
// each returned Row wraps the shared internal map by reference (zero copy);
// projected rows wrap a new smaller map. Concurrent reads are safe.
func (s *embeddedSupplier) GetRows(_ context.Context, _ map[string]string, matchingColumnValues map[string][]string, resultColumns []string) ([]Row, error) {
	out := make([]Row, 0, len(s.rows))
	for _, row := range s.rows {
		if !rowMatches(row, matchingColumnValues) {
			continue
		}
		out = append(out, NewRow(projectColumns(row, resultColumns)))
	}
	return out, nil
}

func rowMatches(row map[string]string, matching map[string][]string) bool {
	for col, allowed := range matching {
		val, ok := row[col]
		if !ok || !containsString(allowed, val) {
			return false
		}
	}
	return true
}

func projectColumns(row map[string]string, cols []string) map[string]string {
	if len(cols) == 0 {
		return row
	}
	projected := make(map[string]string, len(cols))
	for _, c := range cols {
		if v, ok := row[c]; ok {
			projected[c] = v
		}
	}
	return projected
}

func containsString(values []string, target string) bool {
	return slices.Contains(values, target)
}
