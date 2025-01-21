# HttpEgress

Sends (e.g., egresses) content to an HTTP URL. Provides built-in functionality to retry when an error is encountered,
select the HTTP method, and set the HTTP header.

## Parameters

| Name         | Description                                                 | Allowed Values                                      | Required | Default |
|--------------|-------------------------------------------------------------|-----------------------------------------------------|:--------:|---------|
| url          | The URL to send the DeltaFile to                            | String                                              |   Yes    |         |
| retryCount   | Number of times to retry a failing HTTP request             | Integer                                             |          | 3       |
| retryDelayMs | Number of milliseconds to wait for an HTTP retry            | Integer                                             |          | 150     |
| metadataKey  | Send metadata as JSON in this HTTP header field             | String                                              |   Yes    |         |
| method       | HTTP method to use when sending the data                    | DELETE<br/>PATCH<br/>POST<br/>PUT                   |          | POST    |
| extraHeaders | Map of additional key/value pairs to set in the HTTP header | key&nbsp;-&nbsp;String<br/>value&nbsp;-&nbsp;String |          |         |

## Inputs

### Content

Any. Content's mediaType will be used as the HTTP Request `content-type`.

### Metadata

Metadata will be converted to JSON and added to the HTTP Request header tagged
under the `metadataKey` parameter value.

## Outputs

None.

## Notes

Sends the input content as the body of an HTTP Request to specified URL using the provided HTTP method
with metadata and any additional headers added into the HTTP header.

Using `content-type` in the `extraHeaders` can be used to override the content's mediaType.
