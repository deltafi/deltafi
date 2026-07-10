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
// ABOUTME: Action execution engine with poller and worker goroutines.
// ABOUTME: Polls Redis queues for work, dispatches actions via reflection, publishes results.

package actionkit

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"runtime/debug"
	"sync"
	"time"
)

// eventQueue is the subset of queue operations a worker depends on: polling for
// work and publishing results. *actionEventQueue satisfies it; tests use a fake.
type eventQueue interface {
	queuePoller
	Put(ctx context.Context, queueName string, data []byte) error
}

// actionWorker manages polling and execution for a single registered action.
type actionWorker struct {
	name           string
	action         any
	actionType     string
	queue          eventQueue
	contentService ContentServiceBase
	lookupClient   LookupClientBase
	artifactClient ArtifactClientBase
	workerCount    int
	tracker        *inFlightTracker
	appName        string
	dispatcher     *actionDispatcher
	paramDefaults  any
	actionClass    string
}

// queueEvent is the deserialized ActionInput from the Redis queue.
type queueEvent struct {
	DeltaFileMessages []deltaFileMessage `json:"deltaFileMessages"`
	ActionContext     queueActionContext `json:"actionContext"`
	ActionParams      map[string]any     `json:"actionParams"`
	QueueName         string             `json:"queueName"`
	ReturnAddress     string             `json:"returnAddress"`
}

// deltaFileMessage holds content and metadata for a single DeltaFile message.
type deltaFileMessage struct {
	Metadata    map[string]string `json:"metadata"`
	ContentList []ActionContent   `json:"contentList"`
}

// queueActionContext holds the action context fields from the queue event.
type queueActionContext struct {
	DID           string             `json:"did"`
	DeltaFileName string             `json:"deltaFileName"`
	DataSource    string             `json:"dataSource"`
	FlowName      string             `json:"flowName"`
	FlowID        string             `json:"flowId"`
	ActionName    string             `json:"actionName"`
	ActionVersion string             `json:"actionVersion"`
	Hostname      string             `json:"hostname"`
	SystemName    string             `json:"systemName"`
	Memo          string             `json:"memo"`
	Join          *joinConfiguration `json:"join"`
	JoinedDIDs    []string           `json:"joinedDids"`
}

// start launches the poller and worker goroutines for this action.
func (w *actionWorker) start(pollCtx, workerCtx context.Context, wg *sync.WaitGroup) {
	ch := make(chan []byte, w.workerCount)

	// Start poller
	wg.Go(func() {
		w.poll(pollCtx, ch)
	})

	// Start workers
	for i := 0; i < w.workerCount; i++ {
		threadNum := i
		wg.Go(func() {
			w.work(workerCtx, ch, threadNum)
		})
	}
}

// queuePoller reads work events from a named queue.
type queuePoller interface {
	Take(ctx context.Context, queueName string) ([]byte, error)
}

// poll runs the BZPOPMIN loop, sending raw JSON event payloads to the work channel.
func (w *actionWorker) poll(ctx context.Context, ch chan<- []byte) {
	pollQueue(ctx, w.name, w.queue, ch)
}

// pollQueue repeatedly takes events from the queue and forwards them to ch. On a read
// error it applies capped exponential backoff with jitter (100ms -> 5s) so a downed
// Valkey does not cause a tight spin, resetting to the base delay after a healthy read.
func pollQueue(ctx context.Context, name string, q queuePoller, ch chan<- []byte) {
	defer close(ch)
	backoff := pollBackoffBase
	for {
		if ctx.Err() != nil {
			return
		}

		raw, err := q.Take(ctx, name)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("[%s] poll error (retry in ~%v): %v", name, backoff, err)
			var ok bool
			if backoff, ok = backoffAfterError(ctx, backoff); !ok {
				return
			}
			continue
		}
		backoff = pollBackoffBase // healthy read (event or idle timeout) resets backoff
		if raw == nil {
			continue // timeout, retry
		}

		select {
		case ch <- raw:
		case <-ctx.Done():
			return
		}
	}
}

// work receives events from the channel, executes the action, and publishes results.
func (w *actionWorker) work(ctx context.Context, ch <-chan []byte, threadNum int) {
	for raw := range ch {
		event, err := deserializeQueueEvent(raw)
		if err != nil {
			log.Printf("[%s] failed to deserialize event: %v", w.name, err)
			continue
		}

		actionEvent := w.execute(ctx, event, threadNum)

		// Publish result
		resultJSON, err := json.Marshal(actionEvent)
		if err != nil {
			log.Printf("[%s] failed to marshal result: %v", w.name, err)
			continue
		}

		queueName := resultQueueName(event.ReturnAddress)
		if err := w.queue.Put(ctx, queueName, resultJSON); err != nil {
			log.Printf("[%s] failed to publish result to %s: %v", w.name, queueName, err)
		}
	}
}

