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
// ABOUTME: Deltafile is the primary type action authors interact with.
// ABOUTME: Provides content, metadata, annotation, logging, and error/filter operations.

package actionkit

import (
	"context"
	"errors"
	"fmt"
	"io"
	"maps"
)

// Deltafile is the primary object passed to action methods. It carries input
// content and metadata, accumulates output content and metadata changes, and
// supports error/filter signaling.
type Deltafile struct {
	// Context fields (read-only, populated by the kit)
	Did        string
	Name       string
	DataSource string
	FlowName   string
	FlowID     string
	ActionName string
	Memo       string

	// Input content (from the queue event)
	content []ActionContent

	// Output content (created by the action)
	newContent []ActionContent

	// Metadata: input is read-only, changes tracked separately
	metadata            map[string]string // input metadata
	addedMetadata       map[string]string // metadata to add/override
	deletedMetadataKeys map[string]bool   // metadata keys to remove

	// Annotations added by the action
	annotations map[string]string

	// Log messages
	messages []LogMessage

	// Custom metrics emitted by the action
	metrics []Metric

	// Child DeltaFiles for split transforms
	children []*Deltafile

	// Error/filter state
	errCause      string
	errContext    string
	filterMessage string
	filterContext string
	hasError      bool
	hasFilter     bool

	// Internal services (hidden from action authors)
	actx *ActionContext
}

// newDeltafile creates a Deltafile from an ActionContext and queue event message.
func newDeltafile(actx *ActionContext, content []ActionContent, metadata map[string]string) *Deltafile {
	if metadata == nil {
		metadata = make(map[string]string)
	}
	df := &Deltafile{
		Did:                 actx.DID,
		Name:                actx.DeltaFileName,
		DataSource:          actx.DataSource,
		FlowName:            actx.FlowName,
		FlowID:              actx.FlowID,
		ActionName:          actx.ActionName,
		Memo:                actx.Memo,
		content:             content,
		metadata:            metadata,
		addedMetadata:       make(map[string]string),
		deletedMetadataKeys: make(map[string]bool),
		annotations:         make(map[string]string),
		actx:                actx,
	}
	bindContents(actx, df.content)
	return df
}

// newEmptyDeltafile creates an empty Deltafile with its own DID, suitable for
// use as a child in split transforms or an ingress item.
func newEmptyDeltafile(name string, parentActx *ActionContext) *Deltafile {
	childActx := parentActx.ChildContext()
	return &Deltafile{
		Did:                 childActx.DID,
		Name:                name,
		DataSource:          parentActx.DataSource,
		FlowName:            parentActx.FlowName,
		FlowID:              parentActx.FlowID,
		ActionName:          parentActx.ActionName,
		metadata:            make(map[string]string),
		addedMetadata:       make(map[string]string),
		deletedMetadataKeys: make(map[string]bool),
		annotations:         make(map[string]string),
		actx:                childActx,
	}
}

// Context returns the action's context.Context, which is cancelled on plugin
// shutdown. Use this when making external calls (HTTP, database, etc.) so they
// respect graceful shutdown.
func (df *Deltafile) Context() context.Context {
	if df.actx != nil && df.actx.ctx != nil {
		return df.actx.ctx
	}
	return context.Background()
}

// --- Content Access (input) ---

// ContentAt returns a pointer to the content item at the given index.
func (df *Deltafile) ContentAt(index int) (*ActionContent, error) {
	if index < 0 || index >= len(df.content) {
		return nil, &ExpectedContentError{Index: index, Count: len(df.content)}
	}
	return &df.content[index], nil
}

// FirstContent returns a pointer to the first content item.
func (df *Deltafile) FirstContent() (*ActionContent, error) {
	return df.ContentAt(0)
}

// HasContent returns true if the deltafile has at least one content item.
func (df *Deltafile) HasContent() bool {
	return len(df.content) > 0
}

// ContentCount returns the number of input content items.
func (df *Deltafile) ContentCount() int {
	return len(df.content)
}

// ContentNamed returns a pointer to the first content item with the given name.
func (df *Deltafile) ContentNamed(name string) (*ActionContent, error) {
	for i := range df.content {
		if df.content[i].Name == name {
			return &df.content[i], nil
		}
	}
	return nil, fmt.Errorf("no content named %q", name)
}

// GetContentList returns the input content collection.
func (df *Deltafile) GetContentList() []ActionContent {
	return df.content
}

// --- Content Output ---

// SaveStringContent saves a string as content, adds it to the output content
// list, and returns the resulting ActionContent.
func (df *Deltafile) SaveStringContent(name, mediaType, data string) (*ActionContent, error) {
	return df.SaveBytesContent(name, mediaType, []byte(data))
}

// SaveBytesContent saves bytes as content, adds it to the output content list,
// and returns the resulting ActionContent.
func (df *Deltafile) SaveBytesContent(name, mediaType string, data []byte) (*ActionContent, error) {
	saved, err := df.actx.Save(df.actx.ctx, name, mediaType, data)
	if err != nil {
		return nil, err
	}
	df.newContent = append(df.newContent, *saved)
	return &df.newContent[len(df.newContent)-1], nil
}

