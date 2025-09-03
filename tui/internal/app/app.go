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
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/Khan/genqlient/graphql"
	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/types"
)

// App represents the global application state
type App struct {
	config        *Config
	apiClient     *api.Client
	graphqlClient graphql.Client
	orchestrator  orchestration.Orchestrator
	mu            sync.RWMutex
	distroPath    string
	os            string
	arch          string
	running       bool
}

// Global singleton instance
var (
	instance        *App
	once            sync.Once
	SemanticVersion *semver.Version
	NotRunning      = errors.New("not running")
)

// GetInstance returns the singleton App instance
func GetInstance() *App {
	once.Do(build)
	return instance
}

func SetAdminPassword(password string) error {
	req := api.PasswordUpdate{
		Password: password,
	}
	client, err := instance.GetAPIClient()
	if err != nil {
		return err
	}
	_, err = client.SetAdminPassword(req)
	return err
}

// Recreate the singleton App instance based on the current configuration
func ReloadInstance() *App {
	build()
	return instance
}

func GetGraphqlClient() (graphql.Client, error) {
	client := GetInstance().graphqlClient
	if client == nil || !GetInstance().running {
		return nil, NotRunning
	}
	return GetInstance().graphqlClient, nil
}

func GetDistroPath() string {
	return GetInstance().distroPath
}

func GetOS() string {
	return GetInstance().os
}

func GetArch() string {
	return GetInstance().arch
}

func SetVersion(version string) {
	sv, err := semver.NewVersion(version)
	if err != nil {
		sv = semver.MustParse("0.0.0")
	}
	SemanticVersion = sv
}

func GetVersion() string {
	if SemanticVersion != nil {
		return SemanticVersion.String()
	}
	return "0.0.0"
}

func GetSemanticVersion() *semver.Version {
	return SemanticVersion
}

func build() {

	config, error := LoadConfig()

	if error != nil {
		config = DefaultConfig()
	}

	distroPath := ""
	if config.DeploymentMode == types.CoreDevelopment || strings.Contains(GetVersion(), "SNAPSHOT") {
		distroPath = filepath.Join(config.Development.RepoPath, "deltafi")
	} else {
		distroPath = filepath.Join(TuiPath(), GetVersion())
	}

	instance = &App{
		config:       &config,
		orchestrator: orchestration.NewOrchestrator(config.OrchestrationMode, distroPath, config.DataDirectory, config.InstallDirectory, config.SiteDirectory, SemanticVersion, config.DeploymentMode),
		os:           runtime.GOOS,
		arch:         runtime.GOARCH,
		distroPath:   distroPath,
		running:      true,
	}

	// Initialize the API and hit the me endpoint to verify running DeltaFi
	err := instance.initializeAPI()

	if err != nil {
		instance.running = false
	} else {
		_, apiErr := instance.apiClient.Me()
		if apiErr != nil {
			instance.running = false
		}
	}
}

func WaitForRunning() {
	_, err := instance.apiClient.Me()
	if err == nil {
		return
	}
	fmt.Print("Waiting for DeltaFi to start")
	for err != nil {
		time.Sleep(1 * time.Second)
		fmt.Print(".")
		_, err = instance.apiClient.Me()
	}
	fmt.Println()
}

func ConfigPath() string {
	if configPath := os.Getenv("DELTAFI_CONFIG_PATH"); configPath != "" {
		return configPath
	} else {
		return filepath.Join(os.Getenv("HOME"), ".deltafi")
	}
}

func TuiPath() string {
	ex, err := os.Executable()
	if err != nil {
		panic(err)
	}
	return filepath.Dir(ex)
}

func GetInstallDir() string {
	return GetInstance().config.InstallDirectory
}

func GetOrchestrator() orchestration.Orchestrator {
	return GetInstance().orchestrator
}

func GetDataDir() string {
	return GetInstance().config.DataDirectory
}

func IsRunning() bool {
	return GetInstance().running
}

func GetOrchestrationMode() orchestration.OrchestrationMode {
	return GetInstance().config.OrchestrationMode
}

func (a *App) initializeAPI() error {
	a.mu.Lock()
	defer a.mu.Unlock()

	baseURL, err := a.getAPIBaseURL()
	if err != nil {
		return fmt.Errorf("failed to initialize API client: %w", err)
	}

	a.apiClient = api.NewClient(baseURL)

	a.graphqlClient = graphql.NewClient(baseURL+"/api/v2/graphql", a.apiClient)

	return nil
}

func (a *App) getAPIBaseURL() (string, error) {
	base, error := a.orchestrator.GetAPIBaseURL()

	if error != nil {
		return "", error
	}

	coreServiceURL := fmt.Sprintf("http://%s", base)
	return coreServiceURL, nil
}

// GetAPIClient returns the initialized API client
func (a *App) GetAPIClient() (*api.Client, error) {
	a.mu.RLock()
	defer a.mu.RUnlock()

	if a.apiClient == nil || !a.running {
		return nil, NotRunning
	}

	return a.apiClient, nil
}

func (a *App) GetConfig() *Config {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.config
}

func SendEvent(event *api.Event) error {
	client, err := GetInstance().GetAPIClient()
	if err != nil {
		return err
	}

	_, err = client.CreatEvent(*event)
	return err
}
