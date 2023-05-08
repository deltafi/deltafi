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
* `context` the `ActionContext` describing the action's environment and current execution
* `params` the parameters as specified in the action configuration
* `joinInputs` metadata and content from the DeltaFiles that are being joined

### Return Types

The `join` method must return a `JoinResult` or `JoinReinjectResult`. Both variations include the content and metadata joined by the
`JoinAction`, with the content referencing the joined content in content storage.

If reinjecting the joined DeltaFile to another flow, set the flow in the `JoinReinjectResult`.

If not reinjecting to another flow, the Join Action will act like a Load Action, adding domains to the `domains` field in the `JoinResult`.

### Example

```java
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.join.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.passthrough.param.RoteJoinParameters;
import org.deltafi.passthrough.util.RandSleeper;
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
    protected JoinResultType join(ActionContext context, RoteJoinParameters params, List<JoinInput> joinInputs) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        List<Content> contentList = joinInputs.stream()
                .flatMap(joinedFromDeltaFile -> joinedFromDeltaFile.getContentList().stream())
                .collect(Collectors.toList());

        if (params.getReinjectFlow() != null) {
            return new JoinReinjectResult(context, params.getReinjectFlow(), contentList);
        } else {
            JoinResult joinResult = new JoinResult(context, contentList);
            if (params.getDomains() != null) {
                params.getDomains().forEach(domain -> joinResult.addDomain(domain, null, MediaType.TEXT_PLAIN));
            }
            return joinResult;
        }
    }
}
```

## Python

### Interface

A JoinAction must implement the `join` method which receives:
* `Context` describing the action's environment and current execution
* `BaseModel` contains flow parameters for use by the action, matching the type specified by `param_class()` method, which must inherit from `BaseMmodel`, or a default/empty `BaseModel` if unspecified.
* `List[JoinInput]` provides metadata and content input for each joining DeltaFile to the action

### Load Input

A description of each Input field can be found in the Java section above.

```python
class JoinInput(NamedTuple):
    content: List[Content]
    metadata: dict
```

### Return Types

The `join()` method must return one of: `JoinResult`, `JoinReinjectResult`, `ErrorResult`, or `FilterResult`.

The `JoinResult` includes the domains, content, and metadata created by the `JoinAction`.
The `JoinReinjectResult` includes the reinjected flow, content, and metadata created by the `JoinAction`.

### Example

```python
from typing import List, Optional

from deltafi.action import JoinAction
from deltafi.domain import Context
from deltafi.input import JoinInput
from deltafi.result import JoinResult, JoinReinjectResult
from pydantic import BaseModel, Field


class HelloWorldJoinParameters(BaseModel):
    reinject_flow: Optional[str] = Field(alias="reinjectFlow", description="An optional ingress flow to reinject to")
    domains: List[str] = Field(description="The domains used by the join action")


class HelloWorldJoinAction(JoinAction):
    def __init__(self):
        super().__init__('Joins content from multiple DeltaFiles')

    def param_class(self):
        return HelloWorldJoinParameters

    def join(self, context: Context, params: HelloWorldJoinParameters, join_inputs: List[JoinInput]):
        context.logger.debug(f"Joining {len(join_inputs)} to {context.did} with params: {params}")

        if params.reinject_flow:
            join_result = JoinReinjectResult(context, params.reinject_flow)
        else:
            join_result = JoinResult(context)
            if params.domains:
                for domain in params.domains:
                    join_result.add_domain(domain, None, 'text/plan')

        for join_input in join_inputs:
            join_result.add_content(join_input.content)

        return join_result
```

