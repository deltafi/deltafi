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
// ABOUTME: Action dispatch via typed function closures.
// ABOUTME: Generic registration captures concrete types; invocation uses direct calls.

package actionkit

import (
	"fmt"
	"reflect"
	"time"
)

// actionMethod identifies which type of action a dispatcher handles.
type actionMethod int

const (
	methodTransform actionMethod = iota
	methodEgress
	methodIngress
)

// actionDispatcher holds typed function closures for invoking an action.
// Closures are created at registration time via generics, eliminating
// reflection from the invocation hot path.
type actionDispatcher struct {
	method    actionMethod
	paramType reflect.Type
	hasJoin   bool

	transformFn func(*Deltafile, any) *Deltafile
	egressFn    func(*Deltafile, any) *Deltafile
	ingressFn   func(*IngressInput, any) *IngressResult
	joinFn      func([]*Deltafile, *Deltafile, any) *Deltafile
}

// actionTypeString returns the action type constant for registration.
func (d *actionDispatcher) actionTypeString() string {
	switch d.method {
	case methodTransform:
		return ActionTypeTransform
	case methodEgress:
		return ActionTypeEgress
	case methodIngress:
		return ActionTypeTimedIngress
	default:
		return "UNKNOWN"
	}
}

// parseAndSetDefaults creates an instance of the params type, applies defaults
// from the registration, then overlays values from the raw input params.
func (d *actionDispatcher) parseAndSetDefaults(defaults any, rawParams map[string]any) (any, error) {
	pt := d.paramType
	isPtr := pt.Kind() == reflect.Pointer
	if isPtr {
		pt = pt.Elem()
	}

	paramVal := reflect.New(pt)
	dest := paramVal.Interface()

	if defaults != nil {
		defaultVal := reflect.ValueOf(defaults)
		if defaultVal.Kind() == reflect.Pointer {
			defaultVal = defaultVal.Elem()
		}
		if defaultVal.Type() == pt {
			paramVal.Elem().Set(defaultVal)
		}
	}

	if rawParams != nil {
		if err := parseRawParams(rawParams, dest); err != nil {
			return nil, fmt.Errorf("failed to parse parameters: %w", err)
		}
	}

	if isPtr {
		return paramVal.Interface(), nil
	}
	return paramVal.Elem().Interface(), nil
}

// invoke calls the action with the given arguments and returns the result
// as an ActionEvent. This is the main dispatch entry point called by the worker.
func (d *actionDispatcher) invoke(actx *ActionContext, event *queueEvent, defaults any, startTime time.Time) *ActionEvent {
	params, err := d.parseAndSetDefaults(defaults, event.ActionParams)
	if err != nil {
		return errorEvent(actx, startTime, err.Error())
	}

	switch d.method {
	case methodTransform:
		return d.invokeTransform(actx, event, params, startTime)
	case methodEgress:
		return d.invokeEgress(actx, event, params, startTime)
	case methodIngress:
		return d.invokeIngress(actx, event, params, startTime)
	default:
		return errorEvent(actx, startTime, "unknown action method")
	}
}

func (d *actionDispatcher) invokeTransform(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	if d.hasJoin && actx.Join != nil {
		return d.invokeJoinThenTransform(actx, event, params, startTime)
	}

	df := buildDeltafileFromEvent(actx, event)
	d.transformFn(df, params)

	if len(df.children) > 0 {
		return splitToTransformEvent(df.children, df, actx, startTime)
	}
	return deltafileToTransformEvent(df, actx, startTime)
}

func (d *actionDispatcher) invokeJoinThenTransform(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	inputs := make([]*Deltafile, len(event.DeltaFileMessages))
	for i, msg := range event.DeltaFileMessages {
		inputs[i] = newDeltafile(actx, msg.ContentList, msg.Metadata)
	}

	joined := newDeltafile(actx, nil, nil)
	d.joinFn(inputs, joined, params)

	if joined.hasError {
		return deltafileToErrorEvent(joined, actx, startTime)
	}
	if joined.hasFilter {
		return deltafileToFilterEvent(joined, actx, startTime)
	}

	transformDf := &Deltafile{
		Did:                 actx.DID,
		Name:                actx.DeltaFileName,
		DataSource:          actx.DataSource,
		FlowName:            actx.FlowName,
		FlowID:              actx.FlowID,
		ActionName:          actx.ActionName,
		Memo:                actx.Memo,
		content:             joined.newContent,
		metadata:            joined.GetMetadataMap(),
		addedMetadata:       make(map[string]string),
		deletedMetadataKeys: make(map[string]bool),
		annotations:         make(map[string]string),
		actx:                actx,
	}
	bindContents(actx, transformDf.content)

	d.transformFn(transformDf, params)
	return deltafileToTransformEvent(transformDf, actx, startTime)
}

