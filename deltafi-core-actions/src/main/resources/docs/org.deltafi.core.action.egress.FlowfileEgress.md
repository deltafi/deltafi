# FlowfileEgress
Egresses content and metadata in a NiFi V1 FlowFile (application/flowfile).

## Parameters
| Name         | Description                                                                                                                                         | Allowed Values | Required | Default |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| retryCount   | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 3       |
| retryDelayMs | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 150     |
| url          | The URL to send the DeltaFile to                                                                                                                    | string         | ✔        |         |

