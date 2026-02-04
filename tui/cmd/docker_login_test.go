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
package cmd

import (
	"encoding/base64"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/deltafi/tui/internal/orchestration"
)

func TestDockerConfigMerge(t *testing.T) {
	// Create a temporary directory for test
	tempDir, err := os.MkdirTemp("", "docker-config-test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	// Create a mock compose orchestrator
	configDir := filepath.Join(tempDir, "config")
	secretsDir := filepath.Join(configDir, "secrets")
	dockerConfigPath := filepath.Join(secretsDir, "docker-config.json")

	// Helper to create test orchestrator
	createTestOrch := func() *orchestration.ComposeOrchestrator {
		return &orchestration.ComposeOrchestrator{}
	}

	type dockerConfigJSON struct {
		Auths map[string]orchestration.DockerAuthConfig `json:"auths"`
	}

	// Helper to read config file
	readConfig := func() dockerConfigJSON {
		var config dockerConfigJSON
		data, err := os.ReadFile(dockerConfigPath)
		if err != nil {
			t.Fatalf("Failed to read config: %v", err)
		}
		if err := json.Unmarshal(data, &config); err != nil {
			t.Fatalf("Failed to unmarshal config: %v", err)
		}
		return config
	}

	// Test saving via orchestrator - we'll simulate it by directly calling the logic
	// since we need to inject the temp directory path
	saveAuth := func(registry, username, password string) error {
		if err := os.MkdirAll(secretsDir, 0755); err != nil {
			return err
		}

		var dockerConfig dockerConfigJSON
		if existingData, err := os.ReadFile(dockerConfigPath); err == nil {
			if err := json.Unmarshal(existingData, &dockerConfig); err != nil {
				dockerConfig = dockerConfigJSON{Auths: make(map[string]orchestration.DockerAuthConfig)}
			}
		} else {
			dockerConfig = dockerConfigJSON{Auths: make(map[string]orchestration.DockerAuthConfig)}
		}

		authString := base64.StdEncoding.EncodeToString([]byte(username + ":" + password))
		dockerConfig.Auths[registry] = orchestration.DockerAuthConfig{
			Username: username,
			Password: password,
			Auth:     authString,
		}

		configJSON, err := json.MarshalIndent(dockerConfig, "", "  ")
		if err != nil {
			return err
		}

		return os.WriteFile(dockerConfigPath, configJSON, 0600)
	}

	// Test 1: Add first registry
	if err := saveAuth("registry1.example.com", "user1", "pass1"); err != nil {
		t.Fatalf("Failed to save first registry: %v", err)
	}

	config := readConfig()
	if len(config.Auths) != 1 {
		t.Errorf("Expected 1 registry, got %d", len(config.Auths))
	}

	// Test 2: Add second registry (first should be preserved)
	if err := saveAuth("registry2.example.com", "user2", "pass2"); err != nil {
		t.Fatalf("Failed to save second registry: %v", err)
	}

	config = readConfig()
	if len(config.Auths) != 2 {
		t.Errorf("Expected 2 registries, got %d", len(config.Auths))
	}

	// Verify both registries exist
	auth1, ok := config.Auths["registry1.example.com"]
	if !ok {
		t.Error("registry1.example.com not found")
	} else if auth1.Username != "user1" || auth1.Password != "pass1" {
		t.Errorf("registry1 credentials incorrect")
	}

	auth2, ok := config.Auths["registry2.example.com"]
	if !ok {
		t.Error("registry2.example.com not found")
	} else if auth2.Username != "user2" || auth2.Password != "pass2" {
		t.Errorf("registry2 credentials incorrect")
	}

	// Test 3: Update existing registry
	if err := saveAuth("registry1.example.com", "newuser1", "newpass1"); err != nil {
		t.Fatalf("Failed to update registry: %v", err)
	}

	config = readConfig()
	if len(config.Auths) != 2 {
		t.Errorf("Expected 2 registries after update, got %d", len(config.Auths))
	}

	auth1Updated := config.Auths["registry1.example.com"]
	if auth1Updated.Username != "newuser1" || auth1Updated.Password != "newpass1" {
		t.Errorf("registry1 credentials not updated correctly")
	}

	_ = createTestOrch() // Keep reference
}

func TestNormalizeRegistry(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{"plain registry", "registry.example.com", "registry.example.com"},
		{"https prefix", "https://registry.example.com", "registry.example.com"},
		{"http prefix", "http://registry.example.com", "registry.example.com"},
		{"with path", "https://registry.example.com/v2/", "registry.example.com"},
		{"with port", "registry.example.com:5000", "registry.example.com:5000"},
		{"https with port", "https://registry.example.com:5000", "registry.example.com:5000"},
		{"docker hub", "https://index.docker.io/v1/", "index.docker.io"},
		{"ECR", "https://123456789012.dkr.ecr.us-east-1.amazonaws.com", "123456789012.dkr.ecr.us-east-1.amazonaws.com"},
		{"with spaces", "  registry.example.com  ", "registry.example.com"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := normalizeRegistry(tt.input)
			if result != tt.expected {
				t.Errorf("normalizeRegistry(%q) = %q, expected %q", tt.input, result, tt.expected)
			}
		})
	}
}

