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
 * ABOUTME: Load test tool for analytics collector.
 * ABOUTME: Generates data in the correct pipeline state based on age (raw/preagg/aggregated).
 */
package main

import (
	"flag"
	"fmt"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"time"

	"github.com/parquet-go/parquet-go"

	"deltafi.org/deltafi-analytics/internal/schema"
)

const archiveThresholdDays = 3

func main() {
	outputDir := flag.String("output", "/data/analytics", "Output directory for parquet files")
	days := flag.Int("days", 14, "Number of days of data to generate")
	eventsPerDay := flag.Int("events-per-day", 1000000, "Base events per day (actual varies with patterns)")
	flag.Parse()

	fmt.Printf("Load test config:\n")
	fmt.Printf("  Output: %s\n", *outputDir)
	fmt.Printf("  Days: %d\n", *days)
	fmt.Printf("  Base events per day: %d\n", *eventsPerDay)
	fmt.Printf("  Archive threshold: %d days\n", archiveThresholdDays)
	fmt.Println()

	// Data sources with base weights
	dataSources := []string{"passthrough", "stix", "decompress", "transform", "enrich", "validate"}
	baseSourceWeights := []float64{0.35, 0.25, 0.15, 0.12, 0.08, 0.05}

	flowNames := []string{"flow-alpha", "flow-beta", "flow-gamma", "flow-delta", "flow-epsilon"}
	actionNames := []string{"action-1", "action-2", "action-3", "action-4"}
	causes := []string{"timeout", "validation", "transform_error", "size_limit"}

	annotationKeys := []string{"a", "b", "c"}
	annotationValues := []string{"x", "y", "z"}

	now := time.Now().UTC()
	todayStart := now.Truncate(24 * time.Hour)
	startTime := todayStart.AddDate(0, 0, -*days)
	totalEvents := 0

	// Pre-generate variation multipliers
	sourceMultipliers := make([][]float64, len(dataSources))
	for s := range dataSources {
		sourceMultipliers[s] = make([]float64, *days)
		for d := 0; d < *days; d++ {
			sourceMultipliers[s][d] = 0.2 + rand.Float64()*2.3
		}
	}

	dayMultipliers := make([]float64, *days)
	spikeDays := make([]bool, *days)
	incidentDays := make([]int, *days)

	for d := 0; d < *days; d++ {
		dayOfWeek := startTime.AddDate(0, 0, d).Weekday()
		if dayOfWeek == time.Saturday || dayOfWeek == time.Sunday {
			dayMultipliers[d] = 0.3 + rand.Float64()*0.2
		} else {
			dayMultipliers[d] = 0.7 + rand.Float64()*0.8
		}
		if rand.Float64() < 0.10 {
			spikeDays[d] = true
			dayMultipliers[d] *= 1.5 + rand.Float64()
			if dayMultipliers[d] > 3.0 {
				dayMultipliers[d] = 3.0
			}
		}
		if rand.Float64() < 0.15 {
			incidentDays[d] = rand.Intn(2) + 1
		}
	}

	start := time.Now()

	for day := 0; day < *days; day++ {
		dayStart := startTime.AddDate(0, 0, day)
		dateStr := dayStart.Format("20060102")
		daysOld := int(todayStart.Sub(dayStart).Hours() / 24)
		isArchived := daysOld > archiveThresholdDays

		dayEvents := int(float64(*eventsPerDay) * dayMultipliers[day])

		// Generate per-day source weights
		sourceWeights := make([]float64, len(baseSourceWeights))
		for i := range sourceWeights {
			sourceWeights[i] = baseSourceWeights[i] * sourceMultipliers[i][day]
		}
		var sum float64
		for _, w := range sourceWeights {
			sum += w
		}
		for i := range sourceWeights {
			sourceWeights[i] /= sum
		}

		// Hourly distribution
		hourWeights := make([]float64, 24)
		var hourSum float64
		for h := 0; h < 24; h++ {
			if h >= 9 && h <= 17 {
				hourWeights[h] = 1.5 + rand.Float64()*0.5
			} else if h >= 6 && h <= 21 {
				hourWeights[h] = 0.8 + rand.Float64()*0.4
			} else {
				hourWeights[h] = 0.2 + rand.Float64()*0.2
			}
			hourSum += hourWeights[h]
		}

		// Error/filter rates
		baseErrorRate := 0.03 + rand.Float64()*0.04
		baseFilterRate := 0.03 + rand.Float64()*0.04
		if incidentDays[day] == 1 {
			baseErrorRate = 0.15 + rand.Float64()*0.15
		} else if incidentDays[day] == 2 {
			baseFilterRate = 0.15 + rand.Float64()*0.15
		}

		dayTotalEvents := 0

		if isArchived {
			// Generate daily aggregated file directly (hourly buckets)
			var dailyAggregated []schema.AggregatedEvent

			for hour := 0; hour < 24; hour++ {
				hourEvents := int(float64(dayEvents) * hourWeights[hour] / hourSum)
				hourTime := dayStart.Add(time.Duration(hour) * time.Hour)

				// Generate aggregated records for this hour
				hourAgg := generateHourlyAggregated(hourTime, hourEvents, dataSources, sourceWeights,
					flowNames, actionNames, causes, annotationKeys, annotationValues,
					baseErrorRate, baseFilterRate, true) // true = hourly bucket for archived

				dailyAggregated = append(dailyAggregated, hourAgg...)
				dayTotalEvents += hourEvents
			}

			// Write daily aggregated file
			aggregatedDir := filepath.Join(*outputDir, "aggregated")
			if err := os.MkdirAll(aggregatedDir, 0755); err != nil {
				fmt.Printf("Error creating aggregated dir: %v\n", err)
				return
			}
			filename := filepath.Join(aggregatedDir, dateStr+".parquet")
			if err := writeAggregatedFile(filename, dailyAggregated); err != nil {
				fmt.Printf("Error writing daily aggregated: %v\n", err)
				return
			}

			marker := ""
			if spikeDays[day] {
				marker = " [SPIKE]"
			}
			if incidentDays[day] == 1 {
				marker += " [ERROR INCIDENT]"
			} else if incidentDays[day] == 2 {
				marker += " [FILTER INCIDENT]"
			}
			fmt.Printf("Day %d (%s %s): %d events → daily aggregated%s\n",
				day+1, dateStr, dayStart.Weekday().String()[:3], dayTotalEvents, marker)

		} else {
			// Generate raw + preagg + hourly aggregated for recent data
			for hour := 0; hour < 24; hour++ {
				hourStr := fmt.Sprintf("%02d", hour)
				hourTime := dayStart.Add(time.Duration(hour) * time.Hour)
				hourEvents := int(float64(dayEvents) * hourWeights[hour] / hourSum)

				// Skip future hours for today
				if dayStart.Equal(todayStart) && hourTime.After(now) {
					continue
				}

				// Determine if this hour should be fully processed
				hourAge := now.Sub(hourTime)
				isHourComplete := hourAge > time.Hour // Hour is complete if it ended > 1 hour ago

				// Generate events for this hour
				events, annotations := generateHourEvents(hourTime, hourEvents, dateStr, hour,
					dataSources, sourceWeights, flowNames, actionNames, causes,
					annotationKeys, annotationValues, baseErrorRate, baseFilterRate)

				// Always write raw events/annotations
				eventsDir := filepath.Join(*outputDir, "events", dateStr, hourStr)
				annotationsDir := filepath.Join(*outputDir, "annotations", dateStr, hourStr)
				if err := os.MkdirAll(eventsDir, 0755); err != nil {
					fmt.Printf("Error creating events dir: %v\n", err)
					return
				}
				if err := os.MkdirAll(annotationsDir, 0755); err != nil {
					fmt.Printf("Error creating annotations dir: %v\n", err)
					return
				}

				if err := writeEventsFile(filepath.Join(eventsDir, "batch_000.parquet"), events); err != nil {
					fmt.Printf("Error writing events: %v\n", err)
					return
				}
				if len(annotations) > 0 {
					if err := writeAnnotationsFile(filepath.Join(annotationsDir, "batch_000.parquet"), annotations); err != nil {
						fmt.Printf("Error writing annotations: %v\n", err)
						return
					}
				}

				if isHourComplete {
					// Write preagg file
					preaggDir := filepath.Join(*outputDir, "preagg", dateStr)
					if err := os.MkdirAll(preaggDir, 0755); err != nil {
						fmt.Printf("Error creating preagg dir: %v\n", err)
						return
					}
					preaggRecords := generatePreAggregated(events, annotations)
					if err := writePreAggFile(filepath.Join(preaggDir, hourStr+".parquet"), preaggRecords); err != nil {
						fmt.Printf("Error writing preagg: %v\n", err)
						return
					}

					// Write hourly aggregated file (5-min buckets)
					aggregatedDir := filepath.Join(*outputDir, "aggregated", dateStr)
					if err := os.MkdirAll(aggregatedDir, 0755); err != nil {
						fmt.Printf("Error creating aggregated dir: %v\n", err)
						return
					}
					hourlyAgg := generateHourlyAggregated(hourTime, hourEvents, dataSources, sourceWeights,
						flowNames, actionNames, causes, annotationKeys, annotationValues,
						baseErrorRate, baseFilterRate, false) // false = 5-min buckets for rolling
					if err := writeAggregatedFile(filepath.Join(aggregatedDir, hourStr+".parquet"), hourlyAgg); err != nil {
						fmt.Printf("Error writing hourly aggregated: %v\n", err)
						return
					}
				}

				dayTotalEvents += len(events)
			}

			marker := ""
			if spikeDays[day] {
				marker = " [SPIKE]"
			}
			if incidentDays[day] == 1 {
				marker += " [ERROR INCIDENT]"
			} else if incidentDays[day] == 2 {
				marker += " [FILTER INCIDENT]"
			}
			fmt.Printf("Day %d (%s %s): %d events → raw+preagg+hourly%s\n",
				day+1, dateStr, dayStart.Weekday().String()[:3], dayTotalEvents, marker)
		}

		totalEvents += dayTotalEvents
	}

	elapsed := time.Since(start)
	fmt.Printf("\nComplete: %d events in %v (%.0f events/sec)\n",
		totalEvents, elapsed.Round(time.Millisecond), float64(totalEvents)/elapsed.Seconds())
}

