// ABOUTME: Split transform action that demonstrates multi-child DeltaFile creation.
// ABOUTME: Splits one input DeltaFile into multiple children and manages a sample artifact.

package actions

import (
	"context"
	"fmt"
	"log"
	"strconv"
	"strings"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.Register("HelloWorldGoSplitTransformAction", &HelloWorldGoSplitTransform{}).
		Describe("Transform N children based on the numberOfChildren parameter").
		WithParams(HelloWorldGoSplitTransformParams{})
}

// HelloWorldGoSplitTransformParams defines the parameters for the split action.
type HelloWorldGoSplitTransformParams struct {
	NumberOfChildren int `json:"numberOfChildren" jsonschema:"description=Number of child DeltaFiles to create,default=2"`
}

// HelloWorldGoSplitTransform splits input into multiple child DeltaFiles.
type HelloWorldGoSplitTransform struct {
	artifactChecked bool
}

func (a *HelloWorldGoSplitTransform) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
	// Ensure the sample artifact exists on first invocation
	a.ensureArtifact(ctx, input.ActionContext)

	params, err := actionkit.ParseParams[HelloWorldGoSplitTransformParams](input.Params)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to parse parameters").SetContext(err.Error()), nil
	}

	if params.NumberOfChildren <= 0 {
		params.NumberOfChildren = 2
	}

	content, err := input.ContentAt(0)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "No content provided"), nil
	}

	data, err := content.LoadString(ctx, input.ActionContext.ContentService)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to load content").SetContext(err.Error()), nil
	}

	transformed := data + "\nHelloWorldGoSplitTransformAction spawned me"

	savedContent, err := actionkit.SaveContentString(
		ctx,
		input.ActionContext.ContentService,
		input.ActionContext,
		transformed,
		"transformed content",
		"text/plain",
	)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to save content").SetContext(err.Error()), nil
	}

	result := actionkit.NewTransformResults(input.ActionContext)
	result.LogInfo("The parent saved new content")

	for n := 0; n < params.NumberOfChildren; n++ {
		childName := fmt.Sprintf("%s-%d", input.ActionContext.DeltaFileName, n)
		child := actionkit.NewChildTransformResult(input.ActionContext, childName)

		child.AddContent(*savedContent)
		child.AddMetadata("transformManyKey", "manyValue")
		child.AddAnnotation("childIndex", strconv.Itoa(n))
		if n%2 == 1 {
			child.AddMetadata("did", child.Context().DID)
		}
		child.LogInfo(fmt.Sprintf("I am child #%d", n))

		result.AddChild(*child)
	}

	return result, nil
}

// ensureArtifact checks if the hello.txt artifact exists and uploads a default if not.
func (a *HelloWorldGoSplitTransform) ensureArtifact(ctx context.Context, actx *actionkit.ActionContext) {
	if a.artifactChecked {
		return
	}
	a.artifactChecked = true

	fetched, err := actx.ArtifactClient.GetArtifact(ctx, "hello.txt", "")
	if err == nil {
		log.Printf("Manually pulled artifact content:\n %s", string(fetched.Data))
		return
	}

	log.Printf("hello.txt artifact not found, uploading default")
	makeActive := true
	_, err = actx.ArtifactClient.PublishArtifact(
		ctx,
		"hello.txt",
		strings.NewReader("hello world"),
		"text/plain",
		&actionkit.PublishOptions{
			Version:     "1",
			Description: "Sample artifact managed by the ArtifactRegistryService directly from a plugin",
			MakeActive:  &makeActive,
			Labels:      map[string]string{"default": "true"},
		},
	)
	if err != nil {
		log.Printf("Failed to upload default hello.txt artifact: %v", err)
	}
}
