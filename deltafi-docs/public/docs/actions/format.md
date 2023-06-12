# Format Action

## Description

A Format Action creates formatted content for an Egress Flow.

## Java

### Interface

A FormatAction must implement the `format` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `FormatInput` providing the content, metadata, domains, and enrichments used to create the formatted content

A FormatAction also must implement the `getRequiresDomains()` method, and may implement the `getRequiresEnrichments()`
method. These methods return a list of Domains and Enrichments that are required to be present in the input it
receives. Either of these can return `DeltaFiConstants.MATCHES_ANY` if the action can accept any Domain or Enrichment.

### Format Input

```java
public class FormatInput {
    List<ActionContent> contentList;
    Map<String, String> metadata;
    Map<String, Domain> domains;
    Map<String, Enrichment> enrichment;
}
```

### Return Types

The `format` method must return a `FormatResultType`, which is implemented by `FormatResult`, `FormatManyResult`,
`ErrorResult`, and `FilterResult`.

The `FormatResult` contains the content and metadata created by the `FormatAction`.  
The `FormatManyResult` contains a list of `FormatResult`. Each `FormatResult` will be validated and egressed
independently.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.format.*;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelloWorldFormatAction extends FormatAction<Parameters> {
    public HelloWorldFormatAction() {
        super("Format or formatMany");
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of("javaHelloWorld");
    }

    @Override
    public List<String> getRequiresEnrichments() {
        return List.of("helloWorld");
    }

    @Override
    public FormatResultType format(@NotNull ActionContext context, @NotNull Parameters params, @NotNull FormatInput formatInput) {
        String data = formatInput.content(0).loadString() + "\nHelloWorldFormatAction did its thing";
        FormatResult formatResult = new FormatResult(context, data.getBytes(), "formattedHello", "text/plain");
        formatResult.addMetadata("segment", "1");

        if ((Math.random() * 5) > 1) {
            return formatResult;
        }

        FormatManyResult formatManyResult = new FormatManyResult(context);
        formatManyResult.add(formatResult);

        data = data + " a second time";
        FormatResult secondFormatResult = new FormatResult(context, data.getBytes(), "formattedHello", "text/plain");
        secondFormatResult.addMetadata("segment", "2");
        formatManyResult.add(secondFormatResult);

        return formatManyResult;
    }
}
```

## Python

### Interface

A FormatAction must implement the `format` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `FormatInput` providing the content, metadata, domains, and enrichments used to create the formatted content

A list of required domains, and a list of required enrichments must be passed to the FormatAction constructor. If the
Action only requires Domains, an empty list can be passed for required enrichments.

### Format Input

```python
class FormatInput(NamedTuple):
    content: List[Content]
    metadata: dict
    domains: Dict[str, Domain]
    enrichment: Dict[str, Domain]
```

### Return Types

The `format()` method must return one of: `FormatResult`, `FormatManyResult`, `ErrorResult`, or `FilterResult`.

The `FormatResult` contains the content and metadata created by the `FormatAction`.  
The `FormatManyResult` contains a list of `FormatResult`. Each `FormatResult` will be validated and egressed
independently.

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
        data = f"{format_input.content[0].load_str()}\nHelloWorldFormatAction did its thing"
        format_result = FormatResult(context)
        format_result.add_content(data, 'formattedHello', 'text/plain')
        format_result.add_metadata("segment", "1")

        if randrange(5) != 0:
            return format_result
        else:
            format_many_result = FormatManyResult(context)
            format_many_result.add_format_result(format_result)

            data = f"{data} a second time"
            second_format_result = FormatResult(context)
            second_format_result.add_content(data, 'formattedHello', 'text/plain')
            second_format_result.add_metadata("segment", "2")
            format_many_result.add_format_result(second_format_result)

            return format_many_result
```
