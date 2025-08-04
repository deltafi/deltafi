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
	"gopkg.in/yaml.v3"
	"os"
)

type Config struct {
	Deltafi struct {
		Core struct {
			SSL struct {
				Secret string `yaml:"secret"`
			} `yaml:"ssl"`
		} `yaml:"core"`
		Auth struct {
			Secret         string `yaml:"secret"`
			EntityResolver struct {
				SSL struct {
					Secret string `yaml:"secret"`
				} `yaml:"ssl"`
			} `yaml:"entityResolver"`
		} `yaml:"auth"`
		Plugins struct {
			SSL struct {
				Secret string `yaml:"secret"`
			} `yaml:"ssl"`
		} `yaml:"plugins"`
	} `yaml:"deltafi"`
	Ingress struct {
		TLS struct {
			Secrets struct {
				Default string `yaml:"default"`
			} `yaml:"secrets"`
		} `yaml:"tls"`
	} `yaml:"ingress"`
}

type SslInput struct {
	KeyFile       string
	CertFile      string
	SslProtocol   string
	KeyPassphrase string
	SecretName    string
}

type SslOutput struct {
	SecretName string
	Key        string
	Cert       string
}

type SslManagement interface {
	GetKeyCert(string) (*SslOutput, error)
	SaveKeyCert(SslInput) (*SslOutput, error)
	DeleteKeyCert(string) (*SslOutput, error)
	AppendToCaChain(string) (string, error)
	SaveCaChain(string) (string, error)
	GetCaChain() (string, error)
	GetSecretNames() ([]string, error)
}

func parseConfigFromYAML(filename string) (*Config, error) {
	data, err := os.ReadFile(filename)
	if err != nil {
		return nil, err
	}

	var config Config
	if err := yaml.Unmarshal(data, &config); err != nil {
		return nil, err
	}

	return &config, nil
}

func (c *Config) GetDeltafiCoreSSLSecret() string {
	return c.Deltafi.Core.SSL.Secret
}

func (c *Config) GetDeltafiAuthSecret() string {
	return c.Deltafi.Auth.Secret
}

func (c *Config) GetDeltafiAuthEntityResolverSSLSecret() string {
	return c.Deltafi.Auth.EntityResolver.SSL.Secret
}

func (c *Config) GetDeltafiPluginsSSLSecret() string {
	return c.Deltafi.Plugins.SSL.Secret
}

func (c *Config) GetIngressTLSDefaultSecret() string {
	return c.Ingress.TLS.Secrets.Default
}

func (c *Config) GetIngressTLSDefaultEntityResolverSSLSecret() ([]string, error) {
	var names []string
	names = append(names, c.GetIngressTLSDefaultSecret())
	names = append(names, c.GetDeltafiCoreSSLSecret())
	names = append(names, c.GetDeltafiPluginsSSLSecret())
	names = append(names, c.GetDeltafiAuthEntityResolverSSLSecret())
	return names, nil
}
