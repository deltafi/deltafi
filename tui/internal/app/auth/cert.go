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
package auth

import (
	"crypto/tls"
	"fmt"
	"net/http"
)

type CertAuth struct {
	CertPath string
	KeyPath  string
}

func (c *CertAuth) SetupClient(client *http.Client) error {
	cert, err := tls.LoadX509KeyPair(c.CertPath, c.KeyPath)
	if err != nil {
		return fmt.Errorf("failed to load certificate: %w", err)
	}

	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{cert},
	}

	originalTransport := client.Transport
	if originalTransport == nil {
		originalTransport = http.DefaultTransport
	}

	if transport, ok := originalTransport.(*http.Transport); ok {
		transport = transport.Clone()
		transport.TLSClientConfig = tlsConfig
		client.Transport = transport
	} else {
		client.Transport = &http.Transport{
			TLSClientConfig: tlsConfig,
		}
	}
	return nil
}
