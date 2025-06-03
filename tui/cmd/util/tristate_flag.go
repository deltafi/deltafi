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
package util

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"
)

// TristateFlag represents a boolean that can be true, false, or unset
type TristateFlag struct {
	Value *bool // nil means unset, otherwise points to true/false
}

// String implements the flag.Value interface
func (t *TristateFlag) String() string {
	if t.Value == nil {
		return "unset"
	}
	return fmt.Sprintf("%t", *t.Value)
}

func (t *TristateFlag) Unset() {
	t.Value = nil
}

func (t *TristateFlag) SetTrue() {
	val := true
	t.Value = &val
}

func (t *TristateFlag) SetFalse() {
	val := false
	t.Value = &val
}

func NewTriStateFlag() *TristateFlag {
	return &TristateFlag{
		Value: nil,
	}
}

func NewTriStateFlagFromBool(b bool) *TristateFlag {
	t := NewTriStateFlag()
	t.SetFromBool(b)
	return t
}

func NewTriStateFlagFromString(s string) *TristateFlag {
	t := NewTriStateFlag()
	t.Set(s)
	return t
}

// Set implements the flag.Value interface
func (t *TristateFlag) Set(s string) error {
	s = strings.ToLower(strings.TrimSpace(s))

	switch s {
	case "", "unset", "all", "any":
		t.Value = nil
	case "true", "t", "yes", "y", "1":
		val := true
		t.Value = &val
	case "false", "f", "no", "n", "0":
		val := false
		t.Value = &val
	default:
		return fmt.Errorf("invalid boolean value: %q", s)
	}

	return nil
}

func (t *TristateFlag) SetFromBool(b bool) {
	t.Value = &b
}

// Type implements the pflag.Value interface
func (t *TristateFlag) Type() string {
	return "bool"
}

func (t *TristateFlag) IsTrue() bool {
	return t.IsSet() && *t.Value
}

func (t *TristateFlag) IsFalse() bool {
	return t.IsSet() && !*t.Value
}

func (t *TristateFlag) IsSet() bool {
	return t.Value != nil
}

func (t *TristateFlag) IsUnset() bool {
	return t.Value == nil
}

func (t *TristateFlag) RegisterFlag(cmd *cobra.Command, flagName string, flagDescription string, truthy string, falsy string) {
	cmd.Flags().Var(t, flagName, fmt.Sprintf("%s (--%s=[%s|%s])", flagDescription, flagName, truthy, falsy))
	// cmd.Flag(flagName).NoOptDefVal = truthy
	cmd.RegisterFlagCompletionFunc(flagName, func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		return []string{truthy, falsy}, cobra.ShellCompDirectiveNoFileComp
	})
}
