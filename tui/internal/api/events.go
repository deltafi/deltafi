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

import (
	"github.com/google/uuid"
	"time"
)

type Event struct {
	ID           uuid.UUID `json:"id"`
	Severity     string    `json:"severity"`
	Summary      string    `json:"summary"`
	Content      string    `json:"content"`
	Source       string    `json:"source"`
	Timestamp    time.Time `json:"timestamp"`
	Notification bool      `json:"notification"`
	Acknowledged bool      `json:"acknowledged"`
}
