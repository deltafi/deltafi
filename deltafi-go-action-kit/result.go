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
// ABOUTME: Result types returned by actions after execution.
// ABOUTME: Defines TransformResult, EgressResult, IngressResult, ErrorResult, FilterResult and builders.

package actionkit

import (
	"fmt"
	"time"

	"github.com/google/uuid"
)

// Result is the sealed interface implemented by all action result types.
type Result interface {
	toActionEvent(actx *ActionContext, start time.Time) *ActionEvent
}

// baseResult holds fields shared by all result types.
type baseResult struct {
	actionName string
	messages   []LogMessage
}

// appendLog formats the message and appends it to the base result's log.
// An empty args slice is treated as a plain message so callers that already
// pre-formatted their message (or just want to log literal text containing
// % characters) do not need to escape them.
func (b *baseResult) appendLog(severity LogSeverity, format string, args ...any) {
	var msg string
	if len(args) == 0 {
		msg = format
	} else {
		msg = fmt.Sprintf(format, args...)
	}
	b.messages = append(b.messages, newLogMessage(severity, b.actionName, msg))
}

// ActionName returns the name of the action this result was created for.
func (b *baseResult) ActionName() string { return b.actionName }

// newBaseEvent creates an ActionEvent with common fields filled in.
func newBaseEvent(actx *ActionContext, start time.Time, eventType string, messages []LogMessage) *ActionEvent {
	return &ActionEvent{
		DID:        actx.DID,
		FlowName:   actx.FlowName,
		FlowID:     actx.FlowID,
		ActionName: actx.ActionName,
		Start:      formatTime(start),
		Stop:       formatTime(time.Now()),
		Type:       eventType,
		Messages:   messages,
	}
}

// --- TransformResult ---

// TransformResult holds the output of a transform action.
type TransformResult struct {
	baseResult
	actx               *ActionContext
	content            []ActionContent
	metadata           map[string]string
	deleteMetadataKeys []string
	annotations        map[string]string
}

// NewTransformResult creates a new TransformResult for the given action context.
func NewTransformResult(actx *ActionContext) *TransformResult {
	return &TransformResult{
		baseResult:  baseResult{actionName: actx.ActionName},
		actx:        actx,
		content:     []ActionContent{},
		metadata:    make(map[string]string),
		annotations: make(map[string]string),
	}
}

// AddContent appends a content item and returns the result for chaining.
func (r *TransformResult) AddContent(c ActionContent) *TransformResult {
	r.content = append(r.content, c)
	return r
}

// AddMetadata sets a metadata key-value pair and returns the result for chaining.
func (r *TransformResult) AddMetadata(k, v string) *TransformResult {
	r.metadata[k] = v
	return r
}

// DeleteMetadataKey marks a metadata key for removal and returns the result for chaining.
func (r *TransformResult) DeleteMetadataKey(k string) *TransformResult {
	r.deleteMetadataKeys = append(r.deleteMetadataKeys, k)
	return r
}

// AddAnnotation sets an annotation key-value pair and returns the result for chaining.
func (r *TransformResult) AddAnnotation(k, v string) *TransformResult {
	r.annotations[k] = v
	return r
}

// AddSelfAnnotation sets an annotation keyed by the action's own name.
func (r *TransformResult) AddSelfAnnotation(v string) *TransformResult {
	return r.AddAnnotation(r.actionName, v)
}

// AddSelfMetadata sets a metadata entry keyed by the action's own name.
func (r *TransformResult) AddSelfMetadata(v string) *TransformResult {
	return r.AddMetadata(r.actionName, v)
}

