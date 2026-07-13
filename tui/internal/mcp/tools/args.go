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
// ABOUTME: Shared argument-parsing helpers for MCP tool handlers
// ABOUTME: Centralizes UUID parsing, JSON object decoding, and scalar coercion

package tools

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
	"strconv"

	"github.com/google/uuid"
	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/mcp/registry"
)

// requireNonEmptyString extracts a required string argument and rejects it when
// missing or empty. RequireString alone treats a present-but-empty value as
// valid, which would let blank required fields through. On failure it returns a
// non-nil error result that the caller should return to the client.
func requireNonEmptyString(req mcp.CallToolRequest, arg string) (string, *mcp.CallToolResult) {
	s, err := req.RequireString(arg)
	if err != nil || s == "" {
		return "", registry.ErrorResult(arg + " parameter is required")
	}
	return s, nil
}

// optionalNonNegativeInt reads an optional numeric argument as a non-negative
// int. It returns nil when the argument is absent, leaving the caller's default
// in place. Present values must be numeric, integral, and non-negative.
func optionalNonNegativeInt(req mcp.CallToolRequest, arg string) (*int, error) {
	raw, present := req.GetArguments()[arg]
	if !present {
		return nil, nil
	}
	var v float64
	switch value := raw.(type) {
	case float64:
		v = value
	case int:
		v = float64(value)
	default:
		return nil, fmt.Errorf("%s must be an integer", arg)
	}
	if v < 0 {
		return nil, fmt.Errorf("%s must be non-negative", arg)
	}
	if math.Trunc(v) != v {
		return nil, fmt.Errorf("%s must be an integer", arg)
	}
	if v > float64(math.MaxInt) {
		return nil, fmt.Errorf("%s is too large", arg)
	}
	i := int(v)
	return &i, nil
}

// toStringSlice converts a decoded JSON array into a []string, rejecting any
// element that is not a string rather than silently coercing or dropping it.
func toStringSlice(raw []any) ([]string, error) {
	out := make([]string, 0, len(raw))
	for i, item := range raw {
		s, ok := item.(string)
		if !ok {
			return nil, fmt.Errorf("element %d must be a string", i)
		}
		out = append(out, s)
	}
	return out, nil
}

// stringArrayArg extracts a []string from a JSON array argument, returning an
// error result if the argument is present but is not an array of strings. A
// missing argument yields a nil slice and no error.
func stringArrayArg(args map[string]any, name string) ([]string, *mcp.CallToolResult) {
	raw, present := args[name]
	if !present {
		return nil, nil
	}
	list, ok := raw.([]any)
	if !ok {
		return nil, registry.ErrorResult(name + " must be an array of strings")
	}
	out, err := toStringSlice(list)
	if err != nil {
		return nil, registry.ErrorResult(fmt.Sprintf("%s: %v", name, err))
	}
	return out, nil
}

// requireUUID extracts a required string argument and parses it as a UUID. The
// label names the value for error messages (e.g. "snapshot ID"). On failure it
// returns a non-nil error result that the caller should return to the client.
func requireUUID(req mcp.CallToolRequest, arg, label string) (uuid.UUID, *mcp.CallToolResult) {
	s, err := req.RequireString(arg)
	if err != nil {
		return uuid.Nil, registry.ErrorResult(arg + " parameter is required")
	}
	id, err := uuid.Parse(s)
	if err != nil {
		return uuid.Nil, registry.ErrorResult(fmt.Sprintf("invalid %s: %v", label, err))
	}
	return id, nil
}

// decodeJSON re-encodes an already-decoded JSON value (map, slice, scalar) into
// the target Go type. It is the marshal/unmarshal step shared by the typed
// input decoders, which layer their own validation on top.
func decodeJSON(raw any, target any) error {
	encoded, err := json.Marshal(raw)
	if err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(encoded))
	decoder.DisallowUnknownFields()
	return decoder.Decode(target)
}

// scalarToString converts a JSON scalar (string, bool, number, or null) to its
// string form. isNull distinguishes an explicit JSON null from an empty string;
// ok is false when the value is not a supported scalar type.
func scalarToString(v any) (s string, isNull bool, ok bool) {
	switch val := v.(type) {
	case nil:
		return "", true, true
	case string:
		return val, false, true
	case bool:
		return strconv.FormatBool(val), false, true
	case float64:
		return strconv.FormatFloat(val, 'f', -1, 64), false, true
	default:
		return "", false, false
	}
}
