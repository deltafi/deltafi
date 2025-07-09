# Annotate
Adds annotations to a DeltaFile.

## Parameters
| Name             | Description                                                               | Allowed Values | Required | Default |
|------------------|---------------------------------------------------------------------------|----------------|:--------:|:-------:|
| annotations      | Key value pairs of annotations to be added                                | string (map)   |          |         |
| discardPrefix    | Prefix to remove from each metadata key before adding it as an annotation | string         |          |         |
| metadataPatterns | List of regex patterns matching metadata keys to add as annotations       | string (list)  |          |         |

## Output
Content is passed through unchanged.
### Annotations
Annotations set in the annotations parameter will be added.

If the metadataPatterns parameter is set, metadata matching the key patterns will be
added if not already set by the annotations parameter.

## Errors
* On an annotation parameter that contains a zero-length key or value

