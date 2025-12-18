# Base64Decode
Decodes Base64-encoded content.

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
| outputMediaType       | Media type of the decoded content                                     | string         |          | application/octet-stream |
| retainExistingContent | Retain the existing content                                           | boolean        |          | false                    |
| urlSafe               | Use URL-safe Base64 decoding (expects - and _ instead of + and /)     | boolean        |          | false                    |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
Decodes each selected content from Base64. If the content name ends with
'.b64', that suffix is removed; otherwise the name is unchanged.

If retainExistingContent is true, each content will be retained, followed by its transformed content.

## Errors
* On invalid Base64 content
* On failure to decode any content

