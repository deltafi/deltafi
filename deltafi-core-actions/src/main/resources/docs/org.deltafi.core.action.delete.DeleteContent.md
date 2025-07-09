# DeleteContent
Deletes content.

## Parameters
| Name                  | Description                                                           | Allowed Values | Required | Default |
|-----------------------|-----------------------------------------------------------------------|----------------|:--------:|:-------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list) |          |         |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)  |          |         |
| deleteAllContent      | Delete all content                                                    | boolean        |          | false   |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean        |          | false   |
| excludeContentTags    | Exclude specified content tags                                        | boolean        |          | false   |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean        |          | false   |
| excludeMediaTypes     | Exclude specified media types                                         | boolean        |          | false   |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)  |          |         |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | string (list)  |          |         |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
All input content is deleted if the deleteAllContent parameter is true.

