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
// ABOUTME: Tests for worker deserialization, Deltafile building, and orphan detection.
// ABOUTME: Verifies queue event parsing, Deltafile construction, and segment collection logic.

package actionkit

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// takerResponse is a pre-canned Take result used in sequenced fake pollers.
type takerResponse struct {
	data []byte
	err  error
}

// erroringTaker is a queuePoller that returns pre-canned responses in sequence,
// then blocks on ctx.Done(). When responses is nil it always errors immediately.
// It records call counts and signals the first call so tests can observe poll
// behavior deterministically.
type erroringTaker struct {
	calls     atomic.Int32
	called    chan struct{}
	responses []takerResponse // nil = always error; non-nil = sequence then block
	idx       int
}

func (e *erroringTaker) Take(ctx context.Context, queueName string) ([]byte, error) {
	e.calls.Add(1)
	select {
	case e.called <- struct{}{}:
	default:
	}
	if e.responses == nil {
		return nil, errors.New("connection refused")
	}
	if e.idx < len(e.responses) {
		r := e.responses[e.idx]
		e.idx++
		return r.data, r.err
	}
	<-ctx.Done()
	return nil, ctx.Err()
}

func TestPollQueue_BacksOffInsteadOfSpinningOnError(t *testing.T) {
	oldBase, oldMax := pollBackoffBase, pollBackoffMax
	// Long enough that a second Take cannot occur within the test window if
	// the poller correctly backs off after the first error.
	pollBackoffBase, pollBackoffMax = time.Hour, time.Hour
	defer func() { pollBackoffBase, pollBackoffMax = oldBase, oldMax }()

	ft := &erroringTaker{called: make(chan struct{}, 1)}
	ctx, cancel := context.WithCancel(context.Background())
	ch := make(chan []byte)
	done := make(chan struct{})
	go func() {
		pollQueue(ctx, "A", ft, ch)
		close(done)
	}()

	<-ft.called // first Take ran and errored; poller is now in backoff
	cancel()    // cancelling unblocks the backoff and stops the poller
	<-done

	if got := ft.calls.Load(); got != 1 {
		t.Fatalf("Take called %d times; want 1 (poller spun instead of backing off)", got)
	}
}

