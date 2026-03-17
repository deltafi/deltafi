// ABOUTME: Tests for the HelloWorldGoEgressAction.
// ABOUTME: Covers normal egress, missing content, and error-string detection.

package actions

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func TestEgress_Normal(t *testing.T) {
	tc := actionkit.NewTestContext("test-did")
	content := tc.Content("test.txt", "text/plain", "hello world")

	input := actionkit.EgressInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{"key1": "val1", "key2": "val2"},
	}

	event := actionkit.RunEgress(t, &HelloWorldGoEgress{}, input)
	event.RequireEgress(t)

	// Should have log messages about content size, name, type, and metadata
	require.GreaterOrEqual(t, len(event.Messages), 3)
}

func TestEgress_NoContent(t *testing.T) {
	tc := actionkit.NewTestContext("test-did")

	input := actionkit.EgressInput{
		ActionContext: tc.ActionContext,
		Content:       nil,
		Metadata:      map[string]string{},
	}

	event := actionkit.RunEgress(t, &HelloWorldGoEgress{}, input)
	event.RequireError(t, "No content provided")
}

func TestEgress_ContentContainsError(t *testing.T) {
	tc := actionkit.NewTestContext("test-did")
	content := tc.Content("test.txt", "text/plain", "this has an error in it")

	input := actionkit.EgressInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
	}

	event := actionkit.RunEgress(t, &HelloWorldGoEgress{}, input)
	errEvent := event.RequireError(t, "Failed to egress")
	assert.Contains(t, errEvent.Context, "error in content")
}

func TestEgress_ContentWithoutErrorString(t *testing.T) {
	tc := actionkit.NewTestContext("test-did")
	content := tc.Content("output.txt", "text/plain", "clean content with no issues")

	input := actionkit.EgressInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
	}

	event := actionkit.RunEgress(t, &HelloWorldGoEgress{}, input)
	event.RequireEgress(t)
}
