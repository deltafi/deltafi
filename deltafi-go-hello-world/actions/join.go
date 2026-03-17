// ABOUTME: Joining transform action that merges multiple DeltaFiles into one.
// ABOUTME: Demonstrates the JoinAction interface with custom content/metadata aggregation logic.

package actions

import (
	"context"
	"fmt"
	"maps"
	"strconv"
	"strings"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func init() {
	actionkit.Register("HelloWorldGoJoiningTransformAction", &HelloWorldGoJoiningTransform{}).
		Describe("Transforms a list of content in reverse order into a new, single content").
		WithParams(HelloWorldGoJoiningTransformParams{})
}

// HelloWorldGoJoiningTransformParams defines the parameters for the join action.
type HelloWorldGoJoiningTransformParams struct {
	IndexThreshold int `json:"indexThreshold"`
}

// HelloWorldGoJoiningTransform joins multiple DeltaFiles into a single one.
type HelloWorldGoJoiningTransform struct{}

// Join aggregates multiple inputs by reversing content order and summing index metadata.
func (a *HelloWorldGoJoiningTransform) Join(ctx context.Context, inputs []actionkit.TransformInput) (actionkit.TransformInput, error) {
	var reversedContent []actionkit.ActionContent
	allMetadata := make(map[string]string)
	indexSum := 0

	// Process inputs in reverse order
	for i := len(inputs) - 1; i >= 0; i-- {
		input := inputs[i]
		reversedContent = append(reversedContent, input.Content...)

		maps.Copy(allMetadata, input.Metadata)

		if indexStr, ok := input.Metadata["index"]; ok {
			if idx, err := strconv.Atoi(indexStr); err == nil {
				indexSum += idx
			}
		}
	}

	allMetadata["indexSum"] = strconv.Itoa(indexSum)

	return actionkit.TransformInput{
		ActionContext: inputs[0].ActionContext,
		Content:       reversedContent,
		Metadata:      allMetadata,
		Params:        inputs[0].Params,
	}, nil
}

// Transform processes the joined input and creates a single merged content.
func (a *HelloWorldGoJoiningTransform) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
	params, err := actionkit.ParseParams[HelloWorldGoJoiningTransformParams](input.Params)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to parse parameters").SetContext(err.Error()), nil
	}

	if params.IndexThreshold <= 0 {
		params.IndexThreshold = 50
	}

	indexSumStr, err := input.GetMetadata("indexSum")
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Missing required metadata: indexSum"), nil
	}

	var contentBuilder strings.Builder
	for i := range input.Content {
		content, err := input.ContentAt(i)
		if err != nil {
			return actionkit.NewErrorResult(input.ActionContext, fmt.Sprintf("Failed to get content at index %d", i)).SetContext(err.Error()), nil
		}
		data, err := content.LoadString(ctx, input.ActionContext.ContentService)
		if err != nil {
			return actionkit.NewErrorResult(input.ActionContext, fmt.Sprintf("Failed to load content at index %d", i)).SetContext(err.Error()), nil
		}
		contentBuilder.WriteString(data)
		contentBuilder.WriteString("\n")
	}

	contentName := "single-" + indexSumStr
	mergedContent, err := actionkit.SaveContentString(
		ctx,
		input.ActionContext.ContentService,
		input.ActionContext,
		contentBuilder.String(),
		contentName,
		"text/plain",
	)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to save merged content").SetContext(err.Error()), nil
	}

	mergedContent.AddTag("joined")

	result := actionkit.NewTransformResult(input.ActionContext)
	result.AddContent(*mergedContent)

	for k, v := range input.Metadata {
		result.AddMetadata(k, v)
	}

	indexSum, _ := strconv.Atoi(indexSumStr)
	if indexSum >= params.IndexThreshold {
		result.AddAnnotation("indexThresholdMet", "true")
	}

	result.LogInfo(fmt.Sprintf("Joined %d inputs with indexSum=%s (threshold=%d)",
		len(input.Content), indexSumStr, params.IndexThreshold))

	return result, nil
}
