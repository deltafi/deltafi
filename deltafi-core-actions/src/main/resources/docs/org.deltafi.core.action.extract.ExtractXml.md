# ExtractXml
Extract values from XML content and write them to metadata or annotations.

## Parameters
| Name                  | Description                                                                                                                        | Allowed Values                                      | Required | Default                                   |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|:--------:|-------------------------------------------|
| xpathToKeysMap        | Map of XPath expressions to keys. Values will be extracted using XPath and added to the corresponding metadata or annotation keys. | key&nbsp;-&nbsp;String<br/>value&nbsp;-&nbsp;String |   Yes    |                                           |
| extractTarget         | Extract to metadata or annotations.                                                                                                | METADATA<br/>ANNOTATIONS                            |          | METADATA                                  |
| contentIndexes        | List of content indexes to include or exclude                                                                                      | Integer                                             |          | empty&nbsp;- all content is considered    |
| excludeContentIndexes | Exclude specified content indexes                                                                                                  | true<br/>false                                      |          | false                                     |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*)                                                              | String                                              |          | empty&nbsp;- all filenames are considered |
| excludeFilePatterns   | Exclude specified file patterns                                                                                                    | true<br/>false                                      |          | false                                     |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)                                                                | String                                              |          | media type associated with inputFormat    |
| excludeMediaTypes     | Exclude specified media types                                                                                                      | true<br/>false                                      |          | false                                     |
| handleMultipleKeys    | Concatenate all or distinct values extracted from multiple content, or just use the first or last content.                         | ALL<br/>DISTINCT<br/>FIRST<br/>LAST                 |          | ALL                                       |
| allKeysDelimiter      | Delimiter to use if handleMultipleKeys is ALL or DISTINCT                                                                          | String                                              |          | ,&nbsp;(comma)                            |
| errorOnKeyNotFound    | Error if a key is not found.                                                                                                       | true<br/>false                                      |          | false                                     |

## Inputs
### Content
One or more

## Outputs
### Content
Input content is passed through unchanged.

### Metadata
If extractTarget is METADATA, XPath expressions from xpathToKeysMap keys are used to extract values from the input
content and write them to metadata keys which are the corresponding xpathToKeysMap values.

Input content extracted from may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. Values extracted from multiple contents are handled according to handleMultipleKeys.

### Annotations
If extractTarget is ANNOTATIONS, XPath expressions from xpathToKeysMap keys are used to extract values from the
input content and write them to annotation keys which are the corresponding xpathToKeysMap values.

Input content extracted from may be selected (or inversely selected using the exclude parameters) with contentIndexes,
mediaTypes, and/or filePatterns. Values extracted from multiple contents are handled according to handleMultipleKeys.

## Errors
- On failure to parse any content from XML
- On failure to evaluate any XPath expression in xpathToKeysMap keys
- On errorOnKeyNotFound set to true and no values can be extracted from input content for any xpathToKeysMap key
