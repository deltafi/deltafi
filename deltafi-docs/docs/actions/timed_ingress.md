# Timed Ingress Action

## Description

A Timed Ingress Action is called on a schedule specified by a cron expression and produces 0..n new DeltaFiles upon
each execution. Executions are guaranteed to be serial, meaning that even if multiple instances of a timed ingress
action are running it will only be called once at a time.

A Timed Ingress Action may pass a memo back that will in turn be passed to the next execution of
that action, allowing for a bookmark to be kept if the Action needs to keep track of where it left off.

The action may also send back an executeImmediate boolean indicating that it should be called again immediately, not
waiting for the next scheduled execution.

The action can optionally set a status (HEALTHY, DEGRADED, or UNHEALTHY) and freeform statusMessage string. These are
returned to core for informational purposes only, and will be displayed to the operator in the GUI.

In the case of a failure, an action will likely send back an empty list of ingressResultItem, the same memo that was
passed in (plus any additional information it may want to convey to the next execution about the error that occurred),
and an UNHEALTHY status with a descriptive statusMessage about what went wrong. Depending on the scenario,
the action might want to also send back the executeImmediate flag as TRUE to indicate an immediate reattempt.

## Java

### Interface

A TimedIngressAction must implement the `ingress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action

### Return Types

The `ingress` method must return an `IngressResultType`, which is implemented by `IngressResult` and `ErrorResult`.

The `IngressResult` contains a list of `IngressResultItem`, a String `memo`, boolean `executeImmediate`,
IngressStatus enum `status`, and String `statusMessage`.

Each `IngressResultItem` contains the content, metadata, and annotations used to create a DeltaFile. 

### Example

```java
package org.deltafi.helloworld.actions;

import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultItem;
import org.deltafi.actionkit.action.ingress.IngressResultType;
import org.deltafi.actionkit.action.ingress.TimedIngressAction;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldTimedIngressAction extends TimedIngressAction<ActionParameters> {
    public HelloWorldTimedIngressAction() {
        super("Create some DeltaFiles for hello-world consumption");
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull ActionParameters params) {
        int index = 0;
        if (context.getMemo() != null) {
            index = 1 + Integer.parseInt(context.getMemo());
        }

        String filename = context.getFlow() + "-" + index;

        IngressResultItem resultItem = new IngressResultItem(context, filename);

        resultItem.addMetadata("index", String.valueOf(index));
        resultItem.saveContent("Some content, part " + index, filename, "text/plain");

        IngressResult ingressResult = new IngressResult(context);
        ingressResult.addItem(resultItem);
        ingressResult.setMemo(String.valueOf(index));
        return ingressResult;
    }
}
```

## Python

### Interface

A TimedIngressAction must implement the `ingress` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.

### Return Types

The `ingress()` method must return one of: `IngressResult` or `ErrorResult`.

The `IngressResult` contains a list of `IngressResultItem`, a String `memo`, boolean `executeImmediate`,
IngressStatus enum `status`, and String `statusMessage`.

Each `IngressResultItem` contains the content, metadata, and annotations used to create a DeltaFile.

### Example

```python
from deltafi.action import TimedIngressAction
from deltafi.domain import Context
from deltafi.result import IngressResult, IngressResultItem
from pydantic.v1 import BaseModel


class HelloWorldTimedIngressAction(TimedIngressAction):
    def __init__(self):
        super().__init__('Create some DeltaFiles for hello-world consumption')

    def ingress(self, context: Context, params: BaseModel):
        index = 0
        if context.memo is not None:
            index = 1 + int(context.memo)

        filename = f"{context.action_flow}-{index}"

        result_item = IngressResultItem(context, filename)
        result_item.save_string_content(f"Item Number {index}", filename, "text/plain")
        result_item.add_metadata("index", str(index))

        ingress_result = IngressResult(context)
        ingress_result.add_item(result_item)
        ingress_result.memo = str(index)
        return ingress_result
