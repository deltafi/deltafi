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
// ABOUTME: ActionOptions types for declaring action inputs, outputs, errors, and filters.
// ABOUTME: Included in plugin registration so the DeltaFi UI can display action documentation.

package actionkit

// ActionOptions declares an action's expected inputs, outputs, error conditions,
// filter conditions, and documentation notes.
type ActionOptions struct {
	Description string                      `json:"description,omitempty" yaml:"description,omitempty"`
	InputSpec   *InputSpec                  `json:"inputSpec,omitempty" yaml:"inputSpec,omitempty"`
	OutputSpec  *OutputSpec                 `json:"outputSpec,omitempty" yaml:"outputSpec,omitempty"`
	Filters     []DescriptionWithConditions `json:"filters,omitempty" yaml:"filters,omitempty"`
	Errors      []DescriptionWithConditions `json:"errors,omitempty" yaml:"errors,omitempty"`
	Notes       []string                    `json:"notes,omitempty" yaml:"notes,omitempty"`
	Details     string                      `json:"details,omitempty" yaml:"details,omitempty"`
}

// InputSpec describes the content and metadata an action expects to receive.
type InputSpec struct {
	ContentSummary       string             `json:"contentSummary,omitempty" yaml:"contentSummary,omitempty"`
	ContentSpecs         []ContentSpec      `json:"contentSpecs,omitempty" yaml:"contentSpecs,omitempty"`
	MetadataSummary      string             `json:"metadataSummary,omitempty" yaml:"metadataSummary,omitempty"`
	MetadataDescriptions []KeyedDescription `json:"metadataDescriptions,omitempty" yaml:"metadataDescriptions,omitempty"`
}

// OutputSpec describes the content, metadata, and annotations an action produces.
type OutputSpec struct {
	ContentSummary         string             `json:"contentSummary,omitempty" yaml:"contentSummary,omitempty"`
	ContentSpecs           []ContentSpec      `json:"contentSpecs,omitempty" yaml:"contentSpecs,omitempty"`
	MetadataSummary        string             `json:"metadataSummary,omitempty" yaml:"metadataSummary,omitempty"`
	MetadataDescriptions   []KeyedDescription `json:"metadataDescriptions,omitempty" yaml:"metadataDescriptions,omitempty"`
	Passthrough            bool               `json:"passthrough,omitempty" yaml:"passthrough,omitempty"`
	AnnotationsSummary     string             `json:"annotationsSummary,omitempty" yaml:"annotationsSummary,omitempty"`
	AnnotationDescriptions []KeyedDescription `json:"annotationDescriptions,omitempty" yaml:"annotationDescriptions,omitempty"`
}

// ContentSpec describes a single piece of content by name, media type, and purpose.
type ContentSpec struct {
	Name        string `json:"name,omitempty" yaml:"name,omitempty"`
	MediaType   string `json:"mediaType,omitempty" yaml:"mediaType,omitempty"`
	Description string `json:"description,omitempty" yaml:"description,omitempty"`
}

// KeyedDescription pairs a key with a human-readable description.
// Used for metadata and annotation documentation.
type KeyedDescription struct {
	Key         string `json:"key" yaml:"key"`
	Description string `json:"description" yaml:"description"`
}

// DescriptionWithConditions describes an error or filter with optional conditions
// under which it occurs.
type DescriptionWithConditions struct {
	Description string   `json:"description" yaml:"description"`
	Conditions  []string `json:"conditions,omitempty" yaml:"conditions,omitempty"`
}

// SimpleFilters creates a list of DescriptionWithConditions from plain description strings.
func SimpleFilters(descriptions ...string) []DescriptionWithConditions {
	return simpleDescriptions(descriptions)
}

// SimpleErrors creates a list of DescriptionWithConditions from plain description strings.
func SimpleErrors(descriptions ...string) []DescriptionWithConditions {
	return simpleDescriptions(descriptions)
}

func simpleDescriptions(descriptions []string) []DescriptionWithConditions {
	result := make([]DescriptionWithConditions, len(descriptions))
	for i, d := range descriptions {
		result[i] = DescriptionWithConditions{Description: d}
	}
	return result
}
