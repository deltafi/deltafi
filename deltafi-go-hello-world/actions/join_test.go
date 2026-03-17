// ABOUTME: Tests for the HelloWorldGoJoiningTransformAction.
// ABOUTME: Covers join aggregation, transform with merged content, threshold annotation, and error cases.

package actions

import (
	"context"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func TestJoin_ReverseOrderAndSumIndex(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content1 := tc.Content("part1.txt", "text/plain", "first")
	content2 := tc.Content("part2.txt", "text/plain", "second")
	content3 := tc.Content("part3.txt", "text/plain", "third")

	inputs := []actionkit.TransformInput{
		{
			ActionContext: tc.ActionContext,
			Content:       []actionkit.ActionContent{content1},
			Metadata:      map[string]string{"index": "10", "key": "a"},
			Params:        map[string]any{"indexThreshold": 50},
		},
		{
			ActionContext: tc.ActionContext,
			Content:       []actionkit.ActionContent{content2},
			Metadata:      map[string]string{"index": "20", "key": "b"},
			Params:        map[string]any{"indexThreshold": 50},
		},
		{
			ActionContext: tc.ActionContext,
			Content:       []actionkit.ActionContent{content3},
			Metadata:      map[string]string{"index": "30", "key": "c"},
			Params:        map[string]any{"indexThreshold": 50},
		},
	}

	action := &HelloWorldGoJoiningTransform{}
	merged, err := action.Join(context.Background(), inputs)
	require.NoError(t, err)

	// Content should be reversed: third, second, first
	require.Len(t, merged.Content, 3)
	assert.Equal(t, "part3.txt", merged.Content[0].Name)
	assert.Equal(t, "part2.txt", merged.Content[1].Name)
	assert.Equal(t, "part1.txt", merged.Content[2].Name)

	// indexSum = 10+20+30 = 60
	assert.Equal(t, "60", merged.Metadata["indexSum"])

	// Last input's metadata wins for duplicate keys (maps.Copy order)
	// Input processing is reverse order: 3, 2, 1 → key=a from input 1 wins
	assert.Equal(t, "a", merged.Metadata["key"])
}

func TestJoinTransform_MergesContentAndMetadata(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content1 := tc.Content("p1.txt", "text/plain", "AAA")
	content2 := tc.Content("p2.txt", "text/plain", "BBB")

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content1, content2},
		Metadata:      map[string]string{"indexSum": "25", "hello": "join"},
		Params:        map[string]any{"indexThreshold": 50},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoJoiningTransform{}, input)
	te := event.RequireTransform(t)

	require.Len(t, te, 1)
	assert.Equal(t, "single-25", te[0].Content[0].Name)
	assert.True(t, te[0].Content[0].HasTag("joined"))

	data, err := te[0].Content[0].LoadString(context.Background(), tc.Store)
	require.NoError(t, err)
	assert.Contains(t, data, "AAA")
	assert.Contains(t, data, "BBB")

	assert.Equal(t, "25", te[0].Metadata["indexSum"])
	assert.Equal(t, "join", te[0].Metadata["hello"])

	// indexSum 25 < threshold 50 → no indexThresholdMet annotation
	assert.NotContains(t, te[0].Annotations, "indexThresholdMet")
}

func TestJoinTransform_ThresholdMet(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content := tc.Content("p.txt", "text/plain", "data")

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{"indexSum": "100"},
		Params:        map[string]any{"indexThreshold": 50},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoJoiningTransform{}, input)
	te := event.RequireTransform(t)

	assert.Equal(t, "true", te[0].Annotations["indexThresholdMet"])
}

func TestJoinTransform_ThresholdExactlyMet(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content := tc.Content("p.txt", "text/plain", "data")

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{"indexSum": "50"},
		Params:        map[string]any{"indexThreshold": 50},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoJoiningTransform{}, input)
	te := event.RequireTransform(t)

	assert.Equal(t, "true", te[0].Annotations["indexThresholdMet"])
}

func TestJoinTransform_MissingIndexSum(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content := tc.Content("p.txt", "text/plain", "data")

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
		Params:        map[string]any{"indexThreshold": 50},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoJoiningTransform{}, input)
	require.Equal(t, "ERROR", event.Type)
	assert.Contains(t, event.Error.Cause, "indexSum")
}

func TestJoinTransform_DefaultThreshold(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content := tc.Content("p.txt", "text/plain", "data")

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{"indexSum": "49"},
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoJoiningTransform{}, input)
	te := event.RequireTransform(t)

	assert.NotContains(t, te[0].Annotations, "indexThresholdMet")
}

func TestJoinTransform_EndToEnd(t *testing.T) {
	tc := actionkit.NewTestContext("join-did")
	content1 := tc.Content("p1.txt", "text/plain", "alpha")
	content2 := tc.Content("p2.txt", "text/plain", "beta")

	inputs := []actionkit.TransformInput{
		{
			ActionContext: tc.ActionContext,
			Content:       []actionkit.ActionContent{content1},
			Metadata:      map[string]string{"index": "30"},
			Params:        map[string]any{"indexThreshold": 50},
		},
		{
			ActionContext: tc.ActionContext,
			Content:       []actionkit.ActionContent{content2},
			Metadata:      map[string]string{"index": "25"},
			Params:        map[string]any{"indexThreshold": 50},
		},
	}

	action := &HelloWorldGoJoiningTransform{}

	// Join phase
	merged, err := action.Join(context.Background(), inputs)
	require.NoError(t, err)

	// Transform phase
	event := actionkit.RunTransform(t, action, merged)
	te := event.RequireTransform(t)

	assert.Equal(t, "true", te[0].Annotations["indexThresholdMet"])

	// Content should be reversed: beta then alpha
	data, _ := te[0].Content[0].LoadString(context.Background(), tc.Store)
	betaIdx := strings.Index(data, "beta")
	alphaIdx := strings.Index(data, "alpha")
	require.NotEqual(t, -1, betaIdx, "missing beta")
	require.NotEqual(t, -1, alphaIdx, "missing alpha")
	assert.Less(t, betaIdx, alphaIdx, "beta should appear before alpha (reversed order)")
}
