# Actions

Actions are isolated units of business logic that perform a function within Transform, Normalize, Enrichment, and Egress Flows.
Actions receive required data from the DeltaFile on a queue, perform whatever logic is needed, and issue a response that
augments the DeltaFile so that it can be handed off to the next Action in the flow.

Actions are currently implemented on two platforms. Core actions and several plugins are implemented in Spring Boot and
utilize a Java DeltaFi development kit. A Python DeltaFi development kit has also been implemented. However, Actions can
be developed in any language as long as certain integration interfaces are met.

Actions may be configured differently in each flow that uses them. The Action itself defines the configuration knobs
that can be used to specialize it.

## Common Action Interfaces

All actions are derived from the common `Action` class, which is specialized for each action type. The `Action`
interface gives access to some common services. Extend the proper `Action` class (see details below) and it will be
automatically discovered and loaded by the framework when your plugin is installed.

### Context

Execution methods for each Java action type are passed an `ActionContext`. The context gives you access to information
about where the action is running and the DeltaFile(s) being processed.

```java
// the did is the DeltaFile's id
String did = context.getDid();
// the flow in which the action is being invoked
String flow = context.getFlow();
// name of the Action as configured in a flow
String actionName = context.getName();
// the original filename
String sourceFilename = context.getSourceFilename();
// the ingress flow name
String ingressFlow = context.getIngressFlow();
// the egress flow name
String egressFlow = context.getEgressFlow();
// hostname where the Action is running
String hostname = context.getHostname();
// version of Core Actions or plugin containing the Action
String actionVersion = context.getActionVersion();
// when the Action began execution
OffsetDateTime startTime = context.getStartTime();
// system name from DeltaFi System Properties
String systemName = context.getSystemName();
// the optional collect configuration in effect for the action
CollectConfiguration collect = context.getCollect();
// the optional collected DeltaFile ids
List<String> collectedDids = context.getCollectedDids();
```

Execution methods for each Python action type are passed a `Context`. The context gives you access to information about
where the action is running, the DeltaFile(s) being processed, and access to supporting services.

```python
class Context(NamedTuple):
    did: str
    action_name: str
    source_filename: str
    ingress_flow: str
    egress_flow: str
    system: str
    hostname: str
    content_service: ContentService
    collect: dict
    collected_dids: List[str]
    logger: Logger

```

### Input

Each `Action` has a specific `Input` class passed to its execution method. For example, a Load Action receives the
`LoadInput` in the `load()` method. Each `Input` class is unique for each Action type, with some combination of the
fields below.

```java
List<ActionContent> contentList;
Map<String, String> metadata;
Map<String, Domain> domains;
Map<String, Enrichment> enrichments;
```

### Parameters

Actions can be configured with custom parameters by extending the `ActionParameters` class:

```java
package org.deltafi.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class DecompressionTransformParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Description goes here")
    public String myParameter;
}
```

```python
from pydantic import BaseModel, Field

class MyLoadActionParameters(BaseModel):
    domain: str = Field(description="The domain used by the load action")
```

### Content Storage

Actions are passed or can create `ActionContent` that serve as pointers to content data that is stored on disk.

To retrieve content as a byte array, string, or from a stream:

```java
byte[] byteArray = content.loadBytes();
String string = content.loadString();
String encodedString = content.loadString(Charset.forName(encoding));
InputStream inputStream = content.loadInputStream();
```

To retrieve content as a string or byte array in a Python action execution:

```python
    def load(self, context: Context, params: BaseModel, load_input: LoadInput):
        string = load_input.content[0].load_str()
        bytes = load_input.content[0].load_bytes()

```

To store content from a byte array or a stream and add to a Result:

```java
// create a result of the type appropriate for your Action
TransformResult transformResult = new TransformResult();
transformResult.saveContent(byteArray, fileName, MediaType.APPLICATION_JSON);
transformResult.saveContent(inputStream, fileName, MediaType.APPLICATION_JSON);

// you can modify existing content and add it to the Result without having to save new Content to disk:
List<ActionContent> existingContentList = input.getContentList();
transformResult.addContent(existingContentList);

// you can also manipulate existing content to store new content on disk
ActionContent copyOfFirstContent = existingContent.copy();
copyOfFirstContent.setName("new-name.txt");
// get the first 50 bytes
ActionContent partOfSecondContent = anotherContent.subcontent(0, 50);
copyOfFirstContent.append(partOfSecondContent);
// store the pointers to the stitched-together content without writing to disk
transformResult.addContent(copyOfFirstContent);
```

Or in Python save content to a Result:

```python
    result.save_content(data, content_name, media_type)
```

### Results

Each `Action` returns a specific `Result` class from its execution method. The `Result` contains some combination of
content, metadata, domains, and enrichments produced by the execution of that Action.

Actions may return an `ErrorResult` if something goes wrong. Errors terminate the flow and raise the error cause
to an operator's attention for possible retry.

```java
// return with a custom error message
if (somethingWentWrong) {
    return new ErrorResult(context, "Description of why the Action failed");
}

try {
    // something bad happens
} catch (SomeException e) {
    // return with Exception details
    return new ErrorResult(context, e.getMessage(), e.getCause());
}
```

Sometimes you want to halt a flow but not raise an error. In this case use a FilterResult:

```java
return new FilterResult(context, "Description of why this DeltaFile was filtered");
```

### Collect

Transform, Load, and Format actions may be configured to collect multiple DeltaFiles before executing. When the action
is executed, the action context will include a collect configuration and a list of the DeltaFile ids that were
collected. The collect configuration may include the following fields:

* `maxAge` the maximum duration (ISO 8601) to wait after the first DeltaFile is received for a collection before the
  action is executed
* `minNum` the minimum number of DeltaFiles to collect within `maxAge`. If this number is not reached, all collected
  DeltaFiles will have the action marked in error.
* `maxNum` the maximum number of DeltaFiles to collect before the action is executed
* `metadataKey` an optional metadata key used to get the value to group collections by (defaults to collecting all)

By default, Transform and Load actions configured to collect will combine content and metadata from all collected
DeltaFiles into the `Input` passed to the execution method. Format actions configured to collect will default to
combining content, metadata, domains, and enrichments from all collected DeltaFiles into the `Input` passed to the
execution method.

The `collect` method may be defined by an action to override the default behavior:

```java
    @Override
    protected TransformInput collect(List<TransformInput> transformInputs) {
        List<ActionContent> allContent = new ArrayList<>();
        for (TransformInput transformInput : transformInputs) {
            allContent.addAll(transformInput.getContent());
        }
        return TransformInput.builder()
                .content(allContent)
                .build();
    }
```

```python
    def collect(self, transform_inputs: List[TransformInput]):
        all_content = []
        for transform_input in transform_inputs:
            all_content += transform_input.content
        return TransformInput(content=all_content)
```

## Transform Flow Actions
- [Transform Action](/actions/transform)
- [Egress Action](/actions/egress)

## Normalize Flow Actions
- [Transform Action](/actions/transform)
- [Load Action](/actions/load)

## Enrichment Flow Actions
- [Domain Action](/actions/domain)
- [Enrich Action](/actions/enrich)

## Egress Flow Actions
- [Format Action](/actions/format)
- [Validate Action](/actions/validate)
- [Egress Action](/actions/egress)