func generateHourEvents(hourTime time.Time, count int, dateStr string, hour int,
	dataSources []string, sourceWeights []float64,
	flowNames, actionNames, causes []string,
	annotationKeys, annotationValues []string,
	errorRate, filterRate float64) ([]schema.Event, []schema.Annotation) {

	var events []schema.Event
	var annotations []schema.Annotation

	for i := 0; i < count; i++ {
		minute := rand.Intn(60)
		second := rand.Intn(60)
		eventTime := hourTime.Add(time.Duration(minute)*time.Minute + time.Duration(second)*time.Second)

		did := fmt.Sprintf("did-%s-%02d-%08d", dateStr, hour, i)
		dataSource := dataSources[weightedSelect(sourceWeights)]

		var eventType, actionName, cause, ingressType string
		ingressTypes := []string{"DATA_SOURCE", "CHILD", "SURVEY"}
		r := rand.Float64()
		egressRate := (1.0 - errorRate - filterRate) / 2
		switch {
		case r < egressRate:
			eventType = "INGRESS"
			ingressType = ingressTypes[rand.Intn(len(ingressTypes))]
		case r < egressRate*2:
			eventType = "EGRESS"
		case r < egressRate*2+errorRate:
			eventType = "ERROR"
			actionName = actionNames[rand.Intn(len(actionNames))]
			cause = causes[rand.Intn(len(causes))]
		default:
			eventType = "FILTER"
			actionName = actionNames[rand.Intn(len(actionNames))]
			cause = causes[rand.Intn(len(causes))]
		}

		flowWeights := []float64{0.4, 0.25, 0.2, 0.1, 0.05}
		flowName := flowNames[weightedSelect(flowWeights)]
		bytes := int64(math.Exp(rand.NormFloat64()*2 + 10))

		events = append(events, schema.Event{
			DID:          did,
			DataSource:   dataSource,
			EventType:    eventType,
			EventTime:    eventTime,
			CreationTime: eventTime,
			IngestTime:   eventTime.Add(time.Duration(rand.Intn(60)) * time.Second),
			Bytes:        bytes,
			FileCount:    int32(rand.Intn(10) + 1),
			FlowName:     flowName,
			ActionName:   actionName,
			Cause:        cause,
			IngressType:  ingressType,
		})

		if rand.Float64() < 0.6 {
			numAnnotations := 1 + rand.Intn(3)
			usedKeys := make(map[string]bool)
			for a := 0; a < numAnnotations; a++ {
				key := annotationKeys[rand.Intn(len(annotationKeys))]
				if !usedKeys[key] {
					usedKeys[key] = true
					annotations = append(annotations, schema.Annotation{
						DID:          did,
						Key:          key,
						Value:        annotationValues[rand.Intn(len(annotationValues))],
						UpdateTime:   eventTime,
						CreationTime: eventTime,
					})
				}
			}
		}
	}

	return events, annotations
}

