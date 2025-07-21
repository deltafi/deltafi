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

// StatusCheck represents a single system status check
type statusCheck struct {
	Code        int    `json:"code"`
	Description string `json:"description"`
	Message     string `json:"message"`
}

// StatusResponse represents the full system status response
type StatusResponse struct {
	Status struct {
		Code    int           `json:"code"`
		Color   string        `json:"color"`
		State   string        `json:"state"`
		Checks  []statusCheck `json:"checks"`
		Version string        `json:"version"`
	} `json:"status"`
}
