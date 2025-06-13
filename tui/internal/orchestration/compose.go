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
	"bytes"
	"embed"
	"fmt"
	"math/rand"
	"os"
	"os/exec"
	"os/user"
	"path/filepath"
	"strings"
	"text/template"

	"github.com/Masterminds/semver/v3"
	"github.com/deltafi/tui/internal/types"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/mcuadros/go-defaults"
	"gopkg.in/yaml.v3"
)

//go:embed compose.plugin-dev.yaml compose.core-dev.yaml compose.site.yaml compose.values.yaml compose.values.site.yaml
var embeddedFiles embed.FS

const (
	networkName          = "deltafi"
	composeCoreDevFile   = "compose.core-dev.yaml"
	composePluginDevFile = "compose.plugin-dev.yaml"
)

type valuesData struct {
	Tag                   string
	Repo                  string `default:"deltafi"`
	GrafanaContainer      string `default:"deltafi/grafana:11.6.1-0"`
	GraphiteContainer     string `default:"graphiteapp/graphite-statsd:1.1.10-5"`
	LokiContainer         string `default:"grafana/loki:2.9.14"`
	MinioContainer        string `default:"minio/minio:RELEASE.2025-04-22T22-12-26Z"`
	NginxContainer        string `default:"nginx:1.28.0-alpine3.21"`
	PromtailContainer     string `default:"grafana/promtail:2.9.14"`
	ValkeyContainer       string `default:"bitnami/valkey:8.1.1"`
	DockerWebGuiContainer string `default:"deltafi/docker-web-gui:1.0.2-1"`
	PostgresContainer     string `default:"deltafi/timescaledb:2.19.3-pg16-0"`
	JavaIDEContainer      string `default:"deltafi/deltafi-java-dev:jdk21-gradle8.5-1"`
	JavaDevContainer      string `default:"deltafi/devcontainer-java:jdk21-gradle8.5-0"`
}

const defaultValuesTemplate = `deltafi:
  java_ide:
    image: {{.JavaIDEContainer}}
    enabled: false
  java_devcontainer:
    image: {{.JavaDevContainer}}
    enabled: false
  core_actions:
    replicas: 1
    image: {{.Repo}}/deltafi-core-actions:{{.Tag}}
  core:
    image: {{.Repo}}/deltafi-core:{{.Tag}}
    envVar:
      HTTP_MAX_BODY_SIZE: 5G
  core_worker:
    enabled: false
    replicas: 1
  auth:
    mode: disabled # basic, cert, or disabled
    entityResolver:
      enabled: false
      image: {{.Repo}}/deltafi-entity-resolver:{{.Tag}}
      port: 8080
      config:
        README: |-
          Add files to the site/values.yaml at deltafi.auth.config to inject files here
  api:
    image: {{.Repo}}/deltafi-api:{{.Tag}}
    workers: 8
  file_ingress:
    enabled: true
    image: {{.Repo}}/deltafi-file-ingress:{{.Tag}}
  egress_sink:
    enabled: true
    image: {{.Repo}}/deltafi-egress-sink:{{.Tag}}
  nodemonitor:
    image: {{.Repo}}/deltafi-nodemonitor:{{.Tag}}
# nginx domain
ingress:
  domain: local.deltafi.org
  tls:
    enabled: false
dependencies:
  grafana: {{.GrafanaContainer}}
  graphite: {{.GraphiteContainer}}
  loki: {{.LokiContainer}}
  minio: {{.MinioContainer}}
  nginx: {{.NginxContainer}}
  promtail: {{.PromtailContainer}}
  valkey: {{.ValkeyContainer}}
  docker_web_gui: {{.DockerWebGuiContainer}}
  postgres: {{.PostgresContainer}}
`

type ComposeOrchestrator struct {
	Orchestrator
	distroPath        string
	dataPath          string
	reposPath         string
	orchestrationPath string
	secretsPath       string
	configPath        string
	sitePath          string
	coreVersion       *semver.Version
	deploymentMode    types.DeploymentMode
}

func NewComposeOrchestrator(distroPath string) *ComposeOrchestrator {
	return &ComposeOrchestrator{distroPath: distroPath}
}

