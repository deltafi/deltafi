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
// ABOUTME: Action type constants and the ArtifactAware optional interface.
// ABOUTME: Action method detection is handled by reflection in dispatch.go.

package actionkit

// Action type constants used in registration and event payloads.
const (
	ActionTypeTransform    = "TRANSFORM"
	ActionTypeEgress       = "EGRESS"
	ActionTypeTimedIngress = "TIMED_INGRESS"
)

// ArtifactAware is optionally implemented by actions that need to register
// artifact version change callbacks. Detected via reflection at startup;
// called once before workers begin processing.
type ArtifactAware interface {
	ConfigureArtifacts(client *ArtifactClient)
}

// registeredAction pairs an action with its name, detected type, and metadata.
type registeredAction struct {
	name          string
	action        any
	actionType    string
	description   string
	paramDefaults any
	options       *ActionOptions
	dispatcher    *actionDispatcher
}
