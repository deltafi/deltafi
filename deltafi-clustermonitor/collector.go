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

// ABOUTME: Collects per-container CPU and memory metrics from the Kubernetes Metrics API.
// ABOUTME: Maps pods to nodes and deduplicates container names across the cluster.

package main

import (
	"context"
	"fmt"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	metricsv "k8s.io/metrics/pkg/client/clientset/versioned"
)

type Metric struct {
	Name      string
	Namespace string
	Node      string
	CPU       int64
	Memory    int64
}

type Collector struct {
	kubeClient    kubernetes.Interface
	metricsClient metricsv.Interface
}

func (c *Collector) Collect(ctx context.Context) ([]Metric, error) {
	// Build pod → node map
	pods, err := c.kubeClient.CoreV1().Pods("").List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, fmt.Errorf("listing pods: %w", err)
	}

	nodeMap := make(map[string]string)
	for _, pod := range pods.Items {
		key := pod.Namespace + "/" + pod.Name
		nodeMap[key] = pod.Spec.NodeName
	}

	// Get pod metrics from Metrics API
	podMetrics, err := c.metricsClient.MetricsV1beta1().PodMetricses("").List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, fmt.Errorf("listing pod metrics: %w", err)
	}

	// Dedup container names (matching shell script: first keeps name, dupes get -2, -3, ...)
	nameCounts := make(map[string]int)
	var metrics []Metric

	for _, pm := range podMetrics.Items {
		node := nodeMap[pm.Namespace+"/"+pm.Name]

		for _, cm := range pm.Containers {
			name := cm.Name
			nameCounts[name]++
			if nameCounts[name] > 1 {
				name = fmt.Sprintf("%s-%d", cm.Name, nameCounts[name])
			}

			cpu := cm.Usage.Cpu().MilliValue()
			mem := cm.Usage.Memory().Value() / (1024 * 1024)

			metrics = append(metrics, Metric{
				Name:      name,
				Namespace: pm.Namespace,
				Node:      node,
				CPU:       cpu,
				Memory:    mem,
			})
		}
	}

	return metrics, nil
}
