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
// ABOUTME: TimedIngressDeltafile is the single object passed to a timed ingress action.
// ABOUTME: IngressDeltafile is the write-only per-item view used to populate ingress content.

package actionkit

import (
	"context"
	"fmt"
	"io"
)

// IngressStatus is the health status a timed ingress action reports for its
// data source. Use the defined constants rather than raw strings.
type IngressStatus string

const (
	IngressStatusHealthy   IngressStatus = "HEALTHY"
	IngressStatusDegraded  IngressStatus = "DEGRADED"
	IngressStatusUnhealthy IngressStatus = "UNHEALTHY"
)

// TimedIngressDeltafile is the object passed to a timed ingress action. The
// action reads per-cycle context from the public fields, spawns ingress items
// via NewItem, and records event-level state via SetMemo, SetStatus,
// SetStatusMessage, SetExecuteImmediate, Errorf, Filterf, SetErrorContext, and Log*.
type TimedIngressDeltafile struct {
	Memo       string
	FlowName   string
	ActionName string

	items            []*Deltafile
	nextMemo         string
	status           IngressStatus
	statusMessage    string
	executeImmediate bool

	hasError   bool
	errCause   string
	errContext string

	hasFilter     bool
	filterMessage string
	filterContext string

	messages []LogMessage

	metrics []Metric

	actx *ActionContext
}

// newTimedIngressDeltafile creates a TimedIngressDeltafile bound to an
// ActionContext. The status defaults to "HEALTHY".
func newTimedIngressDeltafile(actx *ActionContext) *TimedIngressDeltafile {
	return &TimedIngressDeltafile{
		Memo:       actx.Memo,
		FlowName:   actx.FlowName,
		ActionName: actx.ActionName,
		status:     IngressStatusHealthy,
		actx:       actx,
	}
}

// Context returns the action's context.Context, cancelled on plugin shutdown.
func (t *TimedIngressDeltafile) Context() context.Context {
	if t.actx != nil && t.actx.ctx != nil {
		return t.actx.ctx
	}
	return context.Background()
}

// NewItem creates an IngressDeltafile (write-only Deltafile view) with the
// given name. The kit emits one IngressEventItem per call when the action
// returns.
func (t *TimedIngressDeltafile) NewItem(name string) *IngressDeltafile {
	df := newEmptyDeltafile(name, t.actx)
	t.items = append(t.items, df)
	return newIngressDeltafile(df)
}

// SetMemo sets the memo to persist into the next ingress cycle.
func (t *TimedIngressDeltafile) SetMemo(m string) *TimedIngressDeltafile {
	t.nextMemo = m
	return t
}

// SetStatus sets the ingress health status. Defaults to IngressStatusHealthy.
func (t *TimedIngressDeltafile) SetStatus(s IngressStatus) *TimedIngressDeltafile {
	t.status = s
	return t
}

// SetStatusMessage sets a human-readable status message.
func (t *TimedIngressDeltafile) SetStatusMessage(m string) *TimedIngressDeltafile {
	t.statusMessage = m
	return t
}

// SetExecuteImmediate requests that the next ingress invocation run immediately.
func (t *TimedIngressDeltafile) SetExecuteImmediate(b bool) *TimedIngressDeltafile {
	t.executeImmediate = b
	return t
}

// AddMetric emits a custom metric with optional tags.
func (t *TimedIngressDeltafile) AddMetric(name string, value float64, tags map[string]string) *TimedIngressDeltafile {
	t.metrics = append(t.metrics, Metric{Name: name, Value: value, Tags: tags})
	return t
}

// Errorf marks the entire ingress event as failed. When set, the kit emits an
// ERROR event in place of an INGRESS event.
func (t *TimedIngressDeltafile) Errorf(format string, args ...any) *TimedIngressDeltafile {
	t.hasError = true
	t.hasFilter = false
	t.errCause = fmt.Sprintf(format, args...)
	return t
}

// Filterf marks the entire ingress event as filtered. When set, the kit emits a
// FILTER event in place of an INGRESS event.
func (t *TimedIngressDeltafile) Filterf(format string, args ...any) *TimedIngressDeltafile {
	t.hasFilter = true
	t.hasError = false
	t.filterMessage = fmt.Sprintf(format, args...)
	return t
}

// SetFilterContext sets additional context for a Filterf result and returns it
// for chaining. Only meaningful after calling Filterf.
func (t *TimedIngressDeltafile) SetFilterContext(ctx string) *TimedIngressDeltafile {
	t.filterContext = ctx
	return t
}

// Fail marks the entire ingress event as failed using err's message and returns it for chaining.
func (t *TimedIngressDeltafile) Fail(err error) *TimedIngressDeltafile {
	return t.Errorf("%s", failCause(err))
}

