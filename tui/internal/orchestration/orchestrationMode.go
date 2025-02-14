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
package orchestration

import (
	"fmt"
	"strings"
)

type OrchestrationMode int

const (
	Compose OrchestrationMode = iota
	Kubernetes
	Kind
	unknownOrchestrationMode
)

var orchestrationModeNames []string
var orchestrationModeDescriptions []string
var orchestrationModeNameToValue map[string]OrchestrationMode

func init() {
	orchestrationModeNames = []string{
		"Compose",
		"Kubernetes",
		"KinD",
	}
	orchestrationModeDescriptions = []string{
		"Compose mode uses Docker Compose to orchestrate DeltaFi services.",
		"Kubernetes mode uses Kubernetes to orchestrate DeltaFi services.",
		"KinD mode uses KinD (Kubernetes in Docker) to orchestrate DeltaFi services.  This is a developmental mode and should not be used in production environments.",
	}
	orchestrationModeNameToValue = make(map[string]OrchestrationMode)
	for i, name := range orchestrationModeNames {
		orchestrationModeNameToValue[strings.ToLower(name)] = OrchestrationMode(i)
	}
}

func (e OrchestrationMode) String() string {
	if e < 0 || int(e) >= len(orchestrationModeNames) {
		panic(fmt.Errorf("invalid orchestration mode code: %d", e))
	}
	return orchestrationModeNames[e]
}

func Parse(text string) (OrchestrationMode, error) {
	i, ok := orchestrationModeNameToValue[strings.ToLower(text)]
	if !ok {
		return unknownOrchestrationMode, fmt.Errorf("invalid orchestration mode: %s", text)
	}
	return i, nil
}

func (e OrchestrationMode) MarshalText() ([]byte, error) {
	return []byte(e.String()), nil
}

func (e *OrchestrationMode) UnmarshalText(text []byte) (err error) {
	name := string(text)
	*e, err = Parse(name)
	return
}

func (e OrchestrationMode) Description() string {
	if e < 0 || int(e) >= len(orchestrationModeDescriptions) {
		panic(fmt.Errorf("invalid orchestration mode code: %d", e))
	}
	return orchestrationModeDescriptions[e]
}
