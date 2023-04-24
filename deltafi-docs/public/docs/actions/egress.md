# Egress Action

## Java

### Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `EgressInput` provides source, metadata, and formatted data as input to the action

### Egress Input

```java
public class EgressInput extends FormattedDataInput {
    // Original filename
    String sourceFilename;
    // Ingress flow assigned to the DeltaFile
    String ingressFlow;
}
```

### Return Types

The `egress` method must return an `EgressResultType`, which is currently implemented by `EgressResult`,  `ErrorResult`, and `FilterResult`.

The `EgressResult` requires a destination where the file was egressed and a number of bytes sent. These two fields are used to automatically generate egress metrics.
The return of an `EgressResult` indicates success.

### Example

```java
package org.deltafi.core.action;

import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class FilterEgressAction extends EgressAction<ActionParameters> {
    public FilterEgressAction() {
        super("Filters on egress");
    }

    @Override
    public EgressResultType egress(
        @NotNull ActionContext context,
        @NotNull ActionParameters p,
        @NotNull EgressInput input) {

        return new FilterResult(context, "filtered");
    }
}
```
## Python

### Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `EgressInput` provides source, metadata, and formatted data as input to the action

### Egress Input

A description of each Input field can be found in the Java section above.

```python
class EgressInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    formatted_data: FormattedData
```

### Return Types

The `egress()` method must return one of: `EgressResult`, `ErrorResult`, or `FilterResult`.

The `EgressResult` requires a destination where the file was egressed and a number of bytes sent. These two fields are used to automatically generate egress metrics.

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
        return EgressResult("pocUrl", 100)
```