// execute runs the action with panic recovery and returns an ActionEvent.
func (w *actionWorker) execute(ctx context.Context, event *queueEvent, threadNum int) *ActionEvent {
	startTime := time.Now()
	actx := w.buildActionContext(ctx, event)

	// Track for heartbeat long-running task reporting
	actionClass := w.actionClass
	if actionClass == "" {
		actionClass = actx.ActionName
	}
	w.tracker.start(actx.DID, actionClass, actx.ActionName, threadNum, w.appName)
	defer w.tracker.stop(actx.DID, actionClass, actx.ActionName, threadNum)

	var actionEvent *ActionEvent

	// Panic recovery: any panic inside the action becomes an error event so a
	// crashing action does not take down the worker.
	func() {
		defer func() {
			if r := recover(); r != nil {
				stack := string(debug.Stack())
				log.Printf("[%s] panic: %v\n%s", w.name, r, stack)
				ae := errorEvent(actx, startTime, fmt.Sprintf("panic: %v", r))
				ae.Error.Context = stack
				actionEvent = ae
			}
		}()

		actionEvent = w.dispatcher.invoke(actx, event, w.paramDefaults, startTime)
	}()

	// Orphan cleanup
	w.cleanupOrphans(ctx, actx, actionEvent)

	return actionEvent
}

// buildActionContext creates an ActionContext from the queue event.
func (w *actionWorker) buildActionContext(ctx context.Context, event *queueEvent) *ActionContext {
	return &ActionContext{
		DID:            event.ActionContext.DID,
		DeltaFileName:  event.ActionContext.DeltaFileName,
		DataSource:     event.ActionContext.DataSource,
		FlowName:       event.ActionContext.FlowName,
		FlowID:         event.ActionContext.FlowID,
		ActionName:     event.ActionContext.ActionName,
		ActionVersion:  event.ActionContext.ActionVersion,
		Hostname:       event.ActionContext.Hostname,
		SystemName:     event.ActionContext.SystemName,
		StartTime:      time.Now(),
		Memo:           event.ActionContext.Memo,
		ContentService: w.contentService,
		LookupClient:   w.lookupClient,
		ArtifactClient: w.artifactClient,
		Join:           event.ActionContext.Join,
		JoinedDIDs:     event.ActionContext.JoinedDIDs,
		ctx:            ctx,
	}
}

// cleanupOrphans deletes segments saved during execution but not referenced in the result.
func (w *actionWorker) cleanupOrphans(ctx context.Context, actx *ActionContext, event *ActionEvent) {
	if len(actx.savedSegments) == 0 {
		return
	}

	referenced := collectEventSegments(event)
	refSet := make(map[string]bool, len(referenced))
	for _, seg := range referenced {
		refSet[seg.UUID] = true
	}

	var orphans []Segment
	for _, seg := range actx.savedSegments {
		if !refSet[seg.UUID] {
			orphans = append(orphans, seg)
		}
	}

	if len(orphans) > 0 {
		if err := w.contentService.Delete(ctx, orphans); err != nil {
			log.Printf("[%s] failed to cleanup %d orphan segments: %v", w.name, len(orphans), err)
		}
	}
}

// collectEventSegments extracts all segment references from an ActionEvent's content.
func collectEventSegments(event *ActionEvent) []Segment {
	var segments []Segment
	for _, te := range event.Transform {
		for _, c := range te.Content {
			segments = append(segments, c.Segments...)
		}
	}
	if event.Ingress != nil {
		for _, item := range event.Ingress.IngressItems {
			for _, c := range item.Content {
				segments = append(segments, c.Segments...)
			}
		}
	}
	return segments
}

func deserializeQueueEvent(raw []byte) (*queueEvent, error) {
	var event queueEvent
	if err := json.Unmarshal(raw, &event); err != nil {
		return nil, fmt.Errorf("failed to unmarshal queue event: %w", err)
	}
	return &event, nil
}

func resultQueueName(returnAddress string) string {
	if returnAddress == "" {
		return "dgs"
	}
	return "dgs-" + returnAddress
}
