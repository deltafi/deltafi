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
// ABOUTME: EgressDeltafile is the read-mostly Deltafile view passed to Egress actions.
// ABOUTME: Exposes content and metadata reads, logging, annotations, and error/filter signaling.

package actionkit

import "context"

// EgressDeltafile is the view of a Deltafile passed to Egress actions. It
// exposes content and metadata reads, logging, annotations, and error/filter
// signaling. Output-content mutations, child creation, and metadata mutations
// are intentionally not exposed: the EGRESS event format does not carry them,
// and including them would silently drop the action's writes.
type EgressDeltafile struct {
	Did        string
	Name       string
	DataSource string
	FlowName   string
	FlowID     string
	ActionName string
	Memo       string

	df *Deltafile
}

// newEgressDeltafile wraps a Deltafile for use by an egress action.
func newEgressDeltafile(df *Deltafile) *EgressDeltafile {
	return &EgressDeltafile{
		Did:        df.Did,
		Name:       df.Name,
		DataSource: df.DataSource,
		FlowName:   df.FlowName,
		FlowID:     df.FlowID,
		ActionName: df.ActionName,
		Memo:       df.Memo,
		df:         df,
	}
}

// Context returns the action's context.Context, cancelled on plugin shutdown.
func (e *EgressDeltafile) Context() context.Context {
	return e.df.Context()
}

// --- Content reads ---

// ContentAt returns the input content at the given index.
func (e *EgressDeltafile) ContentAt(index int) (*ActionContent, error) {
	return e.df.ContentAt(index)
}

// FirstContent returns the first input content item.
func (e *EgressDeltafile) FirstContent() (*ActionContent, error) {
	return e.df.FirstContent()
}

// HasContent reports whether the Deltafile has any input content.
func (e *EgressDeltafile) HasContent() bool {
	return e.df.HasContent()
}

// ContentCount returns the number of input content items.
func (e *EgressDeltafile) ContentCount() int {
	return e.df.ContentCount()
}

// ContentNamed returns the input content item with the given name.
func (e *EgressDeltafile) ContentNamed(name string) (*ActionContent, error) {
	return e.df.ContentNamed(name)
}

// GetContentList returns the input content collection.
func (e *EgressDeltafile) GetContentList() []ActionContent {
	return e.df.GetContentList()
}

// --- Metadata reads ---

// GetMetadata returns the metadata value for the given key, or
// MissingMetadataError if the key is not present.
func (e *EgressDeltafile) GetMetadata(key string) (string, error) {
	return e.df.GetMetadata(key)
}

// GetMetadataOrDefault returns the metadata value for the given key, or the
// given default if the key is not present.
func (e *EgressDeltafile) GetMetadataOrDefault(key, defaultValue string) string {
	return e.df.GetMetadataOrDefault(key, defaultValue)
}

// GetMetadataMap returns the input metadata as a map.
func (e *EgressDeltafile) GetMetadataMap() map[string]string {
	return e.df.GetMetadataMap()
}

// --- Annotations ---

// AddAnnotation adds an annotation. Annotations propagate to ERROR and FILTER
// events; they are not carried on a successful EGRESS event.
func (e *EgressDeltafile) AddAnnotation(key, value string) *EgressDeltafile {
	e.df.AddAnnotation(key, value)
	return e
}

// AddMetric emits a custom metric with optional tags.
func (e *EgressDeltafile) AddMetric(name string, value float64, tags map[string]string) *EgressDeltafile {
	e.df.AddMetric(name, value, tags)
	return e
}

// --- Error / Filter ---

// Errorf marks the egress as failed and returns the EgressDeltafile for chaining.
func (e *EgressDeltafile) Errorf(format string, args ...any) *EgressDeltafile {
	e.df.Errorf(format, args...)
	return e
}

// Fail marks the egress as failed using err's message and returns it for chaining.
func (e *EgressDeltafile) Fail(err error) *EgressDeltafile {
	e.df.Errorf("%s", failCause(err))
	return e
}

// Failf marks the egress as failed with a formatted cause and returns it for chaining.
func (e *EgressDeltafile) Failf(format string, args ...any) *EgressDeltafile {
	e.df.Errorf(format, args...)
	return e
}

// Filterf marks the egress as filtered and returns the EgressDeltafile for chaining.
func (e *EgressDeltafile) Filterf(format string, args ...any) *EgressDeltafile {
	e.df.Filterf(format, args...)
	return e
}

// SetFilterContext sets additional context for a Filterf result and returns the
// EgressDeltafile for chaining. Only meaningful after calling Filterf.
func (e *EgressDeltafile) SetFilterContext(ctx string) *EgressDeltafile {
	e.df.SetFilterContext(ctx)
	return e
}

// SetErrorContext sets additional context for an Errorf result and returns the
// EgressDeltafile for chaining. Only meaningful after calling Errorf.
func (e *EgressDeltafile) SetErrorContext(ctx string) *EgressDeltafile {
	e.df.SetErrorContext(ctx)
	return e
}

// --- Logging ---

// LogInfo adds an INFO-level log message.
func (e *EgressDeltafile) LogInfo(format string, args ...any) *EgressDeltafile {
	e.df.LogInfo(format, args...)
	return e
}

// LogWarn adds a WARNING-level log message.
func (e *EgressDeltafile) LogWarn(format string, args ...any) *EgressDeltafile {
	e.df.LogWarn(format, args...)
	return e
}

// LogError adds an ERROR-level log message.
func (e *EgressDeltafile) LogError(format string, args ...any) *EgressDeltafile {
	e.df.LogError(format, args...)
	return e
}
