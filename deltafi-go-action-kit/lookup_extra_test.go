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
// ABOUTME: Tests for the WithMatches lookup option (dynamic column→values merge).
// ABOUTME: Verifies map merge and append-to-existing behavior on lookupQueryParams.

package actionkit

import "testing"

func TestWithMatches_MergesColumns(t *testing.T) {
	var q lookupQueryParams
	WithMatches(map[string][]string{
		"color": {"red", "blue"},
		"size":  {"L"},
	})(&q)

	if got := q.MatchingColumnValues["color"]; len(got) != 2 || got[0] != "red" || got[1] != "blue" {
		t.Errorf("color = %v, want [red blue]", got)
	}
	if got := q.MatchingColumnValues["size"]; len(got) != 1 || got[0] != "L" {
		t.Errorf("size = %v, want [L]", got)
	}
}

func TestWithMatches_AppendsToExisting(t *testing.T) {
	var q lookupQueryParams
	WithMatch("color", "red")(&q)
	WithMatches(map[string][]string{"color": {"blue"}})(&q)

	if got := q.MatchingColumnValues["color"]; len(got) != 2 {
		t.Errorf("color = %v, want two values after merge", got)
	}
}
