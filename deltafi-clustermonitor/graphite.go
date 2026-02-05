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

// ABOUTME: TCP client for sending metrics to Graphite/VictoriaMetrics.
// ABOUTME: Formats and transmits metric lines using the Graphite plaintext protocol.

package main

import (
	"fmt"
	"net"
	"time"
)

type GraphiteClient struct {
	host string
	port string
}

func (g *GraphiteClient) Send(metrics []Metric) error {
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(g.host, g.port), 5*time.Second)
	if err != nil {
		return fmt.Errorf("connecting to graphite: %w", err)
	}
	defer conn.Close()

	ts := time.Now().Unix()
	for _, m := range metrics {
		fmt.Fprintf(conn, "gauge.app.memory;app=%s;namespace=%s;node=%s %d %d\n", m.Name, m.Namespace, m.Node, m.Memory, ts)
		fmt.Fprintf(conn, "gauge.app.cpu;app=%s;namespace=%s;node=%s %d %d\n", m.Name, m.Namespace, m.Node, m.CPU, ts)
	}

	return nil
}
