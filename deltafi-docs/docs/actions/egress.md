# Egress Action

## Description

An Egress Action sends formatted content to a destination.

## Java

### Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `EgressInput` providing the formatted content and metadata to send

### Egress Input

```java
public class EgressInput {
    private ActionContext actionContext;
    private Content content;
    private Map<String, String> metadata;
}
```

### Return Types

The `egress` method must return an `EgressResultType`, which is implemented by `EgressResult`,  `ErrorResult`, and
`FilterResult`.

Returning an `EgressResult` indicates a successful egress. It requires the number of bytes sent, which is used to
generate egress metrics.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class HelloWorldEgressAction extends EgressAction<Parameters> {
    public HelloWorldEgressAction() {
        super("Hello pretends to egress");
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull Parameters params, @NotNull EgressInput egressInput) {
        String data = new String(egressInput.loadFormattedDataBytes(), StandardCharsets.UTF_8);
        if (data.contains("error")) {
            return new ErrorResult(context, "Failed to egress");
        }

        return new EgressResult(context, "pocUrl", 100);
    }
}
```
## Python

### Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `EgressInput` providing the formatted content and metadata to send

### Egress Input

```python
class EgressInput(NamedTuple):
    content: Content
    metadata: dict
```

### Return Types

The `egress()` method must return one of: `EgressResult`, `ErrorResult`, or `FilterResult`.

Returning an `EgressResult` indicates a successful egress. It requires the number of bytes sent, which is used to
generate egress metrics.

### Example

```python
from deltafi.action import EgressAction
from deltafi.domain import Context
from deltafi.input import EgressInput
from deltafi.result import EgressResult
from pydantic import BaseModel


class HelloWorldEgressAction(EgressAction):
    def __init__(self):
        super().__init__('Hello pretends to egress')

    def egress(self, context: Context, params: BaseModel, egress_input: EgressInput):
        return EgressResult(context, 100)
```

## Go

### Interface

An EgressAction must implement the `Egress` method which receives:
* `context.Context` for cancellation and timeouts
* `EgressInput` providing the formatted content and metadata to send

### Egress Input

```go
type EgressInput struct {
    ActionContext *ActionContext
    Content       []ActionContent
    Metadata      map[string]string
    Params        map[string]any
}
```

### Return Types

The `Egress` method must return a `Result` and an `error`. The `Result` is one of: `EgressResult` or `ErrorResult`.

Returning an `EgressResult` indicates a successful egress.

### Example

```go
package actions

import (
    "context"
    "strings"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func init() {
    actionkit.Register("HelloWorldGoEgressAction", &HelloWorldGoEgress{}).
        Describe("Hello pretends to egress")
}

type HelloWorldGoEgress struct{}

func (a *HelloWorldGoEgress) Egress(ctx context.Context, input actionkit.EgressInput) (actionkit.Result, error) {
    content := input.FirstContent()
    data, err := content.LoadString(ctx, input.ActionContext.ContentService)
    if err != nil {
        return actionkit.NewErrorResult(input.ActionContext, "Failed to load content"), nil
    }

    if strings.Contains(data, "error") {
        return actionkit.NewErrorResult(input.ActionContext, "Failed to egress"), nil
    }

    return actionkit.NewEgressResult(input.ActionContext), nil
}
```

## C++

### Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext&` describing the action's environment and current execution
* `EgressInput&` providing the formatted content and metadata to send

Actions with typed parameters receive an additional `Params&` argument between context and input.

### Egress Input

```cpp
struct EgressInput {
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;
};
```

### Return Types

The `egress` method must return an `EgressResultType`, which is a `std::variant` of `EgressResult` and `ErrorResult`.

### Example

```cpp
#pragma once
#include <deltafi/plugin>

class HelloWorldCppEgressAction {
public:
    deltafi::EgressResultType egress(deltafi::ActionContext& context,
                                     deltafi::EgressInput& input) {
        auto& content = input.first_content();
        auto data = content.load_string();

        if (data.find("error") != std::string::npos) {
            return deltafi::ErrorResult(context, "Failed to egress");
        }

        deltafi::EgressResult result(context);
        result.log_info() << "Egressed " << data.size() << " bytes";
        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppEgressAction,
    "HelloWorldCppEgressAction",
    "Hello pretends to egress")
```
