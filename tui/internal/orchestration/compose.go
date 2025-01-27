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

type ComposeOrchestrator struct {
	Orchestrator
}

func NewComposeOrchestrator() *KubernetesOrchestrator {
	return &KubernetesOrchestrator{}
}

func (o *ComposeOrchestrator) GetServiceIP(service string) (string, error) {

	// return fmt.Sprintf("%s:8042", strings.TrimSpace(service)), nil
	return "localhost", nil // FIXME - this is a hack
}

func (o *ComposeOrchestrator) GetPostgresCmd(args []string) (exec.Cmd, error) {
	cmdArgs := []string{"exec", "-it", "deltafi-postgres", "bash", "-c"}

	psqlCmd := fmt.Sprintf("psql $POSTGRES_DB %s", strings.Join(args, " "))
	cmdArgs = append(cmdArgs, psqlCmd)

	return *exec.Command("docker", cmdArgs...), nil
}

func (o *ComposeOrchestrator) GetPostgresExecCmd(args []string) (exec.Cmd, error) {
	cmdArgs := []string{"exec", "-i", "deltafi-postgres", "bash", "-c"}

	psqlCmd := fmt.Sprintf("psql $POSTGRES_DB %s", strings.Join(args, " "))
	cmdArgs = append(cmdArgs, psqlCmd)

	return *exec.Command("docker", cmdArgs...), nil
}
