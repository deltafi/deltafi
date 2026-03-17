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

// LogInfo adds an INFO-level message to the action result.
func (b *baseResult) LogInfo(msg string) {
	b.messages = append(b.messages, newLogMessage(SeverityInfo, b.actionName, msg))
}

// LogWarning adds a WARNING-level message to the action result.
func (b *baseResult) LogWarning(msg string) {
	b.messages = append(b.messages, newLogMessage(SeverityWarning, b.actionName, msg))
}

// LogError adds an ERROR-level message to the action result.
func (b *baseResult) LogError(msg string) {
	b.messages = append(b.messages, newLogMessage(SeverityError, b.actionName, msg))
}

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

// AddContent appends a content item to the transform result.
func (r *TransformResult) AddContent(c ActionContent) { r.content = append(r.content, c) }

// AddMetadata sets a metadata key-value pair on the transform result.
func (r *TransformResult) AddMetadata(k, v string) { r.metadata[k] = v }

// DeleteMetadataKey marks a metadata key for removal from the DeltaFile.
func (r *TransformResult) DeleteMetadataKey(k string) {
	r.deleteMetadataKeys = append(r.deleteMetadataKeys, k)
}

// AddAnnotation sets an annotation key-value pair on the transform result.
func (r *TransformResult) AddAnnotation(k, v string) { r.annotations[k] = v }

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

// AddChild appends a child result to the multi-child transform output.
func (r *TransformResults) AddChild(child ChildTransformResult) {
	r.children = append(r.children, child)
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

// AddContent appends a content item to the child transform result.
func (r *ChildTransformResult) AddContent(c ActionContent) { r.content = append(r.content, c) }

// AddMetadata sets a metadata key-value pair on the child transform result.
func (r *ChildTransformResult) AddMetadata(k, v string) { r.metadata[k] = v }

// DeleteMetadataKey marks a metadata key for removal from the child DeltaFile.
func (r *ChildTransformResult) DeleteMetadataKey(k string) {
	r.deleteMetadataKeys = append(r.deleteMetadataKeys, k)
}

// AddAnnotation sets an annotation key-value pair on the child transform result.
func (r *ChildTransformResult) AddAnnotation(k, v string) { r.annotations[k] = v }

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

// AddItem appends an ingress item (a DeltaFile to be created) to the result.
func (r *IngressResult) AddItem(item IngressResultItem) { r.items = append(r.items, item) }

// SetMemo sets the memo string for stateful ingress (persisted between invocations).
func (r *IngressResult) SetMemo(m string) { r.memo = m }

// SetStatus sets the ingress health status (e.g. "HEALTHY", "DEGRADED", "UNHEALTHY").
func (r *IngressResult) SetStatus(s string) { r.status = s }

// SetStatusMessage sets a human-readable message describing the ingress status.
func (r *IngressResult) SetStatusMessage(m string) { r.statusMessage = m }

// SetExecuteImmediate requests that the next ingress invocation run immediately.
func (r *IngressResult) SetExecuteImmediate(b bool) { r.executeImmediate = b }

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

// AddContent appends a content item to the ingress result item.
func (item *IngressResultItem) AddContent(c ActionContent) { item.content = append(item.content, c) }

// AddMetadata sets a metadata key-value pair on the ingress result item.
func (item *IngressResultItem) AddMetadata(k, v string) { item.metadata[k] = v }

// AddAnnotation sets an annotation key-value pair on the ingress result item.
func (item *IngressResultItem) AddAnnotation(k, v string) { item.annotations[k] = v }

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

// AddAnnotation sets an annotation key-value pair on the error result.
func (r *ErrorResult) AddAnnotation(k, v string) { r.annotations[k] = v }

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

// AddAnnotation sets an annotation key-value pair on the filter result.
func (r *FilterResult) AddAnnotation(k, v string) { r.annotations[k] = v }

func (r *FilterResult) toActionEvent(actx *ActionContext, start time.Time) *ActionEvent {
	event := newBaseEvent(actx, start, "FILTER", r.messages)
	event.Filter = &FilterEvent{
		Message:     r.message,
		Annotations: r.annotations,
	}
	return event
}
