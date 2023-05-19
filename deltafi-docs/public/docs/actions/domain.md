# Domain Action

## Java

### Interface

A DomainAction must implement the `extractAndValidate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `DomainInput` provides source, metadata, and content input to the action

A DomainAction also must implement the `getRequiresDomain` method.  This method return a list of
domains that are required to be present in DeltaFiles that it receives. This can return
`DeltaFiConstants.MATCHES_ANY` if you can accept any domain, which would then be defined in a flow yaml.

### Domain Input

```java
public class DomainInput {
    // Metadata emitted by the Load Action
    Map<String, String> metadata;
    // Named domains and their values
    Map<String, Domain> domains;
}
```

### Return Types

The `extractAndValidate` method must return a `DomainResultType`, which is currently implemented by `DomainResult`, and `ErrorResult`.

A `DomainResult` contains a map of annotations that will be searchable in the system.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.domain.DomainAction;
import org.deltafi.actionkit.action.domain.DomainInput;
import org.deltafi.actionkit.action.domain.DomainResult;
import org.deltafi.actionkit.action.domain.DomainResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.passthrough.param.RoteDomainParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@SuppressWarnings("unused")
public class RoteDomainAction extends DomainAction<RoteDomainParameters> {

    @SuppressWarnings("unused")
    public RoteDomainAction() {
        super("Populate enrichment with the parameterized key/value pairs");
    }

    public DomainResultType extractAndValidate(
        @NotNull ActionContext context,
        @NotNull RoteDomainParameters params,
        @NotNull DomainInput input) {

        if (null == input.domains || input.domains.isEmpty()) {
            return new ErrorResult(context, "no domain was sent");
        }

        DomainResult result = new DomainResult(context);
        if (null != params.getFieldsToIndex()) {
            params.getFieldsToIndex().forEach(field -> result.addAnnotation(field, input.metadata.getOrDefault(field, "missing")));
        }

        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
```
## Python

### Interface

A DomainAction must implement the `domain` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `DomainInput` provides source, metadata, and content input to the action

A list of required domains must be passed to the DomainAction constructor.

### Domain Input

```python
class DomainInput(NamedTuple):
    source_filename: str
    content: List[Content]
    metadata: Dict[str, str]
    domains: Dict[str, Domain]
```

### Return Types

The `domain()` method must return one of: `DomainResult`, or `ErrorResult`.

A `DomainResult` contains a map of key value pairs that will be searchable in the system.

### Example

```python
from deltafi.action import DomainAction
from deltafi.domain import Context
from deltafi.input import DomainInput
from deltafi.result import DomainResult
from pydantic import BaseModel


class HelloWorldDomainAction(DomainAction):
    def __init__(self):
        super().__init__('Hello domain', ['pyHelloWorld'])

    def domain(self, context: Context, params: BaseModel, domain_input: DomainInput):
        return DomainResult(context).annotate('domainKey', 'domainValue')
```
