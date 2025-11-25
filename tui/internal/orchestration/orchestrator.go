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
	GetPostgresLookupCmd([]string) (exec.Cmd, error)
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
	Tag                     string
	Repo                    string `default:"deltafi"`
	GrafanaContainer        string `default:"deltafi/grafana:12.2.1-0"`
	VictoriaMetricsContainer string `default:"victoriametrics/victoria-metrics:v1.129.1"`
	LokiContainer           string `default:"grafana/loki:2.9.17"`
	MinioContainer          string `default:"deltafi/minio:RELEASE.2025-10-15T17-29-55Z-2"`
	NginxContainer          string `default:"nginx:1.29.3-alpine3.22"`
	PromtailContainer       string `default:"grafana/promtail:3.5.8"`
	ValkeyContainer         string `default:"valkey/valkey:9.0.0-alpine"`
	DockerWebGuiContainer   string `default:"deltafi/docker-web-gui:1.0.2-4"`
	PostgresContainer       string `default:"deltafi/timescaledb:2.19.3-pg16-2"`
	PostgresLookupContainer string `default:"postgres:16.10-alpine3.22"`
	JavaIDEContainer        string `default:"deltafi/deltafi-java-dev:jdk21-gradle8.5-2"`
	JavaDevContainer        string `default:"deltafi/devcontainer-java:jdk21-gradle8.5-0"`
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
