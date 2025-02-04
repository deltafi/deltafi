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

	"github.com/deltafi/tui/internal/orchestration"
)

type Config struct {
	OrchestrationMode orchestration.OrchestrationMode `yaml:"orchestrationMode"`
	DeploymentMode    DeploymentMode                  `yaml:"deploymentMode"`
	CoreVersion       string                          `yaml:"coreVersion"`
	Development       DevelopmentConfig               `yaml:"development"`
}

type DevelopmentConfig struct {
	CoreRepo string `yaml:"coreRepo"`
}

type DeploymentConfig struct {
	Mode DeploymentMode `yaml:"mode"`
}

const (
	defaultConfigFileName = "deltafi.yaml"
)

func DefaultConfig() Config {
	return Config{
		OrchestrationMode: orchestration.Compose,
		DeploymentMode:    Deployment,
		CoreVersion:       Version,
		Development:       DefaultDevelopmentConfig(),
	}
}

func DefaultDevelopmentConfig() DevelopmentConfig {
	return DevelopmentConfig{
		CoreRepo: "git@gitlab.com:systolic/deltafi/deltafi.git",
	}
}

func getDistroPath(version string) string {
	return filepath.Join(TuiPath(), version)
}

func configFile() string {
	return filepath.Join(TuiPath(), defaultConfigFileName)
}

// LoadConfig loads the application configuration from the config file
func LoadConfig() (Config, error) {
	configFile := configFile()
	config := DefaultConfig()

	if fileInfo, err := os.Stat(configFile); err == nil {
		if fileInfo.Size() > 0 {
			if err := loadFromFile(configFile, &config); err != nil {
				return config, fmt.Errorf("deltafi.yaml file is invalid: \n%v", err)
			}
		}
	} else if os.IsNotExist(err) {
		fmt.Fprintf(os.Stderr, "Warning: deltafi.yaml file does not exist (%s)\n", configFile)
	} else {
		return config, fmt.Errorf("deltafi.yaml unreadable: %v", err)
	}

	return config, nil
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
