# Domain Action

## Description

A Domain Action validates the provided Domains. It may also add annotations.

## Java

### Interface

A DomainAction must implement the `extractAndValidate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `DomainInput` providing the Domains to be validated along with the content and metadata

A DomainAction also must implement the `getRequiresDomains` method. This method returns a list of Domains that are
required to be present in the input it receives. This can return `DeltaFiConstants.MATCHES_ANY` if it can accept
any Domain.

### Domain Input

```java
public class DomainInput {
    List<ActionContent> contentList;
    Map<String, String> metadata;
    Map<String, Domain> domains;
}
```

### Return Types

The `extractAndValidate` method must return a `DomainResultType`, which is implemented by `DomainResult`, and
`ErrorResult`.

The `DomainResult` contains the annotations to add to the DeltaFile.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.domain.DomainAction;
import org.deltafi.actionkit.action.domain.DomainInput;
import org.deltafi.actionkit.action.domain.DomainResult;
import org.deltafi.actionkit.action.domain.DomainResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelloWorldDomainAction extends DomainAction<Parameters> {
    public HelloWorldDomainAction() {
        super("Hello domain");
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }

    @Override
    public DomainResultType extractAndValidate(@NotNull ActionContext context, @NotNull Parameters params,
            @NotNull DomainInput domainInput) {
        DomainResult domainResult = new DomainResult(context);
        domainResult.addAnnotation("domainKey", "domainValue");
        return domainResult;
    }
}
```
## Python

### Interface

A DomainAction must implement the `domain` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
  method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `DomainInput` providing the Domains to be validated along with the content and metadata

A list of required domains must be passed to the DomainAction constructor.

### Domain Input

```python
class DomainInput(NamedTuple):
    content: List[Content]
    metadata: Dict[str, str]
    domains: Dict[str, Domain]
```

### Return Types

The `domain()` method must return one of: `DomainResult`, or `ErrorResult`.

The `DomainResult` contains the annotations to add to the DeltaFile.

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
