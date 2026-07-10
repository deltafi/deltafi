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
// ABOUTME: Tests for the worker execute/work paths: dispatch, panic recovery, orphan cleanup, publish.
// ABOUTME: Uses a fake event queue and recording content store to exercise the hot path without live services.

package actionkit

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"
)

// fakeEventQueue records Put calls and satisfies the worker's queue dependency.
// When putErr is set, Put records the attempt and returns the error.
type fakeEventQueue struct {
	puts           []putRecord
	putErr         error
	failIfCanceled bool
}

type putRecord struct {
	queueName string
	data      []byte
}

func (f *fakeEventQueue) Take(ctx context.Context, queueName string) ([]byte, error) { return nil, nil }

func (f *fakeEventQueue) Put(ctx context.Context, queueName string, data []byte) error {
	if f.failIfCanceled && ctx.Err() != nil {
		return ctx.Err()
	}
	f.puts = append(f.puts, putRecord{queueName: queueName, data: data})
	return f.putErr
}

// recordingStore wraps InMemoryContentStore to record Delete calls.
type recordingStore struct {
	*InMemoryContentStore
	deleted [][]Segment
}

func (r *recordingStore) Delete(ctx context.Context, segments []Segment) error {
	r.deleted = append(r.deleted, segments)
	return r.InMemoryContentStore.Delete(ctx, segments)
}

type savingErrorTransform struct{}

func (savingErrorTransform) Transform(df *Deltafile, p noParams) *Deltafile {
	// Save content (tracked for orphan cleanup) then error: the ERROR event
	// references no content, so the saved segment becomes an orphan.
	_, _ = df.SaveBytesContent("o.txt", "text/plain", []byte("orphan"))
	df.Errorf("intentional")
	return df
}

type panicTransform struct{}

func (panicTransform) Transform(df *Deltafile, p noParams) *Deltafile {
	panic("kaboom")
}

type childSavingPanicTransform struct{}

func (childSavingPanicTransform) Transform(df *Deltafile, p noParams) *Deltafile {
	child := df.AddChild("child.txt")
	_, _ = child.SaveBytesContent("child.txt", "text/plain", []byte("child orphan"))
	panic("child save failed after storing content")
}

type ingressItemSavingError struct{}

func (ingressItemSavingError) Ingress(df *TimedIngressDeltafile, p noParams) *TimedIngressDeltafile {
	item := df.NewItem("item.txt")
	_, _ = item.SaveBytesContent("item.txt", "text/plain", []byte("ingress orphan"))
	return df.Errorf("ingress failed after storing item content")
}

func newWorkerEvent(params map[string]any, returnAddress string) *queueEvent {
	return &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{Metadata: map[string]string{}}},
		ActionContext:     queueActionContext{DID: "did-1", ActionName: "TestAction"},
		ActionParams:      params,
		ReturnAddress:     returnAddress,
	}
}

func TestWorkerExecute_Success(t *testing.T) {
	withCleanRegistry(t)
	reg := RegisterTransform("WT", dispatchTransform{})
	w := &actionWorker{
		name:           "WT",
		contentService: NewInMemoryContentStore(),
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	ae := w.execute(context.Background(), newWorkerEvent(map[string]any{"name": "hello"}, ""), 0)

	if ae.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", ae.Type)
	}
}

func TestWorkerExecute_PanicBecomesErrorEvent(t *testing.T) {
	withCleanRegistry(t)
	reg := RegisterTransform("PT", panicTransform{})
	w := &actionWorker{
		name:           "PT",
		contentService: NewInMemoryContentStore(),
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	ae := w.execute(context.Background(), newWorkerEvent(nil, ""), 0)

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR (panic should be recovered)", ae.Type)
	}
	if ae.Error == nil || !strings.Contains(ae.Error.Cause, "kaboom") {
		t.Errorf("Error = %+v, want cause containing kaboom", ae.Error)
	}
}