func (o *ComposeOrchestrator) GetServiceIP(service string) (string, error) {
	return fmt.Sprintf("%s:8042", strings.TrimSpace(service)), nil
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

func (o *ComposeOrchestrator) GetExecCmd(name string, tty bool, args []string) (exec.Cmd, error) {
	flags := "-i"
	if tty {
		flags += "t"
	}

	cmdArgs := []string{"exec", flags, name, "sh", "-c"}

	extraCmdArgs := fmt.Sprintf("%s", strings.Join(args, " "))
	cmdArgs = append(cmdArgs, extraCmdArgs)

	return *exec.Command("docker", cmdArgs...), nil
}

func (o *ComposeOrchestrator) GetValkeyName() string {
	return "deltafi-valkey"
}

func (o *ComposeOrchestrator) GetMinioName() (string, error) {
	return "deltafi-minio", nil
}

func (o *ComposeOrchestrator) enforceSiteValuesDir() error {
	if err := os.MkdirAll(o.sitePath, 0755); err != nil {
		return fmt.Errorf("error creating site values directory: %w", err)
	}
	return nil
}

func (o *ComposeOrchestrator) SiteValuesFile() (string, error) {
	if err := o.enforceSiteValuesDir(); err != nil {
		return "", err
	}

	siteValuesFile := filepath.Join(o.sitePath, "values.yaml")
	if _, err := os.Stat(siteValuesFile); os.IsNotExist(err) {
		defaultContent, err := embeddedFiles.ReadFile("compose.values.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site values template: %w", err)
		}

		if err := os.WriteFile(siteValuesFile, defaultContent, 0644); err != nil {
			return siteValuesFile, fmt.Errorf("error creating default %s file: %w", "values.yaml", err)
		}
	}
	return siteValuesFile, nil
}

func (o *ComposeOrchestrator) PluginDevelopmentComposeFile() (string, error) {
	if err := os.MkdirAll(o.configPath, 0755); err != nil {
		return "", fmt.Errorf("error creating config directory: %w", err)
	}

	pluginDevelopmentFile := filepath.Join(o.configPath, composePluginDevFile)
	defaultContent, err := embeddedFiles.ReadFile("compose.plugin-dev.yaml")
	if err != nil {
		return "", fmt.Errorf("error reading plugin development compose template: %w", err)
	}

	if err := os.WriteFile(pluginDevelopmentFile, defaultContent, 0644); err != nil {
		return pluginDevelopmentFile, fmt.Errorf("error creating default %s file: %w", composePluginDevFile, err)
	}
	return pluginDevelopmentFile, nil
}

func (o *ComposeOrchestrator) CoreDevelopmentComposeFile() (string, error) {
	if err := os.MkdirAll(o.configPath, 0755); err != nil {
		return "", fmt.Errorf("error creating config directory: %w", err)
	}

	coreDevelopmentFile := filepath.Join(o.configPath, composeCoreDevFile)
	defaultContent, err := embeddedFiles.ReadFile("compose.core-dev.yaml")
	if err != nil {
		return "", fmt.Errorf("error reading core development compose template: %w", err)
	}

	if err := os.WriteFile(coreDevelopmentFile, defaultContent, 0644); err != nil {
		return coreDevelopmentFile, fmt.Errorf("error creating default %s file: %w", composeCoreDevFile, err)
	}
	return coreDevelopmentFile, nil
}

func (o *ComposeOrchestrator) SiteComposeFile() (string, error) {
	if err := o.enforceSiteValuesDir(); err != nil {
		return "", err
	}

	siteFile := filepath.Join(o.sitePath, "compose.yaml")
	if _, err := os.Stat(siteFile); os.IsNotExist(err) {
		defaultContent, err := embeddedFiles.ReadFile("compose.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site compose template: %w", err)
		}

		if err := os.WriteFile(siteFile, defaultContent, 0644); err != nil {
			return siteFile, fmt.Errorf("error creating default %s file: %w", "compose.yaml", err)
		}
	}
	return siteFile, nil
}

