# HttpEgress
Egresses to an HTTP endpoint.

## Parameters
| Name         | Description                                                                                                                                         | Allowed Values                    | Required | Default |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|:--------:|:-------:|
| extraHeaders | Additional key\/value pairs to set in the HTTP header                                                                                               | string (map)                      |          |         |
| metadataKey  | Send metadata as JSON in this HTTP header field                                                                                                     | string                            | ✔        |         |
| method       | HTTP method to use when sending the data: DELETE, PATCH, POST, or PUT                                                                               | DELETE<br/>PATCH<br/>POST<br/>PUT |          | POST    |
| retryCount   | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer                           |          | 3       |
| retryDelayMs | DEPRECATED: will be removed in a future release. Remove from your data sink configuration. Configure auto retry rules to trigger an external retry. | integer                           |          | 150     |
| url          | The URL to send the DeltaFile to                                                                                                                    | string                            | ✔        |         |

