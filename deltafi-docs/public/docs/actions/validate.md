# Validate Action

## Java

### Interface

A ValidateAction must implement the `validate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `ValidateInput` provides source, metadata, and content input to the action

### Validate Input

```java
public class ValidateInput {
    // Original filename
    String sourceFilename;
    // Ingress flow assigned to the DeltaFile
    String ingressFlow;
    // Metadata passed in with the DeltaFile on ingress
    Map<String, String> sourceMetadata;
    // structure containing the content references that
    // were created by the FormatAction
    FormattedData formattedData;
```

### Return Types

The `validate` method must return a `ValidateResultType`, which is currently implemented by `ValidateResult`, `ErrorResult`, and `FilterResult`.

Any `ValidateResult` is indicative of a passed validation.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.validate.ValidateAction;
import org.deltafi.actionkit.action.validate.ValidateInput;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class RubberStampValidateAction extends ValidateAction<ActionParameters> {
    public RubberStampValidateAction() {
        super("Validate successfully every time");
    }

    public ValidateResultType validate(
        @NotNull ActionContext context,
        @NotNull ActionParameters params,
        @NotNull ValidateInput input) {

        return new ValidateResult(context);
    }
}
```
## Python

### Interface

A ValidateAction must implement the `validate` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `ValidateInput` provides source, metadata, and content input to the action

### Validate Input

A description of each Input field can be found in the Java section above.

```python
class ValidateInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    source_metadata: Dict[str, str]
    formatted_data: FormattedData
```

### Return Types

The `validate()` method must return one of: `ValidateResult`, `ErrorResult`, or `FilterResult`.

Any `ValidateResult` is indicative of a passed validation.

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
        return ValidateResult()
```