func generatePreAggregated(events []schema.Event, annotations []schema.Annotation) []schema.PreAggregatedEvent {
	// Build annotation map by DID
	annoMap := make(map[string]map[string]string)
	for _, a := range annotations {
		if annoMap[a.DID] == nil {
			annoMap[a.DID] = make(map[string]string)
		}
		annoMap[a.DID][a.Key] = a.Value
	}

	var result []schema.PreAggregatedEvent
	for _, e := range events {
		bucket := e.EventTime.Truncate(5 * time.Minute)
		result = append(result, schema.PreAggregatedEvent{
			DID:             e.DID,
			EventTimeBucket: bucket,
			DataSource:      e.DataSource,
			EventType:       e.EventType,
			FlowName:        e.FlowName,
			ActionName:      e.ActionName,
			Cause:           e.Cause,
			IngressType:     e.IngressType,
			Annotations:     annoMap[e.DID],
			EventCount:      1,
			TotalBytes:      e.Bytes,
			TotalFileCount:  int64(e.FileCount),
		})
	}
	return result
}

func generateHourlyAggregated(hourTime time.Time, count int,
	dataSources []string, sourceWeights []float64,
	flowNames, actionNames, causes []string,
	annotationKeys, annotationValues []string,
	errorRate, filterRate float64, hourlyBucket bool) []schema.AggregatedEvent {

	// Generate aggregated records directly (simulating what compaction produces)
	type aggKey struct {
		bucket      time.Time
		dataSource  string
		eventType   string
		flowName    string
		actionName  string
		cause       string
		ingressType string
		annoKey     string // simplified: just one annotation for variety
	}

	aggregates := make(map[aggKey]*schema.AggregatedEvent)

	for i := 0; i < count; i++ {
		minute := rand.Intn(60)
		eventTime := hourTime.Add(time.Duration(minute) * time.Minute)

		var bucket time.Time
		if hourlyBucket {
			bucket = eventTime.Truncate(time.Hour)
		} else {
			bucket = eventTime.Truncate(5 * time.Minute)
		}

		dataSource := dataSources[weightedSelect(sourceWeights)]

		var eventType, actionName, cause, ingressType string
		ingressTypes := []string{"DATA_SOURCE", "CHILD", "SURVEY"}
		r := rand.Float64()
		egressRate := (1.0 - errorRate - filterRate) / 2
		switch {
		case r < egressRate:
			eventType = "INGRESS"
			ingressType = ingressTypes[rand.Intn(len(ingressTypes))]
		case r < egressRate*2:
			eventType = "EGRESS"
		case r < egressRate*2+errorRate:
			eventType = "ERROR"
			actionName = actionNames[rand.Intn(len(actionNames))]
			cause = causes[rand.Intn(len(causes))]
		default:
			eventType = "FILTER"
			actionName = actionNames[rand.Intn(len(actionNames))]
			cause = causes[rand.Intn(len(causes))]
		}

		flowWeights := []float64{0.4, 0.25, 0.2, 0.1, 0.05}
		flowName := flowNames[weightedSelect(flowWeights)]

		// Simplified annotation handling
		var annoKey string
		var annotations map[string]string
		if rand.Float64() < 0.6 {
			key := annotationKeys[rand.Intn(len(annotationKeys))]
			value := annotationValues[rand.Intn(len(annotationValues))]
			annoKey = key + "=" + value
			annotations = map[string]string{key: value}
		}

		key := aggKey{
			bucket:      bucket,
			dataSource:  dataSource,
			eventType:   eventType,
			flowName:    flowName,
			actionName:  actionName,
			cause:       cause,
			ingressType: ingressType,
			annoKey:     annoKey,
		}

		bytes := int64(math.Exp(rand.NormFloat64()*2 + 10))
		fileCount := int64(rand.Intn(10) + 1)

		if agg, exists := aggregates[key]; exists {
			agg.EventCount++
			agg.TotalBytes += bytes
			agg.TotalFileCount += fileCount
		} else {
			aggregates[key] = &schema.AggregatedEvent{
				Bucket:         bucket,
				DataSource:     dataSource,
				EventType:      eventType,
				FlowName:       flowName,
				ActionName:     actionName,
				Cause:          cause,
				IngressType:    ingressType,
				Annotations:    annotations,
				EventCount:     1,
				TotalBytes:     bytes,
				TotalFileCount: fileCount,
			}
		}
	}

	result := make([]schema.AggregatedEvent, 0, len(aggregates))
	for _, agg := range aggregates {
		result = append(result, *agg)
	}
	return result
}

