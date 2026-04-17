// ABOUTME: CLI command for linting an existing DeltaFi plugin.
// ABOUTME: Dispatches to the per-language generator and prints any issues found.

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
	"fmt"

	"github.com/deltafi/tui/internal/generator"
	goGen "github.com/deltafi/tui/internal/generator/golang"
	"github.com/deltafi/tui/internal/generator/java"
	"github.com/deltafi/tui/internal/generator/python"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var pluginLintCmd = &cobra.Command{
	Use:   "lint",
	Short: "Lint an existing plugin for common problems",
	Long: "Run static checks against a plugin's scaffolding, registrations, and flow JSON " +
		"wiring without compiling the code. Defaults to the current directory; pass --in-repo " +
		"to target a plugin in the configured repos directory.",
	Args: cobra.NoArgs,
	RunE: runPluginLint,
}

func init() {
	pluginCmd.AddCommand(pluginLintCmd)
	pluginLintCmd.Flags().String("in-repo", "", "Plugin name in the configured repos directory (default: use current directory)")
	pluginLintCmd.Flags().Bool("fix", false, "Automatically fix issues that have automatic fixes available")
}

func runPluginLint(cmd *cobra.Command, args []string) error {
	pluginDir, pluginName, err := resolvePluginDirFromCmd(cmd)
	if err != nil {
		return err
	}

	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to detect plugin language: %w", err)
	}

	var gen generator.Generator
	switch language {
	case "java":
		gen = java.NewJavaGenerator()
	case "python":
		gen = python.NewPythonGenerator()
	case "go":
		gen = goGen.NewGoGenerator()
	default:
		return fmt.Errorf("unsupported language: %s", language)
	}

	fix, _ := cmd.Flags().GetBool("fix")

	fmt.Printf("Linting %s plugin at %s\n\n", language, pluginDir)

	issues, err := gen.LintPlugin(pluginName, &generator.LintOptions{PluginDir: pluginDir, Fix: fix})
	if err != nil {
		return fmt.Errorf("lint failed: %w", err)
	}

	errorCount := 0
	warnCount := 0
	infoCount := 0
	fixedCount := 0
	for _, issue := range issues {
		if fix && issue.Fix != nil {
			if err := issue.Fix(); err != nil {
				fmt.Printf("%s %s: fix failed: %v\n", styles.ErrorStyle.Render("[fix-error]"), issue.File, err)
			} else {
				fmt.Printf("%s %s: %s\n", styles.SuccessStyle.Render("[fixed]"), issue.File, issue.Message)
				fixedCount++
				continue
			}
		}
		printLintIssue(issue)
		switch issue.Severity {
		case generator.LintError:
			errorCount++
		case generator.LintWarning:
			warnCount++
		case generator.LintInfo:
			infoCount++
		}
	}

	fmt.Println()
	if errorCount == 0 && warnCount == 0 && infoCount == 0 && fixedCount == 0 {
		fmt.Println(styles.SuccessStyle.Render("No issues found."))
		return nil
	}
	if fixedCount > 0 {
		fmt.Printf("%d issue(s) fixed, ", fixedCount)
	}
	fmt.Printf("%d error(s), %d warning(s), %d info\n", errorCount, warnCount, infoCount)

	if errorCount > 0 {
		return fmt.Errorf("lint found %d error(s)", errorCount)
	}
	return nil
}

func printLintIssue(issue generator.LintIssue) {
	label := "[" + issue.Severity.String() + "]"
	switch issue.Severity {
	case generator.LintError:
		label = styles.ErrorStyle.Render(label)
	case generator.LintWarning:
		label = styles.WarningStyle.Render(label)
	case generator.LintInfo:
		label = styles.InfoStyle.Render(label)
	}
	fixable := ""
	if issue.Fix != nil {
		fixable = " " + styles.InfoStyle.Render("[auto-fixable]")
	}
	if issue.File != "" {
		fmt.Printf("%s %s: %s%s\n", label, issue.File, issue.Message, fixable)
	} else {
		fmt.Printf("%s %s%s\n", label, issue.Message, fixable)
	}
}
