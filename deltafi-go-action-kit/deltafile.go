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
	"fmt"
	"io"
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

	// Child DeltaFiles for split transforms
	children []*Deltafile

	// Error/filter state
	errCause      string
	errContext    string
	filterMessage string
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

// GetContentSlice returns the input content slice.
func (df *Deltafile) GetContentSlice() []ActionContent {
	return df.content
}

// --- Content Output ---

// SaveStringContent saves a string as content, adds it to the output content
// list, and returns the resulting ActionContent.
func (df *Deltafile) SaveStringContent(data, name, mediaType string) (*ActionContent, error) {
	return df.SaveBytesContent([]byte(data), name, mediaType)
}

// SaveBytesContent saves bytes as content, adds it to the output content list,
// and returns the resulting ActionContent.
func (df *Deltafile) SaveBytesContent(data []byte, name, mediaType string) (*ActionContent, error) {
	saved, err := df.actx.Save(df.actx.ctx, data, name, mediaType)
	if err != nil {
		return nil, err
	}
	df.newContent = append(df.newContent, *saved)
	return &df.newContent[len(df.newContent)-1], nil
}

// SaveReaderContent saves data from an io.Reader as content, adds it to the
// output content list, and returns the resulting ActionContent. Use size = -1
// if the size is not known in advance.
func (df *Deltafile) SaveReaderContent(r io.Reader, size int64, name, mediaType string) (*ActionContent, error) {
	saved, err := df.actx.SaveFromReader(df.actx.ctx, r, size, name, mediaType)
	if err != nil {
		return nil, err
	}
	df.newContent = append(df.newContent, *saved)
	return &df.newContent[len(df.newContent)-1], nil
}

// AddContent appends a single content item to the output content list.
func (df *Deltafile) AddContent(c ActionContent) *Deltafile {
	df.newContent = append(df.newContent, c)
	return df
}

// AddContentSlice appends multiple content items to the output content list.
func (df *Deltafile) AddContentSlice(contents []ActionContent) *Deltafile {
	df.newContent = append(df.newContent, contents...)
	return df
}

// GetNewContentSlice returns the output content slice.
func (df *Deltafile) GetNewContentSlice() []ActionContent {
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
	for k, v := range df.addedMetadata {
		merged[k] = v
	}
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

// Filterf sets a filter state on the Deltafile and returns it for chaining.
// When a Deltafile with a filter state is returned from an action, the kit
// converts it to a filter result.
func (df *Deltafile) Filterf(format string, args ...any) *Deltafile {
	df.hasFilter = true
	df.hasError = false
	df.filterMessage = fmt.Sprintf(format, args...)
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

// NewChild creates a new empty Deltafile with a unique DID for use as a child
// in a split transform. The child is automatically tracked on the parent; the
// dispatcher produces a multi-child transform event when children are present.
func (df *Deltafile) NewChild(name string) *Deltafile {
	child := newEmptyDeltafile(name, df.actx)
	df.children = append(df.children, child)
	return child
}

// GetChildren returns the child Deltafiles created via NewChild.
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

// collectNewSegments returns all segments from the output content.
func (df *Deltafile) collectNewSegments() []Segment {
	var segs []Segment
	for _, c := range df.newContent {
		segs = append(segs, c.Segments...)
	}
	return segs
}

// --- IngressInput ---

// IngressInput holds context for timed ingress actions.
type IngressInput struct {
	Memo       string
	FlowName   string
	ActionName string
	actx       *ActionContext
}

// Context returns the action's context.Context, which is cancelled on plugin
// shutdown. Use this when making external calls (HTTP, database, etc.) so they
// respect graceful shutdown.
func (input *IngressInput) Context() context.Context {
	if input.actx != nil && input.actx.ctx != nil {
		return input.actx.ctx
	}
	return context.Background()
}

// NewDeltafile creates an empty Deltafile with a unique DID for use as an
// ingress item. The returned Deltafile can save content via its Save methods.
func (input *IngressInput) NewDeltafile(name string) *Deltafile {
	return newEmptyDeltafile(name, input.actx)
}

// --- IngressResult ---

// IngressResult holds the output of a timed ingress action.
type IngressResult struct {
	items            []*Deltafile
	memo             string
	status           string
	statusMessage    string
	executeImmediate bool
	messages         []LogMessage
	hasError         bool
	errCause         string
	errContext        string
}

// NewIngressResult creates an IngressResult with healthy status.
func NewIngressResult() *IngressResult {
	return &IngressResult{
		status: "HEALTHY",
	}
}

// AddItem appends a Deltafile as an ingress item and returns the result for chaining.
func (r *IngressResult) AddItem(df *Deltafile) *IngressResult {
	r.items = append(r.items, df)
	return r
}

// SetMemo sets the memo string and returns the result for chaining.
func (r *IngressResult) SetMemo(m string) *IngressResult {
	r.memo = m
	return r
}

// SetStatus sets the ingress health status and returns the result for chaining.
func (r *IngressResult) SetStatus(s string) *IngressResult {
	r.status = s
	return r
}

// SetStatusMessage sets a human-readable status message and returns the result for chaining.
func (r *IngressResult) SetStatusMessage(m string) *IngressResult {
	r.statusMessage = m
	return r
}

// SetExecuteImmediate requests that the next ingress invocation run immediately.
func (r *IngressResult) SetExecuteImmediate(b bool) *IngressResult {
	r.executeImmediate = b
	return r
}

// Errorf sets an error state on the IngressResult. When the result has an
// error, the kit converts it to an error event instead of an ingress event.
func (r *IngressResult) Errorf(format string, args ...any) *IngressResult {
	r.hasError = true
	r.errCause = fmt.Sprintf(format, args...)
	return r
}

// SetErrorContext sets additional context for an error.
func (r *IngressResult) SetErrorContext(ctx string) *IngressResult {
	r.errContext = ctx
	return r
}

// LogInfo adds an INFO-level log message.
func (r *IngressResult) LogInfo(format string, args ...any) *IngressResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarn adds a WARNING-level log message.
func (r *IngressResult) LogWarn(format string, args ...any) *IngressResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level log message.
func (r *IngressResult) LogError(format string, args ...any) *IngressResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *IngressResult) appendLog(severity LogSeverity, format string, args ...any) {
	var msg string
	if len(args) == 0 {
		msg = format
	} else {
		msg = fmt.Sprintf(format, args...)
	}
	r.messages = append(r.messages, newLogMessage(severity, "ingress", msg))
}

