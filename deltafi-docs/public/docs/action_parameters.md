# Action Parameters

In DeltaFi, `Actions` are the building blocks of flows. Each action in the flow can be configured with a known set of parameters that can tailor the action behavior for the flow. These parameters can be set in three ways, with literal values, from plugin variables, or using templates to extract the value from the `ActionInput` being sent to the `Action`.

### Literal Value

This is a hard coded value used directly in the action parameter. This should be used when you have a parameter that has a fixed value in the flow.

Example - hard code the format parameter to `GZIP` in the Decompress action
```yaml
name: gzip-transform
type: TRANSFORM
description: Flow that expects gzipped data. Decompresses and publishes the data.
subscribe:
  - topic: gzipped-file
transformActions:
  - name: Decompress
    type: org.deltafi.core.action.compress.Decompress
    parameters:
      format: GZIP
publish:
  rules:
    - topic: decompressed-file
```

### Plugin Variable

Plugin variables are typed values that can be referenced in action parameters by name.  They allow operators to change an action parameter value at runtime as well as reuse a value across actions.  Action parameters should be set with a plugin variable when the value needs to be reconfigurable.  To use a plugin variable in your action parameter, you wrap the name of the variable in `${}`.

Example - use a plugin variable named `egressUrl` in url parameter of the HttpEgress action

```yaml
---
name: example-data-sink
type: DATA_SINK
description: An example data sink
subscribe:
  - topic: output-topic
egressAction:
  name: HttpPost
  type: org.deltafi.core.action.egress.HttpEgress
  parameters:
    url: ${egressUrl}
    method: POST
    metadataKey: deltafiMetadata
```

### Parameter Templating

Parameter templating pulls information out of the current `ActionInput` being sent to the `Action` into the action parameter. To use a template wrap the parameter value in `{{ }}`. Parameter templating should be used when parameters need to be adjusted dynamically based on the `ActionInput`.  

The following fields are available to use in a parameter template.

| Field                        | Description                                                                                          | 
|------------------------------|------------------------------------------------------------------------------------------------------|
| `{{ deltaFileName }}`        | The name of the DeltaFile                                                                            |
| `{{ did }}`                  | The did of the DeltaFile                                                                             |
| `{{ metadata }}`             | The metadata from the first DeltaFileMessage                                                         |
| `{{ content }}`              | The content list from the first DeltaFileMessage                                                     |
| `{{ actionContext }}`        | The current ActionContext (see [Action Contex](actions#context) for information about the subfields) |
| `{{ deltaFileMessages }}`    | The full list of DeltaFileMessages, useful for joins                                                 |
| `{{ now() }}`                | Helper method to get the current timestamp                                                           |

Example - use templating to pull out various pieces of information about the DeltaFile being sent to an action
```yaml
---
name: params-to-content
type: TRANSFORM
description: Sample flow that extracts DeltaFile information into action parameters
subscribe:
- topic: params-to-content
transformActions:
- name: AddMetadata
  type: org.deltafi.core.action.metadata.ModifyMetadata
  parameters:
    addOrModifyMetadata:
      type: ${dataType}
- name: ParamsToContent
  type: org.deltafi.sample.actions.ParamsToContent
  parameters:
    contentParams:
      did: "{{ did }}"
      deltaFileName: "{{ deltaFileName }}"
      type: "{{ metadata['type'] }}"
      timestamp: "{{ now() }}"
      dataSource: "{{ actionContext.dataSource }}"
      processedBy: "{{ actionContext.flowName + '.' + actionContext.actionName }}"
      contentInfo:
        name: "{{ content[0].name }}"
        mediaType: "{{ content[0].mediaType }}"
        size: "{{ content[0].getSize() }}"
publish:
  rules:
  - topic: deltafile-param-content
```

#### Templating - Spring Expression Language (SPeL)

The templates used in action parameters can include SPeL expressions which are evaluated to a string value. This gives operators the ability to do things like call methods against the values or filter/search through content and metadata. The template can be embedded inside a literal value, for example - `https://api.service/{{ deltaFileName.toLowerCase() }}` would evaluate to `https://api.service/input.txt` when the DeltaFileName is `Input.txt`. Templates can also be used within the value of a plugin variable.