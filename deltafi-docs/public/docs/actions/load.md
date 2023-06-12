# Load Action

## Description

A Load Action loads the data model for the provided content and metadata into Domains that will be acted on by actions
in Enrich and Egress Flows. Like a Transform Action, it may also transform the content, update metadata, and add
annotations.

## Java

### Interface

A LoadAction must implement the `load` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` containing flow parameters specified for the action
* `LoadInput` providing the content and metadata used to create Domains

### Load Input

```java
public class LoadInput {
    List<ActionContent> contentList;
    Map<String, String> metadata;
}
```

### Return Types

The `load` method must return a `LoadResultType`, which is implemented by `LoadResult`, `ReinjectResult`,
`LoadManyResult`, `ErrorResult`, and `FilterResult`.

The `LoadResult` contains the domains, content, metadata, and annotations created by the `LoadAction`.  
The `LoadManyResult` contains a list of `ChildLoadResult`. Each `ChildLoadResult` will create a new DeltaFile copied
from the current DeltaFile that will continue to be processed independently.  
The `ReinjectResult` contains a list of `ReinjectEvent`. Each `ReinjectEvent` will create a new DeltaFile that will be
ingressed to a specified flow.

### Example

```java
package org.deltafi.example;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.load.*;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HelloWorldLoadAction extends LoadAction<Parameters> {
    public HelloWorldLoadAction() {
        super("Reinject if we haven't already, else load");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull Parameters params, @NotNull LoadInput input) {
        String data = input.getContentList().get(0).loadString();

        if (data.contains("reinjected")) {
            data = data + "\nHelloWorldLoadAction loaded me";
            LoadResult loadResult = new LoadResult(context);
            loadResult.addDomain("example", "Java domain!", "text/plain");
            loadResult.saveContent(data, "loaded content", "text/plain");
            loadResult.addMetadata("loadKey", "loadValue");
            loadResult.addAnnotation("loadAnnotation", "value");
            return loadResult;
        }
        
        data = data + "\nHelloWorldLoadAction reinjected me";
        try {
            Content content = context.getContentStorageService().save(context.getDid(), data.getBytes(), "child content", "text/plain");
            ActionContent actionContent = new ActionContent(content, context.getContentStorageService());
            ReinjectResult reinjectResult = new ReinjectResult(context);
            reinjectResult.addChild("child 1", "hello-java", List.of(actionContent), Map.of("child", "first"));
            reinjectResult.addChild("child 2", "hello-java", List.of(actionContent), Map.of("child", "second"));
            return reinjectResult;
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to store child content", e);
        }
    }
}
```

## Python

### Interface

A LoadAction must implement the `load` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` containing flow parameters for use by the action, matching the type specified by the `param_class()`
method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `LoadInput` providing the content and metadata used to create Domains

### Load Input

```python
class LoadInput(NamedTuple):
    content: List[Content]
    metadata: dict
```

### Return Types

The `load()` method must return one of: `LoadResult`, `LoadManyResult`, `ReinjectResult`, `ErrorResult`, or
`FilterResult`.

The `LoadResult` contains the domains, content, metadata, and annotations created by the `LoadAction`.  
The `LoadManyResult` contains a list of `ChildLoadResult`. Each `ChildLoadResult` will create a new DeltaFile copied
from the current DeltaFile that will continue to be processed independently.  
The `ReinjectResult` contains a list of `ReinjectEvent`. Each `ReinjectEvent` will create a new DeltaFile that will be
ingressed to a specified flow.

### Example

```python
from deltafi.action import LoadAction
from deltafi.domain import Context, Content
from deltafi.input import LoadInput
from deltafi.result import LoadResult, ReinjectResult
from pydantic import BaseModel, Field


class HelloWorldLoadParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")


class HelloWorldLoadAction(LoadAction):
    def __init__(self):
        super().__init__('Reinject if we haven\'t already, else load')

    def param_class(self):
        return HelloWorldLoadParameters

    def load(self, context: Context, params: HelloWorldLoadParameters, load_input: LoadInput):
        context.logger.info(f"Loading {context.did}")
        data = load_input.content[0].load_str()

        if 'reinjected' in data:
            data = f"{data}\nHelloWorldLoadAction loaded me"
            return LoadResult(context)\
                .add_domain(params.domain, 'Python domain!', 'text/plain')\
                .save_string_content(data, 'loaded content', 'text/plain')\
                .add_metadata('loadKey', 'loadValue')\
                .annotate('loadAnnotation', 'value')
        else:
            data = f"{data}\nHelloWorldLoadAction reinjected me"
            content = Content.from_str(context, data, 'child content', 'text/plain')
            reinject_result = ReinjectResult(context)
            reinject_result.add_child('child 1', 'hello-python', [content], {'child': 'first'})
            reinject_result.add_child('child 2', 'hello-python', [content], {'child': 'second'})
            return reinject_result
```
