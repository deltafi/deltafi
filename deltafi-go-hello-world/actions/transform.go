// ABOUTME: Transform action that appends text, optionally uppercases, and checks DID prefixes.
// ABOUTME: Demonstrates content manipulation, metadata, annotations, lookup queries, and artifact usage.

package actions

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"strings"
	"time"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.Register("HelloWorldGoTransformAction", &HelloWorldGoTransform{}).
		WithParams(HelloWorldGoTransformParams{}).
		WithOptions(actionkit.ActionOptions{
			Description: "Add some content noting that we did a really good job",
			InputSpec: &actionkit.InputSpec{
				ContentSummary: "Expects a single text content item to transform",
			},
			OutputSpec: &actionkit.OutputSpec{
				ContentSummary:     "Produces a single text content item with appended message",
				AnnotationsSummary: "Adds language annotation",
				AnnotationDescriptions: []actionkit.KeyedDescription{
					{Key: "language", Description: "The language of the transformed content"},
				},
				MetadataDescriptions: []actionkit.KeyedDescription{
					{Key: "transformKey", Description: "Marker indicating transform was applied"},
					{Key: "RGB Rows", Description: "Color values from scheduled lookup table"},
					{Key: "McLaren Drivers", Description: "Driver names from CSV lookup table"},
				},
			},
			Filters: actionkit.SimpleFilters("Filter due to did_filter_prefix match"),
			Errors: actionkit.SimpleErrors(
				"Error due to did_error_prefix match",
				"No content provided",
				"No RGB results found",
			),
		})
}

// HelloWorldGoTransformParams defines the action's configurable parameters.
type HelloWorldGoTransformParams struct {
	ConvertToUpper          bool         `json:"convertToUpper" jsonschema:"description=If TRUE\\, transformation will convert content to uppercase. Default is FALSE.,default=false"`
	AlwaysErrorOnPrefixHit  bool         `json:"alwaysErrorOnPrefixHit" jsonschema:"description=If TRUE\\, when a DeltaFile with a DID starting with 'didErrorPrefix' is being processed\\, an ERROR will always occur. If FALSE\\, the ERROR occurs randomly with a 50% probability. Default is TRUE (always error).,default=true"`
	AlwaysFilterOnPrefixHit bool         `json:"alwaysFilterOnPrefixHit" jsonschema:"description=If TRUE\\, when a DeltaFile with a DID starting with 'didFilterPrefix' is being processed\\, a FILTER will always occur. If FALSE\\, the FILTER occurs randomly with a 50% probability. Default is TRUE (always filter).,default=true"`
	DidErrorPrefix          string       `json:"didErrorPrefix" jsonschema:"description=The prefix for DIDs that can cause an ERROR to occur. Default is '44'.,default=44"`
	DidFilterPrefix         string       `json:"didFilterPrefix" jsonschema:"description=The prefix for DIDs that can cause a FILTER to occur. Default is '2'.,default=2"`
	Structure1              *Structure1  `json:"structure1" jsonschema:"description=Structure 1 instance"`
	Structure2              *Structure2  `json:"structure2" jsonschema:"description=Structure 2 instance"`
	Structure3List          []Structure3 `json:"structure3List" jsonschema:"description=Structure3 List"`
}

// Structure1 demonstrates a nested parameter structure with primitive fields.
type Structure1 struct {
	S1Boolean bool `json:"s1Boolean" jsonschema:"description=Description of s1Boolean"`
	S1Integer int  `json:"s1Integer" jsonschema:"description=Description of s1Integer"`
}

// Structure2 demonstrates a nested parameter structure with collection fields.
type Structure2 struct {
	S2StringList []string          `json:"s2StringList" jsonschema:"description=Description of s2StringList"`
	S2Map        map[string]string `json:"s2Map" jsonschema:"description=Description of s2Map"`
}

// Structure3 demonstrates a nested parameter structure containing other structures.
type Structure3 struct {
	S2InstanceInS3 *Structure2  `json:"s2InstanceInS3" jsonschema:"description=Description of s2InstanceInS3"`
	S1ListInS3     []Structure1 `json:"s1ListInS3" jsonschema:"description=Description of s1ListInS3"`
}

// HelloWorldGoTransform is a transform action that appends text and optionally uppercases content.
type HelloWorldGoTransform struct {
	extraMetadata actionkit.SafeStringMap
}

func (a *HelloWorldGoTransform) ConfigureArtifacts(client *actionkit.ArtifactClient) {
	client.OnUpdateFetchWithDefault("metadata.json", "",
		actionkit.ArtifactDefault{
			Data:        []byte(`{"custom": "value"}`),
			ContentType: "application/json",
			Description: "Default extra metadata for HelloWorldGoTransformAction",
		},
		func(fetched *actionkit.FetchedArtifact, err error) {
			if err != nil {
				log.Printf("Unable to read metadata.json: %v", err)
				a.extraMetadata.Store(nil)
				return
			}
			var metadata map[string]string
			if err := json.Unmarshal(fetched.Data, &metadata); err != nil {
				log.Printf("Unable to parse metadata.json: %v", err)
				a.extraMetadata.Store(nil)
				return
			}
			a.extraMetadata.Store(metadata)
		})
}

