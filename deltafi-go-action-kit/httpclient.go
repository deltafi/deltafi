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
// ABOUTME: Platform-level HTTP client with TLS from /certs/ and per-action proxy configuration.
// ABOUTME: TLS is configured once at startup; actions embed HTTPClientParams for proxy settings.

package actionkit

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"
)

// Default cert paths matching the Java SslAutoConfiguration.
var (
	defaultKeyPath     = envOrDefault("KEY_PATH", "/certs/tls.key")
	defaultCertPath    = envOrDefault("CERT_PATH", "/certs/tls.crt")
	defaultCAChainPath = envOrDefault("CA_CHAIN_PATH", "/certs/ca.crt")
)

const (
	platformResponseHeaderTimeout = 60 * time.Second
	platformMaxIdleConnsPerHost   = 100
)

// platformClient is the shared HTTP client configured at startup with
// platform-level TLS. Actions use this as the base, adding per-action
// proxy configuration on top.
var platformClient *http.Client

// platformTransport is the base transport with TLS configured. Cloned
// per-action when proxy settings differ.
var platformTransport *http.Transport

func init() {
	configureStructuredLogging()
	transport, err := buildPlatformTransport()
	if err != nil {
		log.Fatalf("Failed to configure platform TLS: %v", err)
	}
	platformTransport = transport
	platformClient = &http.Client{Transport: transport}
}

// buildPlatformTransport creates a transport with TLS configured from the
// cert files mounted at the default paths. If no cert files are found,
// returns a default transport.
func buildPlatformTransport() (*http.Transport, error) {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.ResponseHeaderTimeout = platformResponseHeaderTimeout
	transport.MaxIdleConnsPerHost = platformMaxIdleConnsPerHost

	keyExists := fileExists(defaultKeyPath)
	certExists := fileExists(defaultCertPath)

	if keyExists != certExists {
		return nil, fmt.Errorf("found one of key/cert but not both: key=%s(%v) cert=%s(%v)",
			defaultKeyPath, keyExists, defaultCertPath, certExists)
	}

	if !keyExists {
		log.Printf("Skipping SSL setup - key and cert files were not found at %s and %s", defaultKeyPath, defaultCertPath)
		return transport, nil
	}

	log.Printf("Configuring TLS from key=%s cert=%s", defaultKeyPath, defaultCertPath)

	tlsConfig := &tls.Config{}

	cert, err := tls.LoadX509KeyPair(defaultCertPath, defaultKeyPath)
	if err != nil {
		return nil, fmt.Errorf("failed to load client cert/key: %w", err)
	}
	tlsConfig.Certificates = []tls.Certificate{cert}

	if fileExists(defaultCAChainPath) {
		log.Printf("Configuring TLS truststore from CA chain=%s", defaultCAChainPath)
		caCert, err := os.ReadFile(defaultCAChainPath)
		if err != nil {
			return nil, fmt.Errorf("failed to read CA chain: %w", err)
		}
		caCertPool := x509.NewCertPool()
		if !caCertPool.AppendCertsFromPEM(caCert) {
			return nil, fmt.Errorf("no valid CA certificates found in %s", defaultCAChainPath)
		}
		tlsConfig.RootCAs = caCertPool
	}

	transport.TLSClientConfig = tlsConfig
	return transport, nil
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

// HTTPClientParams provides per-action proxy configuration.
// Embed this struct in egress action parameters to inherit these fields.
type HTTPClientParams struct {
	ProxyURL      string `json:"proxyUrl,omitempty" description:"HTTP proxy URL (e.g. http://proxy.example.com:8080)"`
	ProxyUsername string `json:"proxyUsername,omitempty" description:"Username for proxy authentication"`
	ProxyPassword EnvVar `json:"proxyPassword,omitempty" description:"Environment variable containing the proxy password"`
}

// proxyClientCache caches HTTP clients by resolved proxy URL so repeated
// calls to BuildClient reuse the same transport and connection pool.
var proxyClientCache sync.Map // string -> *http.Client

// BuildClient returns an *http.Client using the platform TLS configuration
// with optional per-action proxy settings. Clients are cached per proxy URL
// so connection pools are reused across invocations.
func (p *HTTPClientParams) BuildClient() (*http.Client, error) {
	if p.ProxyURL == "" {
		return platformClient, nil
	}

	proxyURL, err := url.Parse(p.ProxyURL)
	if err != nil {
		return nil, err
	}

	if p.ProxyUsername != "" && p.ProxyPassword != "" {
		password, err := p.ProxyPassword.Resolve()
		if err != nil {
			return nil, err
		}
		proxyURL.User = url.UserPassword(p.ProxyUsername, password)
	}

	cacheKey := proxyURL.String()
	if cached, ok := proxyClientCache.Load(cacheKey); ok {
		return cached.(*http.Client), nil
	}

	transport := platformTransport.Clone()
	transport.Proxy = http.ProxyURL(proxyURL)
	client := &http.Client{Transport: transport}

	actual, _ := proxyClientCache.LoadOrStore(cacheKey, client)
	return actual.(*http.Client), nil
}
