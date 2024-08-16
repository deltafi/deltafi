# Error
Errors by default or when optional criteria is met in metadata.

## Parameters
| Name            | Description                                                                              | Allowed Values | Required | Default         |
|-----------------|------------------------------------------------------------------------------------------|----------------|:--------:|-----------------|
| message         | Error message                                                                            | String         |          | Errored by fiat |
| metadataTrigger | Metadata key on which to trigger an error. The error message will be the metadata value. | String         |          |                 |

## Inputs
### Content
Any

### Metadata
If metadataTrigger is set, the input metadata will be checked to determine if an error should occur and what the error
message will be.

## Outputs
### Content
Input content is passed through unchanged if metadataTrigger is set and no matching input metadata is present.

## Errors
- On metadataTrigger unset or when input metadata matching metadataTrigger is present
