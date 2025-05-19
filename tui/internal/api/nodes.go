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
package api

type NodeMetrics struct {
	Name      string `json:"name"`
	Resources struct {
		Memory struct {
			Usage int64 `json:"usage"`
			Limit int64 `json:"limit"`
		} `json:"memory"`
		DiskMinio struct {
			Usage int64 `json:"usage"`
			Limit int64 `json:"limit"`
		} `json:"disk-minio"`
		DiskPostgres struct {
			Usage int64 `json:"usage"`
			Limit int64 `json:"limit"`
		} `json:"disk-postgres"`
		CPU struct {
			Usage int64 `json:"usage"`
			Limit int64 `json:"limit"`
		} `json:"cpu"`
	} `json:"resources"`
	Apps []struct {
		Name string `json:"name"`
	} `json:"apps"`
}

type NodesResponse struct {
	Nodes     []NodeMetrics `json:"nodes"`
	Timestamp string        `json:"timestamp"`
}

func (c *Client) Nodes() ([]NodeMetrics, error) {
	var nodes NodesResponse
	err := c.Get("/api/v2/metrics/system/nodes", &nodes, nil)
	return nodes.Nodes, err
}
