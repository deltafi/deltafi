# Load Action

## Java

### Interface

A LoadAction must implement the `load` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization
* `LoadInput` provides source, metadata, and content input to the action

### Load Input

```java
public class LoadInput {
    // Content emitted by previous Transform Action, or as
    // received at Ingress if there was no previous Transform Action
    List<Content> contentList;
    // Metadata produced by previous Transform Action, or
    // an empty Map is there was no previous Transform Action
    Map<String, String> metadata;
}
```

### Return Types

The `load` method must return a `LoadResultType`, which is currently implemented by `LoadResult`, `SplitResult`, `LoadManyResult`, `ErrorResult`, and `FilterResult`.

The `LoadResult` includes the domains, content, and metadata created by the `LoadAction`.  
The `LoadManyResult` contains a list of `ChildLoadResults`. Each `ChildLoadResult` will be split into a child `DeltaFile` that will continue to be processed independently.  
The `SplitResult` includes seperate child DeltaFiles which will be ingressed back into DeltaFi.

### Example

```java
package org.deltafi.passthrough.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteLoadParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;

@Component
@SuppressWarnings("unused")
@Slf4j
public class RoteLoadAction extends LoadAction<RoteLoadParameters> {
    public RoteLoadAction() {
        super("Load a null value into the configured domains. Pass content through as received");
    }

    @Override
    public LoadResultType load(
        @NotNull ActionContext context,
        @NotNull RoteLoadParameters params,
        @NotNull LoadInput input) {

        LoadResult result = new LoadResult(context, input.getContentList());
        if (null != params.getDomains()) {
            params.getDomains().forEach(d -> result.addDomain(d, null, MediaType.TEXT_PLAIN));
        }
        return result;
    }
}
```

## Python

### Interface

A LoadAction must implement the `load` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `LoadInput` provides source, metadata, and content input to the action

### Load Input

A description of each Input field can be found in the Java section above.

```python
class LoadInput(NamedTuple):
    source_filename: str
    content: List[Content]
    metadata: dict
```

### Return Types

The `load()` method must return one of: `LoadResult`, `LoadManyResult`, `SplitResult`, `ErrorResult`, or `FilterResult`.

The `LoadResult` includes the domains, content, and metadata created by the `LoadAction`.
A `SplitResult` includes seperate child DeltaFiles which will be ingressed back into DeltaFi.
The `LoadManyResult` contains a list of `ChildLoadResults`. Each `ChildLoadResult` will be split into a child `DeltaFile` that will continue to be processed independently.

### Example

```python
from deltafi.action import LoadAction
from deltafi.domain import Context, Content
from deltafi.input import LoadInput
from deltafi.result import LoadResult, SplitResult
from pydantic import BaseModel, Field


class HelloWorldLoadParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")


class HelloWorldLoadAction(LoadAction):
    def __init__(self):
        super().__init__('Split if we haven\'t already, else load')

    def param_class(self):
        return HelloWorldLoadParameters

    def load(self, context: Context, params: HelloWorldLoadParameters, load_input: LoadInput):
        context.logger.info(f"Loading {context.did}")
        content_reference = load_input.content[0].content_reference
        data = context.content_service.get_str(content_reference)

        if 'split' in data:
            data = f"{data}\nHelloWorldLoadAction loaded me"
            new_content_reference = context.content_service.put_str(context.did, data, 'test/plain')
            return LoadResult().add_metadata('loadKey', 'loadValue')\
                .add_domain(params.domain, 'Python domain!', 'text/plain')\
                .add_content('loaded content', new_content_reference)
        else:
            data = f"{data}\nHelloWorldLoadAction split me"
            new_content_reference = context.content_service.put_str(context.did, data, 'test/plain')
            content = Content(name='child content',
                              metadata={},
                              content_reference=new_content_reference)
            split_result = SplitResult()
            split_result.add_child('child 1', 'hello-python', {'child': 'first'}, [content])
            split_result.add_child('child 2', 'hello-python', {'child': 'second'}, [content])
            return split_result
```
