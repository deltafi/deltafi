# ExtractJson
Extract values from JSON content and write them to metadata or annotations.

## Parameters
| Name               | Description                                                                                                                              | Allowed Values                                      | Required | Default                                   |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|:--------:|-------------------------------------------|
| jsonPathToKeysMap  | Map of JSONPath expressions to keys. Values will be extracted using JSONPath and added to the corresponding metadata or annotation keys. | key&nbsp;-&nbsp;String<br/>value&nbsp;-&nbsp;String |   Yes    |                                           |
| extractTarget      | Extract to metadata or annotations.                                                                                                      | METADATA<br/>ANNOTATIONS                            |          | METADATA                                  |
| contentIndexes     | List of content indexes to consider                                                                                                      | Integer                                             |          | empty&nbsp;- all content is considered    |
| mediaTypes         | List of media types to consider, supporting wildcards (*)                                                                                | String                                              |          | [application/json]                        |
| filePatterns       | List of file patterns to consider, supporting wildcards (*)                                                                              | String                                              |          | empty&nbsp;- all filenames are considered |
| handleMultipleKeys | Concatenate all or distinct values extracted from multiple content, or just use the first or last content.                               | ALL<br/>DISTINCT<br/>FIRST<br/>LAST                 |          | ALL                                       |
| allKeysDelimiter   | Delimiter to use if handleMultipleKeys is ALL or DISTINCT                                                                                | String                                              |          | ,&nbsp;(comma)                            |
| errorOnKeyNotFound | Error if a key is not found.                                                                                                             | true<br/>false                                      |          | false                                     |

## Inputs
### Content
One or more

## Outputs
### Content
Input content is passed through unchanged.

### Metadata
If extractTarget is METADATA, JSONPath expressions from jsonPathToKeysMap keys are used to extract values from the input
content and write them to metadata keys which are the corresponding jsonPathToKeysMap values.

Input content extracted from may be specified using contentIndexes, mediaTypes, and/or filePatterns. Values extracted
from multiple contents are handled according to handleMultipleKeys.

### Annotations
If extractTarget is ANNOTATIONS, JSONPath expressions from jsonPathToKeysMap keys are used to extract values from the
input content and write them to annotation keys which are the corresponding jsonPathToKeysMap values.

Input content extracted from may be specified using contentIndexes, mediaTypes, and/or filePatterns. Values extracted
from multiple contents are handled according to handleMultipleKeys.

## Errors
- On errorOnKeyNotFound set to true and no values can be extracted from input content for any jsonPathToKeysMap key
