# Transform Action

## Description

A Transform Action transforms the content it receives. It may also update metadata and add annotations.

## Java

### Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `TransformInput` providing the content and metadata to be transformed

### Transform Input

```java
public class TransformInput {
    List<ActionContent> contentList;
    Map<String, String> metadata;
}
```

### Return Types

The `transform` method must return a `TransformResultType`, which is implemented by `TransformResult`, `ReinjectResult`,
`ErrorResult`, and `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.
The `ReinjectResult` contains a list of `ReinjectEvent`. Each `ReinjectEvent` will create a new DeltaFile that will be
ingressed to a specified flow.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldTransformAction extends TransformAction<Parameters> {
    public HelloWorldTransformAction() {
        super("Add some content noting that we did a really good job");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull Parameters params, @NotNull TransformInput input) {
        if (context.getDid().toString().startsWith("2")) {
            return new FilterResult(context, "We prefer dids that do not start with 2");
        }
        
        String data = input.getContentList().get(0).loadString() + "\nHelloWorldTransformAction did a great job";

        TransformResult result = new TransformResult(context);
        result.addMetadata("transformKey", "transformValue");
        result.addAnnotation("transformAnnotation", "value");
        result.saveContent(data, "transform-named-me", "text/plain");
        return result;
    }
}
```

## Python

### Interface

A TransformAction must implement the `transform` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `TransformInput` providing the content and metadata to be transformed

### Transform Input

```python
class TransformInput(NamedTuple):
    content: List[Content]
    metadata: dict
```

### Return Types

The `transform()` method must return one of: `TransformResult`, `ReinjectResult`, `ErrorResult`, or `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.
The `ReinjectResult` contains a list of `ReinjectEvent`. Each `ReinjectEvent` will create a new DeltaFile that will be
ingressed to a specified flow.

### Example

```python
from deltafi.action import TransformAction
from deltafi.domain import Context
from deltafi.input import TransformInput
from deltafi.result import FilterResult, TransformResult
from pydantic import BaseModel


class HelloWorldTransformAction(TransformAction):
    def __init__(self):
        super().__init__('Add some content noting that we did a really good job')

    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        context.logger.info(f"Transforming {context.did}")
        if context.did.startswith('2'):
            return FilterResult(context, 'We prefer dids that do not start with 2')

        data = f"{transform_input.content[0].load_str()}\nHelloWorldTransformAction did a great job"

        return TransformResult(context)
            .save_string_content(data, 'transform-named-me', 'text/plain')
            .add_metadata('transformKey', 'transformValue')
            .annotate('transformAnnotation', 'value')
```

## Go

### Interface

A TransformAction must implement the `Transform` method which receives:
* `context.Context` for cancellation and timeouts
* `TransformInput` providing the content, metadata, and action context

### Transform Input

```go
type TransformInput struct {
    ActionContext *ActionContext
    Content       []ActionContent
    Metadata      map[string]string
    Params        map[string]any
}
```

### Return Types

The `Transform` method must return a `Result` and an `error`. The `Result` is one of: `TransformResult`, `ErrorResult`, or `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.

### Example

```go
package actions

import (
    "context"
    "strings"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func init() {
    actionkit.Register("HelloWorldGoTransformAction", &HelloWorldGoTransform{}).
        Describe("Add some content noting that we did a really good job")
}

type HelloWorldGoTransform struct{}

func (a *HelloWorldGoTransform) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
    if strings.HasPrefix(input.ActionContext.DID, "2") {
        return actionkit.NewFilterResult(input.ActionContext, "We prefer dids that do not start with 2"), nil
    }

    content := input.FirstContent()
    data, err := content.LoadString(ctx, input.ActionContext.ContentService)
    if err != nil {
        return actionkit.NewErrorResult(input.ActionContext, "Failed to load content"), nil
    }

    transformed := data + "\nHelloWorldGoTransformAction did a great job"

    newContent, err := actionkit.SaveContentString(ctx, input.ActionContext.ContentService, input.ActionContext, transformed, "transform-named-me", "text/plain")
    if err != nil {
        return actionkit.NewErrorResult(input.ActionContext, "Failed to save content"), nil
    }

    result := actionkit.NewTransformResult(input.ActionContext)
    result.AddContent(*newContent)
    result.AddMetadata("transformKey", "transformValue")
    result.AddAnnotation("transformAnnotation", "value")
    return result, nil
}
```

## C++

### Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext&` describing the action's environment and current execution
* `TransformInput&` providing the content and metadata to be transformed

Actions with typed parameters receive an additional `Params&` argument between context and input.

### Transform Input

```cpp
struct TransformInput {
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;
};
```

### Return Types

The `transform` method must return a `TransformResultType`, which is a `std::variant` of `TransformResult`, `TransformResults` (multi-child), `FilterResult`, and `ErrorResult`.

### Example

```cpp
#pragma once
#include <deltafi/plugin>

class HelloWorldCppTransformAction {
public:
    deltafi::TransformResultType transform(deltafi::ActionContext& context,
                                            deltafi::TransformInput& input) {
        if (context.did.starts_with("2")) {
            return deltafi::FilterResult(context, "We prefer dids that do not start with 2");
        }

        auto data = input.first_content().load_string()
            + "\nHelloWorldCppTransformAction did a great job";

        deltafi::TransformResult result(context);
        result.save_content(data, "transform-named-me", "text/plain");
        result.add_metadata("transformKey", "transformValue");
        result.add_annotation("transformAnnotation", "value");
        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppTransformAction,
    "HelloWorldCppTransformAction",
    "Add some content noting that we did a really good job")
```