// LogInfo adds an INFO-level message and returns the result for chaining.
// The message is formatted with fmt.Sprintf when args are supplied, or used
// as-is when called with just a format string.
func (r *TransformResult) LogInfo(format string, args ...any) *TransformResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *TransformResult) LogWarning(format string, args ...any) *TransformResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *TransformResult) LogError(format string, args ...any) *TransformResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *TransformResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "TRANSFORM", r.messages)
	event.Transform = []TransformEvent{{
		DID:                actx.DID,
		Content:            r.content,
		Metadata:           r.metadata,
		DeleteMetadataKeys: r.deleteMetadataKeys,
		Annotations:        r.annotations,
	}}
	return event
}

// --- TransformResults (multi-child) ---

// TransformResults holds the output of a transform action that splits into multiple children.
type TransformResults struct {
	baseResult
	actx     *ActionContext
	children []ChildTransformResult
}

// NewTransformResults creates a new TransformResults for multi-child splitting.
func NewTransformResults(actx *ActionContext) *TransformResults {
	return &TransformResults{
		baseResult: baseResult{actionName: actx.ActionName},
		actx:       actx,
	}
}

// AddChild appends a child result and returns the result for chaining.
func (r *TransformResults) AddChild(child ChildTransformResult) *TransformResults {
	r.children = append(r.children, child)
	return r
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *TransformResults) LogInfo(format string, args ...any) *TransformResults {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *TransformResults) LogWarning(format string, args ...any) *TransformResults {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *TransformResults) LogError(format string, args ...any) *TransformResults {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *TransformResults) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "TRANSFORM", r.messages)

	transforms := make([]TransformEvent, len(r.children))
	for i, child := range r.children {
		transforms[i] = TransformEvent{
			Name:               child.deltaFileName,
			DID:                child.actx.DID,
			Content:            child.content,
			Metadata:           child.metadata,
			DeleteMetadataKeys: child.deleteMetadataKeys,
			Annotations:        child.annotations,
			Messages:           child.messages,
		}
	}
	event.Transform = transforms
	return event
}

// ChildTransformResult holds the output for a single child in a multi-child split.
type ChildTransformResult struct {
	baseResult
	actx               *ActionContext
	deltaFileName      string
	content            []ActionContent
	metadata           map[string]string
	deleteMetadataKeys []string
	annotations        map[string]string
}

// NewChildTransformResult creates a child result with a new DID from the parent context.
// The optional deltaFileName sets the name for the child DeltaFile.
func NewChildTransformResult(parentCtx *ActionContext, deltaFileName string) *ChildTransformResult {
	childCtx := parentCtx.ChildContext()
	return &ChildTransformResult{
		baseResult:    baseResult{actionName: parentCtx.ActionName},
		actx:          childCtx,
		deltaFileName: deltaFileName,
		content:       []ActionContent{},
		metadata:      make(map[string]string),
		annotations:   make(map[string]string),
	}
}

// Context returns the child's ActionContext (with its unique DID).
func (r *ChildTransformResult) Context() *ActionContext { return r.actx }

// AddContent appends a content item and returns the result for chaining.
func (r *ChildTransformResult) AddContent(c ActionContent) *ChildTransformResult {
	r.content = append(r.content, c)
	return r
}

// AddMetadata sets a metadata key-value pair and returns the result for chaining.
func (r *ChildTransformResult) AddMetadata(k, v string) *ChildTransformResult {
	r.metadata[k] = v
	return r
}

// DeleteMetadataKey marks a metadata key for removal and returns the result for chaining.
func (r *ChildTransformResult) DeleteMetadataKey(k string) *ChildTransformResult {
	r.deleteMetadataKeys = append(r.deleteMetadataKeys, k)
	return r
}

// AddAnnotation sets an annotation key-value pair and returns the result for chaining.
func (r *ChildTransformResult) AddAnnotation(k, v string) *ChildTransformResult {
	r.annotations[k] = v
	return r
}

// AddSelfAnnotation sets an annotation keyed by the action's own name.
func (r *ChildTransformResult) AddSelfAnnotation(v string) *ChildTransformResult {
	return r.AddAnnotation(r.actionName, v)
}

