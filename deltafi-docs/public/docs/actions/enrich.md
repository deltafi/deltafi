# Enrich Action

## Interface

An EnrichAction must implement the `enrich` method which receives:
* `ActionContext` describing the action's environment and current execution
* `ActionParameters` as specified in the template specialization, unless this is a SimpleEnrichAction
* `SourceInfo` including the flow, filename, and source metadata
* `List<Content>` or `Content` emitted by the LoadAction, depending on whether this is a MultipartEnrichAction or not
* `Map<String, String>` metadata emitted by the previous action
* `Map<String, Domain>` named domains and their values
* `Map<String, Enrichment>` a list of named enrichment and their values

An EnrichAction also must implement getRequiresDomain and getRequiresEnrichment methods.  These methods return a list of
domains and enrichment that are required to be present in DeltaFiles that it receives. Either of these can return
`DeltaFiConstants.MATCHES_ANY` if you can accept any domain or enrichment, which would then be defined in a flow yaml.
If you require either just domains or just enrichment, you can set the other to an empty list.

## Return Types

The `enrich` method should return an `EnrichResult`, `ErrorResult`, or `FilterResult`.

An `EnrichResult` contains the named enrichment entries and indexed metadata created by the `EnrichAction`.

## Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.passthrough.param.RoteEnrichParameters;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Action(requiresDomains = DeltaFiConstants.MATCHES_ANY,
        description = "Populate enrichment with the parameterized key/value pairs")
public class RoteEnrichAction extends EnrichAction<RoteEnrichParameters> {

    @SuppressWarnings("unused")
    public RoteEnrichAction() {
        super(RoteEnrichParameters.class);
    }

    public Result enrich(@NotNull ActionContext context,
                         @NotNull RoteEnrichParameters params,
                         @NotNull SourceInfo sourceInfo,
                         @NotNull Content content,
                         @NotNull Map<String, String> metadata,
                         @NotNull Map<String, Domain> domainList,
                         @NotNull Map<String, Enrichment> enrichmentList) {
        EnrichResult result = new EnrichResult(context);
        if (Objects.nonNull(params.getEnrichments())) {
            params.getEnrichments().forEach((k, v) -> result.addEnrichment(k, v, MediaType.TEXT_PLAIN));
        }
        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
```