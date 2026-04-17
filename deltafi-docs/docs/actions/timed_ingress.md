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

### Interface

A TimedIngressAction must implement the `Ingress` method which receives:
* `*IngressInput` providing the memo, flow name, and action name
* A typed params struct containing flow parameters specified for the action

### Ingress Input

```go
type IngressInput struct {
    Memo       string
    FlowName   string
    ActionName string
}
```

The `IngressInput` provides a `NewDeltafile(name)` method to create new Deltafiles for ingestion.

### Return Types

The `Ingress` method returns an `*IngressResult`. The result contains ingressed Deltafile items, a memo string,
executeImmediate flag, and health status fields.

### Example

```go
package actions

import (
    "fmt"
    "strconv"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
    actionkit.RegisterIngress("HelloWorldGoTimedIngressAction", &HelloWorldGoTimedIngress{}).
        Describe("Create some DeltaFiles for hello-world consumption")
}

type HelloWorldGoTimedIngressParams struct{}

type HelloWorldGoTimedIngress struct{}

func (a *HelloWorldGoTimedIngress) Ingress(input *actionkit.IngressInput, params HelloWorldGoTimedIngressParams) *actionkit.IngressResult {
    index := 0
    if input.Memo != "" {
        if parsed, err := strconv.Atoi(input.Memo); err == nil {
            index = parsed + 1
        }
    }

    filename := fmt.Sprintf("%s-%d", input.FlowName, index)

    item := input.NewDeltafile(filename)
    item.AddMetadata("index", strconv.Itoa(index))

    contentData := fmt.Sprintf("Some content, part %d", index)
    if _, err := item.SaveBytesContent([]byte(contentData), filename, "text/plain"); err != nil {
        return actionkit.NewIngressResult().
            SetStatus("UNHEALTHY").
            SetStatusMessage("Failed to save content: " + err.Error())
    }

    result := actionkit.NewIngressResult()
    result.AddItem(item)
    result.SetMemo(strconv.Itoa(index))
    return result
}
```

## C++

### Interface

A TimedIngressAction must implement the `ingress` method which receives:
* `ActionContext&` describing the action's environment and current execution

Actions with typed parameters receive an additional `Params&` argument.

### Return Types

The `ingress` method must return an `IngressResultType`, which is a `std::variant` of `IngressResult` and `ErrorResult`.

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