func (a *HelloWorldGoTransform) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
	params := HelloWorldGoTransformParams{
		ConvertToUpper:          false,
		AlwaysErrorOnPrefixHit:  true,
		AlwaysFilterOnPrefixHit: true,
		DidErrorPrefix:          "44",
		DidFilterPrefix:         "2",
	}
	if err := input.ParseParameters(&params); err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to parse parameters").SetContext(err.Error()), nil
	}

	did := input.ActionContext.DID

	// Check DID prefix for filter condition
	if strings.HasPrefix(did, params.DidFilterPrefix) {
		if params.AlwaysFilterOnPrefixHit || a.sometimesTrue() {
			return actionkit.NewFilterResult(input.ActionContext, "Filter due to did_filter_prefix match"), nil
		}
	} else if strings.HasPrefix(did, params.DidErrorPrefix) {
		if params.AlwaysErrorOnPrefixHit || a.sometimesTrue() {
			return actionkit.NewErrorResult(input.ActionContext, "Error due to did_error_prefix match").
				SetContext(params.DidErrorPrefix + " detected"), nil
		}
	}

	content, err := input.ContentAt(0)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "No content provided"), nil
	}

	data, err := content.LoadString(ctx, input.ActionContext.ContentService)
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to load content").SetContext(err.Error()), nil
	}

	transformed := data + "\nHelloWorldGoTransformAction did a great job"
	lookForRed := strings.Contains(transformed, "RED")
	if params.ConvertToUpper {
		transformed = strings.ToUpper(transformed)
	}

	contentName := fmt.Sprintf("content-%s", input.GetMetadataOrDefault("index", "0"))

	newContent, err := actionkit.SaveContentString(ctx, input.ActionContext.ContentService, input.ActionContext, transformed, contentName, "text/plain")
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to save content").SetContext(err.Error()), nil
	}

	newContent.AddTag("hello-file")

	result := actionkit.NewTransformResult(input.ActionContext)
	result.AddContent(*newContent)
	result.AddMetadata("transformKey", "transformValue")
	result.AddAnnotation("language", "English")
	result.LogInfo(fmt.Sprintf("Transformed content, size: %d bytes", newContent.Size()))

	// Add extra metadata loaded from artifact (kept in sync via version change callback)
	a.addArtifactMetadata(result)

	// Query scheduled lookup table for RGB colors
	if errResult := a.lookupRGB(ctx, input.ActionContext, lookForRed, result); errResult != nil {
		return errResult, nil
	}

	// Query scheduled lookup table for purple (never exists)
	if errResult := a.lookupPurple(ctx, input.ActionContext, result); errResult != nil {
		return errResult, nil
	}

	// Query CSV lookup table for McLaren drivers
	if errResult := a.lookupDrivers(ctx, input.ActionContext, result); errResult != nil {
		return errResult, nil
	}

	return result, nil
}

func (a *HelloWorldGoTransform) addArtifactMetadata(result *actionkit.TransformResult) {
	a.extraMetadata.ForEach(func(k, v string) {
		result.AddMetadata(k, v)
	})
}

func (a *HelloWorldGoTransform) lookupRGB(ctx context.Context, actx *actionkit.ActionContext, lookForRed bool, result *actionkit.TransformResult) *actionkit.ErrorResult {
	colors := []string{"red", "green", "blue"}
	if lookForRed {
		colors = []string{"red"}
	}

	lookupResults, err := actx.LookupClient.Lookup(ctx, "go_scheduled_lookup_table", &actionkit.LookupOptions{
		MatchingColumnValues: map[string][]string{"color": colors},
		ResultColumns:        []string{"column_a"},
	})
	if err != nil {
		return actionkit.NewErrorResult(actx, "Unable to lookup RGB rows").SetContext(err.Error())
	}

	if lookupResults.TotalCount > 0 {
		var columnAValues []string
		for _, row := range lookupResults.Rows {
			columnAValues = append(columnAValues, row["column_a"])
		}
		result.AddMetadata("RGB Rows", strings.Join(columnAValues, ", "))
		if lookupResults.Modified != nil {
			result.AddMetadata("colorTableLastModified", lookupResults.Modified.Format(time.RFC3339))
		}
		if lookupResults.Created != nil {
			result.AddMetadata("colorTableCreated", lookupResults.Created.Format(time.RFC3339))
		}
	} else {
		return actionkit.NewErrorResult(actx, "No RGB results found")
	}

	return nil
}

func (a *HelloWorldGoTransform) lookupPurple(ctx context.Context, actx *actionkit.ActionContext, result *actionkit.TransformResult) *actionkit.ErrorResult {
	lookupResults, err := actx.LookupClient.Lookup(ctx, "go_scheduled_lookup_table", &actionkit.LookupOptions{
		MatchingColumnValues: map[string][]string{"color": {"purple"}},
		ResultColumns:        []string{"column_a"},
	})
	if err != nil {
		return actionkit.NewErrorResult(actx, "Unable to lookup purple rows").SetContext(err.Error())
	}

	result.AddMetadata("purpleCount", fmt.Sprintf("%d", lookupResults.TotalCount))
	if lookupResults.Created != nil {
		result.AddMetadata("purpleCreated", lookupResults.Created.Format(time.RFC3339))
	}
	if lookupResults.Modified != nil {
		result.AddMetadata("purpleModified", lookupResults.Modified.Format(time.RFC3339))
	}

	return nil
}

func (a *HelloWorldGoTransform) lookupDrivers(ctx context.Context, actx *actionkit.ActionContext, result *actionkit.TransformResult) *actionkit.ErrorResult {
	lookupResults, err := actx.LookupClient.Lookup(ctx, "go_csv_lookup_table", &actionkit.LookupOptions{
		MatchingColumnValues: map[string][]string{"team": {"McLaren"}},
	})
	if err != nil {
		return actionkit.NewErrorResult(actx, "Unable to lookup McLaren drivers").SetContext(err.Error())
	}

	var drivers []string
	for _, row := range lookupResults.Rows {
		drivers = append(drivers, fmt.Sprintf("%s %s", row["first"], row["last"]))
	}
	result.AddMetadata("McLaren Drivers", strings.Join(drivers, ", "))

	return nil
}

func (a *HelloWorldGoTransform) sometimesTrue() bool {
	return rand.Intn(2) == 1
}
