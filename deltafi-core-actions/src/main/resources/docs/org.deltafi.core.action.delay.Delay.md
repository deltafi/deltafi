# Delay
Introduces a set or random delay to a flow.

## Parameters
| Name       | Description                                                                        | Allowed Values | Required | Default |
|------------|------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| maxDelayMS | Maximum time to delay processing in ms. Set equal to minDelayMS for a fixed delay. | integer        |          | 0       |
| minDelayMS | Minimum time to delay processing in ms. Set equal to maxDelayMS for a fixed delay. | integer        |          | 0       |

## Output
Content is passed through unchanged.
### Metadata
Metadata is passed through unchanged

