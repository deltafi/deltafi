# RestPostEgress
Egress to an HTTP endpoint using POST

## Parameters
| Name         | Description                                                                                                                                         | Allowed Values | Required | Default |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| metadataKey  | Send metadata as JSON in this HTTP header field                                                                                                     | string         | ✔        |         |
| retryCount   | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 3       |
| retryDelayMs | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 150     |
| url          | The URL to send the DeltaFile to                                                                                                                    | string         | ✔        |         |

