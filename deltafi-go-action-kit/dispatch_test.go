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
// ABOUTME: Tests for the action dispatcher: parameter defaults and invoke routing.
// ABOUTME: Exercises parseAndSetDefaults and invoke for transform/egress/ingress/join.

package actionkit

import (
	"reflect"
	"testing"
	"time"
)

type dispatchParams struct {
	Count int    `json:"count"`
	Name  string `json:"name"`
}

type dispatchTransform struct{}

func (dispatchTransform) Transform(df *Deltafile, p dispatchParams) *Deltafile {
	switch p.Name {
	case "fail":
		df.Errorf("boom")
	case "filter":
		df.Filterf("nope")
	}
	return df
}

type dispatchSplit struct{}

func (dispatchSplit) Transform(df *Deltafile, p noParams) *Deltafile {
	df.AddChild("c1")
	df.AddChild("c2")
	return df
}

type dispatchSplitError struct{}

func (dispatchSplitError) Transform(df *Deltafile, p noParams) *Deltafile {
	df.AddChild("c1")
	return df.Errorf("split failed")
}

type dispatchSplitFilter struct{}

func (dispatchSplitFilter) Transform(df *Deltafile, p noParams) *Deltafile {
	df.AddChild("c1")
	return df.Filterf("split filtered")
}

type dispatchEgress struct{}

func (dispatchEgress) Egress(e *EgressDeltafile, p dispatchParams) *EgressDeltafile {
	if p.Name == "fail" {
		e.Errorf("egress boom")
	}
	return e
}

type dispatchIngress struct{}

func (dispatchIngress) Ingress(ti *TimedIngressDeltafile, p noParams) *TimedIngressDeltafile {
	ti.NewItem("item-1")
	return ti.SetMemo("next-memo")
}

type dispatchJoin struct{}

func (dispatchJoin) Transform(df *Deltafile, p noParams) *Deltafile { return df }

func (dispatchJoin) Join(inputs []*Deltafile, joined *Deltafile, p noParams) *Deltafile {
	joined.AddMetadata("joined", "yes")
	return joined
}

type dispatchReturnedTransform struct{}

func (dispatchReturnedTransform) Transform(df *Deltafile, p noParams) *Deltafile {
	return newDeltafile(df.actx, nil, nil).AddMetadata("returned", "yes")
}

type dispatchPointerParamsTransform struct{}

func (dispatchPointerParamsTransform) Transform(df *Deltafile, p *dispatchParams) *Deltafile {
	if p == nil {
		return df.Errorf("nil params")
	}
	return df.AddMetadata("name", p.Name)
}

type dispatchNilTransform struct{}

func (dispatchNilTransform) Transform(df *Deltafile, p noParams) *Deltafile { return nil }

type dispatchNilJoin struct{}

func (dispatchNilJoin) Transform(df *Deltafile, p noParams) *Deltafile { return df }

func (dispatchNilJoin) Join(inputs []*Deltafile, joined *Deltafile, p noParams) *Deltafile {
	return nil
}

type dispatchNilEgress struct{}

func (dispatchNilEgress) Egress(e *EgressDeltafile, p noParams) *EgressDeltafile { return nil }

type dispatchNilIngress struct{}

func (dispatchNilIngress) Ingress(ti *TimedIngressDeltafile, p noParams) *TimedIngressDeltafile {
	return nil
}

// withCleanRegistry isolates the global action registry for a test.
func withCleanRegistry(t *testing.T) {
	t.Helper()
	prev := registered
	registered = nil
	t.Cleanup(func() { registered = prev })
}

func singleMessageEvent(params map[string]any) *queueEvent {
	return &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{Metadata: map[string]string{}}},
		ActionParams:      params,
	}
}

func TestParseAndSetDefaults_AppliesDefaults(t *testing.T) {
	d := &actionDispatcher{paramType: reflect.TypeFor[dispatchParams]()}
	got, err := d.parseAndSetDefaults(dispatchParams{Count: 5, Name: "default"}, nil)
	if err != nil {
		t.Fatalf("parseAndSetDefaults: %v", err)
	}
	p := got.(dispatchParams)
	if p.Count != 5 || p.Name != "default" {
		t.Errorf("defaults not applied: %+v", p)
	}
}

func TestParseAndSetDefaults_RawParamsOverrideDefaults(t *testing.T) {
	d := &actionDispatcher{paramType: reflect.TypeFor[dispatchParams]()}
	got, err := d.parseAndSetDefaults(dispatchParams{Count: 5, Name: "default"}, map[string]any{"count": 9})
	if err != nil {
		t.Fatalf("parseAndSetDefaults: %v", err)
	}
	p := got.(dispatchParams)
	if p.Count != 9 {
		t.Errorf("raw param did not override default: Count = %d, want 9", p.Count)
	}
	if p.Name != "default" {
		t.Errorf("unset raw field should keep default: Name = %q, want default", p.Name)
	}
}

