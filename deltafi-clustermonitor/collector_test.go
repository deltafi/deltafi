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

package main

import (
	"context"
	"fmt"
	"testing"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	kubefake "k8s.io/client-go/kubernetes/fake"
	ktesting "k8s.io/client-go/testing"
	metricsv1beta1 "k8s.io/metrics/pkg/apis/metrics/v1beta1"
	metricsfake "k8s.io/metrics/pkg/client/clientset/versioned/fake"
)

func pod(namespace, name, node string) *corev1.Pod {
	return &corev1.Pod{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: namespace,
		},
		Spec: corev1.PodSpec{
			NodeName: node,
		},
	}
}

func container(name, cpu, memory string) metricsv1beta1.ContainerMetrics {
	return metricsv1beta1.ContainerMetrics{
		Name: name,
		Usage: corev1.ResourceList{
			corev1.ResourceCPU:    resource.MustParse(cpu),
			corev1.ResourceMemory: resource.MustParse(memory),
		},
	}
}

func fakeMetricsClient(items ...metricsv1beta1.PodMetrics) *metricsfake.Clientset {
	cs := metricsfake.NewSimpleClientset()
	cs.PrependReactor("list", "*", func(action ktesting.Action) (bool, runtime.Object, error) {
		return true, &metricsv1beta1.PodMetricsList{Items: items}, nil
	})
	return cs
}

func failingMetricsClient(msg string) *metricsfake.Clientset {
	cs := metricsfake.NewSimpleClientset()
	cs.PrependReactor("list", "*", func(action ktesting.Action) (bool, runtime.Object, error) {
		return true, nil, fmt.Errorf("%s", msg)
	})
	return cs
}

func failingKubeClient(msg string) *kubefake.Clientset {
	cs := kubefake.NewSimpleClientset()
	cs.PrependReactor("list", "pods", func(action ktesting.Action) (bool, runtime.Object, error) {
		return true, nil, fmt.Errorf("%s", msg)
	})
	return cs
}

func pm(namespace, name string, containers ...metricsv1beta1.ContainerMetrics) metricsv1beta1.PodMetrics {
	return metricsv1beta1.PodMetrics{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: namespace,
		},
		Containers: containers,
	}
}