func TestWorkerExecute_CleansUpOrphanedSegments(t *testing.T) {
	withCleanRegistry(t)
	store := &recordingStore{InMemoryContentStore: NewInMemoryContentStore()}
	reg := RegisterTransform("OT", savingErrorTransform{})
	w := &actionWorker{
		name:           "OT",
		contentService: store,
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	ae := w.execute(context.Background(), newWorkerEvent(nil, ""), 0)

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if len(store.deleted) != 1 || len(store.deleted[0]) != 1 {
		t.Fatalf("expected one orphan segment deleted, got %v", store.deleted)
	}
}

func TestWorkerExecute_CleansUpChildContextOrphansAfterPanic(t *testing.T) {
	withCleanRegistry(t)
	store := &recordingStore{InMemoryContentStore: NewInMemoryContentStore()}
	reg := RegisterTransform("CPT", childSavingPanicTransform{})
	w := &actionWorker{
		name:           "CPT",
		contentService: store,
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	ae := w.execute(context.Background(), newWorkerEvent(nil, ""), 0)

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if len(store.deleted) != 1 || len(store.deleted[0]) != 1 {
		t.Fatalf("expected child orphan segment deleted, got %v", store.deleted)
	}
}

func TestWorkerExecute_CleansUpIngressItemOrphansAfterError(t *testing.T) {
	withCleanRegistry(t)
	store := &recordingStore{InMemoryContentStore: NewInMemoryContentStore()}
	reg := RegisterIngress("IOT", ingressItemSavingError{})
	w := &actionWorker{
		name:           "IOT",
		contentService: store,
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	ae := w.execute(context.Background(), newWorkerEvent(nil, ""), 0)

	if ae.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ae.Type)
	}
	if len(store.deleted) != 1 || len(store.deleted[0]) != 1 {
		t.Fatalf("expected ingress item orphan segment deleted, got %v", store.deleted)
	}
}

func TestWorkerWork_PublishesResultToReturnAddress(t *testing.T) {
	withCleanRegistry(t)
	fq := &fakeEventQueue{}
	reg := RegisterTransform("WK", dispatchTransform{})
	w := &actionWorker{
		name:           "WK",
		queue:          fq,
		contentService: NewInMemoryContentStore(),
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	raw, err := json.Marshal(newWorkerEvent(map[string]any{"name": "hello"}, "core-1"))
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	ch := make(chan []byte, 1)
	ch <- raw
	close(ch)
	w.work(context.Background(), ch, 0)

	if len(fq.puts) != 1 {
		t.Fatalf("Put calls = %d, want 1", len(fq.puts))
	}
	if fq.puts[0].queueName != "dgs-core-1" {
		t.Errorf("result queue = %q, want dgs-core-1", fq.puts[0].queueName)
	}
	var result ActionEvent
	if err := json.Unmarshal(fq.puts[0].data, &result); err != nil {
		t.Fatalf("unmarshal result: %v", err)
	}
	if result.Type != "TRANSFORM" {
		t.Errorf("result Type = %q, want TRANSFORM", result.Type)
	}
}

func TestWorkerWork_PublishesWithDrainContextAfterPollContextCancelled(t *testing.T) {
	withCleanRegistry(t)
	pollCtx, cancelPoll := context.WithCancel(context.Background())
	cancelPoll()

	fq := &fakeEventQueue{failIfCanceled: true}
	reg := RegisterTransform("DWK", dispatchTransform{})
	w := &actionWorker{
		name:           "DWK",
		queue:          fq,
		contentService: NewInMemoryContentStore(),
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	raw, err := json.Marshal(newWorkerEvent(map[string]any{"name": "hello"}, "core-1"))
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	ch := make(chan []byte, 1)
	ch <- raw
	close(ch)

	// The poll context is cancelled as it would be after SIGTERM. Workers must
	// continue with a non-cancelled drain context so execution and publish finish.
	if pollCtx.Err() == nil {
		t.Fatal("poll context should be cancelled")
	}
	w.work(context.Background(), ch, 0)

	if len(fq.puts) != 1 {
		t.Fatalf("Put calls = %d, want 1", len(fq.puts))
	}
}

func TestWork_PublishFailureDoesNotStopWorker(t *testing.T) {
	withCleanRegistry(t)
	fq := &fakeEventQueue{putErr: errors.New("publish failed")}
	reg := RegisterTransform("PF", dispatchTransform{})
	w := &actionWorker{
		name:           "PF",
		queue:          fq,
		contentService: NewInMemoryContentStore(),
		tracker:        newInFlightTracker(),
		dispatcher:     reg.dispatcher,
	}

	raw, err := json.Marshal(newWorkerEvent(map[string]any{"name": "hello"}, "core-1"))
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	ch := make(chan []byte, 1)
	ch <- raw
	close(ch)
	w.work(context.Background(), ch, 0) // must return cleanly despite Put failing

	// The action ran and attempted to publish exactly once.
	if len(fq.puts) != 1 {
		t.Fatalf("Put calls = %d, want 1 (action ran but publish failed)", len(fq.puts))
	}
}