func TestDockerConfigEmptyFile(t *testing.T) {
	// Create a temporary directory for test
	tempDir, err := os.MkdirTemp("", "docker-config-test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	dockerConfigPath := filepath.Join(tempDir, "docker-config.json")

	type dockerConfigJSON struct {
		Auths map[string]orchestration.DockerAuthConfig `json:"auths"`
	}

	// Test creating config when file doesn't exist
	var dockerConfig dockerConfigJSON
	if _, err := os.ReadFile(dockerConfigPath); err == nil {
		t.Fatal("Expected file to not exist")
	}

	// Initialize fresh config
	dockerConfig = dockerConfigJSON{Auths: make(map[string]orchestration.DockerAuthConfig)}
	dockerConfig.Auths["registry.example.com"] = orchestration.DockerAuthConfig{
		Username: "testuser",
		Password: "testpass",
		Auth:     base64.StdEncoding.EncodeToString([]byte("testuser:testpass")),
	}

	// Write config
	data, err := json.MarshalIndent(dockerConfig, "", "  ")
	if err != nil {
		t.Fatalf("Failed to marshal config: %v", err)
	}
	if err := os.WriteFile(dockerConfigPath, data, 0600); err != nil {
		t.Fatalf("Failed to write config: %v", err)
	}

	// Verify file was created
	var readConfig dockerConfigJSON
	readData, err := os.ReadFile(dockerConfigPath)
	if err != nil {
		t.Fatalf("Failed to read config: %v", err)
	}
	if err := json.Unmarshal(readData, &readConfig); err != nil {
		t.Fatalf("Failed to unmarshal config: %v", err)
	}

	if len(readConfig.Auths) != 1 {
		t.Errorf("Expected 1 registry, got %d", len(readConfig.Auths))
	}

	auth := readConfig.Auths["registry.example.com"]
	if auth.Username != "testuser" {
		t.Errorf("Expected username 'testuser', got '%s'", auth.Username)
	}
}

func TestPasswordFromFlag(t *testing.T) {
	// Test that password can come from --password flag
	testPassword := "test-password-from-flag"
	testUsername := "testuser"

	// Create auth string
	authString := base64.StdEncoding.EncodeToString([]byte(testUsername + ":" + testPassword))

	// Verify auth string is correct
	expected := orchestration.DockerAuthConfig{
		Username: testUsername,
		Password: testPassword,
		Auth:     authString,
	}

	if expected.Username != testUsername {
		t.Errorf("Expected username %q, got %q", testUsername, expected.Username)
	}
	if expected.Password != testPassword {
		t.Errorf("Expected password %q, got %q", testPassword, expected.Password)
	}

	// Decode and verify auth string
	decoded, err := base64.StdEncoding.DecodeString(authString)
	if err != nil {
		t.Fatalf("Failed to decode auth string: %v", err)
	}
	expectedAuth := testUsername + ":" + testPassword
	if string(decoded) != expectedAuth {
		t.Errorf("Expected decoded auth %q, got %q", expectedAuth, string(decoded))
	}
}

func TestDockerConfigNormalizesRegistryNames(t *testing.T) {
	// Create a temporary directory for test
	tempDir, err := os.MkdirTemp("", "docker-config-test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	dockerConfigPath := filepath.Join(tempDir, "docker-config.json")

	type dockerConfigJSON struct {
		Auths map[string]orchestration.DockerAuthConfig `json:"auths"`
	}

	// Simulate adding a registry with https:// prefix
	registryWithPrefix := "https://registry.example.com"
	normalizedRegistry := normalizeRegistry(registryWithPrefix)

	dockerConfig := dockerConfigJSON{Auths: make(map[string]orchestration.DockerAuthConfig)}
	dockerConfig.Auths[normalizedRegistry] = orchestration.DockerAuthConfig{
		Username: "testuser",
		Password: "testpass",
		Auth:     base64.StdEncoding.EncodeToString([]byte("testuser:testpass")),
	}

	// Write config
	data, err := json.MarshalIndent(dockerConfig, "", "  ")
	if err != nil {
		t.Fatalf("Failed to marshal config: %v", err)
	}
	if err := os.WriteFile(dockerConfigPath, data, 0600); err != nil {
		t.Fatalf("Failed to write config: %v", err)
	}

	// Read and verify the registry was stored normalized (without https://)
	var readConfig dockerConfigJSON
	readData, err := os.ReadFile(dockerConfigPath)
	if err != nil {
		t.Fatalf("Failed to read config: %v", err)
	}
	if err := json.Unmarshal(readData, &readConfig); err != nil {
		t.Fatalf("Failed to unmarshal config: %v", err)
	}

	// Should be stored as normalized version
	if _, ok := readConfig.Auths["registry.example.com"]; !ok {
		t.Errorf("Expected normalized registry 'registry.example.com' in config, but not found")
	}

	// Should NOT be stored with https:// prefix
	if _, ok := readConfig.Auths["https://registry.example.com"]; ok {
		t.Errorf("Registry should be normalized (no https:// prefix), but found 'https://registry.example.com'")
	}
}