func (o *ComposeOrchestrator) Up(args []string) error {
	if err := o.defaultValuesYaml(); err != nil {
		return err
	}
	if err := o.ContainerEnvironments(); err != nil {
		return err
	}
	if err := o.startupEnvironment(); err != nil {
		return err
	}
	if err := o.setupSecrets(); err != nil {
		return err
	}
	if err := o.entityResolverConfig(); err != nil {
		return err
	}
	if err := createNetwork(); err != nil {
		return err
	}
	if err := o.createDataDirs(); err != nil {
		return err
	}

	dockerUpArgs := []string{"up", "-d", "--wait", "--remove-orphans"}

	values, err := o.getMergedValues()
	if err != nil {
		return err
	}
	if o.getValue(values, "deltafi.core_worker.enabled") == "true" && o.getValue(values, "deltafi.core_worker.replicas") != "" {
		dockerUpArgs = append(dockerUpArgs, "--scale", "core-worker="+o.getValue(values, "deltafi.core_worker.replicas"))
	}
	if o.getValue(values, "deltafi.core_actions.replicas") != "" {
		dockerUpArgs = append(dockerUpArgs, "--scale", "core-actions="+o.getValue(values, "deltafi.core_actions.replicas"))
	}

	err = o.dockerCompose(dockerUpArgs)
	if err != nil {
		return err
	}

	if o.inDevelopment() {
		err := o.ExecuteMinioCommand([]string{"mc mb -p deltafi/storage > /dev/null"})
		if err != nil {
			fmt.Println(styles.ComposeFAIL("MinIO configured for development", "Error"))
			return err
		}
		err = o.ExecuteMinioCommand([]string{"mc anonymous set public deltafi/storage > /dev/null"})
		if err != nil {
			fmt.Println(styles.ComposeFAIL("MinIO configured for development", "Error"))
			return err
		}
		fmt.Println(styles.ComposeOK("MinIO configured for development", "Ready"))
	}

	return nil
}

func (o *ComposeOrchestrator) dockerCompose(args []string) error {
	dockerComposeFile := filepath.Join(o.orchestrationPath, "compose", "docker-compose.yml")
	envFile := filepath.Join(o.configPath, "startup.env")
	dockerArgs := []string{"compose", "-f", dockerComposeFile, "--env-file", envFile}

	if o.deploymentMode == types.PluginDevelopment {
		pluginDevelopmentFile, err := o.PluginDevelopmentComposeFile()
		if err == nil {
			dockerArgs = append(dockerArgs, "-f", pluginDevelopmentFile)
		} else {
			warning := fmt.Sprintf("%s Unable to find plugin development compose file: %s", styles.WarningStyle.Render("WARNING:"), pluginDevelopmentFile)
			fmt.Println(warning)
		}
	}

	if o.deploymentMode == types.CoreDevelopment {
		coreDevelopmentFile, err := o.CoreDevelopmentComposeFile()
		if err == nil {
			dockerArgs = append(dockerArgs, "-f", coreDevelopmentFile)
		} else {
			warning := fmt.Sprintf("%s Unable to find core development compose file: %s", styles.WarningStyle.Render("WARNING:"), coreDevelopmentFile)
			fmt.Println(warning)
		}
	}

	if overridesFile, err := o.SiteComposeFile(); err == nil {
		dockerArgs = append(dockerArgs, "-f", overridesFile)
	}
	dockerArgs = append(dockerArgs, args...)
	return ShellExec("docker", o.Environment(), dockerArgs)
}

