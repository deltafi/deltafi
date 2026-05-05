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
	GetAPIBaseURL() (string, error)
	Migrate(activeVersion *semver.Version) error
	SaveDockerAuth(registry, username, password string) error
	SslManagement
}

// DockerAuthConfig represents authentication for a single registry
type DockerAuthConfig struct {
	Username string `json:"username,omitempty"`
	Password string `json:"password,omitempty"`
	Auth     string `json:"auth,omitempty"`
}

type valuesData struct {
	Tag                      string
	Repo                     string `default:"deltafi"`
	GrafanaContainer         string `default:"deltafi/grafana:12.4.3-0"`
	VictoriaMetricsContainer string `default:"deltafi/victoria-metrics:v1.140.0-0"`
	LogrotateContainer       string `default:"deltafi/logrotate:1.0.0-6"`
	VectorContainer          string `default:"deltafi/vector:0.54.0-alpine-0"`
	DozzleContainer          string `default:"deltafi/dozzle:v10.3.1-0"`
	S3ProxyContainer         string `default:"deltafi/s3proxy:3.1.0-0"`
	NginxContainer           string `default:"deltafi/nginx:1.29.8-alpine3.23-0"`
	ValkeyContainer          string `default:"deltafi/valkey:9.0.3-1"`
	PostgresContainer        string `default:"deltafi/timescaledb:2.19.3-pg16-10"`
	PostgresLookupContainer  string `default:"deltafi/postgres:16.13-1"`
	JavaIDEContainer         string `default:"deltafi/deltafi-java-dev:jdk21-gradle8.5-2"`
	JavaDevContainer         string `default:"deltafi/devcontainer-java:jdk21-gradle8.5-0"`
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
		logsPath := filepath.Join(dataPath, "logs")
		configPath := filepath.Join(installDirectory, "config")
		secretsPath := filepath.Join(configPath, "secrets")
		return NewComposeOrchestrator(distroPath, dataPath, logsPath, reposPath, configPath, secretsPath, sitePath, orchestrationPath, coreVersion, deploymentMode)
	}
}
