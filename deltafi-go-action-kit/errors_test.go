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
// ABOUTME: Tests for domain-specific error types.
// ABOUTME: Verifies error messages and interface compliance for MissingMetadataError and ExpectedContentError.

package actionkit

import (
	"errors"
	"testing"
)

func TestMissingMetadataError(t *testing.T) {
	err := &MissingMetadataError{Key: "requiredKey"}

	if err.Key != "requiredKey" {
		t.Errorf("Key = %q", err.Key)
	}
	if err.Error() != "missing required metadata key: requiredKey" {
		t.Errorf("Error() = %q", err.Error())
	}

	// Verify it implements error interface
	var e error = err
	if e == nil {
		t.Error("should implement error")
	}
}

func TestExpectedContentError(t *testing.T) {
	err := &ExpectedContentError{Index: 3, Count: 2}

	if err.Index != 3 || err.Count != 2 {
		t.Errorf("Index=%d Count=%d", err.Index, err.Count)
	}
	if err.Error() != "content index 3 out of range (have 2)" {
		t.Errorf("Error() = %q", err.Error())
	}

	var e error = err
	if e == nil {
		t.Error("should implement error")
	}
}

func TestMissingMetadataError_Is(t *testing.T) {
	err := &MissingMetadataError{Key: "test"}
	var target *MissingMetadataError
	if !errors.As(err, &target) {
		t.Error("errors.As should match *MissingMetadataError")
	}
	if target.Key != "test" {
		t.Errorf("Key = %q after errors.As", target.Key)
	}
}

func TestExpectedContentError_Is(t *testing.T) {
	err := &ExpectedContentError{Index: 5, Count: 3}
	var target *ExpectedContentError
	if !errors.As(err, &target) {
		t.Error("errors.As should match *ExpectedContentError")
	}
	if target.Index != 5 || target.Count != 3 {
		t.Errorf("Index=%d Count=%d after errors.As", target.Index, target.Count)
	}
}
