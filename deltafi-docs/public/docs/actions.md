# Actions

Actions are isolated units of business logic that perform a function within Ingress, Enrichment, and Egress Flows.
Actions receive a DeltaFile on a queue, perform whatever logic is needed,
and issue a response that augments the DeltaFile so that it can be handed off to the next Action in the flow.

Actions are currently implemented in Spring Boot and utilize a Java DeltaFi development kit.
However, Actions can be developed in any language as long as certain integration interfaces are met.
We plan to provide DeltaFi development kits in other languages soon.

Actions may be configured differently in each flow that uses them.
The Action itself defines the configuration knobs that can be used to specialize it.

## Common Action Interfaces

All actions are derived from the common `Action` class, which is specialized for each action type.
The `Action` interface gives access to some common services. Extend the proper `Action` class (see details below) and
tag your Action class with the `@Action` annotation so that it can be discovered and loaded by the framework.

### Context

Execution methods for each action type are passed an `ActionContext`.  The context gives you access to information about
where the action is running and the DeltaFile being processed.

```java
// the did is the DeltaFile's id
String did = context.getDid();
// name of the Action as configured in a flow
String actionName = context.getAction();
// hostname where the Action is running
String hostname = context.getHostname();
// version of the Action
String actionVersion = context.getActionVersion();
// when the Action began execution
OffsetDateTime startTime = context.getStartTime();
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

To store content from a byte array or a stream:

```java
try {
    ContentReference contentRefFromBytes=saveContent(context.getDid(),byteArray,MediaType.APPLICATION_JSON);
    ContentReference contentRefFromStream=saveContent(context.getDid(),inputStream,MediaType.APPLICATION_JSON);
} catch (ObjectStorageException e) {
    // something went wrong
}
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

### Action Specializations

Each `Action` type has several specializations that can be implemented. The classes are specialized based on whether they
expect to receive multiple content references and if they use custom parameters.

For example, for Transform Actions:

| Class Name                     | Content Received | Parameterized |
|--------------------------------|------------------|---------------|
| TransformAction                | Single           | true          |
| SimpleTransformAction          | Single           | false         |
| MultipartTransformAction       | Multiple         | true          |
| SimpleMultipartTransformAction | Multiple         | false         |

### SourceInfo

The `SourceInfo` passed to Actions upon execution gives information about how the `DeltaFile` was originally
received by DeltaFi.

```java
// Original filename
String filename = sourceInfo.getFilename();
// Ingress flow assigned to the DeltaFile
String flow = sourceInfo.getFlow();
// Metadata passed in with the DeltaFile on ingress
Map<String, String> metadata = sourceInfo.getMetadataAsMap();
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