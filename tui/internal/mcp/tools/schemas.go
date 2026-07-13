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
// ABOUTME: Reusable JSON Schema definitions for structured MCP tool arguments
// ABOUTME: Keeps client-visible schemas aligned with handler validation

package tools

import "github.com/mark3labs/mcp-go/mcp"

var jsonScalarSchema = map[string]any{
	"type": []string{"string", "number", "boolean", "null"},
}

func eventFilterProperties() map[string]any {
	properties := make(map[string]any, 9)
	for _, name := range []string{"id", "severity", "summary", "content", "source", "acknowledged", "notification", "start", "end"} {
		properties[name] = jsonScalarSchema
	}
	return properties
}

func closedObjectSchema(properties map[string]any, required ...string) map[string]any {
	schema := map[string]any{
		"type":                 "object",
		"properties":           properties,
		"additionalProperties": false,
	}
	if len(required) > 0 {
		schema["required"] = required
	}
	return schema
}

func resumePolicyJSONSchema() map[string]any {
	backOff := closedObjectSchema(map[string]any{
		"delay":      map[string]any{"type": "integer", "minimum": 1},
		"maxDelay":   map[string]any{"type": "integer", "minimum": 0},
		"multiplier": map[string]any{"type": "integer"},
		"random":     map[string]any{"type": "boolean"},
	}, "delay")
	return closedObjectSchema(map[string]any{
		"id":             map[string]any{"type": "string", "format": "uuid"},
		"name":           map[string]any{"type": "string", "minLength": 1},
		"errorSubstring": map[string]any{"type": "string"},
		"dataSource":     map[string]any{"type": "string"},
		"action":         map[string]any{"type": "string"},
		"maxAttempts":    map[string]any{"type": "integer", "minimum": 1},
		"priority":       map[string]any{"type": "integer"},
		"enabled":        map[string]any{"type": "boolean"},
		"backOff":        backOff,
	}, "name", "maxAttempts", "backOff")
}

func timedDeletePolicyJSONSchema() map[string]any {
	return closedObjectSchema(map[string]any{
		"id":             map[string]any{"type": "string", "format": "uuid"},
		"name":           map[string]any{"type": "string", "minLength": 1},
		"enabled":        map[string]any{"type": "boolean"},
		"flow":           map[string]any{"type": "string"},
		"afterCreate":    map[string]any{"type": "string"},
		"afterComplete":  map[string]any{"type": "string"},
		"minBytes":       map[string]any{"type": "integer", "minimum": 0},
		"deleteMetadata": map[string]any{"type": "boolean"},
	}, "name", "enabled", "deleteMetadata")
}

func withStructuredObject(name, description string, schema map[string]any, required bool) mcp.ToolOption {
	return func(tool *mcp.Tool) {
		property := make(map[string]any, len(schema)+1)
		for key, value := range schema {
			property[key] = value
		}
		property["description"] = description
		tool.InputSchema.Properties[name] = property
		if required {
			tool.InputSchema.Required = append(tool.InputSchema.Required, name)
		}
	}
}
