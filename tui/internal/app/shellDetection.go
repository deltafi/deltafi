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
package app

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// ShellType represents the type of shell detected
type ShellType string

const (
	Bash         ShellType = "bash"
	Fish         ShellType = "fish"
	Zsh          ShellType = "zsh"
	Powershell   ShellType = "powershell"
	UnknownShell ShellType = "unknown"
)

// Required configuration line for each shell type
const (
	BashConfig = "[[ -f $HOME/.bashrc.deltafi ]] && . $HOME/.bashrc.deltafi # Required for DeltaFi configuration"
	ZshConfig  = "[[ -f $HOME/.zshrc.deltafi ]] && source $HOME/.zshrc.deltafi # Required for DeltaFi configuration"
	FishConfig = "test -f $HOME/.config/fish/conf.d/deltafi.fish && source $HOME/.config/fish/conf.d/deltafi.fish # Required for DeltaFi configuration"
)

// DetectShell determines the current shell being used
func DetectShell() ShellType {
	// Check SHELL environment variable first
	if shellPath := os.Getenv("SHELL"); shellPath != "" {
		shellName := filepath.Base(strings.ToLower(shellPath))
		switch {
		case strings.Contains(shellName, "bash"):
			return Bash
		case strings.Contains(shellName, "fish"):
			return Fish
		case strings.Contains(shellName, "zsh"):
			return Zsh
		}
	}

	// Check for PowerShell-specific environment variables
	if os.Getenv("PSModulePath") != "" {
		return Powershell
	}

	// Check parent process name as fallback
	ppid := os.Getppid()
	if ppid > 0 {
		if processName, err := os.Readlink(filepath.Join("/proc", fmt.Sprint(ppid), "exe")); err == nil {
			processName = strings.ToLower(filepath.Base(processName))
			switch {
			case strings.Contains(processName, "bash"):
				return Bash
			case strings.Contains(processName, "fish"):
				return Fish
			case strings.Contains(processName, "zsh"):
				return Zsh
			case strings.Contains(processName, "powershell") || strings.Contains(processName, "pwsh"):
				return Powershell
			}
		}
	}

	return UnknownShell
}

// IsKnownShell checks if the detected shell is one of the supported types
func IsKnownShell(shell ShellType) bool {
	switch shell {
	case Bash, Fish, Zsh, Powershell:
		return true
	default:
		return false
	}
}

// ConfigureShell sets up the shell configuration based on the detected shell type
func ConfigureShell(shell ShellType, tuiPath string) error {
	switch shell {
	case Bash:
		return configureBash(tuiPath)
	case Zsh:
		return configureZsh(tuiPath)
	case Fish:
		return configureFish(tuiPath)
	default:
		return fmt.Errorf("shell configuration not supported for %s", shell)
	}
}

// configureBash sets up bash configuration
func configureBash(tuiPath string) error {
	// Add configuration to .bashrc if it doesn't exist
	rcPath := filepath.Join(os.Getenv("HOME"), ".bashrc")
	if err := ensureConfigLine(rcPath, BashConfig); err != nil {
		return fmt.Errorf("failed to configure .bashrc: %w", err)
	}

	// Create or overwrite .bashrc.deltafi
	deltafircPath := filepath.Join(os.Getenv("HOME"), ".bashrc.deltafi")
	content := fmt.Sprintf(`#!/bin/bash
export PATH="%s":$PATH:"%s/bin"
[[ $(command -v deltafi > /dev/null) ]] && source <(deltafi completion bash)
[[ $(command -v deltafi2 > /dev/null) ]] && source <(deltafi2 completion bash)
`, tuiPath, tuiPath)

	if err := os.WriteFile(deltafircPath, []byte(content), 0644); err != nil {
		return fmt.Errorf("failed to write .bashrc.deltafi: %w", err)
	}

	return nil
}

// configureZsh sets up zsh configuration
func configureZsh(tuiPath string) error {
	// Add configuration to .zshrc if it doesn't exist
	rcPath := filepath.Join(os.Getenv("HOME"), ".zshrc")
	if err := ensureConfigLine(rcPath, ZshConfig); err != nil {
		return fmt.Errorf("failed to configure .zshrc: %w", err)
	}

	// Create or overwrite .zshrc.deltafi
	deltafircPath := filepath.Join(os.Getenv("HOME"), ".zshrc.deltafi")
	content := fmt.Sprintf(`#!/bin/zsh
export PATH="%s":$PATH:"%s/bin"
[[ $(command -v deltafi > /dev/null) ]] && source <(deltafi completion zsh)
[[ $(command -v deltafi2 > /dev/null) ]] && source <(deltafi2 completion zsh)
`, tuiPath, tuiPath)

	if err := os.WriteFile(deltafircPath, []byte(content), 0644); err != nil {
		return fmt.Errorf("failed to write .zshrc.deltafi: %w", err)
	}

	return nil
}

// configureFish sets up fish configuration
func configureFish(tuiPath string) error {
	// Create fish config directory if it doesn't exist
	configDir := filepath.Join(os.Getenv("HOME"), ".config/fish/conf.d")
	if err := os.MkdirAll(configDir, 0755); err != nil {
		return fmt.Errorf("failed to create fish config directory: %w", err)
	}

	// Add configuration to config.fish if it doesn't exist
	configPath := filepath.Join(os.Getenv("HOME"), ".config/fish/config.fish")
	if err := ensureConfigLine(configPath, FishConfig); err != nil {
		return fmt.Errorf("failed to configure config.fish: %w", err)
	}

	// Create or overwrite deltafi.fish
	deltafiPath := filepath.Join(configDir, "deltafi.fish")
	content := fmt.Sprintf(`#!/bin/fish
fish_add_path "%s"
fish_add_path "%s/bin"
if command -v deltafi >/dev/null
    deltafi completion fish | source
end
if command -v deltafi2 >/dev/null
    deltafi2 completion fish | source
end
`, tuiPath, tuiPath)

	if err := os.WriteFile(deltafiPath, []byte(content), 0644); err != nil {
		return fmt.Errorf("failed to write deltafi.fish: %w", err)
	}

	return nil
}

// ensureConfigLine ensures a configuration line exists in the specified file
func ensureConfigLine(filepath string, config string) error {
	// Read existing content
	content, err := os.ReadFile(filepath)
	if err != nil && !os.IsNotExist(err) {
		return err
	}

	// Check if config line already exists
	if strings.Contains(string(content), config) {
		return nil
	}

	// Append config line
	f, err := os.OpenFile(filepath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()

	if len(content) > 0 && !strings.HasSuffix(string(content), "\n") {
		if _, err := f.WriteString("\n"); err != nil {
			return err
		}
	}

	_, err = f.WriteString(config + "\n")
	return err
}