func (o *ComposeOrchestrator) Environment() []string {
	mode := "STANDALONE"
	env := os.Environ()
	env = append(env, "DELTAFI_MODE="+mode)
	env = append(env, "DELTAFI_DATA_DIR="+o.dataPath)
	env = append(env, "DELTAFI_SITE_DIR="+o.sitePath)
	env = append(env, "DELTAFI_REPOS_DIR="+o.reposPath)
	env = append(env, "DELTAFI_CONFIG_DIR="+o.configPath)
	env = append(env, "DELTAFI_SECRETS_DIR="+o.secretsPath)

	// Add USER_ID and GROUP_ID to the environment
	currentUser, err := user.Current()
	if err == nil {
		env = append(env, fmt.Sprintf("USER_ID=%s", currentUser.Uid))
		env = append(env, fmt.Sprintf("GROUP_ID=%s", currentUser.Gid))
	} else {
		fmt.Println(styles.WarningStyle.Render("Unable to set UID/GID, defaulting to 1000"))
		env = append(env, "USER_ID=1000")
		env = append(env, "GROUP_ID=1000")
	}

	// Add DOCKER_GID to the environment
	dockerGroup, err := user.LookupGroup("docker")
	if err != nil {
		env = append(env, "DOCKER_GID=0")
	} else {
		env = append(env, fmt.Sprintf("DOCKER_GID=%s", dockerGroup.Gid))
	}

	return env
}

func (o *ComposeOrchestrator) Down(args []string) error {

	err := o.dockerCompose([]string{"down"})

	cmd := exec.Command("docker", "ps", "-a", "--filter", "label=deltafi-group", "-q")
	output, err2 := cmd.Output()
	if err2 != nil {
		return fmt.Errorf("error checking docker ps: %w\n%w", err, err2)
	}
	containerIDs := strings.TrimSpace(string(output))
	if containerIDs != "" {
		fmt.Println()
		ids := strings.Split(containerIDs, "\n")
		for _, id := range ids {
			nameCmd := exec.Command("docker", "inspect", "-f", "{{slice .Name 1}}", id)
			name, err := nameCmd.Output()
			if err != nil {
				name = []byte("unknown")
			}
			container := strings.TrimSpace(string(name))
			stopCmd := exec.Command("docker", "stop", id)
			if err := stopCmd.Run(); err != nil {
				fmt.Printf("Error stopping container %s (%s): %v\n", container, id, err)
				continue
			} else {
				fmt.Printf("   [Halted] Container %s\n", container)
			}
			rmCmd := exec.Command("docker", "rm", id)
			if err := rmCmd.Run(); err != nil {
				fmt.Printf("Error removing container %s: %v\n", id, err)
			}
		}
	}
	return err
}

func createNetwork() error {
	// Check if the network exists
	cmd := exec.Command("docker", "network", "ls")
	output, err := cmd.Output()
	if err != nil {
		return fmt.Errorf("error checking docker networks: %w", err)
	}

	// Look for "deltafi" network in the output
	if !strings.Contains(string(output), "deltafi") {
		// Create the network if it doesn't exist
		createCmd := exec.Command("docker", "network", "create", networkName)
		if err := createCmd.Run(); err != nil {
			return fmt.Errorf("error creating %s network: %w", networkName, err)
		}
		fmt.Printf("Created docker network: %s\n", networkName)
	}

	return nil
}

func (o *ComposeOrchestrator) createDataDirs() error {
	if err := os.MkdirAll(o.dataPath, 0755); err != nil {
		return fmt.Errorf("error creating data directory: %w", err)
	}

	subdirs := []string{
		"postgres",
		"egress-sink",
		"entityResolver",
		"graphite",
		"grafana",
		"auth",
		"loki",
		"minio",
		"certs",
	}

	for _, subdir := range subdirs {
		fullPath := filepath.Join(o.dataPath, subdir)
		if err := os.MkdirAll(fullPath, 0755); err != nil {
			return fmt.Errorf("error creating subdirectory %s: %w", subdir, err)
		}
	}

	return nil
}

func (o *ComposeOrchestrator) defaultValuesYaml() error {
	// Create template

	content, err := embeddedFiles.ReadFile("compose.values.yaml")
	if err != nil {
		return fmt.Errorf("error reading values template: %w", err)
	}

	tmpl, err := template.New("values").Parse(string(content))

	if err != nil {
		return fmt.Errorf("error parsing values template: %w", err)
	}

	// Prepare template data
	data := valuesData{
		Tag: o.coreVersion.String(), // Default to latest if not specified
	}

	defaults.SetDefaults(&data)

	// Create values.yaml file
	if err := os.MkdirAll(o.configPath, 0755); err != nil {
		return fmt.Errorf("error creating config directory: %w", err)
	}
	valuesFile := filepath.Join(o.configPath, "values.yaml")
	file, err := os.Create(valuesFile)
	if err != nil {
		return fmt.Errorf("error creating values.yaml file: %w", err)
	}
	defer file.Close()

	// Execute template
	if err := tmpl.Execute(file, data); err != nil {
		return fmt.Errorf("error executing values template: %w", err)
	}

	return nil
}

