// ABOUTME: Egress action that logs content information and checks for error strings.
// ABOUTME: Demonstrates basic egress pattern with content loading and metadata handling.

package actions

import (
	"context"
	"fmt"
	"strings"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.Register("HelloWorldGoEgressAction", &HelloWorldGoEgress{}).
		Describe("Hello pretends to egress")
}

// HelloWorldGoEgress is an egress action that logs content information.
type HelloWorldGoEgress struct{}

func (a *HelloWorldGoEgress) Egress(ctx context.Context, input actionkit.EgressInput) (actionkit.Result, error) {
	if !input.HasContent() {
		return actionkit.NewErrorResult(input.ActionContext, "No content provided"), nil
	}
	content := input.FirstContent()

	data, err := content.LoadString(ctx, input.ActionContext.ContentService)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to load content").SetContext(err.Error()), nil
	}

	if strings.Contains(data, "error") {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to egress").SetContext("error in content"), nil
	}

	result := actionkit.NewEgressResult(input.ActionContext)
	result.LogInfo(fmt.Sprintf("Egressed %d bytes to stdout", len(data)))
	result.LogInfo(fmt.Sprintf("Content name: %s, type: %s", content.Name, content.MediaType))

	for key, value := range input.Metadata {
		result.LogInfo(fmt.Sprintf("Metadata - %s: %s", key, value))
	}

	return result, nil
}
