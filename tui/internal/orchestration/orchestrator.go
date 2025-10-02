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
	"os/exec"
	"path/filepath"

	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/types"
)

type Orchestrator interface {
	GetPostgresCmd([]string) (exec.Cmd, error)
	GetPostgresExecCmd([]string) (exec.Cmd, error)
	Up([]string) error
	Down([]string) error
	Environment() []string
	GetExecCmd(string, []string) (exec.Cmd, error)
	GetValkeyName() string
	GetMinioName() (string, error)
	ExecuteMinioCommand([]string) error
	GetAPIBaseURL() (string, error)
	Migrate(activeVersion *semver.Version) error
	SslManagement
}

type valuesData struct {
	Tag                   string
	Repo                  string `default:"deltafi"`
	GrafanaContainer      string `default:"deltafi/grafana:12.2.0-0"`
	GraphiteContainer     string `default:"graphiteapp/graphite-statsd:1.1.10-5"`
	LokiContainer         string `default:"grafana/loki:2.9.14"`
	MinioContainer        string `default:"minio/minio:RELEASE.2025-09-07T16-13-09Z"`
	NginxContainer        string `default:"nginx:1.28.0-alpine3.21"`
	PromtailContainer     string `default:"grafana/promtail:2.9.14"`
	ValkeyContainer       string `default:"valkey/valkey:8.1.3-alpine"`
	DockerWebGuiContainer string `default:"deltafi/docker-web-gui:1.0.2-1"`
	PostgresContainer     string `default:"deltafi/timescaledb:2.19.3-pg16-0"`
	JavaIDEContainer      string `default:"deltafi/deltafi-java-dev:jdk21-gradle8.5-1"`
	JavaDevContainer      string `default:"deltafi/devcontainer-java:jdk21-gradle8.5-0"`
}

func NewOrchestrator(mode OrchestrationMode, distroPath string, dataPath string, installDirectory string, sitePath string, coreVersion *semver.Version, deploymentMode types.DeploymentMode) Orchestrator {

	orchestrationPath := filepath.Join(distroPath, "orchestration")
	switch mode {
	case Kubernetes:
		return NewKubernetesOrchestrator(distroPath, sitePath, dataPath)
	case Kind:
		reposPath := filepath.Join(installDirectory, "repos")
		return NewKindOrchestrator(distroPath, sitePath, dataPath, reposPath, installDirectory, coreVersion, deploymentMode)
	default:
		reposPath := filepath.Join(installDirectory, "repos")
		configPath := filepath.Join(installDirectory, "config")
		secretsPath := filepath.Join(configPath, "secrets")
		return NewComposeOrchestrator(distroPath, dataPath, reposPath, configPath, secretsPath, sitePath, orchestrationPath, coreVersion, deploymentMode)
	}
}
