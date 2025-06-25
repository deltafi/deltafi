# Data Sources, Transforms, and Data Sinks

DeltaFi processes data through a system of Data Sources, Transforms, and Data Sinks. Each plays a distinct role in the data
processing pipeline:

1. [Data Sources](#data-sources) establish the entry points for data into the DeltaFi system.
1. [Transforms](#transforms) define sequences of Transform Actions that operate on the data.
1. [Data Sinks](#data-sinks) specify how processed data is sent out of the DeltaFi system.

## Data Sources

Data Sources are the entry points for data into the DeltaFi system. There are three types of Data Sources:
1. [REST Data Sources](#rest-data-sources)
1. [Timed Data Sources](#timed-data-sources)
1. [On-Error Data Sources](#onerror-data-sources) 

### REST Data Sources

REST Data Sources allow external systems to push data into DeltaFi via HTTPS requests. They are defined as follows:

```json
{
  "name": "example-rest-source",
  "type": "REST_DATA_SOURCE",
  "description": "An example REST data source",
  "topic": "input-topic"
}
```

### Timed Data Sources

Timed Data Sources periodically generate or fetch data based on a defined schedule. They are defined as follows:

```json
{
  "name": "example-timed-source",
  "type": "TIMED_DATA_SOURCE",
  "description": "An example timed data source",
  "topic": "timed-input-topic",
  "timedIngressAction": {
    "name": "ExampleTimedIngressAction",
    "type": "org.deltafi.example.action.ExampleTimedIngressAction"
  },
  "cronSchedule": "0 0 * * * ?"
}
```

### On-Error Data Sources

On-Error Data Sources automatically trigger when errors occur in other flows, creating new DeltaFiles from error events. They provide a mechanism for error handling and monitoring failed data. OnError Data Sources can be configured with various filters to control which errors they respond to:

```json
{
  "name": "example-error-handler",
  "type": "ON_ERROR_DATA_SOURCE",
  "description": "Handle critical errors from processing flows",
  "topic": "error-topic",
  "errorMessageRegex": ".*critical.*",
  "sourceFilters": [
    {
      "flowType": "TRANSFORM",
      "flowName": "ImportantTransform",
      "actionName": "SpecificAction"
    },
    {
      "flowType": "DATA_SINK",
      "flowName": "CriticalSink"
    },
    {
      "actionClass": "com.example.ValidationAction"
    }
  ],
  "metadataFilters": [
    {"key": "environment", "value": "production"}
  ],
  "annotationFilters": [
    {"key": "priority", "value": "high"}
  ],
  "includeSourceMetadataRegex": ["customer.*", "order.*"],
  "includeSourceAnnotationsRegex": ["tracking.*"]
}
```

#### Error Event Content and Metadata

When an OnError Data Source is triggered, it creates a new DeltaFile containing both content and metadata with detailed information about the error.

**Content**: The DeltaFile contains content from both the errored flow and the original source, differentiated by tags:

- **Original Content**: Content from the original ingress tagged with "original"
- **Errored Content**: Content from the flow that encountered the error tagged with "errored"

This provides access to both the original data that was being processed and the state of the data when the error occurred.

**Metadata**: Automatically attached metadata fields for easy filtering and processing:

| Metadata Key | Description | Example Value |
|--------------|-------------|---------------|
| `onError.sourceFlowName` | Name of the flow where the error occurred | `"order-processing"` |
| `onError.sourceFlowType` | Type of flow where the error occurred | `"TRANSFORM"` |
| `onError.sourceActionName` | Name of the action that generated the error | `"ValidateOrderAction"` |
| `onError.sourceActionType` | Type of the action that generated the error | `"org.example.ValidateOrderAction"` |
| `onError.errorCause` | The error message/cause | `"Validation failed: missing required field"` |
| `onError.errorContext` | Additional context about the error | `"Field validation in order processing pipeline"` |
| `onError.sourceDid` | DID of the original DeltaFile that errored | `"550e8400-e29b-41d4-a716-446655440000"` |
| `onError.sourceName` | Name of the original DeltaFile that errored | `"original-file.json"` |
| `onError.sourceDataSource` | Data source of the original DeltaFile | `"rest-api-ingress"` |
| `onError.eventTimestamp` | When the error occurred | `"2025-06-09T10:30:45.123Z"` |

Additionally, any metadata and annotations from the source DeltaFile that match the `includeSourceMetadataRegex` and `includeSourceAnnotationsRegex` patterns will be included in the new DeltaFile.

#### OnError Data Source Fields

| Field | Description | Required | Example |
|-------|-------------|----------|---------|
| `errorMessageRegex` | Regular expression to match against error messages. Only errors with messages matching this pattern will trigger the data source. If null, all error messages match. | No | `".*timeout.*"` |
| `sourceFilters` | List of ErrorSourceFilter objects that define which error sources can trigger this data source. All fields within a filter are optional, and filters are OR'd together. If the list is null or empty, errors from any source can trigger it. | No | See ErrorSourceFilter structure below |
| `metadataFilters` | Key-value pairs that must match the source DeltaFile's metadata for the error to trigger this data source. All filters must match. | No | `[{"key": "customer_type", "value": "premium"}]` |
| `annotationFilters` | Key-value pairs that must match the source DeltaFile's annotations for the error to trigger this data source. All filters must match. | No | `[{"key": "region", "value": "us-east"}]` |
| `includeSourceMetadataRegex` | List of regex patterns specifying which metadata keys from the source DeltaFile to include in the new error DeltaFile. If null or empty, no source metadata is included. | No | `["customer.*", "order_id"]` |
| `includeSourceAnnotationsRegex` | List of regex patterns specifying which annotation keys from the source DeltaFile to include in the new error DeltaFile. If null or empty, no source annotations are included. | No | `["tracking.*", "audit_trail"]` |

#### ErrorSourceFilter Structure

The `sourceFilters` field contains a list of ErrorSourceFilter objects. Each filter can specify any combination of the following optional fields:

| Field | Description | Required | Example |
|-------|-------------|----------|---------|
| `flowType` | The type of flow that must match for the error to trigger this data source. | No | `"TRANSFORM"`, `"DATA_SINK"`, `"REST_DATA_SOURCE"`, `"TIMED_DATA_SOURCE"` |
| `flowName` | The name of the specific flow that must match for the error to trigger this data source. | No | `"customer-processing"`, `"order-validation"` |
| `actionName` | The name of the specific action that must match for the error to trigger this data source. Note that action names are not unique across flows. | No | `"ValidateAction"`, `"TransformAction"` |
| `actionClass` | The class name of the action that must match for the error to trigger this data source. This provides a way to target all actions of a specific type regardless of their name or flow. | No | `"com.example.ValidationAction"`, `"org.deltafi.core.action.RestPostEgressAction"` |

**Filter Matching Logic:**
- Within a single ErrorSourceFilter, all specified fields must match (AND logic)
- Multiple ErrorSourceFilter objects are OR'd together
- If no sourceFilters are specified, errors from any source can trigger the data source

#### Use Cases

On-Error Data Sources are useful for:

- **Error Monitoring**: Create DeltaFiles containing error details for external monitoring systems
- **Error Analytics**: Aggregate error information for analysis and reporting
- **Audit Trails**: Maintain records of processing failures with context from the original data

#### Example Configurations

**Catch All Critical Errors:**
```json
{
  "name": "critical-error-monitor",
  "type": "ON_ERROR_DATA_SOURCE",
  "description": "Monitor all critical errors across the system",
  "topic": "critical-errors",
  "errorMessageRegex": ".*(critical|fatal|severe).*"
}
```

**Production Environment Errors:**
```json
{
  "name": "production-error-handler",
  "type": "ON_ERROR_DATA_SOURCE", 
  "description": "Handle errors in production environment",
  "topic": "prod-errors",
  "metadataFilters": [
    {"key": "environment", "value": "production"}
  ],
  "includeSourceMetadataRegex": [".*"]
}
```

**Specific Flow Error Recovery:**
```json
{
  "name": "order-processing-recovery",
  "type": "ON_ERROR_DATA_SOURCE",
  "description": "Recover failed order processing attempts", 
  "topic": "order-recovery",
  "sourceFilters": [
    {
      "flowType": "TRANSFORM",
      "flowName": "order-validation"
    },
    {
      "flowType": "TRANSFORM",
      "flowName": "payment-processing"
    }
  ],
  "includeSourceMetadataRegex": ["order.*", "customer.*"],
  "includeSourceAnnotationsRegex": [".*"]
}
```

**Monitor Specific Action Types:**
```json
{
  "name": "validation-error-monitor",
  "type": "ON_ERROR_DATA_SOURCE",
  "description": "Monitor all validation action errors across all flows",
  "topic": "validation-errors",
  "sourceFilters": [
    {
      "actionClass": "com.example.ValidationAction"
    },
    {
      "actionClass": "org.company.validator.DataValidationAction"
    }
  ],
  "includeSourceMetadataRegex": [".*"]
}
```

**Note**: On-Error Data Sources only trigger for action processing errors. Configuration or wiring errors (such as missing subscribers to a topic or non-existent flows) do not generate OnError events.

## Transforms

Transforms consist of a series of Transform Actions that process the data. They can subscribe to topics published
by Data Sources or other Transforms, and can publish to topics that other flows can subscribe to.

```json
{
  "name": "example-transform",
  "type": "TRANSFORM",
  "description": "An example transform",
  "subscribe": [
    {"topic": "input-topic"}
  ],
  "transformActions": [
    {
      "name": "FirstTransformAction",
      "type": "org.deltafi.example.action.FirstTransformAction"
    },
    {
      "name": "SecondTransformAction",
      "type": "org.deltafi.example.action.SecondTransformAction"
    }
  ],
  "publish": {
    "rules": [
      {"topic": "output-topic"}
    ]
  }
}
```

### Data Sinks

Data Sinks define how data is sent out of the DeltaFi system. They consist of a single Egress Action and can subscribe to
topics published by Data Sources or Transforms.

```json
{
  "name": "example-data-sink",
  "type": "DATA_SINK",
  "description": "An example data sink",
  "subscribe": [
    {"topic": "output-topic"}
  ],
  "egressAction": {
    "name": "ExampleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "url": "${egressUrl}",
      "metadataKey": "deltafiMetadata"
    }
  }
}
```

## Flow Configuration

JSON files containing Flows should be placed in the `src/main/resources/flows` directory. They will be loaded when the
plugin is installed.

## Variables File

Flows may use variables that are set at runtime by operators. They are defined in the
`src/main/resources/flows/variables.json` file:

```json
[
  {
    "name": "egressUrl",
    "description": "Egress URL destination",
    "dataType": "STRING",
    "required": true,
    "defaultValue": "http://deltafi-egress-sink-service"
  }
]
```

Variables can be of `dataType`:
* STRING
* BOOLEAN
* NUMBER
* LIST
* MAP

To use a variable in a Flow, reference it like `${egressUrl}` as shown in the Egress example above.

## Publish-Subscribe Pattern
DeltaFi uses a publish-subscribe pattern to wire Flows together. Data Sources and Transforms can publish to topics,
while Transforms and Data Sinks can subscribe to topics.

### Publishing
Publishers declare rules that determine to which topics a DeltaFile will be sent after processing.
The `publish` configuration has the following options:

|     Field      | Description                                                                                                                                                                                        | Details                                                                                                                                                                                                                                |
|:--------------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| matchingPolicy | Determines whether the DeltaFile will be sent to all the matching topics or the first matching topic                                                                                               | <ul><li>ALL_MATCHING - use all matching rules (this is the default policy)</li><li>FIRST_MATCHING - use the first rule that matches</li></ul>                                                                                          |
|  defaultRule   | Determines the default action to take when no rules match for the given DeltaFile. If the default rule is PUBLISH and the topic has no subscribers, the DeltaFile will be moved to an error state. | <ul><li>ERROR - error the DeltaFile (this is the default behavior)</li><li>FILTER - filter the DeltaFile</li><li>PUBLISH - publish the DeltaFile to a default topic</li>                                                               |
|     rules      | Set of rules that specify which topics the DeltaFile will be sent to, optionally with conditions                                                                                                   | Each rule consists of a the following  <ul><li>topic - a topic to which to send the DeltaFile if the condition matches</li><li>condition - optional condition used to determine if the DeltaFile should be sent to the topic</li></ul> |

### Subscribing
Subscribers declare from which topics they will read DeltaFiles. If a DeltaFile matches multiple subscription rules, it
is only passed to the subscriber once. If the same DeltaFile hits on multiple rules, it is only passed to the subscriber
once. The subscription rules are of the same form as the publisher rules described above.

### Rule Conditions
Conditions are SpEL expressions that evaluate to true or false. They can reference DeltaFile metadata and content information.

Example conditions:

```spel 
// check for the existence of a metadata key
metadata.containsKey('required-key')

// check if a key has a specific value
metadata['required-key'] == 'required-value'

// check for content with a specific media type
hasMediaType('application/json')

// check for content with a specific name
!content.?[name == 'required.name'].isEmpty()
```

By using this publish-subscribe pattern, you can create complex data processing pipelines that are flexible and easy to modify.

### Examples
#### Example Publisher

This Timed Data Source:

1. Runs a smoke test ingress action daily at midnight (as specified by the cron schedule).
2. Creates DeltaFiles and publishes them to different topics based on their content type:
* Files with 'application/json' media type are published to the "json" topic.
* Files with 'application/xml' media type are published to the "xml" topic.
* Any other file types are published to the "unknown-media-type" topic (as specified by the default rule).
3. Uses a "FIRST_MATCHING" policy, meaning it will publish to the first topic whose condition is met.

This setup allows for automatic routing of different file types to appropriate processing flows, demonstrating how the
publish-subscribe pattern can be used to create flexible, content-aware data pipelines.

```json
{
  "name": "sample-timed-ingress",
  "type": "TIMED_INGRESS",
  "description": "Create smoke DeltaFiles and publish them based on content type",
  "timedIngressAction": {
    "name": "SmokeTestIngressAction",
    "type": "org.deltafi.core.action.ingress.SmokeTestIngressAction"
  },
  "publish": {
    "matchingPolicy": "FIRST_MATCHING",
    "defaultRule": {"defaultBehavior": "PUBLISH", "topic": "unknown-media-type"},
    "rules": [
      {"topic": "json", "condition": "hasMediaType('application/json')"},
      {"topic": "xml", "condition": "hasMediaType('application/xml')"}
    ]
  },
  "cronSchedule": "0 0 0 * * ?"
}
```

#### Example Subscriber

This Transform:

1. Subscribes to the "unknown-media-type" topic.
1. Applies a single transform action to detect the media type.
1. Publishes the result to the "processed-content" topic.

```json
{
  "name": "detect-media-type",
  "type": "TRANSFORM",
  "description": "Detect the media type of the content",
  "subscribe": [
    {"topic": "unknown-media-type"}
  ],
  "transformActions": [
    {
      "name": "DetectMediaTypeAction",
      "type": "org.deltafi.core.action.DetectMediaTypeTransformAction"
    }
  ],
  "publish": {
    "rules": [
      {"topic": "processed-content"}
    ]
  }
}
```