func TestParseAndSetDefaults_PointerParamType(t *testing.T) {
	d := &actionDispatcher{paramType: reflect.TypeFor[*dispatchParams]()}
	got, err := d.parseAndSetDefaults(nil, map[string]any{"count": 3})
	if err != nil {
		t.Fatalf("parseAndSetDefaults: %v", err)
	}
	p, ok := got.(*dispatchParams)
	if !ok {
		t.Fatalf("expected *dispatchParams, got %T", got)
	}
	if p.Count != 3 {
		t.Errorf("Count = %d, want 3", p.Count)
	}
}

func TestParseAndSetDefaults_NilPointerDefaultsIgnored(t *testing.T) {
	d := &actionDispatcher{paramType: reflect.TypeFor[dispatchParams]()}
	var defaults *dispatchParams
	got, err := d.parseAndSetDefaults(defaults, map[string]any{"name": "raw"})
	if err != nil {
		t.Fatalf("parseAndSetDefaults: %v", err)
	}
	p := got.(dispatchParams)
	if p.Name != "raw" || p.Count != 0 {
		t.Fatalf("params = %+v, want raw name and zero count", p)
	}
}

func TestParseAndSetDefaults_MismatchedDefaultsIgnored(t *testing.T) {
	d := &actionDispatcher{paramType: reflect.TypeFor[dispatchParams]()}
	got, err := d.parseAndSetDefaults("not-a-struct", nil)
	if err != nil {
		t.Fatalf("parseAndSetDefaults: %v", err)
	}
	p := got.(dispatchParams)
	if p.Count != 0 || p.Name != "" {
		t.Errorf("mismatched defaults should be ignored, got %+v", p)
	}
}

func TestInvokeTransform_Success(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DT", dispatchTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "hello"}), nil, time.Now())

	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
	if len(ae.Transform) != 1 {
		t.Errorf("Transform events = %d, want 1", len(ae.Transform))
	}
}

func TestInvokeTransform_UsesReturnedDeltafile(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DRT", dispatchReturnedTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
	if len(ae.Transform) != 1 {
		t.Fatalf("Transform events = %d, want 1", len(ae.Transform))
	}
	if got := ae.Transform[0].Metadata["returned"]; got != "yes" {
		t.Fatalf("returned metadata = %q, want yes", got)
	}
}

func TestInvokeTransform_PointerParams(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DPP", dispatchPointerParamsTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "ptr"}), nil, time.Now())

	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
	if got := ae.Transform[0].Metadata["name"]; got != "ptr" {
		t.Fatalf("metadata name = %q, want ptr", got)
	}
}

func TestInvokeTransform_NilReturnBecomesError(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DNT", dispatchNilTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "action returned nil Deltafile" {
		t.Fatalf("Error = %+v, want nil-return cause", ae.Error)
	}
}

func TestInvokeTransform_Error(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DT", dispatchTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "fail"}), nil, time.Now())

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "boom" {
		t.Errorf("Error = %+v, want cause boom", ae.Error)
	}
}

func TestInvokeTransform_Filter(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DT", dispatchTransform{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "filter"}), nil, time.Now())

	if ae.Type != "FILTER" {
		t.Fatalf("Type = %q, want FILTER", ae.Type)
	}
}

func TestInvokeTransform_Split(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DS", dispatchSplit{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
	if len(ae.Transform) != 2 {
		t.Errorf("child transform events = %d, want 2", len(ae.Transform))
	}
}

func TestInvokeTransform_ErrorPrecedesSplit(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DSE", dispatchSplitError{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "split failed" {
		t.Fatalf("Error = %+v", ae.Error)
	}
	if len(ae.Transform) != 0 {
		t.Fatalf("Transform events = %d, want 0", len(ae.Transform))
	}
}

func TestInvokeTransform_FilterPrecedesSplit(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterTransform("DSF", dispatchSplitFilter{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "FILTER" {
		t.Fatalf("Type = %q, want FILTER", ae.Type)
	}
	if ae.Filter == nil || ae.Filter.Message != "split filtered" {
		t.Fatalf("Filter = %+v", ae.Filter)
	}
	if len(ae.Transform) != 0 {
		t.Fatalf("Transform events = %d, want 0", len(ae.Transform))
	}
}

func TestInvokeEgress(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterEgress("DE", dispatchEgress{})

	ok := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "ok"}), nil, time.Now())
	if ok.Type != "EGRESS" {
		t.Errorf("Type = %q, want EGRESS", ok.Type)
	}

	failed := reg.dispatcher.invoke(actx, singleMessageEvent(map[string]any{"name": "fail"}), nil, time.Now())
	if failed.Type != "ERROR" {
		t.Errorf("Type = %q, want ERROR", failed.Type)
	}
}

