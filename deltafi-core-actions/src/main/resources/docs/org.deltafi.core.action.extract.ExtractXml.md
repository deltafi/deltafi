# ExtractXml
Extracts values from XML content and writes them to metadata or annotations.

## Parameters
| Name                  | Description                                                                                                                        | Allowed Values                      | Required | Default  |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|:--------:|:--------:|
| allKeysDelimiter      | Delimiter to use if handleMultipleKeys is ALL or DISTINCT                                                                          | string                              |          | ,        |
| contentIndexes        | List of content indexes to include or exclude                                                                                      | integer (list)                      |          |          |
| contentTags           | List of content tags to include or exclude, matching any                                                                           | string (list)                       |          |          |
| errorOnKeyNotFound    | Error if a key is not found.                                                                                                       | boolean                             |          | false    |
| excludeContentIndexes | Exclude specified content indexes                                                                                                  | boolean                             |          | false    |
| excludeContentTags    | Exclude specified content tags                                                                                                     | boolean                             |          | false    |
| excludeFilePatterns   | Exclude specified file patterns                                                                                                    | boolean                             |          | false    |
| excludeMediaTypes     | Exclude specified media types                                                                                                      | boolean                             |          | false    |
| extractTarget         | Extract to metadata or annotations.                                                                                                | ANNOTATIONS<br/>METADATA            |          | METADATA |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*)                                                              | string (list)                       |          |          |
| handleMultipleKeys    | Concatenate all or distinct values extracted from multiple content, or just use the first or last content.                         | ALL<br/>DISTINCT<br/>FIRST<br/>LAST |          | ALL      |
| mediaTypes            | List of media types to consider, supporting wildcards (*)                                                                          | string (list)                       |          |          |
| retainExistingContent | Retain the existing content                                                                                                        | boolean                             |          | false    |
| xpathToKeysMap        | Map of XPath expressions to keys. Values will be extracted using XPath and added to the corresponding metadata or annotation keys. | string (map)                        | ✔        |          |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
Content is passed through unchanged.
### Metadata
If extractTarget is METADATA, XPath expressions from xpathToKeysMap keys are used
to extract values from the input content and write them to metadata keys which are the
corresponding xpathToKeysMap values.

Values extracted from multiple contents are handled according to handleMultipleKeys.

### Annotations
If extractTarget is ANNOTATIONS, XPath expressions from xpathToKeysMap keys are
used to extract values from the input content and write them to annotation keys which
are the corresponding xpathToKeysMap values.

Values extracted from multiple contents are handled according to handleMultipleKeys.

## Errors
* On failure to parse any content from XML
* On failure to evaluate any XPath expression in xpathToKeysMap keys
* On errorOnKeyNotFound set to true and no values can be extracted from input content for any xpathToKeysMap key