func weightedSelect(weights []float64) int {
	var sum float64
	for _, w := range weights {
		sum += w
	}
	r := rand.Float64() * sum
	for i, w := range weights {
		r -= w
		if r <= 0 {
			return i
		}
	}
	return len(weights) - 1
}

func writeEventsFile(filename string, events []schema.Event) error {
	tmpFilename := filename + ".tmp"
	file, err := os.Create(tmpFilename)
	if err != nil {
		return err
	}

	writer := parquet.NewGenericWriter[schema.Event](file, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(events); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	file.Close()
	return os.Rename(tmpFilename, filename)
}

func writeAnnotationsFile(filename string, annotations []schema.Annotation) error {
	tmpFilename := filename + ".tmp"
	file, err := os.Create(tmpFilename)
	if err != nil {
		return err
	}

	writer := parquet.NewGenericWriter[schema.Annotation](file, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(annotations); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	file.Close()
	return os.Rename(tmpFilename, filename)
}

func writePreAggFile(filename string, records []schema.PreAggregatedEvent) error {
	tmpFilename := filename + ".tmp"
	file, err := os.Create(tmpFilename)
	if err != nil {
		return err
	}

	writer := parquet.NewGenericWriter[schema.PreAggregatedEvent](file, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(records); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	file.Close()
	return os.Rename(tmpFilename, filename)
}

func writeAggregatedFile(filename string, records []schema.AggregatedEvent) error {
	tmpFilename := filename + ".tmp"
	file, err := os.Create(tmpFilename)
	if err != nil {
		return err
	}

	writer := parquet.NewGenericWriter[schema.AggregatedEvent](file, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(records); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpFilename)
		return err
	}
	file.Close()
	return os.Rename(tmpFilename, filename)
}
