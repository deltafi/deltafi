# Flows

Flows define the path of actions that data takes through the system.

Actions defined in flows have a `type` which is the fully-qualified package and class name and a `name` which is the
logical name used in the flow. Each `type` of Action can have many named instantiations.

## Ingress Flows

Ingress flows steer data through an optional series of Transform Actions and into a Load or Join Action.

```json
{
  "name": "passthrough",
  "type": "INGRESS",
  "description": "Flow that passes data through unchanged",
  "transformActions": [ {
      "name": "PassthroughTransformAction",
      "type": "org.deltafi.passthrough.action.RoteTransformAction"
    }
  ],
  "loadAction": {
    "name": "PassthroughLoadAction",
    "type": "org.deltafi.passthrough.action.RoteLoadAction",
    "parameters": {
      "domains": [
        "binary"
      ]
    }
  }
}
```
```json
{
  "name": "join-max-ingress",
  "type": "INGRESS",
  "description": "Collects up to 3 ingress files at a time, waiting up to 1 hour, for processing as a single DeltaFile",
  "joinAction": {
    "name": "RoteJoinAction",
    "actionType": "JOIN",
    "type": "org.deltafi.passthrough.action.RoteJoinAction",
    "maxAge": "PT1H",
    "maxNum": 3,
    "parameters": {
      "domains": [
        "binary"
      ]
    }
  }
}
```

## Enrich Flows

Enrich flows steer data through an optional series of Domain Actions followed by an optional series of Enrich Actions.

Domain Actions are triggered when the DeltaFile contains all the Domains required by the action. The `requiresDomains`
list is optional.

Enrich Actions are triggered after Domain Actions when the DeltaFile contains all the Domains, Enrichments, and
metadata required by the action. The `requiresDomains`, `requiresEnrichments`, and `requiresMetadata` lists are
optional.

```json
{
  "name": "artificial-enrichment",
  "type": "ENRICH",
  "description": "A synthetic enrichment that acts on binary domain objects",
  "domainActions": [{
    "name": "BinaryDomainAction",
    "type": "org.deltafi.passthrough.action.RoteDomainAction",
    "requiresDomains": [
      "domain-1",
      "domain-2"
    ]
  }],
  "enrichActions": [{
      "name": "TestEnrichAction",
      "type": "org.deltafi.passthrough.action.RoteEnrichAction",
      "parameters": {
        "enrichments": {
          "test-enrichment": "test enrichment value"
        }
      }
    },{
      "name": "BinaryEnrichAction",
      "type": "org.deltafi.passthrough.action.RoteEnrichAction",
      "requiresDomains": [
        "binary"
      ],
      "requiresEnrichments": [
        "test-enrichment"
      ],
      "requiresMetadata": [{
        "key": "the-key",
        "value": "the-value"
      }],
      "parameters": {
        "enrichments": {
          "binaryEnrichment": "binary enrichment value"
        }
      }
    }
  ]
}
```

## Egress Flows

Egress Flows steer data through a required Format Action, an optional series of Validate Actions, and a required Egress
Action.

Format Actions are triggered when the DeltaFile contains all the Domains and Enrichments required by the action. The
`requiresDomains` and `requiresEnrichments` lists are optional.

```json
{
  "name": "passthrough",
  "type": "EGRESS",
  "description": "Egress flow that passes data through unchanged",
  "includeIngressFlows": [
    "passthrough"
  ],
  "formatAction": {
    "name": "PassthroughFormatAction",
    "type": "org.deltafi.passthrough.action.RoteFormatAction",
    "requiresDomains": [
      "binary"
    ]
  },
  "validateActions": [{
    "name": "PassthroughValidateAction",
    "type": "org.deltafi.passthrough.action.RubberStampValidateAction"
  }],
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

## Flow Files

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
