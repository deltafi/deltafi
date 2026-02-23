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

// ABOUTME: Tests for SSL configuration parsing and secret name extraction.
// ABOUTME: Covers YAML parsing and Config accessor methods.
package orchestration

import (
	"os"
	"path/filepath"
	"testing"
)

func TestParseConfigFromYAML(t *testing.T) {
	t.Run("valid config", func(t *testing.T) {
		tmpDir := t.TempDir()
		configFile := filepath.Join(tmpDir, "config.yaml")

		yamlContent := `
deltafi:
  core:
    ssl:
      secret: core-ssl-secret
  auth:
    secret: auth-secret
    entityResolver:
      ssl:
        secret: entity-resolver-ssl-secret
  plugins:
    ssl:
      secret: plugins-ssl-secret
ingress:
  tls:
    secrets:
      default: default-tls-secret
`
		err := os.WriteFile(configFile, []byte(yamlContent), 0644)
		if err != nil {
			t.Fatalf("failed to write config file: %v", err)
		}

		config, err := parseConfigFromYAML(configFile)
		if err != nil {
			t.Fatalf("parseConfigFromYAML() error: %v", err)
		}

		if config == nil {
			t.Fatal("parseConfigFromYAML() returned nil config")
		}

		// Verify parsed values
		if config.GetDeltafiCoreSSLSecret() != "core-ssl-secret" {
			t.Errorf("GetDeltafiCoreSSLSecret() = %q, want 'core-ssl-secret'",
				config.GetDeltafiCoreSSLSecret())
		}
		if config.GetDeltafiAuthSecret() != "auth-secret" {
			t.Errorf("GetDeltafiAuthSecret() = %q, want 'auth-secret'",
				config.GetDeltafiAuthSecret())
		}
		if config.GetDeltafiAuthEntityResolverSSLSecret() != "entity-resolver-ssl-secret" {
			t.Errorf("GetDeltafiAuthEntityResolverSSLSecret() = %q, want 'entity-resolver-ssl-secret'",
				config.GetDeltafiAuthEntityResolverSSLSecret())
		}
		if config.GetDeltafiPluginsSSLSecret() != "plugins-ssl-secret" {
			t.Errorf("GetDeltafiPluginsSSLSecret() = %q, want 'plugins-ssl-secret'",
				config.GetDeltafiPluginsSSLSecret())
		}
		if config.GetIngressTLSDefaultSecret() != "default-tls-secret" {
			t.Errorf("GetIngressTLSDefaultSecret() = %q, want 'default-tls-secret'",
				config.GetIngressTLSDefaultSecret())
		}
	})

	t.Run("file not found", func(t *testing.T) {
		_, err := parseConfigFromYAML("/nonexistent/path/config.yaml")
		if err == nil {
			t.Error("expected error for nonexistent file, got nil")
		}
	})

	t.Run("invalid yaml", func(t *testing.T) {
		tmpDir := t.TempDir()
		configFile := filepath.Join(tmpDir, "invalid.yaml")

		invalidContent := `
deltafi:
  core:
    ssl:
      secret: [invalid yaml structure
`
		err := os.WriteFile(configFile, []byte(invalidContent), 0644)
		if err != nil {
			t.Fatalf("failed to write config file: %v", err)
		}

		_, err = parseConfigFromYAML(configFile)
		if err == nil {
			t.Error("expected error for invalid YAML, got nil")
		}
	})

	t.Run("empty file", func(t *testing.T) {
		tmpDir := t.TempDir()
		configFile := filepath.Join(tmpDir, "empty.yaml")

		err := os.WriteFile(configFile, []byte(""), 0644)
		if err != nil {
			t.Fatalf("failed to write config file: %v", err)
		}

		config, err := parseConfigFromYAML(configFile)
		if err != nil {
			t.Fatalf("parseConfigFromYAML() error for empty file: %v", err)
		}

		// Empty config should have empty strings for all secrets
		if config.GetDeltafiCoreSSLSecret() != "" {
			t.Errorf("expected empty string for core SSL secret, got %q",
				config.GetDeltafiCoreSSLSecret())
		}
	})

	t.Run("partial config", func(t *testing.T) {
		tmpDir := t.TempDir()
		configFile := filepath.Join(tmpDir, "partial.yaml")

		yamlContent := `
deltafi:
  core:
    ssl:
      secret: only-core-secret
`
		err := os.WriteFile(configFile, []byte(yamlContent), 0644)
		if err != nil {
			t.Fatalf("failed to write config file: %v", err)
		}

		config, err := parseConfigFromYAML(configFile)
		if err != nil {
			t.Fatalf("parseConfigFromYAML() error: %v", err)
		}

		if config.GetDeltafiCoreSSLSecret() != "only-core-secret" {
			t.Errorf("GetDeltafiCoreSSLSecret() = %q, want 'only-core-secret'",
				config.GetDeltafiCoreSSLSecret())
		}

		// Other fields should be empty
		if config.GetDeltafiAuthSecret() != "" {
			t.Errorf("expected empty auth secret, got %q", config.GetDeltafiAuthSecret())
		}
	})
}

