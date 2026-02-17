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
	"io"
	"net"
	"strconv"
	"strings"
	"testing"
	"time"
)

func startTCPServer(t *testing.T) (port string, received func() string) {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to start listener: %v", err)
	}
	t.Cleanup(func() { listener.Close() })

	addr := listener.Addr().(*net.TCPAddr)
	var data []byte
	done := make(chan struct{})

	go func() {
		defer close(done)
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		data, _ = io.ReadAll(conn)
	}()

	return strconv.Itoa(addr.Port), func() string {
		<-done
		return string(data)
	}
}

func TestSend_SingleMetric(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	before := time.Now().Unix()

	err := client.Send([]Metric{
		{Name: "app1", Namespace: "ns1", Node: "node1", CPU: 100, Memory: 256},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	after := time.Now().Unix()
	lines := strings.Split(strings.TrimSpace(received()), "\n")

	if len(lines) != 2 {
		t.Fatalf("expected 2 lines, got %d: %v", len(lines), lines)
	}

	// Memory line
	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=app1;namespace=ns1;node=node1 256 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
	// CPU line
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=app1;namespace=ns1;node=node1 100 ") {
		t.Errorf("unexpected cpu line: %s", lines[1])
	}

	// Verify timestamp is reasonable
	ts := extractTimestamp(t, lines[0])
	if ts < before || ts > after {
		t.Errorf("timestamp %d not in range [%d, %d]", ts, before, after)
	}
}

func TestSend_MultipleMetrics(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "app1", Namespace: "ns1", Node: "node1", CPU: 100, Memory: 256},
		{Name: "app2", Namespace: "ns2", Node: "node2", CPU: 200, Memory: 512},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	// 2 metrics × 2 lines each = 4 lines
	if len(lines) != 4 {
		t.Fatalf("expected 4 lines, got %d: %v", len(lines), lines)
	}

	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=app1;namespace=ns1;node=node1 256 ") {
		t.Errorf("unexpected line[0]: %s", lines[0])
	}
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=app1;namespace=ns1;node=node1 100 ") {
		t.Errorf("unexpected line[1]: %s", lines[1])
	}
	if !strings.HasPrefix(lines[2], "gauge.app.memory;app=app2;namespace=ns2;node=node2 512 ") {
		t.Errorf("unexpected line[2]: %s", lines[2])
	}
	if !strings.HasPrefix(lines[3], "gauge.app.cpu;app=app2;namespace=ns2;node=node2 200 ") {
		t.Errorf("unexpected line[3]: %s", lines[3])
	}
}

func TestSend_EmptyMetrics(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	data := received()
	if data != "" {
		t.Errorf("expected empty payload, got %q", data)
	}
}

func TestSend_ConnectionRefused(t *testing.T) {
	// Use a port that nothing is listening on
	client := &GraphiteClient{host: "127.0.0.1", port: "1"}
	err := client.Send([]Metric{
		{Name: "app", Namespace: "ns", Node: "node", CPU: 1, Memory: 1},
	})
	if err == nil {
		t.Fatal("expected connection error, got nil")
	}
	if !strings.Contains(err.Error(), "connecting to graphite") {
		t.Errorf("expected wrapped connection error, got: %v", err)
	}
}

func TestSend_TagValues(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "deltafi-core", Namespace: "deltafi", Node: "k8s-worker-01", CPU: 1500, Memory: 2048},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")

	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=deltafi-core;namespace=deltafi;node=k8s-worker-01 2048 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=deltafi-core;namespace=deltafi;node=k8s-worker-01 1500 ") {
		t.Errorf("unexpected cpu line: %s", lines[1])
	}
}

func extractTimestamp(t *testing.T, line string) int64 {
	t.Helper()
	parts := strings.Fields(line)
	if len(parts) < 3 {
		t.Fatalf("expected at least 3 fields in line %q", line)
	}
	ts, err := strconv.ParseInt(parts[2], 10, 64)
	if err != nil {
		t.Fatalf("failed to parse timestamp from %q: %v", parts[2], err)
	}
	return ts
}

func TestSend_ZeroValues(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "idle", Namespace: "ns", Node: "node", CPU: 0, Memory: 0},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	if len(lines) != 2 {
		t.Fatalf("expected 2 lines, got %d", len(lines))
	}

	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=idle;namespace=ns;node=node 0 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=idle;namespace=ns;node=node 0 ") {
		t.Errorf("unexpected cpu line: %s", lines[1])
	}
}

func TestSend_EmptyNode(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "orphan", Namespace: "ns", Node: "", CPU: 100, Memory: 256},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	if len(lines) != 2 {
		t.Fatalf("expected 2 lines, got %d", len(lines))
	}

	// Empty node results in node= with no value
	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=orphan;namespace=ns;node= 256 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
}

func TestSend_LargeValues(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "beefy", Namespace: "ns", Node: "node", CPU: 128000, Memory: 524288},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	if len(lines) != 2 {
		t.Fatalf("expected 2 lines, got %d", len(lines))
	}

	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=beefy;namespace=ns;node=node 524288 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=beefy;namespace=ns;node=node 128000 ") {
		t.Errorf("unexpected cpu line: %s", lines[1])
	}
}

func TestSend_NilMetricsSlice(t *testing.T) {
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send(nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	data := received()
	if data != "" {
		t.Errorf("expected empty payload, got %q", data)
	}
}

func TestSend_ManyMetrics(t *testing.T) {
	port, received := startTCPServer(t)

	var metrics []Metric
	for i := range 100 {
		metrics = append(metrics, Metric{
			Name:      "app-" + strconv.Itoa(i),
			Namespace: "ns",
			Node:      "node-" + strconv.Itoa(i%3),
			CPU:       int64(i * 10),
			Memory:    int64(i * 100),
		})
	}

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send(metrics)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	// 100 metrics × 2 lines each = 200 lines
	if len(lines) != 200 {
		t.Fatalf("expected 200 lines, got %d", len(lines))
	}
}

func TestSend_NegativeValues(t *testing.T) {
	// Negative values shouldn't happen in practice but verify formatting
	port, received := startTCPServer(t)

	client := &GraphiteClient{host: "127.0.0.1", port: port}
	err := client.Send([]Metric{
		{Name: "weird", Namespace: "ns", Node: "node", CPU: -1, Memory: -100},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	lines := strings.Split(strings.TrimSpace(received()), "\n")
	if len(lines) != 2 {
		t.Fatalf("expected 2 lines, got %d", len(lines))
	}

	if !strings.HasPrefix(lines[0], "gauge.app.memory;app=weird;namespace=ns;node=node -100 ") {
		t.Errorf("unexpected memory line: %s", lines[0])
	}
	if !strings.HasPrefix(lines[1], "gauge.app.cpu;app=weird;namespace=ns;node=node -1 ") {
		t.Errorf("unexpected cpu line: %s", lines[1])
	}
}