func (o *ComposeOrchestrator) ContainerEnvironments() error {
	// Create common.env
	commonEnv := filepath.Join(o.configPath, "common.env")
	if err := o.writeCommonEnv(commonEnv); err != nil {
		return fmt.Errorf("error writing common.env: %w", err)
	}

	// Create nginx.env
	nginxEnv := filepath.Join(o.configPath, "nginx.env")
	if err := o.writeNginxEnv(nginxEnv); err != nil {
		return fmt.Errorf("error writing nginx.env: %w", err)
	}

	return nil
}

func (o *ComposeOrchestrator) getMergedValues() (map[string]interface{}, error) {
	// Read default values
	defaultValues := filepath.Join(o.configPath, "values.yaml")
	defaultData, err := os.ReadFile(defaultValues)
	if err != nil {
		return nil, fmt.Errorf("error reading default values: %w", err)
	}

	var merged map[string]interface{}
	if err := yaml.Unmarshal(defaultData, &merged); err != nil {
		return nil, fmt.Errorf("error parsing default values: %w", err)
	}

	// Read and merge site values if they exist
	siteValues, _ := o.SiteValuesFile()
	if siteData, err := os.ReadFile(siteValues); err == nil {
		var site map[string]interface{}
		if err := yaml.Unmarshal(siteData, &site); err != nil {
			return nil, fmt.Errorf("error parsing site values: %w", err)
		}
		merged = mergeMaps(merged, site)
	}

	return merged, nil
}

func mergeMaps(base, override map[string]interface{}) map[string]interface{} {
	result := make(map[string]interface{})

	// Copy base map
	for k, v := range base {
		result[k] = v
	}

	// Override with values from override map
	for k, v := range override {
		if baseVal, exists := base[k]; exists {
			if baseMap, ok := baseVal.(map[string]interface{}); ok {
				if overrideMap, ok := v.(map[string]interface{}); ok {
					result[k] = mergeMaps(baseMap, overrideMap)
					continue
				}
			}
		}
		result[k] = v
	}

	return result
}

func (o *ComposeOrchestrator) getValue(values map[string]interface{}, path string) string {
	parts := strings.Split(path, ".")
	current := values

	for i, part := range parts {
		if i == len(parts)-1 {
			if val, ok := current[part]; ok {
				return fmt.Sprintf("%v", val)
			}
			return ""
		}

		if next, ok := current[part].(map[string]interface{}); ok {
			current = next
		} else {
			return ""
		}
	}

	return ""
}

func (o *ComposeOrchestrator) inDevelopment() bool {
	return o.deploymentMode == types.PluginDevelopment || o.deploymentMode == types.CoreDevelopment
}

