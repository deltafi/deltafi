# Flows

Place flow JSON files in the `/flows` folder of a plugin.

Flows define the path that data takes through the system.

Actions defined in flows have a `type` which is the fully qualified package and class name and a `name` which is the
logical name used in the flow. Each `type` of Action can have many named instantiations.

## Variables

The `variables.json` creates variables that can be used in flows and set at runtime by operators.

Variables can be of `dataType`:
* STRING
* BOOLEAN
* NUMBER
* LIST
* MAP

To use a variable in Action configuration in a Flow, reference it like `${myVariable}`.

```json
{
  "variables": [
    {
      "name": "egressUrl",
      "description": "Egress URL destination",
      "dataType": "STRING",
      "required": true,
      "defaultValue": "http://deltafi-egress-sink-service"
    }
  ]
}
```

## Ingress Flows

Ingress flows steer data through a series of 0 to many Transform Actions and into a Load Action.

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

## Enrich Flows

Enrich flows configure Domain Actions and Enrich Actions that should be run with the plugin. Each Domain Action will be triggered when the incoming DeltaFile contains the Domain required by the action. Each Enrich Action can be triggered
by Domains and by other Enrichment, controlling what data is routed to the Action.

```json
{
  "name": "artificial-enrichment",
  "type": "ENRICH",
  "description": "A synthetic enrichment that acts on binary domain objects",
  "domainActions": [{
    "name": "BinaryDomainAction",
    "type": "org.deltafi.passthrough.action.RoteDomainAction",
    "requiresDomain": [
      "domain"
    ]
  }],
  "enrichActions": [ {
      "name": "BinaryEnrichAction",
      "type": "org.deltafi.passthrough.action.RoteEnrichAction",
      "requiresDomains": [
        "binary"
      ],
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

Egress Flows subscribe to Domains and Enrichment in the same manner as Enrich Flows. Egress consists of a Format Action,
0 to many Validate Actions, and an Egress Action.

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
    "requiresDomains": [
      "binary"
    ],
    "type": "org.deltafi.passthrough.action.RoteFormatAction"
  },
  "validateActions": [
    {
      "name": "PassthroughValidateAction",
      "type": "org.deltafi.passthrough.action.RubberStampValidateAction"
    }
  ],
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
