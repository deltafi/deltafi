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
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/deltafi/tui/internal/ui/styles"
)

//go:embed kubernetes.values.site.yaml
var kubernetesEmbeddedFiles embed.FS

type KubernetesOrchestrator struct {
	BaseOrchestrator
	distroPath string
	sitePath   string
	dataPath   string
	namespace  string
}

func NewKubernetesOrchestrator(distroPath string, sitePath string, dataPath string) *KubernetesOrchestrator {
	ko := &KubernetesOrchestrator{
		namespace:  "deltafi",
		distroPath: distroPath,
		sitePath:   sitePath,
		dataPath:   dataPath,
	}
	ko.BaseOrchestrator.Orchestrator = ko
	return ko
}

// Helper for Kubernetes
func (o *KubernetesOrchestrator) GetMasterPod(selector string) (string, error) {
	cmd := exec.Command("kubectl",
		"get", "pod",
		"--selector", selector,
		"-n", o.namespace,
		"-o", "name")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get pod: %v", err)
	}

	podName := strings.TrimSpace(string(output))
	if podName == "" {
		return "", fmt.Errorf("no pod found matching selector: %s", selector)
	}

	return podName, nil
}

func (o *KubernetesOrchestrator) GetMinioName() (string, error) {
	cmd := exec.Command("kubectl", "get", "pod", "-l app=minio", "-o", "name")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get the minio pod: %v", err)
	}

	podName := strings.TrimSpace(string(output))
	if podName == "" {
		return "", fmt.Errorf("no pod found with a label of app=minio")
	}

	return podName, nil
}

func (o *KubernetesOrchestrator) GetAPIBaseURL() (string, error) {
	return getKubernetesServiceIP("deltafi-core-service", o.namespace)
}
func (o *KubernetesOrchestrator) GetPostgresCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetPostgresExecCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetExecCmd(name string, args []string) (exec.Cmd, error) {
	cmdArgs := []string{name, "--", "sh", "-c"}

	extraCmdArgs := fmt.Sprintf("%s", strings.Join(args, " "))
	cmdArgs = append(cmdArgs, extraCmdArgs)

	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetValkeyName() string {
	return "deltafi-valkey-master-0"
}

func (o *KubernetesOrchestrator) Up(args []string) error {

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

	if err := deltafiHelmInstall(o.namespace, deltafiChartPath, siteValuesFile); err != nil {
		fmt.Println(styles.FAIL("DeltaFi installation failed"))
		return fmt.Errorf("Unable to install DeltaFi: %w", err)
	}

	return nil
}

func (o *KubernetesOrchestrator) Down(args []string) error {
	return deltafiHelmUninstall(o.namespace)
}

func (o *KubernetesOrchestrator) Environment() []string {
	mode := "CLUSTER"
	env := os.Environ()
	env = append(env, "DELTAFI_MODE="+mode)
	env = append(env, "DELTAFI_DATA_DIR="+o.dataPath)
	return env
}

func (o *KubernetesOrchestrator) enforceSiteValuesDir() error {
	if err := os.MkdirAll(o.sitePath, 0755); err != nil {
		return fmt.Errorf("error creating site values directory: %w", err)
	}
	return nil
}

func (o *KubernetesOrchestrator) SiteValuesFile() (string, error) {
	if err := o.enforceSiteValuesDir(); err != nil {
		return "", err
	}

	siteValuesFile := filepath.Join(o.sitePath, "values.yaml")
	if _, err := os.Stat(siteValuesFile); os.IsNotExist(err) {
		defaultContent, err := kubernetesEmbeddedFiles.ReadFile("kubernetes.values.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site values template: %w", err)
		}

		if err := os.WriteFile(siteValuesFile, defaultContent, 0644); err != nil {
			return siteValuesFile, fmt.Errorf("error creating default %s file: %w", "values.yaml", err)
		}
	}
	return siteValuesFile, nil
}