func (d *actionDispatcher) invokeEgress(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	df := buildDeltafileFromEvent(actx, event)
	d.egressFn(df, params)
	return deltafileToEgressEvent(df, actx, startTime)
}

func (d *actionDispatcher) invokeIngress(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	input := &IngressInput{
		Memo:       actx.Memo,
		FlowName:   actx.FlowName,
		ActionName: actx.ActionName,
		actx:       actx,
	}
	ir := d.ingressFn(input, params)
	return ingressResultToEvent(ir, actx, startTime)
}

// --- Event builders ---

func buildDeltafileFromEvent(actx *ActionContext, event *queueEvent) *Deltafile {
	var content []ActionContent
	var metadata map[string]string
	if len(event.DeltaFileMessages) > 0 {
		msg := event.DeltaFileMessages[0]
		content = msg.ContentList
		metadata = msg.Metadata
	}
	return newDeltafile(actx, content, metadata)
}

func deltafileToTransformEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	if df.hasError {
		return deltafileToErrorEvent(df, actx, startTime)
	}
	if df.hasFilter {
		return deltafileToFilterEvent(df, actx, startTime)
	}
	event := newBaseEvent(actx, startTime, "TRANSFORM", df.messages)
	te := df.toTransformEvent()
	event.Transform = []TransformEvent{te}
	return event
}

func splitToTransformEvent(children []*Deltafile, parent *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "TRANSFORM", parent.messages)
	transforms := make([]TransformEvent, len(children))
	for i, child := range children {
		te := child.toTransformEvent()
		te.Name = child.Name
		transforms[i] = te
	}
	event.Transform = transforms
	return event
}

func deltafileToEgressEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	if df.hasError {
		return deltafileToErrorEvent(df, actx, startTime)
	}
	if df.hasFilter {
		return deltafileToFilterEvent(df, actx, startTime)
	}
	return newBaseEvent(actx, startTime, "EGRESS", df.messages)
}

func deltafileToErrorEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "ERROR", df.messages)
	event.Error = &ErrorEvent{
		Cause:       df.errCause,
		Context:     df.errContext,
		Annotations: df.annotations,
	}
	return event
}

func deltafileToFilterEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "FILTER", df.messages)
	event.Filter = &FilterEvent{
		Message:     df.filterMessage,
		Annotations: df.annotations,
	}
	return event
}

func ingressResultToEvent(ir *IngressResult, actx *ActionContext, startTime time.Time) *ActionEvent {
	if ir.hasError {
		event := newBaseEvent(actx, startTime, "ERROR", ir.messages)
		event.Error = &ErrorEvent{Cause: ir.errCause, Context: ir.errContext}
		return event
	}
	event := newBaseEvent(actx, startTime, "INGRESS", ir.messages)
	items := make([]IngressEventItem, len(ir.items))
	for i, df := range ir.items {
		items[i] = IngressEventItem{
			DID:           df.Did,
			DeltaFileName: df.Name,
			Content:       df.newContent,
			Metadata:      df.addedMetadata,
			Annotations:   df.annotations,
		}
	}
	event.Ingress = &IngressEvent{
		Memo:             ir.memo,
		ExecuteImmediate: ir.executeImmediate,
		Status:           ir.status,
		StatusMessage:    ir.statusMessage,
		IngressItems:     items,
	}
	return event
}

func errorEvent(actx *ActionContext, startTime time.Time, cause string) *ActionEvent {
	event := newBaseEvent(actx, startTime, "ERROR", nil)
	event.Error = &ErrorEvent{Cause: cause}
	return event
}
