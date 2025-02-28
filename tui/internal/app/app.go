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
	"runtime"
	"strings"
	"sync"

	"github.com/Khan/genqlient/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
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
	instance *App
	once     sync.Once
	Version  string
)

// GetInstance returns the singleton App instance
func GetInstance() *App {
	once.Do(build)
	return instance
}

func GetGraphqlClient() (graphql.Client, error) {
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
	Version = version
}

func build() {

	config, error := LoadConfig()

	if error != nil {
		config = DefaultConfig()
	}

	distroPath := ""
	if config.DeploymentMode == CoreDevelopment || strings.Contains(Version, "SNAPSHOT") {
		distroPath = filepath.Join(config.Development.RepoPath, "deltafi")
	} else {
		distroPath = filepath.Join(TuiPath(), Version)
	}

	instance = &App{
		config:       &config,
		orchestrator: orchestration.NewOrchestrator(config.OrchestrationMode, distroPath, config.DataDirectory),
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

func GetOrchestrator() orchestration.Orchestrator {
	return GetInstance().orchestrator
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
	serviceip, error := a.orchestrator.GetServiceIP("deltafi-core-service")

	if error != nil {
		return "", error
	}

	coreServiceURL := fmt.Sprintf("http://%s", serviceip)
	return coreServiceURL, nil
}

// GetAPIClient returns the initialized API client
func (a *App) GetAPIClient() *api.Client {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.apiClient
}

func (a *App) GetGraphqlClient() graphql.Client {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.graphqlClient
}

func (a *App) GetConfig() *Config {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.config
}

func (a *App) FormatWarning(msg string) string {
	return styles.WarningStyle.Render(msg)
}

func (a *App) FormatErrors(errorPointers []*string) string {
	errors := make([]string, len(errorPointers))
	for i, errorStr := range errorPointers {
		errors[i] = *errorStr
	}

	return a.FormatError(strings.Join(errors, "\n                "))
}

func (a *App) FormatError(msg string) string {
	return styles.ErrorStyle.Render(msg)
}

func (a *App) FormatErrorObj(errorObj error) string {
	return styles.ErrorStyle.Render(errorObj.Error())
}

func (a *App) FormatSuccess(msg string) string {
	return styles.SuccessStyle.Render(msg)
}

func (a *App) FormatMessage(msg string) string {
	return styles.InfoStyle.Render(msg)
}

func (a *App) formatMoniker(moniker string, msg string) string {
	return "[ " + moniker + " ] " + msg
}

func (a *App) OK(msg string) string {
	return a.formatMoniker(
		a.FormatSuccess(" OK "),
		styles.BaseStyle.Render(msg),
	)
}

func (a *App) FAIL(msg string) string {
	return a.formatMoniker(
		a.FormatError("FAIL"),
		styles.BaseStyle.Render(msg),
	)
}
