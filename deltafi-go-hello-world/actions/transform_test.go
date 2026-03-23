// ABOUTME: Tests for the HelloWorldGoTransformAction.
// ABOUTME: Covers content transformation, DID prefix filtering/error, lookups, artifact metadata, and uppercase.

package actions

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func newTransformInput(did, data string, params map[string]any, metadata map[string]string) (actionkit.TransformInput, *actionkit.TestContext) {
	tc := actionkit.NewTestContext(did)
	now := time.Now()

	rgbRows := []map[string]string{
		{"column_a": "row1", "color": "red"},
		{"column_a": "row2", "color": "green"},
		{"column_a": "row3", "color": "blue"},
	}
	driverRows := []map[string]string{
		{"first": "Oscar", "last": "Piastri", "team": "McLaren"},
		{"first": "Lando", "last": "Norris", "team": "McLaren"},
	}

	tc.LookupClient = &actionkit.MockLookupClient{
		LookupFunc: func(ctx context.Context, tableName string, opts *actionkit.LookupOptions) (*actionkit.LookupResults, error) {
			switch tableName {
			case "go_scheduled_lookup_table":
				if opts != nil && opts.MatchingColumnValues != nil {
					wantColors := opts.MatchingColumnValues["color"]
					wantSet := make(map[string]bool, len(wantColors))
					for _, c := range wantColors {
						wantSet[c] = true
					}
					var filtered []map[string]string
					for _, row := range rgbRows {
						if wantSet[row["color"]] {
							filtered = append(filtered, row)
						}
					}
					return &actionkit.LookupResults{
						TotalCount: len(filtered),
						Created:    &now,
						Modified:   &now,
						Rows:       filtered,
					}, nil
				}
				return &actionkit.LookupResults{
					TotalCount: len(rgbRows),
					Created:    &now,
					Modified:   &now,
					Rows:       rgbRows,
				}, nil
			case "go_csv_lookup_table":
				return &actionkit.LookupResults{
					TotalCount: len(driverRows),
					Rows:       driverRows,
				}, nil
			default:
				return &actionkit.LookupResults{}, nil
			}
		},
	}

	content := tc.Content("input.txt", "text/plain", data)
	if metadata == nil {
		metadata = map[string]string{}
	}
	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      metadata,
		Params:        params,
	}
	return input, tc
}

