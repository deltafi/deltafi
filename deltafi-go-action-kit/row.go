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
// ABOUTME: Compile-time read-only view over a single lookup table row.
// ABOUTME: Callers can read but not mutate the backing data; zero-copy on wrap.

package actionkit

import (
	"encoding/json"
	"iter"
)

// Row is a read-only view over one lookup-table row. The backing map is shared
// and never copied; callers may read but not mutate.
type Row struct{ m map[string]string }

// NewRow wraps m in a Row without copying. The caller must not mutate m after
// calling NewRow. Plugin suppliers use this to construct rows.
func NewRow(m map[string]string) Row { return Row{m: m} }

// Get returns the value for the named column and whether it was present.
func (r Row) Get(col string) (string, bool) { v, ok := r.m[col]; return v, ok }

// Has reports whether the named column exists in the row.
func (r Row) Has(col string) bool { _, ok := r.m[col]; return ok }

// Len returns the number of columns in the row.
func (r Row) Len() int { return len(r.m) }

// All returns an iterator over all column-value pairs in the row.
func (r Row) All() iter.Seq2[string, string] {
	return func(yield func(string, string) bool) {
		for k, v := range r.m {
			if !yield(k, v) {
				return
			}
		}
	}
}

// MarshalJSON serializes the backing map, producing identical JSON to
// json.Marshal on the underlying map[string]string.
func (r Row) MarshalJSON() ([]byte, error) { return json.Marshal(r.m) }
