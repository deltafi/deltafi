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
	"errors"
	"fmt"
	"os"
	"os/exec"
)

type BaseOrchestrator struct {
	Orchestrator
}

func (o BaseOrchestrator) ExecuteMinioCommand(cmd []string) error {
	minioCliArgs := []string{"mc alias set deltafi http://deltafi-minio:9000 $MINIO_ROOT_USER $MINIO_ROOT_PASSWORD > /dev/null && "}

	minioName, err := o.GetMinioName()
	if err != nil {
		return err
	}
	c, err := o.GetExecCmd(minioName, append(minioCliArgs, cmd...))
	if err != nil {
		return err
	}

	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr

	err = c.Run()
	if err == nil {
		return nil
	}

	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		// Ignore common interrupt exit codes
		if exitError.ExitCode() == 130 || exitError.ExitCode() == 2 {
			return nil
		}
	}

	return fmt.Errorf("command execution failed: %w", err)
}