func TestTransform_Normal(t *testing.T) {
	input, tc := newTransformInput("abc-did", "hello world", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	te := event.RequireTransform(t)

	require.Len(t, te[0].Content, 1)
	data, err := te[0].Content[0].LoadString(context.Background(), tc.Store)
	require.NoError(t, err)
	assert.Contains(t, data, "hello world")
	assert.Contains(t, data, "HelloWorldGoTransformAction did a great job")

	assert.True(t, te[0].Content[0].HasTag("hello-file"))
	assert.Equal(t, "transformValue", te[0].Metadata["transformKey"])
	assert.Equal(t, "English", te[0].Annotations["language"])

	assert.NotEmpty(t, te[0].Metadata["RGB Rows"])
	assert.Contains(t, te[0].Metadata["McLaren Drivers"], "Piastri")
	assert.Contains(t, te[0].Metadata["McLaren Drivers"], "Norris")
	assert.Equal(t, "0", te[0].Metadata["purpleCount"])
}

func TestTransform_ContentNameFromMetadata(t *testing.T) {
	input, tc := newTransformInput("abc-did", "data", map[string]any{}, map[string]string{"index": "7"})

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	te := event.RequireTransform(t)

	// Verify content loads
	_, err := te[0].Content[0].LoadString(context.Background(), tc.Store)
	require.NoError(t, err)

	assert.Equal(t, "content-7", te[0].Content[0].Name)
}

func TestTransform_ContentNameDefault(t *testing.T) {
	input, _ := newTransformInput("abc-did", "data", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	te := event.RequireTransform(t)

	assert.Equal(t, "content-0", te[0].Content[0].Name)
}

func TestTransform_FilterOnPrefix(t *testing.T) {
	input, _ := newTransformInput("2abc-did", "hello", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireFilter(t, "Filter due to did_filter_prefix match")
}

func TestTransform_ErrorOnPrefix(t *testing.T) {
	input, _ := newTransformInput("44abc-did", "hello", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	errEvent := event.RequireError(t, "Error due to did_error_prefix match")

	assert.Contains(t, errEvent.Context, "44")
}

func TestTransform_CustomPrefixes(t *testing.T) {
	params := map[string]any{
		"didFilterPrefix": "xx",
		"didErrorPrefix":  "yy",
	}
	input, _ := newTransformInput("xx-did", "hello", params, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireFilter(t, "Filter due to did_filter_prefix match")
}

func TestTransform_CustomErrorPrefix(t *testing.T) {
	params := map[string]any{
		"didFilterPrefix": "xx",
		"didErrorPrefix":  "yy",
	}
	input, _ := newTransformInput("yy-did", "hello", params, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireError(t, "Error due to did_error_prefix match")
}

func TestTransform_ConvertToUpper(t *testing.T) {
	params := map[string]any{"convertToUpper": true}
	input, tc := newTransformInput("abc-did", "hello world", params, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	te := event.RequireTransform(t)

	data, _ := te[0].Content[0].LoadString(context.Background(), tc.Store)
	assert.Contains(t, data, "HELLO WORLD")
	assert.Contains(t, data, "HELLOWORLDGOTRANSFORMACTION DID A GREAT JOB")
}

func TestTransform_NoContent(t *testing.T) {
	tc := actionkit.NewTestContext("abc-did")
	tc.LookupClient = &actionkit.MockLookupClient{}

	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       nil,
		Metadata:      map[string]string{},
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireError(t, "No content provided")
}

func TestTransform_LookupError(t *testing.T) {
	tc := actionkit.NewTestContext("abc-did")
	tc.LookupClient = &actionkit.MockLookupClient{
		Err: context.DeadlineExceeded,
	}

	content := tc.Content("input.txt", "text/plain", "data")
	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	require.Equal(t, "ERROR", event.Type)
	assert.Contains(t, event.Error.Cause, "lookup")
	assert.Contains(t, event.Error.Cause, "RGB")
}

func TestTransform_NoRGBResults(t *testing.T) {
	tc := actionkit.NewTestContext("abc-did")
	tc.LookupClient = &actionkit.MockLookupClient{
		Results: map[string]*actionkit.LookupResults{
			"go_scheduled_lookup_table": {TotalCount: 0, Rows: nil},
			"go_csv_lookup_table":       {TotalCount: 0, Rows: nil},
		},
	}

	content := tc.Content("input.txt", "text/plain", "data")
	input := actionkit.TransformInput{
		ActionContext: tc.ActionContext,
		Content:       []actionkit.ActionContent{content},
		Metadata:      map[string]string{},
		Params:        map[string]any{},
	}

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireError(t, "No RGB results found")
}

func TestTransform_ArtifactMetadata(t *testing.T) {
	input, _ := newTransformInput("abc-did", "hello", map[string]any{}, nil)

	action := &HelloWorldGoTransform{}
	action.extraMetadata.Store(map[string]string{
		"custom":  "value",
		"another": "meta",
	})

	event := actionkit.RunTransform(t, action, input)
	te := event.RequireTransform(t)

	assert.Equal(t, "value", te[0].Metadata["custom"])
	assert.Equal(t, "meta", te[0].Metadata["another"])
}

func TestTransform_NoArtifactMetadata(t *testing.T) {
	input, _ := newTransformInput("abc-did", "hello", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireTransform(t)
}

func TestTransform_FilterPrefixTakesPriority(t *testing.T) {
	input, _ := newTransformInput("2-did", "hello", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireFilter(t, "Filter due to did_filter_prefix match")
}

func TestTransform_NeitherPrefixMatches(t *testing.T) {
	input, _ := newTransformInput("abc-did", "hello", map[string]any{}, nil)

	event := actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)
	event.RequireTransform(t)
}

func TestTransform_LookupCallsVerified(t *testing.T) {
	input, _ := newTransformInput("abc-did", "hello", map[string]any{}, nil)
	mockLookup := input.ActionContext.LookupClient.(*actionkit.MockLookupClient)

	actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)

	require.Len(t, mockLookup.LookupCalls, 3)
	assert.Equal(t, "go_scheduled_lookup_table", mockLookup.LookupCalls[0].TableName)
	assert.Equal(t, "go_scheduled_lookup_table", mockLookup.LookupCalls[1].TableName)
	assert.Equal(t, "go_csv_lookup_table", mockLookup.LookupCalls[2].TableName)
}

func TestTransform_REDInContent_FiltersLookup(t *testing.T) {
	input, _ := newTransformInput("abc-did", "color is RED", map[string]any{}, nil)
	mockLookup := input.ActionContext.LookupClient.(*actionkit.MockLookupClient)

	actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)

	colors := mockLookup.LookupCalls[0].Opts.MatchingColumnValues["color"]
	assert.Equal(t, []string{"red"}, colors)
}

func TestTransform_NoREDInContent_AllColors(t *testing.T) {
	input, _ := newTransformInput("abc-did", "no special color", map[string]any{}, nil)
	mockLookup := input.ActionContext.LookupClient.(*actionkit.MockLookupClient)

	actionkit.RunTransform(t, &HelloWorldGoTransform{}, input)

	colors := mockLookup.LookupCalls[0].Opts.MatchingColumnValues["color"]
	assert.Len(t, colors, 3)
}
