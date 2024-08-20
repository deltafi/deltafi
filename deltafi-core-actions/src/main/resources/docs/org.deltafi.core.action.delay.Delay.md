# Delay
Introduces a set or random delay to a flow.

## Parameters
| Name       | Description                                                                        | Allowed Values | Required | Default |
|------------|------------------------------------------------------------------------------------|----------------|:--------:|---------|
| minDelayMS | Minimum time to delay processing in ms. Set equal to maxDelayMS for a fixed delay. | Integer        |          | 0       |
| maxDelayMS | Maximum time to delay processing in ms. Set equal to minDelayMS for a fixed delay. | Integer        |          | 0       |

## Inputs
### Content
Any

### Metadata
Any

## Outputs
### Content
Input content is passed through unchanged.

### Metadata
Input metadata is passed through unchanged.

## Errors
None
