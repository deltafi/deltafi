# Format Action

## Interface

A FormatAction must implement the `format` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleFormatAction
* `SourceInfo` including the flow, filename, and source metadata
* `List<Content>` or `Content` emitted by the LoadAction, depending on whether this is a MultipartFormatAction or not
* `Map<String, String>` metadata emitted by the LoadAction
* `Map<String, Domain>` named domains and their values
* `Map<String, Enrichment>` named enrichment and their values

A FormatAction also must implement getRequiresDomain and getRequiresEnrichment methods that return the domains and
enrichment that allow it to select which DeltaFiles it receives. Either of these can return
`DeltaFiConstants.MATCHES_ANY` if you do not wish to filter based on domain or enrichment.

## Return Types

The `format` method should return a `FormatResult`, `FormatManyResult`, `ErrorResult`, or `FilterResult`.

A `FormatResult` includes the content and metadata created by the `FormatAction`.

A `FormatManyResult` is like a list of `FormatResult` where each entry will be validated and egressed independently.

## Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.SimpleFormatAction;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;
import org.deltafi.common.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Action(requiresDomains = DeltaFiConstants.MATCHES_ANY,
        description = "Format the result created by the load action with no transformation")
public class RoteFormatAction extends SimpleFormatAction {
  public Result format(@NotNull ActionContext context,
                       @NotNull SourceInfo sourceInfo,
                       @NotNull Content content,
                       @NotNull Map<String, String> metadata,
                       @NotNull Map<String, Domain> domains,
                       @NotNull Map<String, Enrichment> enrichment) {
    FormatResult result = new FormatResult(context, sourceInfo.getFilename());
    result.setContentReference(content.getContentReference());
    result.addMetadata(sourceInfo.getMetadata(), "sourceInfo.");
    result.addMetadata(content.getMetadata());
    return result;
  }

  @Override
  public List<String> getRequiresDomains() {
    return List.of(DeltaFiConstants.MATCHES_ANY);
  }

  @Override
  public List<String> getRequiresEnrichment() {
    return List.of(DeltaFiConstants.MATCHES_ANY);
  }
}
```