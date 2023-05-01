# Transform Action

## Java

### Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `TransformInput` provides source, metadata, and content input to the action

### Transform Input

```java
public class TransformInput {
    // Original filename
    String sourceFilename;
    // Ingress flow assigned to the DeltaFile
    String ingressFlow;
    // Content emitted by previous Transform Action, or as
    // received at Ingress if there was no previous Transform Action
    List<Content> contentList;
    // Metadata produced by previous Transform Action, or
    // an empty Map is there was no previous Transform Action
    Map<String, String> metadata;
}
```

### Return Types

The `transform` method must return a `TransformResultType`, which is currently implemented by `TransformResult`, `ErrorResult`, and `FilterResult`.

The `TransformResult` includes the content and metadata created by the `TransformAction`.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class RoteTransformAction extends TransformAction<ActionParameters> {
    public RoteTransformAction() {
        super("NOOP passthrough Action");
    }

    @Override
    public TransformResultType transform(
        @NotNull ActionContext context,
        @NotNull ActionParameters params,
        @NotNull TransformInput input) {

        TransformResult result = new TransformResult(context);
        result.setContent(input.getContentList());
        result.addMetadata(input.getMetadata());
        return result;
    }
}
```

## Python

### Interface

A TransformAction must implement the `transform` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `TransformInput` provides source, metadata, and content input to the action

### Transform Input

A description of each Input field can be found in the Java section above.

```python
class TransformInput(NamedTuple):
    source_filename: str
    content: List[Content]
    metadata: dict
```

### Return Types

The `transofrm()` method must return one of: `TransformResult`, `ErrorResult`, or `FilterResult`.

The `TransformResult` includes the content and metadata created by the `TransformAction`.

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
            return FilterResult('We prefer dids that do not start with 2')

        content_reference = transform_input.content[0].content_reference
        data = f"{context.content_service.get_str(content_reference)}\nHelloWorldTransformAction did a great job"
        new_content_reference = context.content_service.put_str(context.did, data, 'test/plain')

        return TransformResult().add_metadata('transformKey', 'transformValue')\
            .add_content('transform-named-me', new_content_reference)
```
