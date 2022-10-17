# Validate Action

## Interface

A ValidateAction must implement the `validate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleValidateAction
* `SourceInfo` including the flow, filename, and source metadata
* `FormattedData` a structure containing the content references that were created by the FormatAction

## Return Types

The `validate` method should return a `ValidateResult`, `ErrorResult`, or `FilterResult`.

Any `ValidateResult` is indicative of a passed validation.

## Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.validate.SimpleValidateAction;
import org.deltafi.actionkit.action.validate.ValidateResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.FormattedData;
import org.jetbrains.annotations.NotNull;

@Action(description = "Validate successfully every time")
public class RubberStampValidateAction extends SimpleValidateAction {
    public Result validate(@NotNull ActionContext context,
                           @NotNull SourceInfo sourceInfo,
                           @NotNull FormattedData formattedData) {
        return new ValidateResult(context);
    }
}
```