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
	"os/exec"
	"strings"
)

type KubernetesOrchestrator struct {
	Orchestrator
	namespace string
}

func NewKubernetesOrchestrator() *KubernetesOrchestrator {
	return &KubernetesOrchestrator{
		namespace: "deltafi",
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

func (o *KubernetesOrchestrator) GetServiceIP(service string) (string, error) {
	// if IsStandalone() {
	// 	return fmt.Sprintf("%s:8042", service), nil
	// }

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

func (o *KubernetesOrchestrator) GetPostgresCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		"exec", "-it",
		"-n", "deltafi",
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	cmd := exec.Command("kubectl", cmdArgs...)

	return *cmd, nil
}

func (o *KubernetesOrchestrator) GetPostgresExecCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		"exec", "-i",
		"-n", "deltafi",
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	cmd := exec.Command("kubectl", cmdArgs...)

	return *cmd, nil
}