func TestConfig_GetIngressTLSDefaultEntityResolverSSLSecret(t *testing.T) {
	config := &Config{}
	config.Deltafi.Core.SSL.Secret = "core-secret"
	config.Deltafi.Auth.EntityResolver.SSL.Secret = "resolver-secret"
	config.Deltafi.Plugins.SSL.Secret = "plugins-secret"
	config.Ingress.TLS.Secrets.Default = "ingress-secret"

	names, err := config.GetIngressTLSDefaultEntityResolverSSLSecret()
	if err != nil {
		t.Fatalf("GetIngressTLSDefaultEntityResolverSSLSecret() error: %v", err)
	}

	expectedNames := []string{
		"ingress-secret",
		"core-secret",
		"plugins-secret",
		"resolver-secret",
	}

	if len(names) != len(expectedNames) {
		t.Fatalf("expected %d names, got %d", len(expectedNames), len(names))
	}

	for i, expected := range expectedNames {
		if names[i] != expected {
			t.Errorf("names[%d] = %q, want %q", i, names[i], expected)
		}
	}
}

func TestConfig_Getters_EmptyConfig(t *testing.T) {
	config := &Config{}

	tests := []struct {
		name   string
		getter func() string
	}{
		{"GetDeltafiCoreSSLSecret", config.GetDeltafiCoreSSLSecret},
		{"GetDeltafiAuthSecret", config.GetDeltafiAuthSecret},
		{"GetDeltafiAuthEntityResolverSSLSecret", config.GetDeltafiAuthEntityResolverSSLSecret},
		{"GetDeltafiPluginsSSLSecret", config.GetDeltafiPluginsSSLSecret},
		{"GetIngressTLSDefaultSecret", config.GetIngressTLSDefaultSecret},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := tt.getter()
			if result != "" {
				t.Errorf("%s() on empty config = %q, want empty string", tt.name, result)
			}
		})
	}
}

func TestSslInput_Fields(t *testing.T) {
	input := SslInput{
		KeyFile:       "/path/to/key.pem",
		CertFile:      "/path/to/cert.pem",
		SslProtocol:   "TLSv1.2",
		KeyPassphrase: "secret123",
		SecretName:    "my-ssl-secret",
	}

	if input.KeyFile != "/path/to/key.pem" {
		t.Errorf("KeyFile = %q, want '/path/to/key.pem'", input.KeyFile)
	}
	if input.CertFile != "/path/to/cert.pem" {
		t.Errorf("CertFile = %q, want '/path/to/cert.pem'", input.CertFile)
	}
	if input.SslProtocol != "TLSv1.2" {
		t.Errorf("SslProtocol = %q, want 'TLSv1.2'", input.SslProtocol)
	}
	if input.KeyPassphrase != "secret123" {
		t.Errorf("KeyPassphrase = %q, want 'secret123'", input.KeyPassphrase)
	}
	if input.SecretName != "my-ssl-secret" {
		t.Errorf("SecretName = %q, want 'my-ssl-secret'", input.SecretName)
	}
}

func TestSslOutput_Fields(t *testing.T) {
	output := SslOutput{
		SecretName: "my-secret",
		Key:        "-----BEGIN PRIVATE KEY-----\n...",
		Cert:       "-----BEGIN CERTIFICATE-----\n...",
	}

	if output.SecretName != "my-secret" {
		t.Errorf("SecretName = %q, want 'my-secret'", output.SecretName)
	}
	if output.Key != "-----BEGIN PRIVATE KEY-----\n..." {
		t.Errorf("Key mismatch")
	}
	if output.Cert != "-----BEGIN CERTIFICATE-----\n..." {
		t.Errorf("Cert mismatch")
	}
}

func BenchmarkParseConfigFromYAML(b *testing.B) {
	tmpDir := b.TempDir()
	configFile := filepath.Join(tmpDir, "config.yaml")

	yamlContent := `
deltafi:
  core:
    ssl:
      secret: core-ssl-secret
  auth:
    secret: auth-secret
    entityResolver:
      ssl:
        secret: entity-resolver-ssl-secret
  plugins:
    ssl:
      secret: plugins-ssl-secret
ingress:
  tls:
    secrets:
      default: default-tls-secret
`
	err := os.WriteFile(configFile, []byte(yamlContent), 0644)
	if err != nil {
		b.Fatalf("failed to write config file: %v", err)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		parseConfigFromYAML(configFile)
	}
}