func TestPollQueue_BacksOffThenRecovers(t *testing.T) {
	oldBase := pollBackoffBase
	pollBackoffBase = time.Millisecond
	t.Cleanup(func() { pollBackoffBase = oldBase })

	connErr := errors.New("connection refused")
	raw := []byte(`{"deltaFileMessages":[],"actionContext":{"did":"d1","actionName":"A"},"actionParams":{},"queueName":"A"}`)
	ft := &erroringTaker{
		called: make(chan struct{}, 1),
		responses: []takerResponse{
			{nil, connErr},
			{nil, connErr},
			{raw, nil},
		},
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ch := make(chan []byte, 1)
	done := make(chan struct{})
	go func() {
		pollQueue(ctx, "A", ft, ch)
		close(done)
	}()

	select {
	case got := <-ch:
		if string(got) != string(raw) {
			t.Errorf("got payload %q, want %q", got, raw)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for event to reach channel")
	}

	cancel()
	<-done

	if got := ft.calls.Load(); got < 3 {
		t.Fatalf("Take called %d times, want at least 3 (2 errors + 1 success)", got)
	}
}

func TestPollQueue_EscalatesThenResetsOnSuccess(t *testing.T) {
	var mu sync.Mutex
	var slept []time.Duration
	oldSleep := pollSleep
	pollSleep = func(ctx context.Context, d time.Duration) bool {
		mu.Lock()
		slept = append(slept, d)
		mu.Unlock()
		return ctx.Err() == nil
	}
	t.Cleanup(func() { pollSleep = oldSleep })

	connErr := errors.New("connection refused")
	raw := []byte(`{"deltaFileMessages":[],"actionContext":{"did":"d1","actionName":"A"},"actionParams":{},"queueName":"A"}`)
	ft := &erroringTaker{
		called: make(chan struct{}, 8),
		responses: []takerResponse{
			{nil, connErr}, // sleep ~jitter(100ms), escalate -> 200ms
			{nil, connErr}, // sleep ~jitter(200ms), escalate -> 400ms
			{nil, connErr}, // sleep ~jitter(400ms), escalate -> 800ms
			{raw, nil},     // healthy read resets backoff to 100ms
			{nil, connErr}, // sleep ~jitter(100ms) again (reset took effect)
		},
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ch := make(chan []byte, 1)
	done := make(chan struct{})
	go func() {
		pollQueue(ctx, "A", ft, ch)
		close(done)
	}()

	deadline := time.After(2 * time.Second)
	for {
		mu.Lock()
		n := len(slept)
		mu.Unlock()
		if n >= 4 {
			break
		}
		select {
		case <-deadline:
			t.Fatalf("only %d sleeps recorded, want >= 4", n)
		case <-time.After(time.Millisecond):
		}
	}
	cancel()
	<-done

	mu.Lock()
	defer mu.Unlock()
	// escalate 100->200->400 across consecutive errors, then reset to 100 after the success
	bands := []struct{ lo, hi time.Duration }{
		{50 * time.Millisecond, 100 * time.Millisecond},
		{100 * time.Millisecond, 200 * time.Millisecond},
		{200 * time.Millisecond, 400 * time.Millisecond},
		{50 * time.Millisecond, 100 * time.Millisecond},
	}
	for i, b := range bands {
		if slept[i] < b.lo || slept[i] > b.hi {
			t.Errorf("sleep[%d] = %v, want within [%v, %v]", i, slept[i], b.lo, b.hi)
		}
	}
}

func TestPollQueue_StopsOnContextCancel(t *testing.T) {
	ft := &erroringTaker{
		called:    make(chan struct{}, 1),
		responses: []takerResponse{}, // non-nil empty: blocks immediately on ctx.Done()
	}

	ctx, cancel := context.WithCancel(context.Background())
	ch := make(chan []byte, 1)
	done := make(chan struct{})
	go func() {
		pollQueue(ctx, "A", ft, ch)
		close(done)
	}()

	<-ft.called // Take is now blocking on ctx.Done()
	cancel()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("pollQueue did not return after context cancel")
	}

	if _, ok := <-ch; ok {
		t.Error("ch should be closed after pollQueue returned")
	}
}

func TestDeserializeQueueEvent_FullEvent(t *testing.T) {
	raw := `{
		"deltaFileMessages": [{
			"metadata": {"key1": "val1"},
			"contentList": [{
				"name": "file.txt",
				"mediaType": "text/plain",
				"segments": [{"uuid": "seg-1", "offset": 0, "size": 100, "did": "did-1"}]
			}]
		}],
		"actionContext": {
			"did": "did-1",
			"deltaFileName": "test.txt",
			"dataSource": "rest",
			"flowName": "my-flow",
			"flowId": "flow-123",
			"actionName": "TestAction",
			"actionVersion": "1.0",
			"hostname": "host1",
			"systemName": "deltafi",
			"memo": "state-0"
		},
		"actionParams": {"convertToUpper": true, "count": 5},
		"queueName": "TestAction",
		"returnAddress": "core-1"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	// ActionContext
	if event.ActionContext.DID != "did-1" {
		t.Errorf("DID = %q", event.ActionContext.DID)
	}
	if event.ActionContext.DeltaFileName != "test.txt" {
		t.Errorf("DeltaFileName = %q", event.ActionContext.DeltaFileName)
	}
	if event.ActionContext.DataSource != "rest" {
		t.Errorf("DataSource = %q", event.ActionContext.DataSource)
	}
	if event.ActionContext.FlowName != "my-flow" {
		t.Errorf("FlowName = %q", event.ActionContext.FlowName)
	}
	if event.ActionContext.FlowID != "flow-123" {
		t.Errorf("FlowID = %q", event.ActionContext.FlowID)
	}
	if event.ActionContext.ActionName != "TestAction" {
		t.Errorf("ActionName = %q", event.ActionContext.ActionName)
	}
	if event.ActionContext.Memo != "state-0" {
		t.Errorf("Memo = %q", event.ActionContext.Memo)
	}

	// DeltaFileMessages
	if len(event.DeltaFileMessages) != 1 {
		t.Fatalf("DeltaFileMessages count = %d, want 1", len(event.DeltaFileMessages))
	}
	msg := event.DeltaFileMessages[0]
	if msg.Metadata["key1"] != "val1" {
		t.Errorf("metadata key1 = %q", msg.Metadata["key1"])
	}
	if len(msg.ContentList) != 1 {
		t.Fatalf("ContentList count = %d, want 1", len(msg.ContentList))
	}
	if msg.ContentList[0].Name != "file.txt" {
		t.Errorf("content name = %q", msg.ContentList[0].Name)
	}
	if msg.ContentList[0].Segments[0].UUID != "seg-1" {
		t.Errorf("segment uuid = %q", msg.ContentList[0].Segments[0].UUID)
	}

	// Params
	if event.ActionParams["convertToUpper"] != true {
		t.Errorf("param convertToUpper = %v", event.ActionParams["convertToUpper"])
	}
	if event.ActionParams["count"] != float64(5) {
		t.Errorf("param count = %v", event.ActionParams["count"])
	}

	// Queue info
	if event.QueueName != "TestAction" {
		t.Errorf("QueueName = %q", event.QueueName)
	}
	if event.ReturnAddress != "core-1" {
		t.Errorf("ReturnAddress = %q", event.ReturnAddress)
	}
}

func TestDeserializeQueueEvent_EmptyMessages(t *testing.T) {
	raw := `{
		"deltaFileMessages": [],
		"actionContext": {"did": "d1", "actionName": "A"},
		"actionParams": {},
		"queueName": "A"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}
	if len(event.DeltaFileMessages) != 0 {
		t.Errorf("expected empty messages, got %d", len(event.DeltaFileMessages))
	}
}

func TestDeserializeQueueEvent_InvalidJSON(t *testing.T) {
	_, err := deserializeQueueEvent([]byte("not valid json"))
	if err == nil {
		t.Error("expected error for invalid JSON")
	}
}

func TestBuildDeltafileFromEvent(t *testing.T) {
	actx := &ActionContext{DID: "d1", ActionName: "TestAction"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{
			Metadata:    map[string]string{"mk": "mv"},
			ContentList: []ActionContent{{Name: "file.txt", MediaType: "text/plain"}},
		}},
		ActionParams: map[string]any{"p1": "v1"},
	}

	df := buildDeltafileFromEvent(actx, event)

	if !df.HasContent() {
		t.Error("expected content")
	}
	content := df.GetContentList()
	if len(content) != 1 || content[0].Name != "file.txt" {
		t.Errorf("Content = %v", content)
	}
	val, err := df.GetMetadata("mk")
	if err != nil {
		t.Fatalf("GetMetadata(mk): %v", err)
	}
	if val != "mv" {
		t.Errorf("Metadata mk = %q", val)
	}
}

func TestBuildDeltafileFromEvent_NoMessages(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{},
		ActionParams:      map[string]any{},
	}

	df := buildDeltafileFromEvent(actx, event)

	if df.HasContent() {
		t.Error("Content should be empty")
	}
}

