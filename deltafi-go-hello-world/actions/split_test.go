// ABOUTME: Tests for the HelloWorldGoSplitTransformAction.
// ABOUTME: Covers multi-child creation, metadata/annotations per child, artifact management, and defaults.

package actions

import (
	"context"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func newSplitInput(did string, data string, params map[string]any) (actionkit.TransformInput, *actionkit.TestContext) {
	tc := actionkit.NewTestContext(did)
	tc.DeltaFileName = "source-file"

	// Wire up a mock artifact client
	tc.ArtifactClient = &actionkit.MockArtifactClient{
		Artifacts: map[string]*actionkit.FetchedArtifact{
			"plugin/hello.txt": {
				Data:     []byte("hello world"),
				Manifest: actionkit.ArtifactManifest{ContentType: "text/plain"},
			},
		},
	}

	content := tc.Content("input.txt", "text/plain", data)
	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
		Params:        params,
	}
	return input, tc
}

func TestSplit_DefaultTwoChildren(t *testing.T) {
	input, tc := newSplitInput("split-did", "original content", map[string]any{})

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	te := event.RequireTransform(t)

	require.Len(t, te, 2)

	for i, child := range te {
		require.Len(t, child.Content, 1, "child %d", i)

		data, err := child.Content[0].LoadString(context.Background(), tc.Store)
		require.NoError(t, err, "child %d", i)
		assert.Contains(t, data, "original content", "child %d", i)
		assert.Contains(t, data, "HelloWorldGoSplitTransformAction spawned me", "child %d", i)

		assert.Equal(t, "manyValue", child.Metadata["transformManyKey"], "child %d", i)
		assert.Equal(t, strconv.Itoa(i), child.Annotations["childIndex"], "child %d", i)
		assert.Equal(t, "source-file-"+strconv.Itoa(i), child.Name, "child %d", i)

		// Odd children get DID metadata
		if i%2 == 1 {
			assert.Contains(t, child.Metadata, "did", "child %d: odd index should have 'did'", i)
		} else {
			assert.NotContains(t, child.Metadata, "did", "child %d: even index should not have 'did'", i)
		}
	}
}

func TestSplit_ThreeChildren(t *testing.T) {
	input, _ := newSplitInput("split-did", "data", map[string]any{"numberOfChildren": 3})

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	te := event.RequireTransform(t)

	assert.Len(t, te, 3)
}

func TestSplit_OneChild(t *testing.T) {
	input, _ := newSplitInput("split-did", "data", map[string]any{"numberOfChildren": 1})

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	te := event.RequireTransform(t)

	require.Len(t, te, 1)
	assert.NotContains(t, te[0].Metadata, "did")
}

func TestSplit_NoContent(t *testing.T) {
	tc := actionkit.NewTestContext("split-did")
	tc.ArtifactClient = &actionkit.MockArtifactClient{}

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       nil,
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	event.RequireError(t, "No content provided")
}

func TestSplit_ArtifactNotFound_UploadsDefault(t *testing.T) {
	tc := actionkit.NewTestContext("split-did")
	tc.DeltaFileName = "source-file"

	mockArtifact := &actionkit.MockArtifactClient{}
	tc.ArtifactClient = mockArtifact

	content := tc.Content("input.txt", "text/plain", "data")
	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	event.RequireTransform(t)

	require.Len(t, mockArtifact.PublishCalls, 1)
	assert.Equal(t, "hello.txt", mockArtifact.PublishCalls[0].Name)
	assert.Equal(t, "hello world", string(mockArtifact.PublishCalls[0].Data))
}

func TestSplit_ArtifactAlreadyExists_SkipsUpload(t *testing.T) {
	input, tc := newSplitInput("split-did", "data", map[string]any{})
	mockArtifact := tc.ArtifactClient.(*actionkit.MockArtifactClient)

	actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)

	assert.Empty(t, mockArtifact.PublishCalls)
}

func TestSplit_ArtifactCheckOnlyOnce(t *testing.T) {
	tc := actionkit.NewTestContext("split-did")
	tc.DeltaFileName = "source-file"

	mockArtifact := &actionkit.MockArtifactClient{}
	tc.ArtifactClient = mockArtifact

	content := tc.Content("input.txt", "text/plain", "data")
	action := &HelloWorldGoSplitTransform{}

	input1 := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Params:        map[string]any{},
	}
	actionkit.RunTransform(t, action, input1)

	input2 := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Params:        map[string]any{},
	}
	actionkit.RunTransform(t, action, input2)

	assert.Len(t, mockArtifact.PublishCalls, 1)
}

func TestSplit_ChildrenHaveUniqueDIDs(t *testing.T) {
	input, tc := newSplitInput("split-did", "data", map[string]any{"numberOfChildren": 3})

	event := actionkit.RunTransform(t, &HelloWorldGoSplitTransform{}, input)
	te := event.RequireTransform(t)

	dids := make(map[string]bool)
	for _, child := range te {
		assert.False(t, dids[child.DID], "duplicate DID: %s", child.DID)
		dids[child.DID] = true
		assert.NotEqual(t, tc.DID, child.DID, "child DID should differ from parent")
	}
}
