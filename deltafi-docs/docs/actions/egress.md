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
* `*Deltafile` providing the content, metadata, and context fields
* A typed params struct containing flow parameters specified for the action

The action returns the same `*Deltafile`. A successful egress simply returns the Deltafile unchanged.

### Return Types

The `Egress` method returns a `*Deltafile`. The kit inspects the Deltafile's state:

* **Egress result** — when the Deltafile is returned without error or filter state (successful egress)
* **Error result** — when `Errorf` has been called on the Deltafile
* **Filter result** — when `Filterf` has been called on the Deltafile

### Example

```go
package actions

import (
    "strings"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
    actionkit.RegisterEgress("HelloWorldGoEgressAction", &HelloWorldGoEgress{}).
        Describe("Hello pretends to egress")
}

type HelloWorldGoEgressParams struct{}

type HelloWorldGoEgress struct{}

func (a *HelloWorldGoEgress) Egress(df *actionkit.Deltafile, params HelloWorldGoEgressParams) *actionkit.Deltafile {
    content, err := df.FirstContent()
    if err != nil {
        return df.Errorf("Failed to load content").SetErrorContext(err.Error())
    }

    data, err := content.LoadString()
    if err != nil {
        return df.Errorf("Failed to load content").SetErrorContext(err.Error())
    }

    if strings.Contains(data, "error") {
        return df.Errorf("Failed to egress")
    }

    return df
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
