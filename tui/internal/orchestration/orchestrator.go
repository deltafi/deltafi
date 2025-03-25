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
	"os"
	"os/exec"
	"path/filepath"
)

type Orchestrator interface {
	GetServiceIP(string) (string, error)
	GetPostgresCmd([]string) (exec.Cmd, error)
	GetPostgresExecCmd([]string) (exec.Cmd, error)
	Deploy([]string) error
	Destroy([]string) error
	Environment() []string
}

func NewOrchestrator(mode OrchestrationMode, distroPath string, dataPath string, installDirectory string) Orchestrator {

	switch mode {
	case Kubernetes:
		return &KubernetesOrchestrator{distroPath: distroPath, dataPath: dataPath}
	case Compose:
		reposPath := filepath.Join(installDirectory, "repos")
		configPath := filepath.Join(installDirectory, "config")
		secretsPath := filepath.Join(configPath, "secrets")
		return &ComposeOrchestrator{distroPath: distroPath, dataPath: dataPath, reposPath: reposPath, configPath: configPath, secretsPath: secretsPath}
	case Kind:
		return &KubernetesOrchestrator{distroPath: distroPath, dataPath: dataPath}
	default:
		return &ComposeOrchestrator{distroPath: distroPath, dataPath: dataPath}
	}
}

func ShellExec(executable string, env []string, args []string) error {
	c := *exec.Command(executable, args...)
	c.Env = env
	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr
	return c.Run()
}
