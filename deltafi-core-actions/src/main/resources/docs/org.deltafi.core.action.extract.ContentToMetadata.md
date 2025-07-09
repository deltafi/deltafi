# ContentToMetadata
Moves selected content to metadata or annotations.

## Parameters
| Name                  | Description                                                           | Allowed Values           | Required | Default  |
|-----------------------|-----------------------------------------------------------------------|--------------------------|:--------:|:--------:|
| contentIndexes        | List of content indexes to include or exclude                         | integer (list)           |          |          |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)            |          |          |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean                  |          | false    |
| excludeContentTags    | Exclude specified content tags                                        | boolean                  |          | false    |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean                  |          | false    |
| excludeMediaTypes     | Exclude specified media types                                         | boolean                  |          | false    |
| extractTarget         | Extract to metadata or annotation.                                    | ANNOTATIONS<br/>METADATA |          | METADATA |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)            |          |          |
| key                   | Key to use for metadata or annotation                                 | string                   | ✔        |          |
| maxSize               | Maximum number of characters in the key's value                       | integer                  |          | 512      |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | string (list)            |          |          |
| multiValueDelimiter   | Delimiter to use if multiple content matched                          | string                   |          | ,        |
| retainExistingContent | Retain the existing content                                           | boolean                  |          | false    |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged.

## Output
### Content
If content is not selected or retainExistingContent is true, it will pass through
unchanged. Otherwise, content will be removed.

### Metadata
If extractTarget is METADATA, content will be set in metadata with the `key`
parameter.

### Annotations
If extractTarget is ANNOTATIONS, content will be set in annotations with the `key`
parameter.

