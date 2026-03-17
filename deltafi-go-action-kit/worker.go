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
// ABOUTME: Polls Redis queues for work, executes actions, publishes results, and cleans up orphans.

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

// actionWorker manages polling and execution for a single registered action.
type actionWorker struct {
	name           string
	action         any
	actionType     string
	queue          *ActionEventQueue
	contentService ContentServiceBase
	lookupClient   LookupClientBase
	artifactClient ArtifactClientBase
	workerCount    int
	tracker        *inFlightTracker
	appName        string
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
	Join          *JoinConfiguration `json:"join"`
	JoinedDIDs    []string           `json:"joinedDids"`
}

// start launches the poller and worker goroutines for this action.
func (w *actionWorker) start(ctx context.Context, wg *sync.WaitGroup) {
	ch := make(chan string, w.workerCount)

	// Start poller
	wg.Go(func() {
		w.poll(ctx, ch)
	})

	// Start workers
	for i := 0; i < w.workerCount; i++ {
		wg.Go(func() {
			w.work(ctx, ch)
		})
	}
}

// poll runs the BZPOPMIN loop, sending raw JSON events to the work channel.
func (w *actionWorker) poll(ctx context.Context, ch chan<- string) {
	defer close(ch)
	for {
		if ctx.Err() != nil {
			return
		}

		raw, err := w.queue.Take(ctx, w.name)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("[%s] poll error: %v", w.name, err)
			continue
		}
		if raw == "" {
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
func (w *actionWorker) work(ctx context.Context, ch <-chan string) {
	for raw := range ch {
		event, err := deserializeQueueEvent(raw)
		if err != nil {
			log.Printf("[%s] failed to deserialize event: %v", w.name, err)
			continue
		}

		actionEvent := w.execute(ctx, event)

		// Publish result
		resultJSON, err := json.Marshal(actionEvent)
		if err != nil {
			log.Printf("[%s] failed to marshal result: %v", w.name, err)
			continue
		}

		queueName := resultQueueName(event.ReturnAddress)
		if err := w.queue.Put(ctx, queueName, string(resultJSON)); err != nil {
			log.Printf("[%s] failed to publish result to %s: %v", w.name, queueName, err)
		}
	}
}

// execute runs the action with panic recovery and returns an ActionEvent.
func (w *actionWorker) execute(ctx context.Context, event *queueEvent) *ActionEvent {
	startTime := time.Now()
	actx := w.buildActionContext(event)

	// Track for heartbeat long-running task reporting
	w.tracker.start(actx.DID, actx.ActionName, w.appName)
	defer w.tracker.stop(actx.DID, actx.ActionName)

	var result Result

	// Panic recovery
	func() {
		defer func() {
			if r := recover(); r != nil {
				stack := string(debug.Stack())
				log.Printf("[%s] panic: %v\n%s", w.name, r, stack)
				result = NewErrorResult(actx, fmt.Sprintf("panic: %v", r)).SetContext(stack)
			}
		}()

		var err error
		result, err = w.invokeAction(ctx, actx, event)
		if err != nil {
			result = NewErrorResult(actx, err.Error())
		}
	}()

	actionEvent := result.toActionEvent(actx, startTime)

	// Orphan cleanup
	w.cleanupOrphans(ctx, actx, result)

	return actionEvent
}

// invokeAction calls the appropriate method on the action based on its type.
func (w *actionWorker) invokeAction(ctx context.Context, actx *ActionContext, event *queueEvent) (Result, error) {
	switch action := w.action.(type) {
	case TransformAction:
		if joinAction, ok := w.action.(JoinAction); ok && actx.Join != nil {
			inputs := buildTransformInputs(actx, event)
			merged, err := joinAction.Join(ctx, inputs)
			if err != nil {
				return nil, err
			}
			return action.Transform(ctx, merged)
		}
		input := buildTransformInput(actx, event)
		return action.Transform(ctx, input)
	case EgressAction:
		input := buildEgressInput(actx, event)
		return action.Egress(ctx, input)
	case TimedIngressAction:
		input := buildIngressInput(actx, event)
		return action.Ingress(ctx, input)
	default:
		return nil, fmt.Errorf("unknown action type for %s", w.name)
	}
}

// buildActionContext creates an ActionContext from the queue event.
func (w *actionWorker) buildActionContext(event *queueEvent) *ActionContext {
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
	}
}

func buildTransformInput(actx *ActionContext, event *queueEvent) TransformInput {
	input := TransformInput{
		ActionContext: actx,
		Params:        event.ActionParams,
		Metadata:      make(map[string]string),
	}
	if len(event.DeltaFileMessages) > 0 {
		msg := event.DeltaFileMessages[0]
		input.Content = msg.ContentList
		input.Metadata = msg.Metadata
	}
	return input
}

// buildTransformInputs creates one TransformInput per DeltaFileMessage for join actions.
func buildTransformInputs(actx *ActionContext, event *queueEvent) []TransformInput {
	inputs := make([]TransformInput, len(event.DeltaFileMessages))
	for i, msg := range event.DeltaFileMessages {
		inputs[i] = TransformInput{
			ActionContext: actx,
			Content:       msg.ContentList,
			Metadata:      msg.Metadata,
			Params:        event.ActionParams,
		}
		if inputs[i].Metadata == nil {
			inputs[i].Metadata = make(map[string]string)
		}
	}
	return inputs
}

func buildEgressInput(actx *ActionContext, event *queueEvent) EgressInput {
	input := EgressInput{
		ActionContext: actx,
		Params:        event.ActionParams,
		Metadata:      make(map[string]string),
	}
	if len(event.DeltaFileMessages) > 0 {
		msg := event.DeltaFileMessages[0]
		input.Content = msg.ContentList
		input.Metadata = msg.Metadata
	}
	return input
}

func buildIngressInput(actx *ActionContext, event *queueEvent) IngressInput {
	return IngressInput{
		ActionContext: actx,
		Params:        event.ActionParams,
	}
}

// cleanupOrphans deletes segments saved during execution but not referenced in the result.
func (w *actionWorker) cleanupOrphans(ctx context.Context, actx *ActionContext, result Result) {
	if len(actx.savedSegments) == 0 {
		return
	}

	referenced := collectResultSegments(result)
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

// collectResultSegments extracts all segment references from a result's content.
func collectResultSegments(result Result) []Segment {
	var segments []Segment

	switch r := result.(type) {
	case *TransformResult:
		for _, c := range r.content {
			segments = append(segments, c.Segments...)
		}
	case *TransformResults:
		for _, child := range r.children {
			for _, c := range child.content {
				segments = append(segments, c.Segments...)
			}
		}
	case *IngressResult:
		for _, item := range r.items {
			for _, c := range item.content {
				segments = append(segments, c.Segments...)
			}
		}
		// ErrorResult, FilterResult, EgressResult have no content segments
	}

	return segments
}

func deserializeQueueEvent(raw string) (*queueEvent, error) {
	var event queueEvent
	if err := json.Unmarshal([]byte(raw), &event); err != nil {
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
