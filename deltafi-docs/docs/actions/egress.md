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

::: warning Beta
The Go action kit is currently in beta. Interfaces may change in future releases.
:::

### Interface

An EgressAction must implement the `Egress` method which receives:
* `*EgressDeltafile` providing the content, metadata, and context fields
* A typed params struct containing flow parameters specified for the action

The action returns the same `*EgressDeltafile`. A successful egress simply returns it unchanged.

`EgressDeltafile` is a constrained view of a Deltafile. It exposes content and metadata reads, logging, annotations, and error/filter signaling, but does not expose output-content or metadata mutations (`SaveStringContent`, `AddContent`, `PassthroughContent`, `AddChild`, `AddMetadata`, etc.). The EGRESS event format does not carry those mutations, so the restriction is enforced at compile time rather than as a silent drop.

### Return Types

The `Egress` method returns an `*EgressDeltafile`. The kit inspects the underlying state:

* **Egress result** — when the EgressDeltafile is returned without error or filter state (successful egress)
* **Error result** — when `Errorf` has been called on the EgressDeltafile
* **Filter result** — when `Filterf` has been called on the EgressDeltafile

### File layout

Each Go action lives in two files inside the plugin's `actions/` directory: `<name>_action.go` holds the action struct, `init()` registration, and the action method; `<name>_action_params.go` holds the typed `*Params` struct only. See the [transform action docs](./transform.md) for the full description of supported parameter tag types and the `make jsonFromParams` / `make paramsFromJson` round-trip tooling — the same conventions apply to every Go action type.

### Example

`actions/hello_world_go_egress_action.go`:

```go
package actions

import (
    "strings"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func init() {
    actionkit.RegisterEgress("HelloWorldGoEgressAction", &HelloWorldGoEgressAction{}).
        WithParams(HelloWorldGoEgressActionParams{}).
        Describe("Hello pretends to egress")
}

type HelloWorldGoEgressAction struct{}

func (a *HelloWorldGoEgressAction) Egress(df *actionkit.EgressDeltafile, params HelloWorldGoEgressActionParams) *actionkit.EgressDeltafile {
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

`actions/hello_world_go_egress_action_params.go`:

```go
package actions

type HelloWorldGoEgressActionParams struct{}
```

## C++

::: warning Beta
The C++ action kit is currently in beta. Interfaces may change in future releases.
:::

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
