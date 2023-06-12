# Enrich Action

## Description

An Enrich Action adds Enrichments. It may also add annotations.

## Java

### Interface

An EgressAction must implement the `enrich` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `EnrichInput` providing the Domains and prior Enrichments along with the content and metadata to be used to add
Enrichments

An EnrichAction also must implement the `getRequiresDomains()` method, and may implement the `getRequiresEnrichments()`
method. These methods return a list of Domains and Enrichments that are required to be present in the input it
receives. Either of these can return `DeltaFiConstants.MATCHES_ANY` if the action can accept any Domain or Enrichment.

### Enrich Input

```java
public class EnrichInput {
    List<ActionContent> contentList;
    Map<String, String> metadata;
    Map<String, Domain> domains;
    Map<String, Enrichment> enrichments;
}
```

### Return Types

The `enrich` method must return an `EnrichResultType`, which is implemented by `EnrichResult`, and `ErrorResult`.

The `EnrichResult` contains the Enrichments and Annotations to add.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichInput;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.enrich.EnrichResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelloWorldEnrichAction extends EnrichAction<Parameters> {
    public HelloWorldEnrichAction() {
        super("We need more hellos in the world");
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of("javaHelloWorld");
    }

    @Override
    public EnrichResultType enrich(@NotNull ActionContext context, @NotNull Parameters params, @NotNull EnrichInput enrichInput) {
        if ((Math.random() * 1000) < 1)  {
            return new ErrorResult(context, "Something bad happened I guess");
        }
        
        EnrichResult enrichResult = new EnrichResult(context);
        enrichResult.addEnrichment("helloWorld", "java was here", "text/plain");
        enrichResult.addAnnotation("enrichKey", "enrichValue");
        return enrichResult;
    }
}
```

## Python

### Interface

An EnrichAction must implement the `enrich` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `EnrichInput` providing the Domains and prior Enrichments along with the content and metadata to be used to add
Enrichments

A list of required domains, and a list of required enrichments must be passed to the EnrichAction constructor. If the
Action only requires Domains, an empty list can be passed for required enrichments.

### Enrich Input

```python
class EnrichInput(NamedTuple):
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]
```

### Return Types

The `enrich()` method must return one of: `EnrichResult`, or `ErrorResult`.

The `EnrichResult` contains the Enrichments and Annotations to add.

### Example

```python
from deltafi.action import EnrichAction
from deltafi.domain import Context
from deltafi.input import EnrichInput
from deltafi.result import EnrichResult, ErrorResult
from pydantic import BaseModel
from random import randrange


class HelloWorldEnrichAction(EnrichAction):
    def __init__(self):
        super().__init__('We need more hellos in the world', ['pyHelloWorld'], [])

    def enrich(self, context: Context, params: BaseModel, enrich_input: EnrichInput):
        if randrange(1000) != 0:
            return EnrichResult(context)
                .enrich('helloWorld', 'python was here', 'text/plain')\
                .annotate('enrichKey', 'enrichValue')
        else:
            context.logger.error('haha gremlins')
            return ErrorResult(context, 'Something bad happened I guess', 'try again?')
```
