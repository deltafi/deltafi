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
package types

import (
	"fmt"
	"strings"
)

type DeploymentMode int

const (
	Deployment DeploymentMode = iota
	PluginDevelopment
	CoreDevelopment
	unknownDeploymentMode
)

var deploymentModeNames []string
var deploymentModeDisplayNames []string
var deploymentModeDescriptions []string
var deploymentModeNameToValue map[string]DeploymentMode

func init() {
	deploymentModeNames = []string{
		"Deployment",
		"PluginDevelopment",
		"CoreDevelopment",
	}
	deploymentModeDisplayNames = []string{
		"Deployment",
		"Plugin Development",
		"Core Development",
	}
	deploymentModeDescriptions = []string{
		"Deployment mode should be selected for production and test systems. " +
			"  Other modes should only be selected if you intend to use this system for local core or plugin development.",
		"Plugin Development mode configures your environment for local plugin development.",
		"Core Development mode enables local development of the DeltaFi core services and plugins.",
	}
	deploymentModeNameToValue = make(map[string]DeploymentMode)
	for i, name := range deploymentModeNames {
		deploymentModeNameToValue[strings.ToLower(name)] = DeploymentMode(i)
	}
}

func (e DeploymentMode) String() string {
	if e < 0 || int(e) >= len(deploymentModeNames) {
		panic(fmt.Errorf("invalid deployment mode code: %d", e))
	}
	return deploymentModeNames[e]
}

func (e DeploymentMode) DisplayName() string {
	if e < 0 || int(e) >= len(deploymentModeDisplayNames) {
		panic(fmt.Errorf("invalid deployment mode code: %d", e))
	}
	return deploymentModeDisplayNames[e]
}

func (e DeploymentMode) Description() string {
	if e < 0 || int(e) >= len(deploymentModeDescriptions) {
		panic(fmt.Errorf("invalid deployment mode code: %d", e))
	}
	return deploymentModeDescriptions[e]
}

func ParseDeploymentMode(text string) (DeploymentMode, error) {
	i, ok := deploymentModeNameToValue[strings.ToLower(text)]
	if !ok {
		return unknownDeploymentMode, fmt.Errorf("invalid deployment mode: %s", text)
	}
	return i, nil
}

func (e DeploymentMode) MarshalText() ([]byte, error) {
	return []byte(e.String()), nil
}

func (e *DeploymentMode) UnmarshalText(text []byte) (err error) {
	name := string(text)
	*e, err = ParseDeploymentMode(name)
	return
}
