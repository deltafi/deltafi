# Actions

Actions are isolated units of business logic that perform a function within Ingress, Enrichment, and Egress Flows.
Actions receive a DeltaFile on a queue, perform whatever logic is needed,
and issue a response that augments the DeltaFile so that it can be handed off to the next Action in the flow.

Actions are currently implemented on two platforms. Core actions and several plugins are implemented in Spring Boot and utilize a Java DeltaFi development kit.
A Python DeltaFi development kit has also been implemented.
However, Actions can be developed in any language as long as certain integration interfaces are met.

Actions may be configured differently in each flow that uses them.
The Action itself defines the configuration knobs that can be used to specialize it.

## Common Action Interfaces

All actions are derived from the common `Action` class, which is specialized for each action type.
The `Action` interface gives access to some common services. Extend the proper `Action` class (see details below) and
it will be automatically
be discovered and loaded by the framework when your plugin is installed.

### Context

Execution methods for each Java action type are passed an `ActionContext`. The context gives you access to information about
where the action is running and the DeltaFile being processed.

```java
// the did is the DeltaFile's id
String did = context.getDid();
// name of the Action as configured in a flow
String actionName = context.getName();
// the ingress flow name
String ingressFlow = context.getIngressFlow();
// the egress flow name
String ingressFlow = context.getEgressFlow();
// hostname where the Action is running
String hostname = context.getHostname();
// version of the Action
String actionVersion = context.getActionVersion();
// when the Action began execution
OffsetDateTime startTime = context.getStartTime();
// system name from DeltaFi System Properties
String systemName = context.getSystemName();
```

Execution methods for each Python action type are passed a `Context`. The context gives you access to information about
where the action is running, the DeltaFile being processed, and access to supporting services.

```python
class Context(NamedTuple):
    did: str
    action_name: str
    ingress_flow: str
    egress_flow: str
    system: str
    hostname: str
    content_service: ContentService
    logger: Logger

```

### Content Storage

Actions are passed or can create `contentReferences` that serve as pointers to content data that is stored on disk.

To retrieve content as a byte array or from a stream:

```java
try {
    byte[]content=loadContent(contentReference);
    // do something with your content
} catch (ObjectStorageException e) {
    // something went wrong
}

try (InputStream content = loadContentAsInputStream(contentReference)) {
    // do something with your content
} catch (ObjectStorageException e) {
    // something went wrong
}
```

To retrieve content as a string or byte array in a Python action execution, use the `ContextService` methods `get_str()` or `get_bytes()`. For example, in a Load action execute:

```python
    def load(self, context: Context, params: BaseModel, load_input: LoadInput):
        content_reference = load_input.content[0].content_reference
        data = context.content_service.get_str(content_reference)

```

To store content from a byte array or a stream:

```java
try {
    ContentReference contentRefFromBytes=saveContent(context.getDid(),byteArray,MediaType.APPLICATION_JSON);
    ContentReference contentRefFromStream=saveContent(context.getDid(),inputStream,MediaType.APPLICATION_JSON);
} catch (ObjectStorageException e) {
    // something went wrong
}
```

Or in Python use `put_bytes()` or `put_str()` in your action execution:

```python
    new_content_reference = context.content_service.put_bytes(context.did, data, media_type)
    result.add_content(content_name, new_content_reference)
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

### Action Specializations

Each `Action` type is provided a `Content` list through the associated `Input` class. Actions may receive a single content or multiple depending on the behaviour of the previous `Action`. A single interface for each `Action` is used in both cases.

### Input

As metnioned in the previous section, each `Action` has a specific `Input` class passed to the execution mehod. For example, a Load Action receives the `LoadInput` in the `load()` method. Each `Input` class is unique for each Action type, with some combination of the fields below.

```java
/* These first 3 are part of all Input classes: */
// Original filename
String sourceFilename;
// Ingress flow assigned to the DeltaFile
String ingressFlow;
// Metadata passed in with the DeltaFile on ingress
Map<String, String> sourceMetadata;

/* These remaining fields vary by Action type: */
Map<String, Domain> domains;
Map<String, Enrichment> enrichment;
Map<String, String> metadata;
List<Content> contentList;
FormattedData formattedData;
```

### Results

Each `Action` type has a specialized `Result` class that contains some combination of content, metadata, domains,
and enrichment produced by the execution of that Action.

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

## Ingress Flow Actions
- [Transform Actions](/actions/transform)
- [Load Actions](/actions/load)

## Enrichment Flow Actions
- [Domain Actions](/actions/domain)
- [Enrich Actions](/actions/enrich)

## Egress Flow Actions

- [Format Actions](/actions/format)
- [Validate Actions](/actions/validate)
- [Egress Actions](/actions/egress)
