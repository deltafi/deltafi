# Egress Action

## Interface

An EgressAction must implement the `egress` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleEgressAction
* `SourceInfo` including the flow, filename, and source metadata
* `FormattedData` a structure containing the content references that were created by the FormatAction

## Return Types

The `egress` method should return an `EgressResult`, `ErrorResult`, or `FilterResult`.

The `EgressResult` includes a location where the file was egressed and a number of bytes sent.
These are for informational purposes only. The return of any `EgressResult` indicates success.

## Example

```java
package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;
import org.deltafi.common.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class FilterEgressAction extends SimpleEgressAction {
    @Override
    public Result egress(@NotNull ActionContext context,
                         @NotNull SourceInfo sourceInfo,
                         @NotNull FormattedData formattedData) {
        return new FilterResult(context, "filtered");
    }
}
```