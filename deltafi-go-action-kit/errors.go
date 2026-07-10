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
// ABOUTME: Domain-specific error types for action input validation.
// ABOUTME: Defines MissingMetadataError and ExpectedContentError for cleaner action code.

package actionkit

import "fmt"

// MissingMetadataError is returned when a required metadata key is not found.
type MissingMetadataError struct {
	Key string
}

func (e *MissingMetadataError) Error() string {
	return fmt.Sprintf("missing required metadata key: %s", e.Key)
}

// ExpectedContentError is returned when content is expected but not available.
type ExpectedContentError struct {
	Index int
	Count int
}

func (e *ExpectedContentError) Error() string {
	return fmt.Sprintf("content index %d out of range (have %d)", e.Index, e.Count)
}

// failCause returns err's message, or a placeholder when err is nil so the
// Fail helpers never panic on a nil error.
func failCause(err error) string {
	if err == nil {
		return "nil error"
	}
	return err.Error()
}
