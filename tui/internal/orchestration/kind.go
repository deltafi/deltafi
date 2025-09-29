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
	"text/template"

	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/types"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/mcuadros/go-defaults"
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
	coreVersion    *semver.Version
}

func NewKindOrchestrator(distroPath string, sitePath string, dataPath string, reposPath string, installDirectory string, coreVersion *semver.Version, deploymentMode types.DeploymentMode) *KindOrchestrator {
	configPath := filepath.Join(installDirectory, ".kind.config")
	secretsPath := filepath.Join(configPath, "secrets")

	ko := &KindOrchestrator{
		KubernetesOrchestrator: NewKubernetesOrchestrator(distroPath, sitePath, dataPath),
		reposPath:              reposPath,
		deploymentMode:         deploymentMode,
		configPath:             configPath,
		secretsPath:            secretsPath,
		coreVersion:            coreVersion,
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

func (o *KindOrchestrator) Load(args []string) error {
	executable := filepath.Join(o.distroPath, "orchestration", "kind", "kind")
	arglist := []string{"load", "-n", o.namespace, "docker-image"}
	arglist = append(arglist, args...)
	return executeShellCommand(executable, arglist, o.Environment())
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

		tmpl, err := template.New("values").Parse(string(devValuesContent))
		if err != nil {
			return fmt.Errorf("error parsing values template: %w", err)
		}

		data := valuesData{
			Tag: o.coreVersion.String(),
		}
		defaults.SetDefaults(&data)

		// Create values.yaml file
		if err := os.MkdirAll(o.configPath, 0755); err != nil {
			return fmt.Errorf("error creating config directory: %w", err)
		}
		devValuesFile := filepath.Join(o.configPath, "kind.dev.values.yaml")
		file, err := os.Create(devValuesFile)
		if err != nil {
			return fmt.Errorf("error creating values.yaml file: %w", err)
		}
		defer file.Close()

		// Execute template
		if err := tmpl.Execute(file, data); err != nil {
			return fmt.Errorf("error executing values template: %w", err)
		}

		additionalValuesFiles = append(additionalValuesFiles, devValuesFile)
	}

	siteTemplatesPath, err := o.TemplatesDir()
	if err != nil {
		return fmt.Errorf("Unable to get site templates directory: %w", err)
	}

	if err := deltafiHelmInstall(o.namespace, deltafiChartPath, siteValuesFile, siteTemplatesPath, additionalValuesFiles...); err != nil {
		fmt.Println(styles.FAIL("DeltaFi installation failed"))
		return fmt.Errorf("Unable to install DeltaFi: %w", err)
	}

	fmt.Println(styles.HEADER("Checking for out-of-date pods"))

	if err := o.executeClusterShellCommand("/usr/dev/deltafi/orchestration/kind/sync", []string{"--namespace", o.namespace}, o.Environment()); err != nil {
		return fmt.Errorf("error listing files in cluster: %w", err)
	}
	fmt.Println(styles.OK("All pods are current"))

	return nil
}

func (o *KindOrchestrator) Down(args []string) error {

	return o.StopKind()
}

func (o *KindOrchestrator) Environment() []string {
	env := o.KubernetesOrchestrator.Environment()
	env = append(env, "DELTAFI_CONFIG_DIR="+o.configPath)
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

func (o *KindOrchestrator) executeClusterShellCommand(executable string, args []string, env []string) error {

	dockerArgs := []string{"exec", "-i", "deltafi-control-plane", executable}
	dockerArgs = append(dockerArgs, args...)
	return executeShellCommand("docker", dockerArgs, env)
}
