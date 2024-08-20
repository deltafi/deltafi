# Annotate
Adds annotations to a DeltaFile.

## Parameters
| Name             | Description                                                               | Allowed Values                                      | Required | Default |
|------------------|---------------------------------------------------------------------------|-----------------------------------------------------|:--------:|---------|
| annotations      | Key value pairs of annotations to be added                                | key&nbsp;-&nbsp;String<br/>value&nbsp;-&nbsp;String |          |         |
| metadataPatterns | List of regex patterns matching metadata keys to add as annotations       | String                                              |          |         |
| discardPrefix    | Prefix to remove from each metadata key before adding it as an annotation | String                                              |          |         |

## Inputs
### Content
Any

### Metadata
If metadataPatterns is set, input metadata may be used for annotations.

## Outputs
### Content
Input content is passed through unchanged.

### Annotations
Annotations set in the annotations parameter will be added.

If metadataPatterns is set, metadata matching the key patterns will be added as annotations if they are not already
set by the annotations parameter.

## Errors
- On an annotations parameter that contains a zero-length key or zero-length value