// SaveReaderContent streams data from an io.Reader as content, adds it to the
// output content list, and returns the resulting ActionContent. The content
// streams to storage and is never fully buffered in memory; the resulting size
// reflects the bytes actually streamed.
func (df *Deltafile) SaveReaderContent(name, mediaType string, r io.Reader) (*ActionContent, error) {
	saved, err := df.actx.SaveFromReader(df.actx.ctx, name, mediaType, r)
	if err != nil {
		return nil, err
	}
	df.newContent = append(df.newContent, *saved)
	return &df.newContent[len(df.newContent)-1], nil
}

// SaveWriterContent streams content produced by fn as output content, appends it
// to the output content list, and returns the resulting ActionContent. The content
// streams to storage and is never fully buffered in memory.
func (df *Deltafile) SaveWriterContent(name, mediaType string, fn func(io.Writer) error) (*ActionContent, error) {
	saved, err := df.actx.SaveFromWriter(df.actx.ctx, name, mediaType, fn)
	if err != nil {
		return nil, err
	}
	df.newContent = append(df.newContent, *saved)
	return &df.newContent[len(df.newContent)-1], nil
}

// SaveContentBatch saves each item, appends successes to the output content list,
// and returns the saved contents alongside a joined error of any per-item failures.
// Partially-saved-but-unreferenced segments are reclaimed by the worker's orphan
// cleanup if the action ultimately returns an error instead of the content.
func (df *Deltafile) SaveContentBatch(items []ContentItem) ([]ActionContent, error) {
	saved := make([]ActionContent, 0, len(items))
	var errs []error
	for _, item := range items {
		c, err := df.SaveBytesContent(item.Name, item.MediaType, item.Data)
		if err != nil {
			errs = append(errs, fmt.Errorf("save %q: %w", item.Name, err))
			continue
		}
		saved = append(saved, *c)
	}
	return saved, errors.Join(errs...)
}

// SaveWriterContentBatch streams each write, appends successes to the output content
// list, and returns the saved contents alongside a joined error of any failures.
func (df *Deltafile) SaveWriterContentBatch(writes []ContentWrite) ([]ActionContent, error) {
	saved := make([]ActionContent, 0, len(writes))
	var errs []error
	for _, cw := range writes {
		c, err := df.SaveWriterContent(cw.Name, cw.MediaType, cw.Write)
		if err != nil {
			errs = append(errs, fmt.Errorf("save %q: %w", cw.Name, err))
			continue
		}
		saved = append(saved, *c)
	}
	return saved, errors.Join(errs...)
}

// AddContent appends a single content item to the output content list.
func (df *Deltafile) AddContent(c ActionContent) *Deltafile {
	df.newContent = append(df.newContent, c)
	return df
}

// AddContentList appends multiple content items to the output content list.
func (df *Deltafile) AddContentList(contents []ActionContent) *Deltafile {
	df.newContent = append(df.newContent, contents...)
	return df
}

// GetNewContentList returns the output content collection.
func (df *Deltafile) GetNewContentList() []ActionContent {
	return df.newContent
}

// PassthroughContent copies all input content to the output content list.
func (df *Deltafile) PassthroughContent() *Deltafile {
	df.newContent = append(df.newContent, df.content...)
	return df
}

// --- Metadata ---

// AddMetadata adds a metadata key-value pair.
func (df *Deltafile) AddMetadata(key, value string) *Deltafile {
	df.addedMetadata[key] = value
	delete(df.deletedMetadataKeys, key)
	return df
}

// AddMetadataMap copies all entries from the given map into metadata.
func (df *Deltafile) AddMetadataMap(m map[string]string) *Deltafile {
	for k, v := range m {
		df.addedMetadata[k] = v
		delete(df.deletedMetadataKeys, k)
	}
	return df
}

// GetMetadata returns the metadata value for the given key, checking added
// metadata first, then input metadata. Returns a MissingMetadataError if the
// key is not present or has been deleted.
func (df *Deltafile) GetMetadata(key string) (string, error) {
	if v, ok := df.addedMetadata[key]; ok {
		return v, nil
	}
	if df.deletedMetadataKeys[key] {
		return "", &MissingMetadataError{Key: key}
	}
	if v, ok := df.metadata[key]; ok {
		return v, nil
	}
	return "", &MissingMetadataError{Key: key}
}

// GetMetadataOrDefault returns the metadata value for the given key, or the
// default value if the key is not present.
func (df *Deltafile) GetMetadataOrDefault(key, defaultValue string) string {
	if v, err := df.GetMetadata(key); err == nil {
		return v
	}
	return defaultValue
}

// GetMetadataMap returns a merged view of input and added metadata, excluding
// deleted keys.
func (df *Deltafile) GetMetadataMap() map[string]string {
	merged := make(map[string]string, len(df.metadata)+len(df.addedMetadata))
	for k, v := range df.metadata {
		if !df.deletedMetadataKeys[k] {
			merged[k] = v
		}
	}
	maps.Copy(merged, df.addedMetadata)
	return merged
}

