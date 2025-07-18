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
	"embed"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/deltafi/tui/internal/types"
	"github.com/deltafi/tui/internal/ui/styles"
)

//go:embed kind.values.site.yaml
//go:embed kind.dev.values.yaml
var kindEmbeddedFiles embed.FS

type KindOrchestrator struct {
	*KubernetesOrchestrator
	reposPath      string
	deploymentMode types.DeploymentMode
	configPath     string
	secretsPath    string
}

func NewKindOrchestrator(distroPath string, sitePath string, dataPath string, reposPath string, installDirectory string, deploymentMode types.DeploymentMode) *KindOrchestrator {
	configPath := filepath.Join(installDirectory, ".config")
	secretsPath := filepath.Join(configPath, "secrets")

	ko := &KindOrchestrator{
		KubernetesOrchestrator: NewKubernetesOrchestrator(distroPath, sitePath, dataPath),
		reposPath:              reposPath,
		deploymentMode:         deploymentMode,
		configPath:             configPath,
		secretsPath:            secretsPath,
	}
	ko.BaseOrchestrator.Orchestrator = ko
	return ko
}

func (o *KindOrchestrator) GetAPIBaseURL() (string, error) {
	return "deltafi-core-service", nil
}

func (o *KindOrchestrator) StartKind() error {

	executable := filepath.Join(o.distroPath, "orchestration", "kind", "kind")
	args := []string{"up"}

	return executeShellCommand(executable, args, o.Environment())
}

func (o *KindOrchestrator) StopKind() error {
	executable := filepath.Join(o.distroPath, "orchestration", "kind", "kind")
	args := []string{"down"}
	return executeShellCommand(executable, args, o.Environment())
}

func (o *KindOrchestrator) DestroyKind() error {
	executable := filepath.Join(o.distroPath, "orchestration", "kind", "kind")
	args := []string{"destroy"}
	return executeShellCommand(executable, args, o.Environment())
}

func (o *KindOrchestrator) Up(args []string) error {

	if err := o.StartKind(); err != nil {
		return fmt.Errorf("Unable to start Kind cluster: %w", err)
	}

	if err := kubernetesPrerequisites(o.namespace); err != nil {
		return fmt.Errorf("Unable to configure Kubernetes for DeltaFi: %w", err)
	}

	postgresOperatorPath := filepath.Join(o.distroPath, "orchestration", "charts", "postgres-operator")

	if err := postgresOperatorInstall(o.namespace, postgresOperatorPath); err != nil {
		fmt.Println(styles.FAIL("Postgres Operator installation failed"))
		return fmt.Errorf("Unable to install Postgres Operator: %w", err)
	}

	deltafiChartPath := filepath.Join(o.distroPath, "orchestration", "charts", "deltafi")

	siteValuesFile, err := o.SiteValuesFile()
	if err != nil {
		return fmt.Errorf("Unable to get site values file: %w", err)
	}

	// Check if we should include dev values for core development mode
	var additionalValuesFiles []string
	if o.deploymentMode == types.CoreDevelopment {
		// Ensure config directory exists
		if err := os.MkdirAll(o.configPath, 0755); err != nil {
			return fmt.Errorf("Unable to create config directory: %w", err)
		}

		// Read the embedded kind.dev.values.yaml file
		devValuesContent, err := kindEmbeddedFiles.ReadFile("kind.dev.values.yaml")
		if err != nil {
			return fmt.Errorf("Unable to read kind.dev.values.yaml: %w", err)
		}

		// Write the dev values to a file in configPath (overwrites if exists)
		devValuesFile := filepath.Join(o.configPath, "kind.dev.values.yaml")
		if err := os.WriteFile(devValuesFile, devValuesContent, 0644); err != nil {
			return fmt.Errorf("Unable to write dev values file: %w", err)
		}

		additionalValuesFiles = append(additionalValuesFiles, devValuesFile)
	}

	if err := deltafiHelmInstall(o.namespace, deltafiChartPath, siteValuesFile, additionalValuesFiles...); err != nil {
		fmt.Println(styles.FAIL("DeltaFi installation failed"))
		return fmt.Errorf("Unable to install DeltaFi: %w", err)
	}

	return nil
}

func (o *KindOrchestrator) Down(args []string) error {

	isRunning, err := o.isClusterRunning()
	if err != nil {
		return fmt.Errorf("error checking KinDcluster status: %w", err)
	}

	if !isRunning {
		return fmt.Errorf("KinD cluster is not running")
	}

	return o.StopKind()
}

func (o *KindOrchestrator) Environment() []string {
	env := o.KubernetesOrchestrator.Environment()
	env = append(env, "DELTAFI_KIND=true")
	return env
}

func (o *KindOrchestrator) SiteValuesFile() (string, error) {
	if err := o.enforceSiteValuesDir(); err != nil {
		return "", err
	}

	siteValuesFile := filepath.Join(o.sitePath, "kind.values.yaml")
	if _, err := os.Stat(siteValuesFile); os.IsNotExist(err) {
		defaultContent, err := kindEmbeddedFiles.ReadFile("kind.values.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site values template: %w", err)
		}

		if err := os.WriteFile(siteValuesFile, defaultContent, 0644); err != nil {
			return siteValuesFile, fmt.Errorf("error creating default %s file: %w", "values.yaml", err)
		}
	}
	return siteValuesFile, nil
}

func (o *KindOrchestrator) isClusterRunning() (bool, error) {
	executable := filepath.Join("kind")
	args := []string{"get", "clusters", "-q"}

	output, err := executeShellCommandWithOutput(executable, args, o.Environment())
	if err != nil {
		return false, fmt.Errorf("error checking cluster status: %w", err)
	}

	// Check if the output contains exactly "deltafi" (trimmed of whitespace)
	output = strings.TrimSpace(output)
	return output == "deltafi", nil
}
