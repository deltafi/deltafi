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
	"maps"
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
	ingressFn   func(*TimedIngressDeltafile, any) *TimedIngressDeltafile
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
			if defaultVal.IsNil() {
				defaultVal = reflect.Zero(pt)
			} else {
				defaultVal = defaultVal.Elem()
			}
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
	df = d.transformFn(df, params)
	if df == nil {
		return errorEvent(actx, startTime, "action returned nil Deltafile")
	}

	if df.hasError {
		return deltafileToErrorEvent(df, actx, startTime)
	}
	if df.hasFilter {
		return deltafileToFilterEvent(df, actx, startTime)
	}
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
	joined = d.joinFn(inputs, joined, params)
	if joined == nil {
		return errorEvent(actx, startTime, "join returned nil Deltafile")
	}

	if joined.hasError {
		return deltafileToErrorEvent(joined, actx, startTime)
	}
	if joined.hasFilter {
		return deltafileToFilterEvent(joined, actx, startTime)
	}

	// Carry the join phase's accumulated annotations, log messages, and metrics
	// into the transform phase so output produced during the join is not lost.
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
		annotations:         maps.Clone(joined.annotations),
		messages:            joined.messages,
		metrics:             joined.metrics,
		actx:                actx,
	}
	bindContents(actx, transformDf.content)

	transformDf = d.transformFn(transformDf, params)
	if transformDf == nil {
		return errorEvent(actx, startTime, "action returned nil Deltafile")
	}
	if transformDf.hasError {
		return deltafileToErrorEvent(transformDf, actx, startTime)
	}
	if transformDf.hasFilter {
		return deltafileToFilterEvent(transformDf, actx, startTime)
	}
	if len(transformDf.children) > 0 {
		return splitToTransformEvent(transformDf.children, transformDf, actx, startTime)
	}
	return deltafileToTransformEvent(transformDf, actx, startTime)
}

func (d *actionDispatcher) invokeEgress(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	df := buildDeltafileFromEvent(actx, event)
	df = d.egressFn(df, params)
	if df == nil {
		return errorEvent(actx, startTime, "action returned nil Deltafile")
	}
	return deltafileToEgressEvent(df, actx, startTime)
}

func (d *actionDispatcher) invokeIngress(actx *ActionContext, event *queueEvent, params any, startTime time.Time) *ActionEvent {
	deltafile := newTimedIngressDeltafile(actx)
	deltafile = d.ingressFn(deltafile, params)
	if deltafile == nil {
		return errorEvent(actx, startTime, "action returned nil TimedIngressDeltafile")
	}
	return timedIngressToEvent(deltafile, actx, startTime)
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
	event.Metrics = df.metrics
	te := df.toTransformEvent()
	event.Transform = []TransformEvent{te}
	return event
}

func splitToTransformEvent(children []*Deltafile, parent *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "TRANSFORM", parent.messages)
	event.Metrics = parent.metrics
	transforms := make([]TransformEvent, len(children))
	for i, child := range children {
		te := child.toTransformEvent()
		te.Name = child.Name
		transforms[i] = te
		event.Metrics = append(event.Metrics, child.metrics...)
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
	event := newBaseEvent(actx, startTime, "EGRESS", df.messages)
	event.Metrics = df.metrics
	return event
}

func deltafileToErrorEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "ERROR", df.messages)
	event.Metrics = df.metrics
	event.Error = &ErrorEvent{
		Cause:       df.errCause,
		Context:     df.errContext,
		Annotations: df.annotations,
	}
	return event
}

func deltafileToFilterEvent(df *Deltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	event := newBaseEvent(actx, startTime, "FILTER", df.messages)
	event.Metrics = df.metrics
	event.Filter = &FilterEvent{
		Message:     df.filterMessage,
		Context:     df.filterContext,
		Annotations: df.annotations,
	}
	return event
}

func timedIngressToEvent(t *TimedIngressDeltafile, actx *ActionContext, startTime time.Time) *ActionEvent {
	if t.hasError {
		event := newBaseEvent(actx, startTime, "ERROR", t.messages)
		event.Metrics = t.metrics
		event.Error = &ErrorEvent{Cause: t.errCause, Context: t.errContext}
		return event
	}
	if t.hasFilter {
		event := newBaseEvent(actx, startTime, "FILTER", t.messages)
		event.Metrics = t.metrics
		event.Filter = &FilterEvent{Message: t.filterMessage, Context: t.filterContext}
		return event
	}
	event := newBaseEvent(actx, startTime, "INGRESS", t.messages)
	event.Metrics = t.metrics
	items := make([]IngressEventItem, len(t.items))
	for i, df := range t.items {
		items[i] = IngressEventItem{
			DID:           df.Did,
			DeltaFileName: df.Name,
			Content:       df.newContent,
			Metadata:      df.addedMetadata,
			Annotations:   df.annotations,
		}
	}
	event.Ingress = &IngressEvent{
		Memo:             t.nextMemo,
		ExecuteImmediate: t.executeImmediate,
		Status:           string(t.status),
		StatusMessage:    t.statusMessage,
		IngressItems:     items,
	}
	return event
}

func errorEvent(actx *ActionContext, startTime time.Time, cause string) *ActionEvent {
	event := newBaseEvent(actx, startTime, "ERROR", nil)
	event.Error = &ErrorEvent{Cause: cause}
	return event
}
