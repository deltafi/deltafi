# Validate Action

## Description

A Validate Action validates formatted content for an Egress Flow.

## Java

### Interface

A ValidateAction must implement the `validate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `ValidateInput` providing the formatted content and metadata to validate

### Validate Input

```java
public class ValidateInput {
    private ActionContext actionContext;
    private Content content;
    private Map<String, String> metadata;
}
```

### Return Types

The `validate` method must return a `ValidateResultType`, which is implemented by `ValidateResult`, `ErrorResult`, and
`FilterResult`.

Returning a `ValidateResult` is indicative of a passed validation.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.validate.ValidateAction;
import org.deltafi.actionkit.action.validate.ValidateInput;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class HelloWorldValidateAction extends ValidateAction<Parameters> {
    public HelloWorldValidateAction() {
        super("Hello validation");
    }

    @Override
    public ValidateResultType validate(@NotNull ActionContext context, @NotNull Parameters params, @NotNull ValidateInput validateInput) {
        String data = new String(validateInput.loadFormattedDataBytes(), StandardCharsets.UTF_8);
        if (data.contains("error")) {
            return new ErrorResult(context, "Failed to validate");
        }

        return new ValidateResult(context);
    }
}
```
## Python

### Interface

A ValidateAction must implement the `validate` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `ValidateInput` providing the formatted content and metadata to validate

### Validate Input

```python
class ValidateInput(NamedTuple):
    content: Content
    metadata: dict
```

### Return Types

The `validate()` method must return one of: `ValidateResult`, `ErrorResult`, or `FilterResult`.

Returning a `ValidateResult` is indicative of a passed validation.

### Example

```python
from deltafi.action import ValidateAction
from deltafi.domain import Context
from deltafi.input import ValidateInput
from deltafi.result import ValidateResult
from pydantic import BaseModel


class HelloWorldValidateAction(ValidateAction):
    def __init__(self):
        super().__init__('Hello validation')

    def validate(self, context: Context, params: BaseModel, validate_input: ValidateInput):
        return ValidateResult(context)
```
