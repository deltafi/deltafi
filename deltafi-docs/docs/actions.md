# Actions

Actions are isolated units of business logic that perform functions within Timed Data Sources, Transforms, and Data Sinks.
Actions receive required data from the DeltaFile on a queue, perform the necessary logic, and issue a response that
augments the DeltaFile so that it can be handed off to the next Action in the flow or published to a topic.

Actions are currently implemented on two platforms. Core actions and several plugins are implemented in Spring Boot and
utilize a Java DeltaFi development kit. A Python DeltaFi development kit has also been implemented. However, Actions can
be developed in any language as long as certain integration interfaces are met.

Actions may be configured differently in each flow that uses them. The Action itself defines the configuration parameters
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
UUID did = context.getDid();
// the name of the DeltaFile, typically the original filename
String deltaFileName = context.getDeltaFileName();
// the original dataSource of this DeltaFile
String dataSource = context.getDataSource();
// the name of the flow in which the action is being invoked
String flowName = context.getFlowName();
// the id of the flow in which the action is being invoked
UUID flowId = context.getFlowId();
// name of the Action as configured in a flow
String actionName = context.getActionName();
// id of the Action in the current flow
UUID actionId = context.getActionId();
// hostname where the Action is running
String hostname = context.getHostname();
// version of Core Actions or plugin containing the Action
String actionVersion = context.getActionVersion();
// when the Action began execution
OffsetDateTime startTime = context.getStartTime();
// system name from DeltaFi System Properties
String systemName = context.getSystemName();
// the optional join configuration in effect for the action
JoinConfiguration join = context.getJoin();
// the optional joined DeltaFile ids
List<UUID> joinedDids = context.getJoinedDids();
// the optional memo field used to pass bookmarking info to timed ingress actions
String memo = context.getMemo();
```

Execution methods for each Python action type are passed a `Context`. The context gives you access to information about
where the action is running, the DeltaFile(s) being processed, and access to supporting services.

```python
class Context(NamedTuple):
  did: str
  delta_file_name: str
  data_source: str
  flow_name: str
  flow_id: str
  action_name: str
  action_id: str
  action_version: str
  hostname: str
  system_name: str
  content_service: ContentService
  join: dict = None
  joined_dids: List[str] = None
  memo: str = None
  logger: Logger = None
```

### Input

Each `Action` has a specific `Input` class passed to its execution method. For example, a Transform Action receives the
`TransformInput` in the `transform()` method. Each `Input` class is unique for each Action type, typically containing:

```java
List<ActionContent> contentList;
Map<String, String> metadata;
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

class DecompressionTransformParameters(BaseModel):
    myParameter: str = Field(description="Description goes here")
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
    def transform(self, context: Context, params: BaseModel, transform_input: TransformInput):
        string = transform_input.content[0].load_str()
        bytes = transform_input.content[0].load_bytes()

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
content, metadata, and annotations produced by the execution of that Action.

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
or
```java
return new FilterResult(context, "Common summary reason of why this DeltaFile was filtered", "Detailed reason");
```

### Join

Transform actions may be configured to join multiple DeltaFiles before executing the transform method. When a transform action
is configured for joining, DeltaFi will collect a batch of DeltaFiles until the join criteria is met.
Once the criteria is met, the batch is sent to the Transform action where it is joined into a single input and
passed to the transform method.

By default, Transform actions configured to join will combine content and metadata from all collected
DeltaFiles into the `TransformInput` passed to the transform method.

The `join` method may be overridden by an action to change the default behavior:

```java
    @Override
    protected TransformInput join(List<TransformInput> transformInputs) {
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
    def join(self, transform_inputs: List[TransformInput]):
        all_content = []
        for transform_input in transform_inputs:
            all_content += transform_input.content
        return TransformInput(content=all_content)
```

The join configuration that defines the criteria for collecting DeltaFiles that will be joined may include the following fields: 
* `maxAge` the maximum duration (ISO 8601) to wait after the first DeltaFile is received for a collection before the
  action is executed
* `minNum` the minimum number of DeltaFiles to collect within `maxAge`. If this number is not reached, all collected
  DeltaFiles will have the action marked in error.
* `maxNum` the maximum number of DeltaFiles to collect before the action is executed
* `metadataKey` an optional metadata key used to get the value to group collections by (defaults to collecting all)

## SSL Setup

### Java SSLContext

The `deltafi-action-kit` will autoconfigure a `SslContextProvider` bean which is available for injection.
The provider will contain a populated `SslBundle` when the `certs` directory is fully populated (see [Plugins SSL Config](/install/plugins#ssl-config)).
The action-kit also provides a `HttpClient` bean that is preconfigured with an `SSLContext` when the `SslContextProvider` is configured.

To get a new `SSlContext` backed by the files in `/cert`, inject the `SslContextProvider` bean and call the `createSslContext` method.
For more advanced use cases the `SslContextProvider` provides direct access the `SslBundle` (getSslBundle) and private key (getPrivateKey).

### Python

Python actions can access the files necessary to configure SSL in the `/certs` directory.

## Action Pages
- [Timed Ingress Action](/actions/timed_ingress)
- [Transform Action](/actions/transform)
- [Egress Action](/actions/egress)
