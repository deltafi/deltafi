# ValidateMetadata
Validates metadata.

## Parameters
| Name             | Description                                                     | Allowed Values | Required | Default |
|------------------|-----------------------------------------------------------------|----------------|:--------:|:-------:|
| requiredMetadata | Required metadata keys and required RegEx pattern, if not empty | string (map)   |          |         |

## Output
Content is passed through unchanged.
### Metadata
Required metadata is validated.

The requiredMetadata map is used to specify metadata keys that are required from the
input. Within the map, a required metadata key may optional specify a RegEx pattern the
key's value must match. An empty string ("") or RegEx pattern of ".*" may be used of the
key's value can be of any value pattern.

