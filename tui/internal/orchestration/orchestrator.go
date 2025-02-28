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

import "os/exec"

type Orchestrator interface {
	GetServiceIP(string) (string, error)
	GetPostgresCmd([]string) (exec.Cmd, error)
	GetPostgresExecCmd([]string) (exec.Cmd, error)
	Deploy([]string) error
	Destroy([]string) error
}

func NewOrchestrator(mode OrchestrationMode, distroPath string, dataPath string) Orchestrator {

	switch mode {
	case Kubernetes:
		return &KubernetesOrchestrator{distroPath: distroPath, dataPath: dataPath}
	case Compose:
		return &ComposeOrchestrator{distroPath: distroPath, dataPath: dataPath}
	case Kind:
		return &KubernetesOrchestrator{distroPath: distroPath, dataPath: dataPath}
	default:
		return &ComposeOrchestrator{distroPath: distroPath, dataPath: dataPath}
	}
}