func TestCollectEventSegments_TransformEvent(t *testing.T) {
	event := &ActionEvent{
		Transform: []TransformEvent{
			{
				Content: []ActionContent{
					{Segments: []Segment{{UUID: "s1"}, {UUID: "s2"}}},
					{Segments: []Segment{{UUID: "s3"}}},
				},
			},
		},
	}

	segs := collectEventSegments(event)
	if len(segs) != 3 {
		t.Fatalf("expected 3 segments, got %d", len(segs))
	}
	uuids := make(map[string]bool)
	for _, s := range segs {
		uuids[s.UUID] = true
	}
	for _, want := range []string{"s1", "s2", "s3"} {
		if !uuids[want] {
			t.Errorf("missing segment %q", want)
		}
	}
}

func TestCollectEventSegments_IngressEvent(t *testing.T) {
	event := &ActionEvent{
		Ingress: &IngressEvent{
			IngressItems: []IngressEventItem{
				{
					Content: []ActionContent{
						{Segments: []Segment{{UUID: "is1"}, {UUID: "is2"}}},
					},
				},
			},
		},
	}

	segs := collectEventSegments(event)
	if len(segs) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(segs))
	}
}

func TestCollectEventSegments_ErrorEvent(t *testing.T) {
	event := &ActionEvent{
		Error: &ErrorEvent{Cause: "err"},
	}

	segs := collectEventSegments(event)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for ErrorEvent, got %d", len(segs))
	}
}

func TestCollectEventSegments_FilterEvent(t *testing.T) {
	event := &ActionEvent{
		Filter: &FilterEvent{Message: "filtered"},
	}

	segs := collectEventSegments(event)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for FilterEvent, got %d", len(segs))
	}
}

func TestCollectEventSegments_EgressEvent(t *testing.T) {
	event := &ActionEvent{
		Type: "EGRESS",
	}

	segs := collectEventSegments(event)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for EgressEvent, got %d", len(segs))
	}
}