func (o *ComposeOrchestrator) writeCommonEnv(path string) error {
	values, err := o.getMergedValues()
	if err != nil {
		return err
	}

	hostname, err := os.Hostname()
	if err != nil {
		return fmt.Errorf("error getting hostname: %w", err)
	}

	envVars := map[string]string{
		"CORE_URL":                "http://deltafi-core:8080",
		"DELTAFI_GRAFANA_URL":     "http://deltafi-grafana:3000",
		"DELTAFI_MODE":            "STANDALONE",
		"GRAPHITE_HOST":           "deltafi-graphite",
		"GRAPHITE_PORT":           "2003",
		"INGRESS_URL":             "http://deltafi-core:8080",
		"PERIOD":                  "5",
		"METRICS_PERIOD_SECONDS":  "10",
		"MINIO_PARTSIZE":          "5242880",
		"MINIO_URL":               "http://deltafi-minio:9000",
		"RACK_ENV":                "production",
		"VALKEY_URL":              "http://deltafi-valkey:6379",
		"VALKEY_HOST":             "deltafi-valkey",
		"VALKEY_PORT":             "6379",
		"REDIS_URL":               "http://deltafi-valkey:6379",
		"REDIS_HOST":              "deltafi-valkey",
		"REDIS_PORT":              "6379",
		"RUNNING_IN_CLUSTER":      "false",
		"STATSD_HOSTNAME":         "deltafi-graphite",
		"STATSD_PORT":             "8125",
		"AUTH_MODE":               o.getValue(values, "deltafi.auth.mode"),
		"NODE_NAME":               hostname,
		"HOSTNAME":                hostname,
		"DELTAFI_UI_DOMAIN":       o.getValue(values, "ingress.domain"),
		"DOMAIN":                  o.getValue(values, "ingress.domain"),
		"CONFIG_DIR":              o.configPath,
		"DATA_DIR":                o.dataPath,
		"SITE_DIR":                o.sitePath,
		"SECRETS_DIR":             o.secretsPath,
		"ORCHESTRATION_DIR":       o.orchestrationPath,
		"DISTRO_DIR":              o.distroPath,
		"REPOS_DIR":               o.reposPath,
		"ENTITY_RESOLVER_ENABLED": o.getValue(values, "deltafi.auth.entityResolver.enabled"),
		"ENTITY_RESOLVER_URL":     fmt.Sprintf("http://deltafi-entity-resolver:%s", o.getValue(values, "deltafi.auth.entityResolver.port")),
	}

	return writeEnvFile(path, envVars)
}

func (o *ComposeOrchestrator) writeNginxEnv(path string) error {
	values, err := o.getMergedValues()
	if err != nil {
		return err
	}

	domain := o.getValue(values, "ingress.domain")
	authMode := o.getValue(values, "deltafi.auth.mode")
	tlsEnabled := o.getValue(values, "ingress.tls.enabled")

	proxyCacheKey := "no-auth"
	includeCertAuth := ""
	nginxConfDir := "http"

	if authMode == "basic" {
		proxyCacheKey = "$$remote_user$$http_authorization"
	} else if authMode == "cert" {
		if tlsEnabled != "true" {
			return fmt.Errorf("cannot run with certificate authentication without .ingress.tls.enabled set to true")
		}
		includeCertAuth = "include /opt/nginx/https/ssl_verify.conf;"
		proxyCacheKey = "$$ssl_client_s_dn$$http_authorization"
	}

	if tlsEnabled == "true" {
		nginxConfDir = "https"
	}

	envVars := map[string]string{
		"DOMAIN":            domain,
		"PROXY_CACHE_KEY":   proxyCacheKey,
		"INCLUDE_CERT_AUTH": includeCertAuth,
		"NGINX_CONF_DIR":    nginxConfDir,
	}

	return writeEnvFile(path, envVars)
}

func writeEnvFile(path string, envVars map[string]string) error {
	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("error creating env file: %w", err)
	}
	defer file.Close()

	for key, value := range envVars {
		if _, err := fmt.Fprintf(file, "%s=%s\n", key, value); err != nil {
			return fmt.Errorf("error writing env var: %w", err)
		}
	}

	return nil
}

