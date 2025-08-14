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

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var setAdminPasswordCmd = &cobra.Command{
	Use:     "set-admin-password [password]",
	Short:   "Set the admin password",
	Long:    `Set the admin password for the DeltaFi system. If a password is provided as an argument, it will be used directly. Otherwise, an interactive form will be shown.`,
	GroupID: "deltafi",
	RunE:    runSetAdminPassword,
}

func init() {
	rootCmd.AddCommand(setAdminPasswordCmd)
	setAdminPasswordCmd.Flags().BoolP("verbose", "v", false, "Show detailed response information")
}

type passwordModel struct {
	form     *huh.Form
	formData *api.PasswordUpdate
	errorMsg string
}

func initialPasswordModel() passwordModel {
	passwordFormData := &api.PasswordUpdate{}
	model := passwordModel{
		errorMsg: "",
		formData: passwordFormData,
	}
	model.form = model.newForm()

	return model
}

func (m passwordModel) Init() tea.Cmd {
	return m.form.Init()
}

func (m passwordModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd

	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "esc":
			m.errorMsg = "Password setting halted"
			m.form = nil
			return m, tea.Batch(tea.Quit)
		}
	}

	form, cmd := m.form.Update(msg)
	if f, ok := form.(*huh.Form); ok {
		m.form = f
	}

	if m.form.State == huh.StateCompleted {
		return m, tea.Batch(tea.Quit)
	}

	return m, cmd
}

func (m passwordModel) View() string {
	if m.form == nil {
		return "Initializing..."
	}

	return m.form.View()
}

func (m passwordModel) newForm() *huh.Form {
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().
				Title("Admin Password").
				Description("Enter the new admin password").
				EchoMode(huh.EchoModePassword).
				Validate(huh.ValidateNotEmpty()).
				Value(&m.formData.Password),
		),
	).WithTheme(styles.HuhTheme()).WithShowErrors(true)
}

func runSetAdminPassword(cmd *cobra.Command, args []string) error {

	if len(args) > 1 {
		return fmt.Errorf("too many arguments")
	}

	var request *api.PasswordUpdate

	if len(args) == 1 {
		request = &api.PasswordUpdate{}
		request.Password = args[0]
	} else {
		p := tea.NewProgram(initialPasswordModel())
		m, err := p.Run()
		if err != nil {
			return fmt.Errorf("error running password form: %v", err)
		}

		model := m.(passwordModel)
		if model.errorMsg != "" {
			return fmt.Errorf("%s", model.errorMsg)
		}
		request = model.formData
	}

	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return clientError(err)
	}
	response, err := client.SetAdminPassword(*request)

	verbose, _ := cmd.Flags().GetBool("verbose")
	if verbose {
		prettyPrint(cmd, response)
	}

	if err != nil {
		fmt.Println(styles.FAIL("Set admin password"))
		return fmt.Errorf("error setting admin password: %v", err)
	}

	fmt.Println(styles.OK("Set admin password"))

	return nil
}