func TestCollectEventSegments_MultiChildTransform(t *testing.T) {
	event := &ActionEvent{
		Transform: []TransformEvent{
			{
				Content: []ActionContent{
					{Segments: []Segment{{UUID: "seg-a", DID: "child-did-0", Size: 100}}},
				},
			},
			{
				Content: []ActionContent{
					{Segments: []Segment{{UUID: "seg-b", DID: "child-did-1", Size: 200}}},
					{Segments: []Segment{{UUID: "seg-c", DID: "child-did-1", Size: 300}}},
				},
			},
		},
	}

	segs := collectEventSegments(event)
	if len(segs) != 3 {
		t.Fatalf("expected 3 segments, got %d", len(segs))
	}

	uuids := map[string]bool{}
	for _, seg := range segs {
		uuids[seg.UUID] = true
	}
	for _, expected := range []string{"seg-a", "seg-b", "seg-c"} {
		if !uuids[expected] {
			t.Errorf("missing segment %q", expected)
		}
	}
}

func TestDeserializeQueueEvent_WithJoinConfig(t *testing.T) {
	raw := `{
		"deltaFileMessages": [
			{"metadata": {"index": "0"}, "contentList": []},
			{"metadata": {"index": "1"}, "contentList": []}
		],
		"actionContext": {
			"did": "join-did",
			"actionName": "JoinAction",
			"join": {
				"maxAge": 3.0,
				"minNum": 2,
				"maxNum": 10,
				"metadataKey": "groupKey"
			},
			"joinedDids": ["parent-1", "parent-2"]
		},
		"actionParams": {},
		"queueName": "JoinAction"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if event.ActionContext.Join == nil {
		t.Fatal("Join should not be nil")
	}
	if event.ActionContext.Join.MaxAge != 3.0 {
		t.Errorf("MaxAge = %f", event.ActionContext.Join.MaxAge)
	}
	if event.ActionContext.Join.MinNum != 2 {
		t.Errorf("MinNum = %d", event.ActionContext.Join.MinNum)
	}
	if event.ActionContext.Join.MaxNum != 10 {
		t.Errorf("MaxNum = %d", event.ActionContext.Join.MaxNum)
	}
	if event.ActionContext.Join.MetadataKey != "groupKey" {
		t.Errorf("MetadataKey = %q", event.ActionContext.Join.MetadataKey)
	}
	if len(event.ActionContext.JoinedDIDs) != 2 {
		t.Fatalf("JoinedDIDs count = %d, want 2", len(event.ActionContext.JoinedDIDs))
	}
	if event.ActionContext.JoinedDIDs[0] != "parent-1" {
		t.Errorf("JoinedDIDs[0] = %q", event.ActionContext.JoinedDIDs[0])
	}
}

func TestDeserializeQueueEvent_NoJoinConfig(t *testing.T) {
	raw := `{
		"deltaFileMessages": [],
		"actionContext": {"did": "d1", "actionName": "A"},
		"actionParams": {},
		"queueName": "A"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if event.ActionContext.Join != nil {
		t.Error("Join should be nil when not present")
	}
	if event.ActionContext.JoinedDIDs != nil {
		t.Error("JoinedDIDs should be nil when not present")
	}
}

func TestDeserializeQueueEvent_FullRoundTrip(t *testing.T) {
	// Verify the event can survive JSON round-trip
	original := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{
			Metadata: map[string]string{"k": "v"},
			ContentList: []ActionContent{{
				Name:      "test.txt",
				MediaType: "text/plain",
				Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 50, DID: "d1"}},
				Tags:      []string{"tag1"},
			}},
		}},
		ActionContext: queueActionContext{
			DID:        "d1",
			FlowName:   "flow",
			ActionName: "Act",
		},
		ActionParams:  map[string]any{"p": "v"},
		QueueName:     "Act",
		ReturnAddress: "ret",
	}

	data, err := json.Marshal(original)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	parsed, err := deserializeQueueEvent(data)
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if parsed.ActionContext.DID != "d1" {
		t.Errorf("DID = %q", parsed.ActionContext.DID)
	}
	if parsed.ReturnAddress != "ret" {
		t.Errorf("ReturnAddress = %q", parsed.ReturnAddress)
	}
	if parsed.DeltaFileMessages[0].ContentList[0].Tags[0] != "tag1" {
		t.Error("tags not preserved in round-trip")
	}
}