func (o *ComposeOrchestrator) startupEnvironment() error {
	values, err := o.getMergedValues()
	if err != nil {
		return err
	}

	envVars := map[string]string{
		"SETTINGS_DIR":              filepath.Join(o.orchestrationPath, "compose", "settings"),
		"SECRETS_DIR":               o.secretsPath,
		"ENV_DIR":                   o.configPath,
		"CONFIG_DIR":                o.configPath,
		"DATA_DIR":                  o.dataPath,
		"SITE_DIR":                  o.sitePath,
		"REPOS_DIR":                 o.reposPath,
		"COMPOSE_PROJECT_NAME":      "deltafi",
		"DELTAFI_API":               o.getValue(values, "deltafi.api.image"),
		"DELTAFI_ENTITY_RESOLVER":   o.getValue(values, "deltafi.auth.entityResolver.image"),
		"DELTAFI_JAVA_DEVCONTAINER": o.getValue(values, "deltafi.java_devcontainer.image"),
		"DELTAFI_JAVA_IDE":          o.getValue(values, "deltafi.java_ide.image"),
		"DELTAFI_CORE":              o.getValue(values, "deltafi.core.image"),
		"DELTAFI_CORE_ACTIONS":      o.getValue(values, "deltafi.core_actions.image"),
		"DELTAFI_FILE_INGRESS":      o.getValue(values, "deltafi.file_ingress.image"),
		"DELTAFI_EGRESS_SINK":       o.getValue(values, "deltafi.egress_sink.image"),
		"DELTAFI_NODEMONITOR":       o.getValue(values, "deltafi.nodemonitor.image"),
		"GRAFANA":                   o.getValue(values, "dependencies.grafana"),
		"GRAPHITE":                  o.getValue(values, "dependencies.graphite"),
		"LOKI":                      o.getValue(values, "dependencies.loki"),
		"MINIO":                     o.getValue(values, "dependencies.minio"),
		"POSTGRES":                  o.getValue(values, "dependencies.postgres"),
		"NGINX":                     o.getValue(values, "dependencies.nginx"),
		"PROMTAIL":                  o.getValue(values, "dependencies.promtail"),
		"VALKEY":                    o.getValue(values, "dependencies.valkey"),
		"REDIS":                     o.getValue(values, "dependencies.valkey"),
		"DOCKER_WEB_GUI":            o.getValue(values, "dependencies.docker_web_gui"),
		"ENTITY_RESOLVER_PORT":      o.getValue(values, "deltafi.auth.entityResolver.port"),
	}

	// Add compose profiles based on enabled features
	profiles := []string{}
	if o.getValue(values, "deltafi.file_ingress.enabled") == "true" {
		profiles = append(profiles, "file-ingress")
	}
	if o.getValue(values, "deltafi.egress_sink.enabled") == "true" {
		profiles = append(profiles, "egress-sink")
	}
	if o.getValue(values, "deltafi.auth.entityResolver.enabled") == "true" {
		profiles = append(profiles, "entity-resolver")
	}
	if o.getValue(values, "deltafi.java_devcontainer.enabled") == "true" {
		profiles = append(profiles, "java-devcontainer")
	}
	if o.getValue(values, "deltafi.java_ide.enabled") == "true" {
		profiles = append(profiles, "java-ide")
	}
	if o.getValue(values, "deltafi.core_worker.enabled") == "true" {
		profiles = append(profiles, "worker")
	}

	if len(profiles) > 0 {
		envVars["COMPOSE_PROFILES"] = strings.Join(profiles, ",")
	}

	startupEnv := filepath.Join(o.configPath, "startup.env")
	return writeEnvFile(startupEnv, envVars)
}