// DeleteMetadataKeys marks the given keys for removal from metadata.
func (df *Deltafile) DeleteMetadataKeys(keys ...string) *Deltafile {
	for _, k := range keys {
		df.deletedMetadataKeys[k] = true
		delete(df.addedMetadata, k)
	}
	return df
}

// ClearMetadata marks all input metadata keys for deletion.
func (df *Deltafile) ClearMetadata() *Deltafile {
	for k := range df.metadata {
		df.deletedMetadataKeys[k] = true
	}
	df.addedMetadata = make(map[string]string)
	return df
}

// --- Annotations ---

// AddAnnotation adds an annotation key-value pair.
func (df *Deltafile) AddAnnotation(key, value string) *Deltafile {
	df.annotations[key] = value
	return df
}

// AddMetric emits a custom metric with optional tags.
func (df *Deltafile) AddMetric(name string, value float64, tags map[string]string) *Deltafile {
	df.metrics = append(df.metrics, Metric{Name: name, Value: value, Tags: tags})
	return df
}

// --- Error / Filter ---

// Errorf sets an error state on the Deltafile and returns it for chaining.
// When a Deltafile with an error state is returned from an action, the kit
// converts it to an error result.
func (df *Deltafile) Errorf(format string, args ...any) *Deltafile {
	df.hasError = true
	df.hasFilter = false
	df.errCause = fmt.Sprintf(format, args...)
	return df
}

// Fail marks the Deltafile as errored using err's message as the cause and
// returns it for chaining. Equivalent to Errorf("%s", err.Error()).
func (df *Deltafile) Fail(err error) *Deltafile {
	return df.Errorf("%s", failCause(err))
}

// Failf marks the Deltafile as errored with a formatted cause and returns it
// for chaining.
func (df *Deltafile) Failf(format string, args ...any) *Deltafile {
	return df.Errorf(format, args...)
}

// Filterf sets a filter state on the Deltafile and returns it for chaining.
// When a Deltafile with a filter state is returned from an action, the kit
// converts it to a filter result.
func (df *Deltafile) Filterf(format string, args ...any) *Deltafile {
	df.hasFilter = true
	df.hasError = false
	df.filterMessage = fmt.Sprintf(format, args...)
	return df
}

// SetFilterContext sets additional context for a filter result and returns the
// Deltafile for chaining. Only meaningful after calling Filterf.
func (df *Deltafile) SetFilterContext(ctx string) *Deltafile {
	df.filterContext = ctx
	return df
}

// SetErrorContext sets additional context for an error and returns the
// Deltafile for chaining. Only meaningful after calling Errorf.
func (df *Deltafile) SetErrorContext(ctx string) *Deltafile {
	df.errContext = ctx
	return df
}

// --- Logging ---

// LogInfo adds an INFO-level log message.
func (df *Deltafile) LogInfo(format string, args ...any) *Deltafile {
	df.appendLog(SeverityInfo, format, args...)
	return df
}

// LogWarn adds a WARNING-level log message.
func (df *Deltafile) LogWarn(format string, args ...any) *Deltafile {
	df.appendLog(SeverityWarning, format, args...)
	return df
}

// LogError adds an ERROR-level log message.
func (df *Deltafile) LogError(format string, args ...any) *Deltafile {
	df.appendLog(SeverityError, format, args...)
	return df
}

func (df *Deltafile) appendLog(severity LogSeverity, format string, args ...any) {
	var msg string
	if len(args) == 0 {
		msg = format
	} else {
		msg = fmt.Sprintf(format, args...)
	}
	df.messages = append(df.messages, newLogMessage(severity, df.ActionName, msg))
}

// AddChild creates a new empty Deltafile with a unique DID for use as a child
// in a split transform. The child is automatically tracked on the parent; the
// dispatcher produces a multi-child transform event when children are present.
func (df *Deltafile) AddChild(name string) *Deltafile {
	child := newEmptyDeltafile(name, df.actx)
	df.children = append(df.children, child)
	return child
}

// GetChildren returns the child Deltafiles created via AddChild.
func (df *Deltafile) GetChildren() []*Deltafile {
	return df.children
}

// --- Internal conversion ---

// deleteMetadataKeySlice returns the deleted metadata keys as a string slice.
func (df *Deltafile) deleteMetadataKeySlice() []string {
	if len(df.deletedMetadataKeys) == 0 {
		return nil
	}
	keys := make([]string, 0, len(df.deletedMetadataKeys))
	for k := range df.deletedMetadataKeys {
		keys = append(keys, k)
	}
	return keys
}

// toTransformEvent converts the Deltafile's output state to a TransformEvent.
func (df *Deltafile) toTransformEvent() TransformEvent {
	return TransformEvent{
		DID:                df.Did,
		Content:            df.newContent,
		Metadata:           df.addedMetadata,
		DeleteMetadataKeys: df.deleteMetadataKeySlice(),
		Annotations:        df.annotations,
		Messages:           df.messages,
	}
}