func TestInvokeEgress_NilReturnBecomesError(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterEgress("DNE", dispatchNilEgress{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "action returned nil Deltafile" {
		t.Fatalf("Error = %+v, want nil-return cause", ae.Error)
	}
}

func TestInvokeIngress(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterIngress("DI", dispatchIngress{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "INGRESS" {
		t.Fatalf("Type = %q, want INGRESS", ae.Type)
	}
	if ae.Ingress == nil {
		t.Fatal("Ingress event is nil")
	}
	if ae.Ingress.Memo != "next-memo" {
		t.Errorf("Memo = %q, want next-memo", ae.Ingress.Memo)
	}
	if len(ae.Ingress.IngressItems) != 1 {
		t.Errorf("IngressItems = %d, want 1", len(ae.Ingress.IngressItems))
	}
}

func TestInvokeIngress_NilReturnBecomesError(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	reg := RegisterIngress("DNI", dispatchNilIngress{})

	ae := reg.dispatcher.invoke(actx, singleMessageEvent(nil), nil, time.Now())

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "action returned nil TimedIngressDeltafile" {
		t.Fatalf("Error = %+v, want nil-return cause", ae.Error)
	}
}

func TestErrorEvent(t *testing.T) {
	actx := NewTestContext("did-1").ActionContext
	ae := errorEvent(actx, time.Now(), "the cause")
	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "the cause" {
		t.Errorf("Error = %+v, want cause %q", ae.Error, "the cause")
	}
}

func TestInvokeJoinThenTransform(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	actx.Join = &joinConfiguration{MinNum: 2}
	reg := RegisterJoinTransform("DJ", dispatchJoin{})

	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{
			{Metadata: map[string]string{"i": "0"}},
			{Metadata: map[string]string{"i": "1"}},
		},
	}

	ae := reg.dispatcher.invoke(actx, event, nil, time.Now())
	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
}

func TestInvokeJoin_NilReturnBecomesError(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	actx.Join = &joinConfiguration{MinNum: 2}
	reg := RegisterJoinTransform("DNJ", dispatchNilJoin{})

	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{
			{Metadata: map[string]string{"i": "0"}},
			{Metadata: map[string]string{"i": "1"}},
		},
	}

	ae := reg.dispatcher.invoke(actx, event, nil, time.Now())
	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if ae.Error == nil || ae.Error.Cause != "join returned nil Deltafile" {
		t.Fatalf("Error = %+v, want nil-join cause", ae.Error)
	}
}

type dispatchJoinWithOutputs struct{}

func (dispatchJoinWithOutputs) Transform(df *Deltafile, p noParams) *Deltafile { return df }

func (dispatchJoinWithOutputs) Join(inputs []*Deltafile, joined *Deltafile, p noParams) *Deltafile {
	joined.AddMetric("join.metric", 1, nil)
	joined.LogInfo("joined %d inputs", len(inputs))
	joined.AddAnnotation("phase", "join")
	return joined
}

func TestInvokeJoinThenTransform_CarriesJoinPhaseOutputs(t *testing.T) {
	withCleanRegistry(t)
	actx := NewTestContext("did-1").ActionContext
	actx.Join = &joinConfiguration{MinNum: 2}
	reg := RegisterJoinTransform("DJO", dispatchJoinWithOutputs{})

	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{
			{Metadata: map[string]string{"i": "0"}},
			{Metadata: map[string]string{"i": "1"}},
		},
	}

	ae := reg.dispatcher.invoke(actx, event, nil, time.Now())
	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
	if len(ae.Metrics) != 1 || ae.Metrics[0].Name != "join.metric" {
		t.Errorf("join-phase metric not carried, Metrics = %+v", ae.Metrics)
	}
	if len(ae.Transform) != 1 {
		t.Fatalf("Transform events = %d, want 1", len(ae.Transform))
	}
	if ae.Transform[0].Annotations["phase"] != "join" {
		t.Errorf("join-phase annotation not carried, Annotations = %+v", ae.Transform[0].Annotations)
	}
	if len(ae.Transform[0].Messages) != 1 || ae.Transform[0].Messages[0].Message != "joined 2 inputs" {
		t.Errorf("join-phase log not carried, Messages = %+v", ae.Transform[0].Messages)
	}
}