```

## Go

::: warning Beta
The Go action kit is currently in beta. Interfaces may change in future releases.
:::

### Interface

A TimedIngressAction must implement the `Ingress` method which receives:
* `*TimedIngressDeltafile` providing per-cycle context (memo, flow name, action name), item construction (`NewItem`), event-level state setters (`SetMemo`, `SetStatus`, `SetStatusMessage`, `SetExecuteImmediate`), error signaling (`Errorf`, `SetErrorContext`), and logging (`LogInfo`/`LogWarn`/`LogError`)
* A typed params struct containing flow parameters specified for the action

The action returns the same `*TimedIngressDeltafile`. Each call to `NewItem(name)` returns an `*IngressDeltafile` — a write-only view of a Deltafile that exposes only content saves (`SaveStringContent`/`SaveBytesContent`/`SaveReaderContent`/`AddContent`/`AddContentList`), metadata writes (`AddMetadata`/`AddMetadataMap`), and annotations (`AddAnnotation`). Reads, child creation, error/filter signaling, and logging are intentionally not exposed on `IngressDeltafile` — those concerns belong on the surrounding `TimedIngressDeltafile`.

### Return Types

The kit inspects the returned `*TimedIngressDeltafile` to determine the outcome:

* **Ingress result** — when no error has been signaled; contains each item produced via `NewItem`, plus memo/status/executeImmediate state
* **Error result** — when `Errorf` has been called on the `*TimedIngressDeltafile`

### File layout

Each Go action lives in two files inside the plugin's `actions/` directory: `<name>_action.go` holds the action struct, `init()` registration, and the action method; `<name>_action_params.go` holds the typed `*Params` struct only. See the [transform action docs](./transform.md) for the full description of supported parameter tag types and the `make jsonFromParams` / `make paramsFromJson` round-trip tooling — the same conventions apply to every Go action type.

### Example

`actions/hello_world_go_timed_ingress_action.go`:

```go
package actions

import (
    "fmt"
    "strconv"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
    actionkit.RegisterIngress("HelloWorldGoTimedIngressAction", &HelloWorldGoTimedIngressAction{}).
        WithParams(HelloWorldGoTimedIngressActionParams{}).
        Describe("Create some DeltaFiles for hello-world consumption")
}

type HelloWorldGoTimedIngressAction struct{}

func (a *HelloWorldGoTimedIngressAction) Ingress(deltafile *actionkit.TimedIngressDeltafile, params HelloWorldGoTimedIngressActionParams) *actionkit.TimedIngressDeltafile {
    index := 0
    if deltafile.Memo != "" {
        if parsed, err := strconv.Atoi(deltafile.Memo); err == nil {
            index = parsed + 1
        }
    }

    filename := fmt.Sprintf("%s-%d", deltafile.FlowName, index)

    item := deltafile.NewItem(filename)
    item.AddMetadata("index", strconv.Itoa(index))

    contentData := fmt.Sprintf("Some content, part %d", index)
    if _, err := item.SaveBytesContent(filename, "text/plain", []byte(contentData)); err != nil {
        return deltafile.Errorf("Failed to save content: %v", err)
    }

    return deltafile.SetMemo(strconv.Itoa(index))
}
```

`actions/hello_world_go_timed_ingress_action_params.go`:

```go
package actions

type HelloWorldGoTimedIngressActionParams struct{}
```

## C++

::: warning Beta
The C++ action kit is currently in beta. Interfaces may change in future releases.
:::

### Interface

A TimedIngressAction must implement the `ingress` method which receives:
* `ActionContext&` describing the action's environment and current execution

Actions with typed parameters receive an additional `Params&` argument.

### Return Types

The `ingress` method must return an `IngressResultType`, which is a `std::variant` of `IngressResult`, `FilterResult`, and `ErrorResult`.

### Example

```cpp
#pragma once
#include <deltafi/plugin>

class HelloWorldCppTimedIngressAction {
public:
    deltafi::IngressResultType ingress(deltafi::ActionContext& context) {
        int index = 0;
        if (!context.memo.empty()) {
            try { index = std::stoi(context.memo) + 1; } catch (...) {}
        }

        auto filename = context.flow_name + "-" + std::to_string(index);

        deltafi::IngressResult result(context);
        auto& item = result.create_item(filename);
        item.add_metadata("index", std::to_string(index));
        item.save_content("Some content, part " + std::to_string(index), filename, "text/plain");

        result.set_memo(std::to_string(index));
        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppTimedIngressAction,
    "HelloWorldCppTimedIngressAction",
    "Create some DeltaFiles for hello-world consumption")
```
