# Enrich Action

## Java

### Interface

An EgressAction must implement the `enrich` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `EnrichInput` provides source, metadata, and content input to the action

An EnrichAction also must implement the `getRequiresDomain()` method, and my implemented the `getRequiresEnrichment()` method.  These methods return a list of
domains and enrichment that are required to be present in DeltaFiles that it receives. Either of these can return
`DeltaFiConstants.MATCHES_ANY` if you can accept any domain or enrichment, which would then be defined in a flow yaml.
If you require either just domains or just enrichment, you can set the other to an empty list.

### Enrich Input

```java
public class EnrichInput {
    // Original filename
    String sourceFilename;
    // Ingress flow assigned to the DeltaFile
    String ingressFlow;
    // Content emitted by the last ingress flow action, or as
    // received at Ingress if there was no action-generated content.
    List<Content> contentList;
    // Metadata produced by the Load Action
    Map<String, String> metadata;
    // named domains and their values
    Map<String, Domain> domains;
    // named enrichment and their values
    Map<String, Enrichment> enrichment;
}
```

### Return Types

The `enrich` method must return an `EnrichResultType`, which is currently implemented by `EnrichResult`,  and `ErrorResult`.

An `EnrichResult` contains the named enrichment entries and indexed metadata created by the `EnrichAction`.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichInput;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.enrich.EnrichResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.passthrough.param.RoteEnrichParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
@SuppressWarnings("unused")
public class RoteEnrichAction extends EnrichAction<RoteEnrichParameters> {

    @SuppressWarnings("unused")
    public RoteEnrichAction() {
        super("Populate enrichment with the parameterized key/value pairs");
    }

    public EnrichResultType enrich(
        @NotNull ActionContext context,
        @NotNull RoteEnrichParameters params,
        @NotNull EnrichInput input) {

        EnrichResult result = new EnrichResult(context);
        if (null != params.getEnrichments()) {
            params.getEnrichments().forEach((k, v) -> result.addEnrichment(k, v, MediaType.TEXT_PLAIN));
        }

        if (null != params.getIndexedMetadata()) {
            result.addIndexedMetadata(params.getIndexedMetadata());
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

An EnrichAction must implement the `enrich` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `EnrichInput` provides source, metadata, and content input to the action

A list of required domains, and list of required enrichment  must be passed to the EnrichAction constructor.
If you require either just domains or just enrichment, you can set the other to an empty list.

### Enrich Input

A description of each Input field can be found in the Java section above.

```python
class EnrichInput(NamedTuple):
    source_filename: str
    ingress_flow: str
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]
```

### Return Types

The `enrich()` method must return one of: `EnrichResult`, or `ErrorResult`.

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
            return EnrichResult().enrich('helloWorld', 'python was here', 'text/plain')\
                .index_metadata('enrichKey', 'enrichValue')
        else:
            context.logger.error('haha gremlins')
            return ErrorResult('Something bad happened I guess', 'try again?')
```
