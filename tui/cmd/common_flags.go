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
package cmd

import (
	"github.com/spf13/cobra"
)

func AddFormatFlag(cmds ...*cobra.Command) {
	for _, cmd := range cmds {
		cmd.Flags().StringP("format", "o", "json", "Output format (json|yaml)")
		_ = cmd.RegisterFlagCompletionFunc("format", formatCompletion)
	}
}

func formatCompletion(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	return []string{"json", "yaml"}, cobra.ShellCompDirectiveNoFileComp
}

func AddLoadFlags(cmd *cobra.Command) {
	cmd.PersistentFlags().StringP("file", "f", "", "Path to file to load (file must have a json or yaml/yml extension)")
	_ = cmd.MarkPersistentFlagFilename("file", "yaml", "yml", "json")
	_ = cmd.MarkPersistentFlagRequired("file")

	// flags to control the output format after loading the resource
	AddFormatFlag(cmd)
}
