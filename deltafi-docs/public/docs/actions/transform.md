# Transform Action

## Interface

A TransformAction must implement the `transform` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleTransformAction
* `SourceInfo` including the flow, filename, and source metadata
* `List<Content>` or `Content` depending on whether this is a MultipartTransformAction or not. This is the content
emitted by the previous TransformAction, or as received at Ingress if there was no previous TransformAction.
* `Map<String, String>` metadata emitted by the previous TransformAction, or as received at Ingress if there was no
previous TransformAction.

## Return Types

The `transform` method should return a `TransformResult`, `ErrorResult`, or `FilterResult`.

The `TransformResult` includes the content and metadata created by the `TransformAction`. 

## Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.transform.SimpleMultipartTransformAction;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Action(description = "NOOP passthrough Action")
public class RoteTransformAction extends SimpleMultipartTransformAction {

    @Override
    public Result transform(@NotNull ActionContext context,
                            @NotNull SourceInfo sourceInfo,
                            @NotNull List<Content> contentList,
                            @NotNull Map<String, String> metadata) {
        TransformResult result = new TransformResult(context);
        result.setContent(contentList);
        result.addMetadata(metadata);
        return result;
    }
}
```