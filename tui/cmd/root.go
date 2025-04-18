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
	"fmt"
	"os"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/command"
	"github.com/deltafi/tui/internal/ui/art"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var splash bool
var printVersion bool

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Version: app.Version,
	Use:     "deltafi",
	Short:   "The DeltaFi Text User Interface",
	Long: art.Logo + `
	Text User Interface (TUI) for DeltaFi`,
	SilenceUsage: true,
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {

	if !app.ConfigExists() {
		app.CreateConfig()
	}

	rootCmd.Version = app.Version
	rootCmd.SetVersionTemplate("{{.Version}}\n")

	rootCmd.SilenceErrors = true
	rootCmd.SilenceUsage = true

	err := rootCmd.Execute()

	if err != nil {
		// always print the error first
		fmt.Println(styles.ErrorStyle.Render(err.Error()))

		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(preExecutionHook)

	// Here you will define your flags and configuration settings.
	// Cobra supports persistent flags, which, if defined here,
	// will be global for your application.

	rootCmd.AddGroup(&cobra.Group{ID: "orchestration", Title: "System Orchestration"})
	rootCmd.AddGroup(&cobra.Group{ID: "legacy", Title: "Legacy CLI Commands"})
	rootCmd.AddGroup(&cobra.Group{ID: "deltafi", Title: "DeltaFi System Management"})
	rootCmd.AddGroup(&cobra.Group{ID: "flow", Title: "DeltaFi Flow Management"})
	rootCmd.AddGroup(&cobra.Group{ID: "data", Title: "Data and Metrics"})

	rootCmd.PersistentFlags().BoolVar(&splash, "splash", false, "Show splash screen")
	rootCmd.PersistentFlags().BoolVar(&printVersion, "version", false, "Print version and exit")
	// Cobra also supports local flags, which will only run
	// when this action is called directly.
	// RootCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}

// initConfig reads in config file and ENV variables if set.
func preExecutionHook() {
	if splash {
		runProgram(command.NewSplashCommand([]string{}))
	}
}