func (o *ComposeOrchestrator) setupSecrets() error {
	secretsDir := filepath.Join(o.configPath, "secrets")
	if err := os.MkdirAll(secretsDir, 0755); err != nil {
		return fmt.Errorf("error creating secrets directory: %w", err)
	}

	// Setup Grafana secrets
	grafanaPath := filepath.Join(secretsDir, "grafana.env")
	if _, err := os.Stat(grafanaPath); os.IsNotExist(err) {
		grafanaPassword := randomPassword(16)
		grafanaContent := fmt.Sprintf("GF_SECURITY_ADMIN_USER='admin'\nGF_SECURITY_ADMIN_PASSWORD='%s'\n", grafanaPassword)
		if err := os.WriteFile(grafanaPath, []byte(grafanaContent), 0600); err != nil {
			return fmt.Errorf("error writing grafana secrets: %w", err)
		}
	}

	// Setup Minio secrets
	minioPath := filepath.Join(secretsDir, "minio.env")
	if _, err := os.Stat(minioPath); os.IsNotExist(err) {
		minioPassword := randomPassword(40)
		minioContent := fmt.Sprintf("MINIO_ROOT_USER='deltafi'\nMINIO_ROOT_PASSWORD='%s'\nMINIO_ACCESSKEY='deltafi'\nMINIO_SECRETKEY='%s'\n", minioPassword, minioPassword)
		if err := os.WriteFile(minioPath, []byte(minioContent), 0600); err != nil {
			return fmt.Errorf("error writing minio secrets: %w", err)
		}
	}

	// Setup Postgres secrets
	postgresPath := filepath.Join(secretsDir, "postgres.env")
	if _, err := os.Stat(postgresPath); os.IsNotExist(err) {
		postgresPassword := randomPassword(20)
		postgresContent := fmt.Sprintf("POSTGRES_USER='postgres'\nPOSTGRES_PASSWORD='%s'\nPOSTGRES_DB='postgres'\nPGUSER='postgres'\n", postgresPassword)
		if err := os.WriteFile(postgresPath, []byte(postgresContent), 0600); err != nil {
			return fmt.Errorf("error writing postgres secrets: %w", err)
		}
	}

	// Setup Valkey secrets
	valkeyPath := filepath.Join(secretsDir, "valkey.env")
	if _, err := os.Stat(valkeyPath); os.IsNotExist(err) {
		valkeyPassword := randomPassword(16)
		valkeyContent := fmt.Sprintf("REDIS_PASSWORD='%s'\nVALKEY_PASSWORD='%s'\n", valkeyPassword, valkeyPassword)
		if err := os.WriteFile(valkeyPath, []byte(valkeyContent), 0600); err != nil {
			return fmt.Errorf("error writing valkey secrets: %w", err)
		}
	}

	// Setup SSL secrets
	sslPath := filepath.Join(secretsDir, "ssl.env")
	if _, err := os.Stat(sslPath); os.IsNotExist(err) {
		if err := os.WriteFile(sslPath, []byte{}, 0600); err != nil {
			return fmt.Errorf("error creating ssl secrets file: %w", err)
		}
	}

	return nil
}

func randomPassword(length int) string {
	const charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, length)
	for i := range b {
		b[i] = charset[rand.Intn(len(charset))]
	}
	return string(b)
}

func (o *ComposeOrchestrator) entityResolverConfig() error {
	values, err := o.getMergedValues()
	if err != nil {
		return err
	}

	// Recreate the entityResolver directory
	entityResolverDir := filepath.Join(o.dataPath, "entityResolver")
	if err := os.RemoveAll(entityResolverDir); err != nil {
		return fmt.Errorf("error removing entityResolver directory: %w", err)
	}
	if err := os.MkdirAll(entityResolverDir, 0755); err != nil {
		return fmt.Errorf("error creating entityResolver directory: %w", err)
	}

	// Get the config map from values
	configPath := "deltafi.auth.entityResolver.config"
	configValue := values
	for _, part := range strings.Split(configPath, ".") {
		if next, ok := configValue[part].(map[string]interface{}); ok {
			configValue = next
		} else {
			// No config or invalid config, return without error
			return nil
		}
	}

	// Write each config file
	for filename, content := range configValue {
		filePath := filepath.Join(entityResolverDir, filename)
		var contentBytes []byte

		switch v := content.(type) {
		case string:
			contentBytes = []byte(v)
		case map[string]interface{}, []interface{}:
			// Create a new YAML node
			var node yaml.Node
			if err := node.Encode(v); err != nil {
				return fmt.Errorf("error encoding config content for %s: %w", filename, err)
			}
			// Create a buffer and encoder with 2-space indentation
			buf := new(bytes.Buffer)
			encoder := yaml.NewEncoder(buf)
			encoder.SetIndent(2)
			if err := encoder.Encode(&node); err != nil {
				return fmt.Errorf("error encoding config content for %s: %w", filename, err)
			}
			contentBytes = buf.Bytes()
		default:
			contentBytes = []byte(fmt.Sprintf("%v", v))
		}

		if err := os.WriteFile(filePath, contentBytes, 0644); err != nil {
			return fmt.Errorf("error writing entity resolver config file %s: %w", filename, err)
		}
	}

	return nil
}

func (o *ComposeOrchestrator) ExecuteMinioCommand(cmd []string) error {
	return execMinioCommand(o, cmd)
}