// Failf marks the entire ingress event as failed with a formatted cause and returns it for chaining.
func (t *TimedIngressDeltafile) Failf(format string, args ...any) *TimedIngressDeltafile {
	return t.Errorf(format, args...)
}

// SetErrorContext sets additional context for an Errorf result.
func (t *TimedIngressDeltafile) SetErrorContext(ctx string) *TimedIngressDeltafile {
	t.errContext = ctx
	return t
}

// LogInfo records an INFO-level log message on the ingress event.
func (t *TimedIngressDeltafile) LogInfo(format string, args ...any) *TimedIngressDeltafile {
	t.appendLog(SeverityInfo, format, args...)
	return t
}

// LogWarn records a WARNING-level log message on the ingress event.
func (t *TimedIngressDeltafile) LogWarn(format string, args ...any) *TimedIngressDeltafile {
	t.appendLog(SeverityWarning, format, args...)
	return t
}

// LogError records an ERROR-level log message on the ingress event.
func (t *TimedIngressDeltafile) LogError(format string, args ...any) *TimedIngressDeltafile {
	t.appendLog(SeverityError, format, args...)
	return t
}

func (t *TimedIngressDeltafile) appendLog(severity LogSeverity, format string, args ...any) {
	var msg string
	if len(args) == 0 {
		msg = format
	} else {
		msg = fmt.Sprintf(format, args...)
	}
	t.messages = append(t.messages, newLogMessage(severity, "ingress", msg))
}

// IngressDeltafile is the write-only Deltafile view used to populate a single
// ingress item. It exposes content saves, metadata writes, and annotations,
// but not reads, error/filter signaling, logging, or child creation — those
// concerns belong to the surrounding TimedIngressDeltafile.
type IngressDeltafile struct {
	Did  string
	Name string

	df *Deltafile
}

// newIngressDeltafile wraps a Deltafile for use as a single ingress item.
func newIngressDeltafile(df *Deltafile) *IngressDeltafile {
	return &IngressDeltafile{
		Did:  df.Did,
		Name: df.Name,
		df:   df,
	}
}

// --- Content writes ---

// SaveStringContent saves a string as content and appends it to the item.
func (i *IngressDeltafile) SaveStringContent(name, mediaType, data string) (*ActionContent, error) {
	return i.df.SaveStringContent(name, mediaType, data)
}

// SaveBytesContent saves bytes as content and appends it to the item.
func (i *IngressDeltafile) SaveBytesContent(name, mediaType string, data []byte) (*ActionContent, error) {
	return i.df.SaveBytesContent(name, mediaType, data)
}

// SaveReaderContent streams reader data as content and appends it to the item.
// The resulting size reflects the bytes actually streamed.
func (i *IngressDeltafile) SaveReaderContent(name, mediaType string, r io.Reader) (*ActionContent, error) {
	return i.df.SaveReaderContent(name, mediaType, r)
}

// SaveWriterContent streams content produced by fn as content and appends it to
// the item. The resulting size reflects the bytes actually streamed.
func (i *IngressDeltafile) SaveWriterContent(name, mediaType string, fn func(io.Writer) error) (*ActionContent, error) {
	return i.df.SaveWriterContent(name, mediaType, fn)
}

// SaveContentBatch saves each item and appends successes to the ingress item.
func (i *IngressDeltafile) SaveContentBatch(items []ContentItem) ([]ActionContent, error) {
	return i.df.SaveContentBatch(items)
}

// SaveWriterContentBatch streams each write and appends successes to the ingress item.
func (i *IngressDeltafile) SaveWriterContentBatch(writes []ContentWrite) ([]ActionContent, error) {
	return i.df.SaveWriterContentBatch(writes)
}

// AddContent appends an existing ActionContent to the item.
func (i *IngressDeltafile) AddContent(c ActionContent) *IngressDeltafile {
	i.df.AddContent(c)
	return i
}

// AddContentList appends multiple ActionContent items to this item.
func (i *IngressDeltafile) AddContentList(contents []ActionContent) *IngressDeltafile {
	i.df.AddContentList(contents)
	return i
}

// --- Metadata writes ---

// AddMetadata adds a metadata key-value pair to this item.
func (i *IngressDeltafile) AddMetadata(key, value string) *IngressDeltafile {
	i.df.AddMetadata(key, value)
	return i
}

// AddMetadataMap merges the provided metadata map into this item.
func (i *IngressDeltafile) AddMetadataMap(m map[string]string) *IngressDeltafile {
	i.df.AddMetadataMap(m)
	return i
}

// --- Annotations ---

// AddAnnotation adds an annotation key-value pair to this item.
func (i *IngressDeltafile) AddAnnotation(key, value string) *IngressDeltafile {
	i.df.AddAnnotation(key, value)
	return i
}
