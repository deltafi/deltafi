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
	"net/http"

	"github.com/deltafi/tui/internal/app/auth"
)

type Auth struct {
	Basic *auth.BasicAuth `yaml:"basic,omitempty"`
	Cert  *auth.CertAuth  `yaml:"cert,omitempty"`
}

func (a *Auth) SetupClient(client *http.Client) error {
	if a.Basic != nil && a.Cert != nil {
		return fmt.Errorf("basic auth config and cert auth config cannot both be set")
	}

	if a.Basic != nil {
		return a.Basic.SetupClient(client)
	} else if a.Cert != nil {
		return a.Cert.SetupClient(client)
	} else {
		return auth.DisableAuthSetup(client)
	}
}