// AddSelfMetadata sets a metadata entry keyed by the action's own name.
func (r *ChildTransformResult) AddSelfMetadata(v string) *ChildTransformResult {
	return r.AddMetadata(r.actionName, v)
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *ChildTransformResult) LogInfo(format string, args ...any) *ChildTransformResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *ChildTransformResult) LogWarning(format string, args ...any) *ChildTransformResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *ChildTransformResult) LogError(format string, args ...any) *ChildTransformResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

// --- EgressResult ---

// EgressResult holds the output of an egress action.
type EgressResult struct {
	baseResult
	actx *ActionContext
}

// NewEgressResult creates a new EgressResult for the given action context.
func NewEgressResult(actx *ActionContext) *EgressResult {
	return &EgressResult{
		baseResult: baseResult{actionName: actx.ActionName},
		actx:       actx,
	}
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *EgressResult) LogInfo(format string, args ...any) *EgressResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *EgressResult) LogWarning(format string, args ...any) *EgressResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *EgressResult) LogError(format string, args ...any) *EgressResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *EgressResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	return newBaseEvent(actx, start, "EGRESS", r.messages)
}

// --- IngressResult ---

// IngressResult holds the output of a timed ingress action.
type IngressResult struct {
	baseResult
	actx             *ActionContext
	items            []IngressResultItem
	memo             string
	status           string
	statusMessage    string
	executeImmediate bool
}

// NewIngressResult creates a new IngressResult for the given action context.
func NewIngressResult(actx *ActionContext) *IngressResult {
	return &IngressResult{
		baseResult: baseResult{actionName: actx.ActionName},
		actx:       actx,
		status:     "HEALTHY",
	}
}

// AddItem appends an ingress item and returns the result for chaining.
func (r *IngressResult) AddItem(item IngressResultItem) *IngressResult {
	r.items = append(r.items, item)
	return r
}

// SetMemo sets the memo string and returns the result for chaining.
func (r *IngressResult) SetMemo(m string) *IngressResult { r.memo = m; return r }

// SetStatus sets the ingress health status and returns the result for chaining.
func (r *IngressResult) SetStatus(s string) *IngressResult { r.status = s; return r }

// SetStatusMessage sets a human-readable message and returns the result for chaining.
func (r *IngressResult) SetStatusMessage(m string) *IngressResult { r.statusMessage = m; return r }

// SetExecuteImmediate requests that the next ingress invocation run immediately.
func (r *IngressResult) SetExecuteImmediate(b bool) *IngressResult {
	r.executeImmediate = b
	return r
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *IngressResult) LogInfo(format string, args ...any) *IngressResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *IngressResult) LogWarning(format string, args ...any) *IngressResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *IngressResult) LogError(format string, args ...any) *IngressResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *IngressResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "INGRESS", r.messages)

	items := make([]IngressEventItem, len(r.items))
	for i, item := range r.items {
		items[i] = IngressEventItem{
			DID:           item.did,
			DeltaFileName: item.deltaFileName,
			Content:       item.content,
			Metadata:      item.metadata,
			Annotations:   item.annotations,
		}
	}

	event.Ingress = &IngressEvent{
		Memo:             r.memo,
		ExecuteImmediate: r.executeImmediate,
		Status:           r.status,
		StatusMessage:    r.statusMessage,
		IngressItems:     items,
	}
	return event
}

// --- IngressResultItem ---

// IngressResultItem describes a single DeltaFile to be created by ingress.
type IngressResultItem struct {
	did           string
	deltaFileName string
	content       []ActionContent
	metadata      map[string]string
	annotations   map[string]string
}

// NewIngressResultItem creates a new item with a generated DID.
func NewIngressResultItem(deltaFileName string) *IngressResultItem {
	return &IngressResultItem{
		did:           uuid.New().String(),
		deltaFileName: deltaFileName,
		content:       []ActionContent{},
		metadata:      make(map[string]string),
		annotations:   make(map[string]string),
	}
}

