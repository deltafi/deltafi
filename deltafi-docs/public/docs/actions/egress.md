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

Returning an `EgressResult` indicates a successful egress. It requires a destination where the file was sent and the
number of bytes sent. These fields are used to automatically generate egress metrics.

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

Returning an `EgressResult` indicates a successful egress. It requires a destination where the file was sent and the
number of bytes sent. These fields are used to automatically generate egress metrics.

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
        return EgressResult(context, "pocUrl", 100)
```
