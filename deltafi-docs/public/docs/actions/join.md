# Join Action

## Description

A Join Action may be added to an ingress flow as an alternative to a Load Action. When present, DeltaFiles created for
files ingested to the flow will be collected before being sent to the Join Action. A Join Action adds the following
configuration options:

* `maxAge` - the maximum duration to wait before forcing a join
* `maxNum` - an optional maximum number of DeltaFiles to join
* `metadataKey` - an optional metadata key used to get the value to group joins by (defaults to joining all)
* `metadataIndexKey` - an optional metadata key used to get the index to order by (defaults to the order received)

See [Flows](/flows) for an example ingress flow containing a Join Action.

DeltaFiles waiting to be joined will have a stage of `JOINING`. Once joined to a new DeltaFile, they will have a stage
of `JOINED`.

The joined DeltaFile may be reinjected to another flow, or it may add domains and continue in the same flow.

## Java

### Interface

A JoinAction must implement the `join` method which receives:
* `deltaFile` the joined DeltaFile
* `joinedDeltaFiles` the DeltaFiles that were joined to make `deltaFile`
* `context` the `ActionContext` describing the action's environment and current execution
* `params` the parameters as specified in the action configuration

### Return Types

The `join` method must return a `JoinResult`. The `JoinResult` includes the content and metadata joined by the
`JoinAction`, with the content referencing the joined content in content storage.

If reinjecting the joined DeltaFile to another flow, the flow in the `sourceInfo` field should be set.

If not reinjecting to another flow, the Join Action will act as a Load Action, adding domains to the `domains` field.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.join.JoinAction;
import org.deltafi.actionkit.action.join.JoinResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.passthrough.param.RoteJoinParameters;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RoteJoinAction extends JoinAction<RoteJoinParameters> {
    public RoteJoinAction() {
        super("Merges multiple files");
    }

    @Override
    protected JoinResult join(DeltaFile deltaFile, List<DeltaFile> joinedFromDeltaFiles, ActionContext context,
            RoteJoinParameters params) {
        SourceInfo sourceInfo = deltaFile.getSourceInfo();
        if (params.getReinjectFlow() != null) {
            sourceInfo.setFlow(params.getReinjectFlow());
        }

        List<Content> contentList = joinedFromDeltaFiles.stream()
                .flatMap(joinedFromDeltaFile -> joinedFromDeltaFile.getLastProtocolLayerContent().stream())
                .collect(Collectors.toList());

        JoinResult joinResult = new JoinResult(context, sourceInfo, contentList);
        if (params.getDomains() != null) {
            params.getDomains().forEach(domain -> joinResult.addDomain(domain, null, MediaType.TEXT_PLAIN));
        }
        return joinResult;
    }
}
```
