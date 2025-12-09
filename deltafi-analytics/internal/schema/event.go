/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
/*
 * ABOUTME: Defines the analytics event and annotation schemas for Parquet storage.
 * ABOUTME: Uses normalized design to support late-arriving annotations.
 */
package schema

import "time"

// Event represents a core analytics event without annotations.
// Annotations are stored separately to support late-arriving updates.
type Event struct {
	DID        string `parquet:"did"`
	DataSource string `parquet:"data_source"`

	EventType    string    `parquet:"event_type"` // INGRESS, EGRESS, ERROR, FILTER, CANCEL
	EventTime    time.Time `parquet:"event_time,timestamp(millisecond)"`
	IngestTime   time.Time `parquet:"ingest_time,timestamp(millisecond)"`
	CreationTime time.Time `parquet:"-"` // DeltaFile creation time for hour-based partitioning (not stored in parquet)

	Bytes     int64 `parquet:"bytes"`
	FileCount int32 `parquet:"file_count"`

	FlowName    string `parquet:"flow_name,optional"`
	ActionName  string `parquet:"action_name,optional"`
	Cause       string `parquet:"cause,optional"`
	IngressType string `parquet:"ingress_type,optional"` // DATA_SOURCE, CHILD, SURVEY
}

// Annotation represents a key-value annotation for a DID.
// Stored separately from events to support late-arriving updates.
type Annotation struct {
	DID          string    `parquet:"did"`
	Key          string    `parquet:"key"`
	Value        string    `parquet:"value"`
	UpdateTime   time.Time `parquet:"update_time,timestamp(millisecond)"`
	CreationTime time.Time `parquet:"-"` // DeltaFile creation time for hour-based partitioning (not stored in parquet)
}

// PreAggregatedEvent is the intermediate stage between raw events and final aggregation.
// Preserves DID for annotation joins, partitioned by creation_time, has event_time bucket computed.
// This stage allows annotations to be joined efficiently before final aggregation by event_time.
type PreAggregatedEvent struct {
	DID            string    `parquet:"did"`                               // Preserved for annotation joins
	EventTimeBucket time.Time `parquet:"event_time_bucket,timestamp(millisecond)"` // 5-min bucket by EVENT time
	DataSource     string    `parquet:"data_source"`
	EventType      string    `parquet:"event_type"`
	FlowName       string    `parquet:"flow_name,optional"`
	ActionName     string    `parquet:"action_name,optional"`
	Cause          string    `parquet:"cause,optional"`
	IngressType    string    `parquet:"ingress_type,optional"`

	// Annotations joined from annotation files
	Annotations map[string]string `parquet:"annotations,optional"`

	EventCount     int64 `parquet:"event_count"`
	TotalBytes     int64 `parquet:"total_bytes"`
	TotalFileCount int64 `parquet:"total_file_count"`
}

// AggregatedEvent represents final rolled-up metrics for querying.
// Groups by 5-minute bucket + dimensions + annotations, drops DIDs, sums metrics.
// Partitioned by EVENT_TIME date for efficient time-range queries.
// Annotations stored as Parquet MAP type for efficient columnar storage and querying.
type AggregatedEvent struct {
	Bucket      time.Time `parquet:"bucket,timestamp(millisecond)"` // 5-min bucket by EVENT time
	DataSource  string    `parquet:"data_source"`
	EventType   string    `parquet:"event_type"`
	FlowName    string    `parquet:"flow_name,optional"`
	ActionName  string    `parquet:"action_name,optional"`
	Cause       string    `parquet:"cause,optional"`
	IngressType string    `parquet:"ingress_type,optional"` // DATA_SOURCE, CHILD, SURVEY

	// Annotations as MAP type - queryable as annotations['key'] in DuckDB
	Annotations map[string]string `parquet:"annotations,optional"`

	EventCount     int64 `parquet:"event_count"`
	TotalBytes     int64 `parquet:"total_bytes"`
	TotalFileCount int64 `parquet:"total_file_count"`
}

// EventRequest is the JSON structure received via HTTP POST for events
type EventRequest struct {
	DID          string            `json:"did"`
	DataSource   string            `json:"dataSource"`
	EventType    string            `json:"eventType"`
	EventTime    int64             `json:"eventTime"`    // Unix millis - when the event occurred
	CreationTime int64             `json:"creationTime"` // Unix millis - when the DeltaFile was created (used for partitioning)
	Bytes        int64             `json:"bytes"`
	FileCount    int32             `json:"fileCount"`
	FlowName     string            `json:"flowName,omitempty"`
	ActionName   string            `json:"actionName,omitempty"`
	Cause        string            `json:"cause,omitempty"`
	IngressType  string            `json:"ingressType,omitempty"`
	Annotations  map[string]string `json:"annotations,omitempty"`
}

// AnnotationRequest is the JSON structure for annotation updates
type AnnotationRequest struct {
	DID          string            `json:"did"`
	Annotations  map[string]string `json:"annotations"`
	CreationTime int64             `json:"creationTime,omitempty"` // DeltaFile creation time (Unix millis) for hour-based partitioning
}
