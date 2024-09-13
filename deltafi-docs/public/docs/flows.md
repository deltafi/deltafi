# Data Sources, Flows, and Egresses

DeltaFi processes data through a system of Data Sources, Flows, and Egresses. Each plays a distinct role in the data
processing pipeline:

1. Data Sources: These are the entry points for data into the DeltaFi system.
1. Flows: These define sequences of Transform Actions that operate on the data.
1. Egresses: These define how processed data is sent out of the DeltaFi system.

## Data Sources

Data Sources are the entry points for data into the DeltaFi system. There are two types of Data Sources:

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

## Transform Flows

Transform Flows consist of a series of Transform Actions that process the data. They can subscribe to topics published
by Data Sources or other Transform Flows, and can publish to topics that other flows can subscribe to.

```json
{
  "name": "example-transform",
  "type": "TRANSFORM",
  "description": "An example transform flow",
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

### Egresses

Egresses define how data is sent out of the DeltaFi system. They consist of a single Egress Action and can subscribe to
topics published by Data Sources or Transform Flows.

```json
{
  "name": "example-egress",
  "type": "EGRESS",
  "description": "An example egress flow",
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
DeltaFi uses a publish-subscribe pattern to wire Flows together. Data Sources and Transform Flows can publish to topics,
while Transform Flows and Egresses can subscribe to topics.

### Publishing
Publishers declare rules that determine which topics a DeltaFile will be sent to after processing.
The publish configuration has the following options:

|     Field      | Description                                                                                                                                                                                        | Details                                                                                                                                                                                                                           |
|:--------------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| matchingPolicy | Determines whether the DeltaFile will be sent to all the matching topics or the first matching topic                                                                                               | <ul><li>ALL_MATCHING - use all matching rules (this is the default policy)</li><li>FIRST_MATCHING - use the first rule that matches</li></ul>                                                                                     |
|  defaultRule   | Determines the default action to take when no rules match for the given DeltaFile. If the default rule is PUBLISH and the topic has no subscribers, the DeltaFile will be moved to an error state. | <ul><li>ERROR - error the DeltaFile (this is the default behavior)</li><li>FILTER - filter the DeltaFile</li><li>PUBLISH - publish the DeltaFile to a default topic</li>                                                          |
|     rules      | Set of rules that specify which topics the DeltaFile will be sent to, optionally with conditions                                                                                                   | Each rule consists of a the following  <ul><li>topics - set of topics to send to if the condition matches</li><li>condition - optional condition used to determine if the DeltaFile should be sent to the set of topics</li></ul> |

### Subscribing
Subscribers declare which topics they will read DeltaFiles from. If a DeltaFile matches multiple subscription rules, it
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
  "publishRules": {
    "matchingPolicy": "FIRST_MATCHING",
    "defaultRule": {"defaultBehavior": "PUBLISH", "topic": "unknown-media-type"},
    "rules": [
      {"topics": ["json"], "condition": "hasMediaType('application/json')"},
      {"topics": ["xml"], "condition": "hasMediaType('application/xml')"}
    ]
  },
  "cronSchedule": "0 0 0 * * ?"
}
```

#### Example Subscriber

This Transform Flow:

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
