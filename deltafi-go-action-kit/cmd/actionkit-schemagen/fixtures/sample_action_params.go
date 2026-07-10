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
// ABOUTME: Test fixture covering primitives, nested structs, slices, maps, defaults, and required tags.
// ABOUTME: Lives in a non-testdata package so it can be both AST-parsed and reflect-loaded.

package fixtures

// SampleParams exercises the parameter features the schemagen tool supports.
type SampleParams struct {
	ConvertToUpper bool              `json:"convertToUpper" description:"convert to upper" default:"false"`
	MaxItems       int               `json:"maxItems" description:"max items" default:"10" minimum:"0" maximum:"100"`
	Mode           string            `json:"mode" description:"processing mode" enum:"fast,slow,auto"`
	Label          string            `json:"label" description:"label" required:"true"`
	Nested         *NestedStruct     `json:"nested" description:"a nested struct"`
	StringList     []string          `json:"stringList" description:"a list of strings"`
	StructList     []NestedStruct    `json:"structList" description:"a list of nested structs"`
	StringMap      map[string]string `json:"stringMap" description:"a string-to-string map"`
}

// NestedStruct demonstrates a nested parameter type.
type NestedStruct struct {
	Flag  bool `json:"flag" description:"a flag" default:"true"`
	Count int  `json:"count" description:"a count" default:"5"`
}
