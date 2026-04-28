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
// ABOUTME: Re-exports registry types for convenience
// ABOUTME: This file is kept for backward compatibility

package mcp

import (
	"github.com/deltafi/tui/internal/mcp/registry"
)

// Re-export types from registry package
type Tool = registry.Tool
type Registry = registry.Registry

// GetRegistry returns the singleton registry instance
func GetRegistry() *registry.Registry {
	return registry.Get()
}

// Re-export helper functions
var (
	FormatJSON         = registry.FormatJSON
	ResultFromJSON     = registry.ResultFromJSON
	ErrorResult        = registry.ErrorResult
	ErrorResultFromErr = registry.ErrorResultFromErr
)
