/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Event MCP tools for the DeltaFi event log REST API
// ABOUTME: Provides list, get, create, acknowledge, and delete operations for events

package tools

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_events",
			"List events from the event log. Supports optional filtering and paging; results are ordered newest first.",
			mcp.WithObject("filters", mcp.Properties(eventFilterProperties()), mcp.AdditionalProperties(false), mcp.Description("Event filters")),
			mcp.WithInteger("offset", mcp.Min(0), mcp.Description("Number of records to skip (default 0)")),
			mcp.WithInteger("size", mcp.Min(0), mcp.Description("Maximum number of records to return (default 20)")),
		),
		Handler: listEvents,
		Tags:    []string{"events"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_events_with_count",
			"List events with total count metadata, useful for pagination UIs",
			mcp.WithObject("filters", mcp.Properties(eventFilterProperties()), mcp.AdditionalProperties(false), mcp.Description("Event filters")),
			mcp.WithInteger("offset", mcp.Min(0), mcp.Description("Number of records to skip (default 0)")),
			mcp.WithInteger("size", mcp.Min(0), mcp.Description("Maximum number of records to return (default 20)")),
		),
		Handler: listEventsWithCount,
		Tags:    []string{"events"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_event",
			"Get a single event by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Event ID (UUID)")),
		),
		Handler: getEvent,
		Tags:    []string{"events"},
	})

	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("create_event",
			"Create a new event in the event log",
			mcp.WithString("summary", mcp.Required(), mcp.Description("Short summary of the event")),
			mcp.WithString("severity", mcp.Description("Severity: error, warn, info (default), or success")),
			mcp.WithString("content", mcp.Description("Optional detailed body, markdown supported")),
			mcp.WithString("source", mcp.Description("Source label for the event (default MCP)")),
			mcp.WithBoolean("notification", mcp.Description("If true, mark the event as important / notify users")),
		),
		Handler: createEvent,
		Tags:    []string{"events"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("acknowledge_event",
			"Set the acknowledged state of an event",
			mcp.WithString("id", mcp.Required(), mcp.Description("Event ID (UUID)")),
			mcp.WithBoolean("acknowledged", mcp.Required(), mcp.Description("True to acknowledge, false to unacknowledge")),
		),
		Handler: acknowledgeEvent,
		Tags:    []string{"events"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("delete_event",
			"Delete an event from the event log",
			mcp.WithString("id", mcp.Required(), mcp.Description("Event ID (UUID)")),
		),
		Handler: deleteEvent,
		Tags:    []string{"events"},
	})
}

func listEvents(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	path, err := buildEventsPath("/api/v2/events", req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var events []api.Event
	if err := client.Get(path, &events, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(events)
}

func listEventsWithCount(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	path, err := buildEventsPath("/api/v2/events/with-count", req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var result map[string]any
	if err := client.Get(path, &result, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(result)
}

func getEvent(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "event ID")
	if errResult != nil {
		return errResult, nil
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var event api.Event
	if err := client.Get("/api/v2/events/"+url.PathEscape(id.String()), &event, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(event)
}

func createEvent(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	summary, errResult := requireNonEmptyString(req, "summary")
	if errResult != nil {
		return errResult, nil
	}

	event := api.NewEvent()
	event.Source = "MCP"
	event.Summary = summary
	event.Severity = api.InfoSeverity

	if sev, err := req.RequireString("severity"); err == nil && sev != "" {
		switch sev {
		case api.ErrorSeverity, api.WarnSeverity, api.InfoSeverity, api.SuccessSeverity:
			event.Severity = sev
		default:
			return registry.ErrorResult(fmt.Sprintf("invalid severity %q (expected error, warn, info, or success)", sev)), nil
		}
	}
	if content, err := req.RequireString("content"); err == nil {
		event.Content = content
	}
	if source, err := req.RequireString("source"); err == nil && source != "" {
		event.Source = source
	}
	if notification, err := req.RequireBool("notification"); err == nil {
		event.Notification = notification
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var created api.Event
	if err := client.Post("/api/v2/events", event, &created, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(created)
}

func acknowledgeEvent(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "event ID")
	if errResult != nil {
		return errResult, nil
	}
	acknowledged, err := req.RequireBool("acknowledged")
	if err != nil {
		return registry.ErrorResult("acknowledged parameter is required"), nil
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	suffix := "/acknowledge"
	if !acknowledged {
		suffix = "/unacknowledge"
	}
	path := "/api/v2/events/" + url.PathEscape(id.String()) + suffix

	var event api.Event
	if err := client.Put(path, nil, &event, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(event)
}

func deleteEvent(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "event ID")
	if errResult != nil {
		return errResult, nil
	}

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	path := "/api/v2/events/" + url.PathEscape(id.String())
	var event api.Event
	if err := client.DoWithBody("DELETE", path, nil, &event, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(event)
}

func buildEventsPath(base string, req mcp.CallToolRequest) (string, error) {
	values := url.Values{}
	supportedFilters := map[string]struct{}{
		"id": {}, "severity": {}, "summary": {}, "content": {}, "source": {},
		"acknowledged": {}, "notification": {}, "start": {}, "end": {},
	}

	args := req.GetArguments()
	if raw, ok := args["filters"]; ok && raw != nil {
		filters, ok := raw.(map[string]any)
		if !ok {
			return "", fmt.Errorf("filters must be a JSON object mapping filter keys to values")
		}
		for key, v := range filters {
			if _, ok := supportedFilters[key]; !ok {
				return "", fmt.Errorf("unsupported event filter %q", key)
			}
			switch val := v.(type) {
			case nil:
				// skip nil filters
			case string:
				values.Set(key, val)
			case bool:
				values.Set(key, strconv.FormatBool(val))
			case float64:
				values.Set(key, strconv.FormatFloat(val, 'f', -1, 64))
			default:
				return "", fmt.Errorf("filters[%q] must be a string, number, bool, or null", key)
			}
		}
	}

	if offset, err := optionalNonNegativeInt(req, "offset"); err != nil {
		return "", err
	} else if offset != nil {
		values.Set("offset", strconv.Itoa(*offset))
	}
	if size, err := optionalNonNegativeInt(req, "size"); err != nil {
		return "", err
	} else if size != nil {
		values.Set("size", strconv.Itoa(*size))
	}

	if len(values) == 0 {
		return base, nil
	}
	return base + "?" + values.Encode(), nil
}
