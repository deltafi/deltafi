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

	"gopkg.in/yaml.v3"

	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/types"
)

type Config struct {
	OrchestrationMode orchestration.OrchestrationMode `yaml:"orchestrationMode"`
	DeploymentMode    types.DeploymentMode            `yaml:"deploymentMode"`
	CoreVersion       string                          `yaml:"coreVersion"`
	InstallDirectory  string                          `yaml:"installDirectory"`
	DataDirectory     string                          `yaml:"dataDirectory"`
	SiteDirectory     string                          `yaml:"siteDirectory"`
	Development       DevelopmentConfig               `yaml:"development"`
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
	} else if os.IsNotExist(err) {
		fmt.Fprintf(os.Stderr, "Warning: config.yaml file does not exist (%s)\n", configFile)
	} else {
		return config, fmt.Errorf("config.yaml unreadable: %v", err)
	}

	return config, nil
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

// Save saves the current configuration to file
func (c *Config) Save() error {

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
