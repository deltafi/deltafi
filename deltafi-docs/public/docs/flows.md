# Flows

Flows define the sequence of Actions that operate on data travelling through the system.

Actions defined in flows have a `type` which is the fully-qualified package and class name and a `name` which is the
logical name used in the flow. Each `type` of Action can have many named instantiations.

## Transform Flows

Transform Flows consist of a series of Transform Actions.
The data takes a straight path through the system.

```json
{
  "name": "simple-transform",
  "type": "TRANSFORM",
  "description": "A simple transform flow that processes data and sends it out using REST",
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
  "egressAction": {
    "name": "SimpleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "egressFlow": "simpleTransformEgressFlow",
      "metadataKey": "deltafiMetadata",
      "url": "${egressUrl}"
    }
  }
}
```

### Egress Flows

Egress Flows contain a single Egress Action.

```json
{
  "name": "passthrough",
  "type": "EGRESS",
  "description": "Egress flow that passes data through unchanged",
  "egressAction": {
    "name": "PassthroughEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "egressFlow": "egressFlow",
      "metadataKey": "deltafiMetadata",
      "url": "${egressUrl}"
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

To use a variable in a Flow, reference it like `${egressUrl}`:

```java
{
  "name": "passthrough",
  "type": "EGRESS",
  ...
  "egressAction": {
    "name": "PassthroughEgressAction",
    "actionType": "EGRESS",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "url": "${egressUrl}"
    }
  }
}
```

## Publish - Subscribe
Flows can be wired together using the publish-subscribe pattern. When a publisher finishes processing a DeltaFile, it will evaluate the DeltaFile against a set of publish rules to determine which topics to send the DeltaFile to. Subscribers, subscribe to topics, receiving DeltaFiles from the topics that meet the conditions specified in the subscription rule.

Currently, TimedIngressActions support publishing DeltaFiles and TransformFlows support subscriptions.

### Publishers
A publisher declares a set of rules that determine which topics a DeltaFile will be sent to after the publisher has completed its work. The publisher has the following options.

|     Field      | Description                                                                                                                                                                                        | Details                                                                                                                                                                                                                           |
|:--------------:|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| matchingPolicy | Determines whether the DeltaFile will be sent to all the matching topics or the first matching topic                                                                                               | <ul><li>ALL_MATCHING - use all matching rules (this is the default policy)</li><li>FIRST_MATCHING - use the first rule that matches</li></ul>                                                                                     |
|  defaultRule   | Determines the default action to take when no rules match for the given DeltaFile. If the default rule is PUBLISH and the topic has no subscribers, the DeltaFile will be moved to an error state. | <ul><li>ERROR - error the DeltaFile (this is the default behavior)</li><li>FILTER - filter the DeltaFile</li><li>PUBLISH - publish the DeltaFile to a default topic</li>                                                          |
|     rules      | Set of rules that specify which topics the DeltaFile will be sent to.                                                                                                                              | Each rule consists of a the following  <ul><li>topics - set of topics to send to if the condition matches</li><li>condition - optional condition used to determine if the DeltaFile should be sent to the set of topics</li></ul> |

### Subscribers
A subscriber declares a set of subscriptions that determine which topics it will read DeltaFiles from. If the same DeltaFile matches multiple rules, it is only passed to the subscriber once. The subscription rules are of the same form as the publisher rules described above.

### Rule Conditions
Conditions are SPeL expressions that must evaluate to true or false. They are run against the DeltaFile metadata and content information (content name, mediaType, and size).

Example conditions:

```spel 
// check for the existence of a metdata key
metadata.containsKey('required-key')

// check the key/value exists
metadata['required-key'] == 'required-value'

// check for content with the given media type
hasMediaType('application/json')

// check for content with the given name
!content.?[name == 'required.name'].isEmpty()
```

### Examples
#### Example Publisher
Timed ingress that publishes the `DeltaFile` based on the content type
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
Transform flow that subscribes to a specific topic.

```json
{
  "name": "subscriber-unknown-types",
  "type": "TRANSFORM",
  "description": "Detect the media type of the content",
  "subscriptions": [
    {"topics": ["unknown-media-type"]}
  ],
  "transformActions": [
    {
      "name": "SampleTransformAction",
      "type": "org.deltafi.core.action.DetectMediaTypeTransformAction"
    }
  ],
  "egressAction": {
    "name": "SampleEgressAction",
    "type": "org.deltafi.core.action.RestPostEgressAction",
    "parameters": {
      "url": "http://deltafi-egress-sink-service/blackhole",
      "metadataKey": "detlafi"
    }
  }
}
```