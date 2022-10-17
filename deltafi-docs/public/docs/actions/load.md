# Load Action

## Interface

A LoadAction must implement the `load` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleLoadAction
* `SourceInfo` including the flow, filename, and source metadata
* `List<Content>` or `Content` depending on whether this is a MultipartLoadAction or not. This is the content
  emitted by the previous TransformAction, or as received at Ingress if there was no previous TransformAction.
* `Map<String, String>` metadata emitted by the previous TransformAction, or as received at Ingress if there was no
  previous TransformAction.

## Return Types

The `load` method should return a `LoadResult`, `ErrorResult`, `FilterResult`, or `SplitResult`.

The `LoadResult` includes the domains, content, and metadata created by the `LoadAction`.

## Example

```java
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.MultipartLoadAction;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.passthrough.param.RoteLoadParameters;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Action(description = "Load a null value into the configured domains. Pass content through as received")
public class RoteLoadAction extends MultipartLoadAction<RoteLoadParameters> {
  public RoteLoadAction() {
    super(RoteLoadParameters.class);
  }

  @Override
  public Result load(@NotNull ActionContext context,
                     @NotNull RoteLoadParameters params,
                     @NotNull SourceInfo sourceInfo,
                     @NotNull List<Content> contentList,
                     @NotNull Map<String, String> metadata) {
    LoadResult result = new LoadResult(context, contentList);
    result.addMetadata("domainsAdded", String.join(", ", params.getDomains()));
    params.getDomains().forEach(d -> result.addDomain(d, null, MediaType.TEXT_PLAIN));
    return result;
  }
}
```