# Domain Action

## Interface

A DomainAction must implement the `extractAndValidate` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleDomainAction
* `SourceInfo` including the flow, filename, and source metadata
* `Map<String, String>` metadata emitted by the LoadAction
* `Map<String, Domain>` named domains and their values

An DomainAction also must implement the `getRequiresDomain` method.  This method return a list of
domains that are required to be present in DeltaFiles that it receives. This can return
`DeltaFiConstants.MATCHES_ANY` if you can accept any domain, which would then be defined in a flow yaml.

## Return Types

The `extractAndValidate` method should return a `DomainResult`, `ErrorResult`, or `FilterResult`.

A `DomainResult` contains a map of key value pairs that will be indexed in the system.

## Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.domain.DomainAction;
import org.deltafi.actionkit.action.domain.DomainResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.passthrough.param.RoteDomainParameters;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Action(requiresDomains = DeltaFiConstants.MATCHES_ANY,
        description = "Populate enrichment with the parameterized key/value pairs")
public class RoteDomainAction extends DomainAction<RoteDomainParameters> {

    @SuppressWarnings("unused")
    public RoteDomainAction() {
        super(RoteDomainParameters.class);
    }

    public Result extractAndValidate(@NotNull ActionContext context,
                                     @NotNull RoteDomainParameters params,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull Map<String, String> metadata,
                                     @NotNull Map<String, Domain> domainList) {

        if (null == domainList || domainList.isEmpty()) {
            return new ErrorResult(context, "no domain was sent");
        }

        DomainResult result = new DomainResult(context);
        if (null != params.getFieldsToIndex()) {
            params.getFieldsToIndex().forEach(field -> result.addIndexedMetadata(field, metadata.getOrDefault(field, "missing")));
        }

        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
```