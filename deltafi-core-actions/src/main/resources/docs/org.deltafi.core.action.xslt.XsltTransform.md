# XsltTransform
Transforms XML using XSLT.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|:-------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list) |          |         |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)  |          |         |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean        |          | false   |
| excludeContentTags    | Exclude specified content tags                                        | boolean        |          | false   |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean        |          | false   |
| excludeMediaTypes     | Exclude specified media types                                         | boolean        |          | false   |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)  |          |         |
| mediaTypes            | List of media types to consider, supporting wildcards (*)             | string (list)  |          |         |
| retainExistingContent | Retain the existing content                                           | boolean        |          | false   |
| xslt                  | XSLT transformation specification                                     | string         | ✔        |         |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
Transforms each content using the provided XSLT transformation specification. The
transformed content will have the same name as the input content and the media type will
be application/xml.

If retainExistingContent is true, each content will be retained, followed by its transformed content.

## Errors
* On invalid XSLT specification provided
* On failure to transform any content

