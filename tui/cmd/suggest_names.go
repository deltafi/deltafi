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
package cmd

import (
	"github.com/spf13/cobra"
)

// Toggleable is the minimum surface a domain entity must expose to be name-suggested
// with enabled-state filtering.
type Toggleable interface {
	GetName() string
	GetEnabled() bool
}

type IncludeMode int

const (
	IncludeEnabled IncludeMode = iota
	IncludeDisabled
	IncludeAll
)

// suggestNames returns a cobra ValidArgsFunction that completes a single positional
// argument with the names of items returned by fetch, filtered by mode.
func suggestNames[T Toggleable](mode IncludeMode, fetch func() ([]T, error)) func(*cobra.Command, []string, string) ([]string, cobra.ShellCompDirective) {
	return func(_ *cobra.Command, args []string, _ string) ([]string, cobra.ShellCompDirective) {
		if len(args) >= 1 {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}
		items, err := fetch()
		if err != nil {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}

		matchEnabled := mode == IncludeEnabled
		names := make([]string, 0, len(items))
		for _, item := range items {
			if mode == IncludeAll || matchEnabled == item.GetEnabled() {
				names = append(names, item.GetName())
			}
		}
		return names, cobra.ShellCompDirectiveNoFileComp
	}
}
