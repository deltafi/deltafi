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

var (
	splash       bool
	printVersion bool
)

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Version: app.GetVersion(),
	Use:     "deltafi",
	Short:   "The DeltaFi Text User Interface",
	Long: art.Logo + `
	Text User Interface (TUI) for DeltaFi`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if !app.ConfigExists() {
			configCmd, _, err := cmd.Root().Find([]string{"config"})
			if err != nil {
				fmt.Println(styles.ErrorStyle.Render(err.Error()))
				os.Exit(1)
			}
			if configCmd != nil {
				return configCmd.RunE(configCmd, args)
			} else {
				fmt.Println(styles.ErrorStyle.Render("configuration wizard not found"))
				os.Exit(1)
			}
		}
		return cmd.Help()
	},
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if cmd.Name() != "config" && !app.ConfigExists() {
			configCmd, _, err := cmd.Root().Find([]string{"config"})
			if err != nil {
				return err
			}
			if configCmd != nil {
				return configCmd.RunE(configCmd, args)
			} else {
				return fmt.Errorf("configuration wizard not found")
			}
		} else if cmd.Flags().Changed("api-url") {
			apiURL, _ := cmd.Flags().GetString("api-url")
			_ = os.Setenv("DELTAFI_API_URL", apiURL)
		}
		return nil
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	rootCmd.Version = app.GetVersion()
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
	rootCmd.AddGroup(&cobra.Group{ID: "deltafile", Title: "DeltaFiles"})
	rootCmd.AddGroup(&cobra.Group{ID: "metrics", Title: "Metrics"})
	rootCmd.AddGroup(&cobra.Group{ID: "testing", Title: "Testing"})

	rootCmd.PersistentFlags().BoolVar(&splash, "splash", false, "Show splash screen")
	rootCmd.PersistentFlags().BoolVar(&printVersion, "version", false, "Print version and exit")
	rootCmd.PersistentFlags().String("api-url", "", "Set the DeltaFi API URL, this takes precedence over DELTAFI_API_URL env var and config.yaml")
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
