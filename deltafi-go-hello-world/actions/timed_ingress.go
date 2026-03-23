// ABOUTME: Timed ingress action that creates DeltaFiles on a schedule.
// ABOUTME: Demonstrates memo-based state tracking and conditional metadata/annotation logic.

package actions

import (
	"context"
	"fmt"
	"strconv"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
	actionkit.Register("HelloWorldGoTimedIngressAction", &HelloWorldGoTimedIngress{}).
		Describe("Create some DeltaFiles for hello-world consumption")
}

// HelloWorldGoTimedIngress creates DeltaFiles on a cron schedule.
type HelloWorldGoTimedIngress struct{}

func (a *HelloWorldGoTimedIngress) Ingress(ctx context.Context, input actionkit.IngressInput) (actionkit.Result, error) {
	// Parse index from memo (defaults to 0, increments each run)
	index := 0
	if input.ActionContext.Memo != "" {
		if parsed, err := strconv.Atoi(input.ActionContext.Memo); err == nil {
			index = parsed + 1
		}
	}

	filename := fmt.Sprintf("%s-%d", input.ActionContext.FlowName, index)

	item := actionkit.NewIngressResultItem(filename)
	item.AddMetadata("index", strconv.Itoa(index))

	if index%3 == 1 {
		item.AddMetadata("hello", "transform")
		item.AddAnnotation("helloMode", "basic")
	} else if index%3 == 2 {
		item.AddMetadata("hello", "join")
		item.AddAnnotation("helloMode", "advanced")
		if index%2 == 0 {
			item.AddMetadata("join-group", "even")
		} else {
			item.AddMetadata("join-group", "odd")
		}
	}

	contentData := fmt.Sprintf("Some content, part %d", index)
	content, err := actionkit.SaveContentString(ctx, input.ActionContext.ContentService, input.ActionContext, contentData, filename, "text/plain")
	if err != nil {
		return actionkit.NewErrorResult(input.ActionContext, "Failed to save content").SetContext(err.Error()), nil
	}

	item.AddContent(*content)

	result := actionkit.NewIngressResult(input.ActionContext)
	result.AddItem(*item)
	result.SetMemo(strconv.Itoa(index))
	result.SetExecuteImmediate(false)
	result.SetStatusMessage(fmt.Sprintf("Successfully created %s", filename))
	result.LogInfo(fmt.Sprintf("Created timed ingress file: %s (index=%d)", filename, index))

	return result, nil
}
