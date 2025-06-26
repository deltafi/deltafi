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
)

//go:embed kubernetes.values.site.yaml
var kubernetesEmbeddedFiles embed.FS

type KubernetesOrchestrator struct {
	Orchestrator
	distroPath string
	sitePath   string
	dataPath   string
	namespace  string
}

func NewKubernetesOrchestrator(distroPath string) *KubernetesOrchestrator {
	return &KubernetesOrchestrator{
		namespace:  "deltafi",
		distroPath: distroPath,
	}
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

func (o *KubernetesOrchestrator) GetServiceIP(service string) (string, error) {
	cmd := exec.Command("kubectl",
		"get", "service", service,
		"-n", o.namespace,
		"-o", "jsonpath={.spec.clusterIP}")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get service IP: %v", err)
	}

	return strings.TrimSpace(string(output)), nil
}

type KubernetesHelper struct {
	namespace string
}

func NewKubernetesHelper() *KubernetesHelper {
	return &KubernetesHelper{
		namespace: "deltafi",
	}
}

func getKubectlExecCmd(args []string) (exec.Cmd, error) {
	cmdArgs := []string{"exec"}
	if isTty() {
		cmdArgs = append(cmdArgs, "-it")
	} else {
		cmdArgs = append(cmdArgs, "-i")
	}
	cmdArgs = append(cmdArgs, "-n", "deltafi")
	cmdArgs = append(cmdArgs, args...)
	return *exec.Command("kubectl", cmdArgs...), nil
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

	siteValues, err := o.SiteValuesFile()
	if err != nil {
		return fmt.Errorf("Unable to findsite values file: %w", err)
	}

	// TODO: Implement CLI functionality here
	executable := filepath.Join(o.distroPath, "deltafi-cli", "deltafi", "-f", siteValues)

	args = append([]string{"install"}, args...)

	c := *exec.Command(executable, args...)
	c.Env = o.Environment()
	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr

	return c.Run()
}

func (o *KubernetesOrchestrator) Down(args []string) error {

	// TODO: Implement CLI functionality here
	executable := filepath.Join(o.distroPath, "deltafi-cli", "deltafi")

	args = append([]string{"uninstall"}, args...)

	c := *exec.Command(executable, args...)
	c.Env = o.Environment()
	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr

	return c.Run()
}

func (o *KubernetesOrchestrator) Environment() []string {
	mode := "CLUSTER"
	env := os.Environ()
	env = append(env, "DELTAFI_MODE="+mode)
	env = append(env, "DELTAFI_DATA_DIR="+o.dataPath)
	env = append(env, "DELTAFI_WRAPPER=true")
	return env
}

func (o *KubernetesOrchestrator) ExecuteMinioCommand(cmd []string) error {
	return execMinioCommand(o, cmd)
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
		defaultContent, err := composeEmbeddedFiles.ReadFile("kubernetes.values.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site values template: %w", err)
		}

		if err := os.WriteFile(siteValuesFile, defaultContent, 0644); err != nil {
			return siteValuesFile, fmt.Errorf("error creating default %s file: %w", "values.yaml", err)
		}
	}
	return siteValuesFile, nil
}
