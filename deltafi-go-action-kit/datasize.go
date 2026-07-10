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
// ABOUTME: DataSize parameter type for human-readable byte sizes.
// ABOUTME: Parses strings like "10MB" or "512KB" and stores the value in bytes.

package actionkit

import (
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"strings"

	"github.com/invopop/jsonschema"
)

const (
	Byte     DataSize = 1
	Kilobyte DataSize = 1024
	Megabyte DataSize = 1024 * Kilobyte
	Gigabyte DataSize = 1024 * Megabyte
	Terabyte DataSize = 1024 * Gigabyte
)

// DataSize represents a size in bytes, parsed from human-readable strings
// like "10MB", "512KB", "1GB". Supported units: B, KB, MB, GB, TB.
type DataSize int64

// Bytes returns the size in bytes.
func (d DataSize) Bytes() int64 {
	return int64(d)
}

// String returns the human-readable representation.
// Uses the largest unit that evenly divides the value.
func (d DataSize) String() string {
	switch {
	case d >= Terabyte && d%Terabyte == 0:
		return fmt.Sprintf("%dTB", d/Terabyte)
	case d >= Gigabyte && d%Gigabyte == 0:
		return fmt.Sprintf("%dGB", d/Gigabyte)
	case d >= Megabyte && d%Megabyte == 0:
		return fmt.Sprintf("%dMB", d/Megabyte)
	case d >= Kilobyte && d%Kilobyte == 0:
		return fmt.Sprintf("%dKB", d/Kilobyte)
	default:
		return fmt.Sprintf("%dB", d)
	}
}

// ParseDataSize parses a human-readable size string into a DataSize.
// Supported formats: "10B", "512KB", "10MB", "1GB", "2TB".
func ParseDataSize(s string) (DataSize, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0, fmt.Errorf("empty data size string")
	}

	// Find where the digits end and the unit begins
	i := 0
	for i < len(s) && s[i] >= '0' && s[i] <= '9' {
		i++
	}
	if i == 0 {
		return 0, fmt.Errorf("invalid data size %q: must start with a number", s)
	}

	numStr := s[:i]
	unit := strings.ToUpper(s[i:])

	n, err := strconv.ParseInt(numStr, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid data size %q: %w", s, err)
	}

	var multiplier DataSize
	switch unit {
	case "B":
		multiplier = Byte
	case "KB":
		multiplier = Kilobyte
	case "MB":
		multiplier = Megabyte
	case "GB":
		multiplier = Gigabyte
	case "TB":
		multiplier = Terabyte
	default:
		return 0, fmt.Errorf("invalid data size unit %q: must be B, KB, MB, GB, or TB", unit)
	}

	if multiplier != 0 && n > math.MaxInt64/int64(multiplier) {
		return 0, fmt.Errorf("data size %q overflows int64", s)
	}

	return DataSize(n) * multiplier, nil
}

// UnmarshalJSON parses a JSON string like "10MB" into a DataSize.
func (d *DataSize) UnmarshalJSON(data []byte) error {
	var s string
	if err := json.Unmarshal(data, &s); err != nil {
		return err
	}
	parsed, err := ParseDataSize(s)
	if err != nil {
		return err
	}
	*d = parsed
	return nil
}

// MarshalJSON serializes the DataSize as a human-readable JSON string.
func (d DataSize) MarshalJSON() ([]byte, error) {
	return json.Marshal(d.String())
}

// JSONSchema provides custom schema generation for DataSize fields.
func (DataSize) JSONSchema() *jsonschema.Schema {
	return &jsonschema.Schema{
		Type:    "string",
		Pattern: `^[0-9]+(B|KB|MB|GB|TB)$`,
		Extras: map[string]any{
			"x-deltafi-type": "DataSize",
		},
	}
}
