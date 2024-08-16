# MetadataToContent
Converts metadata to JSON content.

## Parameters
| Name                  | Description                                                                                   | Allowed Values | Required | Default                          |
|-----------------------|-----------------------------------------------------------------------------------------------|----------------|:--------:|----------------------------------|
| filename              | Filename for the new JSON content                                                             | String         |          | metadata.json                    |
| metadataPatterns      | List of regex patterns matching metadata keys to include. If empty, all metadata is included. | String         |          | empty - all metadata is included |
| retainExistingContent | Retain the existing content                                                                   | true<br/>false |          | true                             |

## Inputs
### Content
Any

### Metadata
Any

## Outputs
### Content
If non-empty metadataPatterns is configured, input metadata matching metadataPatterns is converted to JSON content.
Otherwise, all input metadata is converted to JSON content.

The JSON content will be named filename with a media type of application/json.

If retainExistingContent is true, input content is passed through unchanged before appending the new JSON content. If
retainExistingContent is false, the new JSON content is set as the only content.

## Errors
- On failure to convert metadata to JSON