// AddContent appends a content item and returns the item for chaining.
func (item *IngressResultItem) AddContent(c ActionContent) *IngressResultItem {
	item.content = append(item.content, c)
	return item
}

// AddMetadata sets a metadata key-value pair and returns the item for chaining.
func (item *IngressResultItem) AddMetadata(k, v string) *IngressResultItem {
	item.metadata[k] = v
	return item
}

// AddAnnotation sets an annotation key-value pair and returns the item for chaining.
func (item *IngressResultItem) AddAnnotation(k, v string) *IngressResultItem {
	item.annotations[k] = v
	return item
}

// --- ErrorResult ---

// ErrorResult indicates an error during action execution.
type ErrorResult struct {
	baseResult
	actx         *ActionContext
	cause        string
	errorContext string
	annotations  map[string]string
}

// NewErrorResult creates a new ErrorResult with the given cause.
func NewErrorResult(actx *ActionContext, cause string) *ErrorResult {
	return &ErrorResult{
		baseResult:  baseResult{actionName: actx.ActionName},
		actx:        actx,
		cause:       cause,
		annotations: make(map[string]string),
	}
}

// SetContext sets additional error context and returns the result for chaining.
func (r *ErrorResult) SetContext(ctx string) *ErrorResult {
	r.errorContext = ctx
	return r
}

// AddAnnotation sets an annotation key-value pair and returns the result for chaining.
func (r *ErrorResult) AddAnnotation(k, v string) *ErrorResult {
	r.annotations[k] = v
	return r
}

// AddSelfAnnotation sets an annotation keyed by the action's own name.
func (r *ErrorResult) AddSelfAnnotation(v string) *ErrorResult {
	return r.AddAnnotation(r.actionName, v)
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *ErrorResult) LogInfo(format string, args ...any) *ErrorResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *ErrorResult) LogWarning(format string, args ...any) *ErrorResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *ErrorResult) LogError(format string, args ...any) *ErrorResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *ErrorResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "ERROR", r.messages)
	event.Error = &ErrorEvent{
		Cause:       r.cause,
		Context:     r.errorContext,
		Annotations: r.annotations,
	}
	return event
}

// --- FilterResult ---

// FilterResult indicates the action chose to filter this DeltaFile.
type FilterResult struct {
	baseResult
	actx        *ActionContext
	message     string
	annotations map[string]string
}

// NewFilterResult creates a new FilterResult with the given message.
func NewFilterResult(actx *ActionContext, message string) *FilterResult {
	return &FilterResult{
		baseResult:  baseResult{actionName: actx.ActionName},
		actx:        actx,
		message:     message,
		annotations: make(map[string]string),
	}
}

// AddAnnotation sets an annotation key-value pair and returns the result for chaining.
func (r *FilterResult) AddAnnotation(k, v string) *FilterResult {
	r.annotations[k] = v
	return r
}

// AddSelfAnnotation sets an annotation keyed by the action's own name.
func (r *FilterResult) AddSelfAnnotation(v string) *FilterResult {
	return r.AddAnnotation(r.actionName, v)
}

// LogInfo adds an INFO-level message and returns the result for chaining.
func (r *FilterResult) LogInfo(format string, args ...any) *FilterResult {
	r.appendLog(SeverityInfo, format, args...)
	return r
}

// LogWarning adds a WARNING-level message and returns the result for chaining.
func (r *FilterResult) LogWarning(format string, args ...any) *FilterResult {
	r.appendLog(SeverityWarning, format, args...)
	return r
}

// LogError adds an ERROR-level message and returns the result for chaining.
func (r *FilterResult) LogError(format string, args ...any) *FilterResult {
	r.appendLog(SeverityError, format, args...)
	return r
}

func (r *FilterResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "FILTER", r.messages)
	event.Filter = &FilterEvent{
		Message:     r.message,
		Annotations: r.annotations,
	}
	return event
}
