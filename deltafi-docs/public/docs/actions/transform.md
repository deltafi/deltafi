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

The `transform` method must return a `TransformResultType`, which is implemented by `TransformResult`, `ErrorResult`,
and `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.

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
        if (context.getDid().startsWith("2")) {
            return new FilterResult(context, "We prefer dids that do not start with 2");
        }
        
        String data = input.getContentList().get(0).loadString() + "\nHelloWorldTransformAction did a great job";

        TransformResult result = new TransformResult(context);
        result.addMetadata("transformKey", "transformValue");
        result.addAnnotation("transformAnnotation", "value");
        result.saveContent(data, "transform-named-me", "test/plain");
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

The `transform()` method must return one of: `TransformResult`, `ErrorResult`, or `FilterResult`.

The `TransformResult` contains the content, metadata, and annotations created by the `TransformAction`.

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
            .save_string_content(data, 'transform-named-me', 'test/plain')
            .add_metadata('transformKey', 'transformValue')
            .annotate('transformAnnotation', 'value')
```
