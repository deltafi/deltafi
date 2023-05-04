# Format Action

## Java

### Interface

A FormatAction must implement the `format` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `FormatInput` provides source, metadata, and content input to the action

A FormatAction also must implement the `getRequiresDomain()` method, and my implemented the `getRequiresEnrichment()` method.  These methods return a list of
domains and enrichment that are required to be present in DeltaFiles that it receives. Either of these can return
`DeltaFiConstants.MATCHES_ANY` if you can accept any domain or enrichment, which would then be defined in a flow yaml.
If you require either just domains or just enrichment, you can set the other to an empty list.

### Format Input

```java
public class FormatInput {
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

The `format` method must return a `FormatResultType`, which is currently implemented by `FormatResult`, `FormatManyResult`, `ErrorResult`, and `FilterResult`.

A `FormatResult` includes the content and metadata created by the `FormatAction`.

A `FormatManyResult` is like a list of `FormatResult` where each entry will be validated and egressed independently.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoteFormatAction extends FormatAction<RoteParameters> {
    public RoteFormatAction() {
        super("Format the first result created by the load action with no transformation");
    }

    public FormatResultType format(@NotNull ActionContext context, @NotNull RoteParameters parameters, @NotNull FormatInput input) {
        return new FormatResult(context, input.getContentList().get(0));
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }

    @Override
    public List<String> getRequiresEnrichments() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
```

## Python

### Interface

A FormatAction must implement the `format` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `FormatInput` provides source, metadata, and content input to the action

A list of required domains, and list of required enrichment  must be passed to the FormatAction constructor.
If you require either just domains or just enrichment, you can set the other to an empty list.

### Format Input

A description of each Input field can be found in the Java section above.

```python
class FormatInput(NamedTuple):
    source_filename: str
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]
```

### Return Types

The `format()` method must return one of: `FormatResult`, `FormatManyResult`, `ErrorResult`, or `FilterResult`.

A `FormatResult` includes the content and metadata created by the `FormatAction`.

A `FormatManyResult` is like a list of `FormatResult` where each entry will be validated and egressed independently.

### Example

```python
from deltafi.action import FormatAction
from deltafi.domain import Context
from deltafi.input import FormatInput
from deltafi.result import FormatResult, FormatManyResult
from pydantic import BaseModel
from random import randrange


class HelloWorldFormatAction(FormatAction):
    def __init__(self):
        super().__init__('Format or formatMany', ['pyHelloWorld'], ['helloWorld'])

    def format(self, context: Context, params: BaseModel, format_input: FormatInput):
        content_reference = format_input.content[0].content_reference
        data = f"{context.content_service.get_str(content_reference)}\nHelloWorldFormatAction did its thing"
        new_content_reference = context.content_service.put_str(context.did, data, 'test/plain')
        format_result = FormatResult("formattedHello", new_content_reference)
        format_result.add_metadata("segment", "1")

        if randrange(5) != 0:
            return format_result
        else:
            format_many_result = FormatManyResult()
            format_many_result.add_format_result(format_result)

            data = f"{data} a second time"
            second_content_reference = context.content_service.put_str(context.did, data, 'test/plain')
            second_format_result = FormatResult("formattedHello", second_content_reference)
            second_format_result.add_metadata("segment", "2")
            format_many_result.add_format_result(second_format_result)

            return format_many_result
```
