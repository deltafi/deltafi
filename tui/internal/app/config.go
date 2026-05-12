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
package app

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v3"

	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/types"
)

// configure the API connection, this can be expanded to support timeouts/keepalive settings
type Api struct {
	Url string `yaml:"url"`
}

type Context struct {
	Api            *Api  `yaml:"api"`
	Authentication *Auth `yaml:"authentication"`
}

type Config struct {
	OrchestrationMode orchestration.OrchestrationMode `yaml:"orchestrationMode"`
	DeploymentMode    types.DeploymentMode            `yaml:"deploymentMode"`
	CoreVersion       string                          `yaml:"coreVersion"`
	InstallDirectory  string                          `yaml:"installDirectory"`
	DataDirectory     string                          `yaml:"dataDirectory"`
	SiteDirectory     string                          `yaml:"siteDirectory"`
	Development       DevelopmentConfig               `yaml:"development"`
	Context           *Context                        `yaml:"context"`
}

type DevelopmentConfig struct {
	RepoPath string `yaml:"repoPath"`
	CoreRepo string `yaml:"coreRepo"`
}

const (
	defaultConfigFileName = "config.yaml"
)

func DefaultConfig() Config {
	return Config{
		OrchestrationMode: orchestration.Compose,
		DeploymentMode:    types.Deployment,
		CoreVersion:       GetVersion(),
		InstallDirectory:  TuiPath(),
		DataDirectory:     filepath.Join(TuiPath(), "data"),
		SiteDirectory:     filepath.Join(TuiPath(), "site"),
		Development:       DefaultDevelopmentConfig(),
	}
}

func (c *Config) IsDevelopment() bool {
	if c.OrchestrationMode != orchestration.Compose {
		return false
	}

	return c.DeploymentMode == types.CoreDevelopment || c.DeploymentMode == types.PluginDevelopment
}

func (c *Config) GetCoreVersion() *semver.Version {
	if c.CoreVersion == "" {
		return GetSemanticVersion()
	}

	version, err := semver.NewVersion(c.CoreVersion)
	if err != nil {
		return GetSemanticVersion()
	}

	return version
}

func (c *Config) SetCoreVersion(version *semver.Version) {
	c.CoreVersion = version.String()
	c.Save()
}

func DefaultDevelopmentConfig() DevelopmentConfig {
	return DevelopmentConfig{
		RepoPath: filepath.Join(TuiPath(), "repos"),
		CoreRepo: "git@gitlab.com:deltafi/deltafi.git",
	}
}

func configFile() string {
	return filepath.Join(ConfigPath(), defaultConfigFileName)
}

func ConfigExists() bool {
	_, err := os.Stat(configFile())
	return !os.IsNotExist(err)
}

func CreateConfig() error {
	err := os.MkdirAll(ConfigPath(), 0755)

	if err != nil {
		return fmt.Errorf("failed to create config directory: %w", err)
	}

	config := DefaultConfig()
	return config.Save()
}

// MigrateLegacyConfig moves config.yaml from the legacy ~/.deltafi
// directory into the executable-relative <TuiPath>/config directory and
// recreates the shell rc file in the new location. Invoked explicitly via
// `deltafi config --migrate-config`.
func MigrateLegacyConfig() error {
	legacyDir := LegacyConfigPath()
	targetDir := TuiConfigPath()
	if legacyDir == targetDir {
		return nil
	}

	legacyConfig := filepath.Join(legacyDir, defaultConfigFileName)
	if _, err := os.Stat(legacyConfig); err != nil {
		if os.IsNotExist(err) {
			fmt.Printf("No legacy config found at %s; nothing to migrate.\n", legacyConfig)
			return nil
		}
		return fmt.Errorf("failed to stat legacy config %s: %w", legacyConfig, err)
	}

	newConfig := filepath.Join(targetDir, defaultConfigFileName)
	if _, err := os.Stat(newConfig); err == nil {
		return fmt.Errorf("refusing to overwrite existing config at %s; remove it first if you want to re-migrate", newConfig)
	}

	if err := os.MkdirAll(targetDir, 0755); err != nil {
		return fmt.Errorf("failed to create config directory %s: %w", targetDir, err)
	}

	data, err := os.ReadFile(legacyConfig)
	if err != nil {
		return fmt.Errorf("failed to read legacy config %s: %w", legacyConfig, err)
	}
	if err := os.WriteFile(newConfig, data, 0644); err != nil {
		return fmt.Errorf("failed to write config to %s: %w", newConfig, err)
	}
	if err := os.Remove(legacyConfig); err != nil {
		return fmt.Errorf("failed to remove legacy config %s: %w", legacyConfig, err)
	}

	removeLegacyRcFiles(legacyDir)

	shell := DetectShell()
	if IsKnownShell(shell) {
		if err := ConfigureShell(shell, TuiPath()); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: failed to configure shell rc: %v\n", err)
		}
	}

	fmt.Printf("Migrated DeltaFi config from %s to %s\n", legacyConfig, newConfig)
	return nil
}

func removeLegacyRcFiles(legacyDir string) {
	for _, name := range []string{"rc.bash", "rc.zsh", "rc.fish"} {
		_ = os.Remove(filepath.Join(legacyDir, name))
	}
}

// LoadConfig loads the application configuration from the config file
func LoadConfig() (Config, error) {
	configFile := configFile()
	config := DefaultConfig()

	if fileInfo, err := os.Stat(configFile); err == nil {
		if fileInfo.Size() > 0 {
			if err := loadFromFile(configFile, &config); err != nil {
				return config, fmt.Errorf("config.yaml file is invalid: \n%v", err)
			}
		}
	} else if !os.IsNotExist(err) {
		return config, fmt.Errorf("config.yaml unreadable: %v", err)
	}

	if os.Getenv("DELTAFI_WEBTERMINAL") == "true" {
		config.applyWebTerminalOverrides()
	}

	return config, nil
}

// applyWebTerminalOverrides replaces directory paths with container-local
// paths so the mounted host config.yaml is read but not modified.
func (c *Config) applyWebTerminalOverrides() {
	tuiPath := TuiPath()
	c.InstallDirectory = tuiPath
	c.DataDirectory = filepath.Join(tuiPath, "data")
	c.SiteDirectory = filepath.Join(tuiPath, "site")
	c.Development.RepoPath = filepath.Join(tuiPath, "repos")
}

func LoadConfigOrDefault() Config {
	config, _ := LoadConfig()
	return config
}

// loadFromFile loads configuration from a yaml file
func loadFromFile(path string, config *Config) error {
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("failed to open config file: %w", err)
	}
	defer file.Close()

	if err := yaml.NewDecoder(file).Decode(config); err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}

	return nil
}

// Save saves the current configuration to file. In web terminal mode the
// mounted host config must not be modified, so Save is a no-op.
func (c *Config) Save() error {
	if os.Getenv("DELTAFI_WEBTERMINAL") == "true" {
		return nil
	}

	// Ensure the config directory exists
	if err := os.MkdirAll(ConfigPath(), 0755); err != nil {
		return fmt.Errorf("failed to create config directory: %w", err)
	}

	file, err := os.Create(configFile())
	if err != nil {
		return fmt.Errorf("failed to create config file: %w", err)
	}
	defer file.Close()

	encoder := yaml.NewEncoder(file)
	if err := encoder.Encode(c); err != nil {
		return fmt.Errorf("failed to encode config: %w", err)
	}

	return nil
}