func TestCollect_SingleContainer(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("deltafi", "core-0", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("deltafi", "core-0",
				container("deltafi-core", "250m", "512Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 1 {
		t.Fatalf("expected 1 metric, got %d", len(metrics))
	}

	m := metrics[0]
	assertEqual(t, "Name", "deltafi-core", m.Name)
	assertEqual(t, "Namespace", "deltafi", m.Namespace)
	assertEqual(t, "Node", "node-1", m.Node)
	assertEqualInt(t, "CPU", 250, m.CPU)
	assertEqualInt(t, "Memory", 512, m.Memory)
}

func TestCollect_MultipleContainersInPod(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("deltafi", "core-0", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("deltafi", "core-0",
				container("deltafi-core", "100m", "256Mi"),
				container("sidecar", "50m", "64Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 2 {
		t.Fatalf("expected 2 metrics, got %d", len(metrics))
	}

	assertEqual(t, "metrics[0].Name", "deltafi-core", metrics[0].Name)
	assertEqualInt(t, "metrics[0].CPU", 100, metrics[0].CPU)
	assertEqualInt(t, "metrics[0].Memory", 256, metrics[0].Memory)

	assertEqual(t, "metrics[1].Name", "sidecar", metrics[1].Name)
	assertEqualInt(t, "metrics[1].CPU", 50, metrics[1].CPU)
	assertEqualInt(t, "metrics[1].Memory", 64, metrics[1].Memory)
}

func TestCollect_DuplicateContainerNames(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("deltafi", "app-a", "node-1"),
			pod("deltafi", "app-b", "node-2"),
			pod("deltafi", "app-c", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("deltafi", "app-a",
				container("nginx", "100m", "128Mi"),
			),
			pm("deltafi", "app-b",
				container("nginx", "200m", "256Mi"),
			),
			pm("deltafi", "app-c",
				container("nginx", "300m", "384Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 3 {
		t.Fatalf("expected 3 metrics, got %d", len(metrics))
	}

	// First occurrence keeps the original name
	assertEqual(t, "metrics[0].Name", "nginx", metrics[0].Name)
	// Second and third get suffixed
	assertEqual(t, "metrics[1].Name", "nginx-2", metrics[1].Name)
	assertEqual(t, "metrics[2].Name", "nginx-3", metrics[2].Name)
}

func TestCollect_DedupAcrossPods(t *testing.T) {
	// Two different container names: "app" appears in two pods, "sidecar" in one.
	// Only "app" should get dedup suffixes.
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("ns", "pod-a", "node-1"),
			pod("ns", "pod-b", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("ns", "pod-a",
				container("app", "100m", "128Mi"),
				container("sidecar", "10m", "16Mi"),
			),
			pm("ns", "pod-b",
				container("app", "200m", "256Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 3 {
		t.Fatalf("expected 3 metrics, got %d", len(metrics))
	}

	assertEqual(t, "metrics[0].Name", "app", metrics[0].Name)
	assertEqual(t, "metrics[1].Name", "sidecar", metrics[1].Name)
	assertEqual(t, "metrics[2].Name", "app-2", metrics[2].Name)
}

func TestCollect_MultipleNamespaces(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("deltafi", "core-0", "node-1"),
			pod("kube-system", "coredns-abc", "node-2"),
		),
		metricsClient: fakeMetricsClient(
			pm("deltafi", "core-0",
				container("deltafi-core", "500m", "1024Mi"),
			),
			pm("kube-system", "coredns-abc",
				container("coredns", "10m", "32Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 2 {
		t.Fatalf("expected 2 metrics, got %d", len(metrics))
	}

	namespaces := map[string]bool{}
	for _, m := range metrics {
		namespaces[m.Namespace] = true
	}
	if !namespaces["deltafi"] || !namespaces["kube-system"] {
		t.Errorf("expected both namespaces, got %v", namespaces)
	}
}

func TestCollect_NodeMapping(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("ns", "pod-a", "worker-1"),
			pod("ns", "pod-b", "worker-2"),
		),
		metricsClient: fakeMetricsClient(
			pm("ns", "pod-a",
				container("app", "100m", "128Mi"),
			),
			pm("ns", "pod-b",
				container("app", "200m", "256Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 2 {
		t.Fatalf("expected 2 metrics, got %d", len(metrics))
	}

	assertEqual(t, "metrics[0].Node", "worker-1", metrics[0].Node)
	assertEqual(t, "metrics[1].Node", "worker-2", metrics[1].Node)
}

func TestCollect_PodNotInPodList(t *testing.T) {
	// Metrics exist for a pod that's not in the pod list (e.g., pod was deleted
	// between the two API calls). Node should be empty string.
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(),
		metricsClient: fakeMetricsClient(
			pm("deltafi", "ghost-pod",
				container("app", "100m", "128Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 1 {
		t.Fatalf("expected 1 metric, got %d", len(metrics))
	}

	assertEqual(t, "Node", "", metrics[0].Node)
}

func TestCollect_NoMetrics(t *testing.T) {
	c := &Collector{
		kubeClient:    kubefake.NewSimpleClientset(),
		metricsClient: fakeMetricsClient(),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 0 {
		t.Fatalf("expected 0 metrics, got %d", len(metrics))
	}
}

func TestCollect_MemoryBytesToMiB(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("ns", "pod-0", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("ns", "pod-0",
				// 1Gi = 1024Mi
				container("app", "1000m", "1Gi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 1 {
		t.Fatalf("expected 1 metric, got %d", len(metrics))
	}

	assertEqualInt(t, "Memory", 1024, metrics[0].Memory)
	assertEqualInt(t, "CPU", 1000, metrics[0].CPU)
}

func TestCollect_SubMillicoreCPU(t *testing.T) {
	c := &Collector{
		kubeClient: kubefake.NewSimpleClientset(
			pod("ns", "pod-0", "node-1"),
		),
		metricsClient: fakeMetricsClient(
			pm("ns", "pod-0",
				container("app", "1m", "1Mi"),
			),
		),
	}

	metrics, err := c.Collect(context.Background())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(metrics) != 1 {
		t.Fatalf("expected 1 metric, got %d", len(metrics))
	}

	assertEqualInt(t, "CPU", 1, metrics[0].CPU)
	assertEqualInt(t, "Memory", 1, metrics[0].Memory)
}

func TestCollect_PodListError(t *testing.T) {
	c := &Collector{
		kubeClient:    failingKubeClient("connection refused"),
		metricsClient: fakeMetricsClient(),
	}

	_, err := c.Collect(context.Background())
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if got := err.Error(); got != "listing pods: connection refused" {
		t.Errorf("unexpected error message: %s", got)
	}
}

func TestCollect_MetricsListError(t *testing.T) {
	c := &Collector{
		kubeClient:    kubefake.NewSimpleClientset(),
		metricsClient: failingMetricsClient("metrics unavailable"),
	}

	_, err := c.Collect(context.Background())
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if got := err.Error(); got != "listing pod metrics: metrics unavailable" {
		t.Errorf("unexpected error message: %s", got)
	}
}

func assertEqual(t *testing.T, field, expected, actual string) {
	t.Helper()
	if expected != actual {
		t.Errorf("%s: expected %q, got %q", field, expected, actual)
	}
}

func assertEqualInt(t *testing.T, field string, expected, actual int64) {
	t.Helper()
	if expected != actual {
		t.Errorf("%s: expected %d, got %d", field, expected, actual)
	}
}
