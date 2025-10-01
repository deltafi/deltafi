# Advanced Routing

DeltaFiles are typically ingressed into DeltaFi by specifying an ingress flow name during ingress. This puts the
responsibility of choosing the correct flow in the client. However, DeltaFi supports advanced routing of DeltaFiles
which automatically detects the correct ingress flow. Advanced routing is accomplished by comparing the metadata and/or
filename of the ingressed data against an ordered set of rules.

## Rules

Advanced routing rules consist of the following:

* rule name (id)
* flow name
* priority
* match criteria

Match criteria must specify one or both of the following:

* filename regular expression (regex) match pattern
* one or more key/value pairs

In some cases, it may be necessary to create more than one rule for the same flow. This is supported, though each rule
must have its own unique name/id.

## Routing Decision

When data is ingressed into DeltaFi which does not specify the flow parameter or uses the reserved flow parameter value
of `auto-resolve`, the DeltaFi ingress process will automatically check advanced routing rules to attempt a routing
decision. Rules will be checked in priority order, and match criteria will be evaluated against the ingress parameters.
The first rule satisfied will determine the ingress flow. Thus, priority ordering of rules is important, in particular
when some march criteria is more specific than others.

## Sample Rules

### Filename Regex Match

The following rule routes any ingress data which has a filename that starts with `MyFlow` to ingress flow named `myflow`
.

```json
  {
  "name": "myFlowRegexRule",
  "flow": "myflow",
  "priority": 500,
  "filenameRegex": "^MyFlow.*",
  "requiredMetadata": null
}
```

### Metadata (Key/Value) Match

The following rule routes any ingress data which contains metadata with a key/value pairs of `foo/bar` to the ingress
flow 1fb-flow1. For a metadata match, the ingress data must contain all the key/value pairs specified. The ingress
metadata can contain other key/value pairs, and will still match the rule so long as all pairs in the rule are found.

```json
  {
  "name": "fooBarRule",
  "flow": "fb-flow",
  "priority": 500,
  "filenameRegex": null,
  "requiredMetadata": [
    {
      "key": "foo",
      "value": "bar"
    }
  ]
}
```

