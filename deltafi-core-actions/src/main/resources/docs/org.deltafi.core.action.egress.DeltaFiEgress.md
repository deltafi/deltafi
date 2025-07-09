# DeltaFiEgress
Egresses to local or remote DeltaFi.

## Parameters
| Name         | Description                                                                                                                                         | Allowed Values | Required | Default |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| flow         | Name of the data source on the receiving DeltaFi                                                                                                    | string         |          |         |
| retryCount   | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 3       |
| retryDelayMs | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer        |          | 150     |
| sendLocal    | Send to the local DeltaFi; determines URL automatically                                                                                             | boolean        |          | false   |
| url          | The URL to send the DeltaFile to                                                                                                                    | string         |          |         |

