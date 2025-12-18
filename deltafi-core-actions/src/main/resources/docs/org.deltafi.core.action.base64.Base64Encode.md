# Base64Encode
Encodes content using Base64.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default                  |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|:------------------------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list) |          |                          |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)  |          |                          |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean        |          | false                    |
| excludeContentTags    | Exclude specified content tags                                        | boolean        |          | false                    |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean        |          | false                    |
| excludeMediaTypes     | Exclude specified media types                                         | boolean        |          | false                    |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)  |          |                          |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | string (list)  |          |                          |
| outputMediaType       | Media type of the encoded content                                     | string         |          | text/plain               |
| retainExistingContent | Retain the existing content                                           | boolean        |          | false                    |
| urlSafe               | Use URL-safe Base64 encoding (uses - and _ instead of + and /)        | boolean        |          | false                    |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
Encodes each selected content using Base64 encoding. The encoded content
will have the same name as the input content with '.b64' appended.

If retainExistingContent is true, each content will be retained, followed by its transformed content.

## Errors
* On failure to encode any content

