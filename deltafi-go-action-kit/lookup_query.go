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
// ABOUTME: Builder for lookup table queries with match, exclude, sort, and pagination options.
// ABOUTME: ExcludingMatch filters results client-side after the query returns from core.

package actionkit

import "slices"

// LookupQueryBuilder constructs a lookup query using the builder pattern.
// Create one via LookupQuery() and chain methods to configure the query.
type LookupQueryBuilder struct {
	matchingColumns  map[string][]string
	excludingColumns map[string][]string
	resultColumns    []string
	sortColumn       string
	sortDirection    SortDirection
	offset           *int
	limit            *int
}

// LookupQuery creates a new LookupQueryBuilder.
func LookupQuery() *LookupQueryBuilder {
	return &LookupQueryBuilder{}
}

// WithMatch adds an inclusion criterion: the named column must contain any of the given values.
func (q *LookupQueryBuilder) WithMatch(column string, values ...string) *LookupQueryBuilder {
	if q.matchingColumns == nil {
		q.matchingColumns = make(map[string][]string)
	}
	q.matchingColumns[column] = append(q.matchingColumns[column], values...)
	return q
}

// ExcludingMatch adds an exclusion criterion: rows where the named column matches
// any of the given values are removed from results. Filtering is applied client-side
// after the query returns from core.
func (q *LookupQueryBuilder) ExcludingMatch(column string, values ...string) *LookupQueryBuilder {
	if q.excludingColumns == nil {
		q.excludingColumns = make(map[string][]string)
	}
	q.excludingColumns[column] = append(q.excludingColumns[column], values...)
	return q
}

// WithResultColumns restricts the query to return only the named columns.
func (q *LookupQueryBuilder) WithResultColumns(columns ...string) *LookupQueryBuilder {
	q.resultColumns = append(q.resultColumns, columns...)
	return q
}

// WithSort orders results by the given column in the given direction.
func (q *LookupQueryBuilder) WithSort(column string, direction SortDirection) *LookupQueryBuilder {
	q.sortColumn = column
	q.sortDirection = direction
	return q
}

// WithLimit caps the number of returned rows.
func (q *LookupQueryBuilder) WithLimit(n int) *LookupQueryBuilder {
	q.limit = &n
	return q
}

// WithOffset skips the first N results.
func (q *LookupQueryBuilder) WithOffset(n int) *LookupQueryBuilder {
	q.offset = &n
	return q
}

// toLookupOptions converts the builder into the existing LookupOption slice
// used by LookupClientBase.Lookup. ExcludingMatch criteria are NOT included
// here; they are applied client-side after the query returns.
func (q *LookupQueryBuilder) toLookupOptions() []LookupOption {
	if q == nil {
		return nil
	}
	var opts []LookupOption
	for col, vals := range q.matchingColumns {
		opts = append(opts, WithMatch(col, vals...))
	}
	if len(q.resultColumns) > 0 {
		opts = append(opts, WithResultColumns(q.resultColumns...))
	}
	if q.sortColumn != "" {
		opts = append(opts, WithSort(q.sortColumn, q.sortDirection))
	}
	if q.offset != nil {
		opts = append(opts, WithOffset(*q.offset))
	}
	if q.limit != nil {
		opts = append(opts, WithLimit(*q.limit))
	}
	return opts
}

// applyExclusions removes rows matching any ExcludingMatch criteria.
func (q *LookupQueryBuilder) applyExclusions(results *LookupResults) *LookupResults {
	if q == nil || len(q.excludingColumns) == 0 || results == nil {
		return results
	}
	filtered := make([]map[string]string, 0, len(results.Rows))
	for _, row := range results.Rows {
		excluded := false
		for col, vals := range q.excludingColumns {
			if slices.Contains(vals, row[col]) {
				excluded = true
				break
			}
		}
		if !excluded {
			filtered = append(filtered, row)
		}
	}
	return &LookupResults{
		TotalCount: len(filtered),
		Created:    results.Created,
		Modified:   results.Modified,
		Rows:       filtered,
	}
}
