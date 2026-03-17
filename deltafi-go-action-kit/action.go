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
// ABOUTME: Action interfaces and type detection for DeltaFi actions.
// ABOUTME: Defines TransformAction, EgressAction, TimedIngressAction, and optional interfaces.

package actionkit

import (
	"context"
	"fmt"
	"maps"
)

// Action type constants used in registration and event payloads.
const (
	ActionTypeTransform    = "TRANSFORM"
	ActionTypeEgress       = "EGRESS"
	ActionTypeTimedIngress = "TIMED_INGRESS"
)

// TransformAction processes content and produces transformed output.
type TransformAction interface {
	Transform(ctx context.Context, input TransformInput) (Result, error)
}

// EgressAction sends content to an external destination.
type EgressAction interface {
	Egress(ctx context.Context, input EgressInput) (Result, error)
}

// TimedIngressAction creates new DeltaFiles on a schedule.
type TimedIngressAction interface {
	Ingress(ctx context.Context, input IngressInput) (Result, error)
}

// ArtifactAware is optionally implemented by actions that need to register
// artifact version change callbacks. Called once during plugin startup before
// workers begin processing.
type ArtifactAware interface {
	ConfigureArtifacts(client *ArtifactClient)
}

// JoinAction is optionally implemented by TransformActions that support joining
// multiple DeltaFileMessages into a single TransformInput before transformation.
type JoinAction interface {
	Join(ctx context.Context, inputs []TransformInput) (TransformInput, error)
}

// DefaultJoin merges multiple TransformInputs by concatenating all content
// and combining all metadata maps. Later inputs override earlier metadata keys.
func DefaultJoin(inputs []TransformInput) TransformInput {
	var allContent []ActionContent
	allMetadata := make(map[string]string)
	for _, input := range inputs {
		allContent = append(allContent, input.Content...)
		maps.Copy(allMetadata, input.Metadata)
	}
	return TransformInput{
		ActionContext: inputs[0].ActionContext,
		Content:       allContent,
		Metadata:      allMetadata,
		Params:        inputs[0].Params,
	}
}

// registeredAction pairs an action with its name, detected type, and metadata.
type registeredAction struct {
	name        string
	action      any
	actionType  string
	description string
	paramType   any
	options     *ActionOptions
}

// detectActionType determines the action type from which interface it implements.
func detectActionType(action any) (string, error) {
	switch action.(type) {
	case TransformAction:
		return ActionTypeTransform, nil
	case EgressAction:
		return ActionTypeEgress, nil
	case TimedIngressAction:
		return ActionTypeTimedIngress, nil
	default:
		return "", fmt.Errorf("action does not implement TransformAction, EgressAction, or TimedIngressAction")
	}
}
