# HashContent
Computes cryptographic hashes of content and stores them in metadata.

## Parameters
| Name                  | Description                                                           | Allowed Values                              | Required | Default  |
|-----------------------|-----------------------------------------------------------------------|---------------------------------------------|:--------:|:--------:|
| algorithm             | Hash algorithm to use                                                 | MD5<br/>SHA-1<br/>SHA-256<br/>SHA-384<br/>SHA-512 |    | SHA-256  |
| contentIndexes        | List of content indexes to include or exclude                         | integer (list)                              |          |          |
| contentTags           | List of content tags to include or exclude, matching any              | string (list)                               |          |          |
| excludeContentIndexes | Exclude specified content indexes                                     | boolean                                     |          | false    |
| excludeContentTags    | Exclude specified content tags                                        | boolean                                     |          | false    |
| excludeFilePatterns   | Exclude specified file patterns                                       | boolean                                     |          | false    |
| excludeMediaTypes     | Exclude specified media types                                         | boolean                                     |          | false    |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*) | string (list)                               |          |          |
| mediaTypes            | List of media types to include or exclude, supporting wildcards (*)   | string (list)                               |          |          |
| metadataKey           | Metadata key for the hash value                                       | string                                      |          | hash     |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. If any of these are set and the content is not matched, the
content is passed through unchanged without computing a hash.

## Output
### Content
Content is passed through unchanged.

### Metadata
| Key                         | Description                                           |
|-----------------------------|-------------------------------------------------------|
| hash (or hash.0, hash.1...) | The computed hash value in lowercase hexadecimal      |

For a single content piece, the hash is stored with the configured metadataKey (default: "hash").
For multiple content pieces, the index is appended (e.g., "hash.0", "hash.1").

## Errors
* On unsupported hash algorithm
* On failure to compute hash

