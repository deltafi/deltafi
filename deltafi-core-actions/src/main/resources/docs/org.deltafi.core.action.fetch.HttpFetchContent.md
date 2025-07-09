# HttpFetchContent
Fetches binary content from a given URL and stores it as content.

## Parameters
| Name                       | Description                                                                                                                                                                  | Allowed Values | Required | Default |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|:--------:|:-------:|
| connectTimeout             | The timeout (in milliseconds) for establishing an HTTP connection. Default is 5000ms.                                                                                        | integer        |          | 5000    |
| contentName                | The name to assign to the fetched content. If not specified, the filename will be extracted from the Content-Disposition header or default to 'fetched-file'.                | string         |          |         |
| filenameMetadataKey        | The metadata key used to store the detected filename, the entry is only added when the filename can be determined from the response headers                                  | string         |          |         |
| headersToMetadata          | The list of response headers to store as individual metadata entries. Multi-valued headers are joined together (comma seperated). Only non-empty header values are preserved | string (list)  |          |         |
| httpMethod                 | The HTTP method to use when making the request. Default is GET.                                                                                                              | string         |          | GET     |
| mediaType                  | The media type of the fetched content. If not specified, it will be inferred from the HTTP response headers.                                                                 | string         |          |         |
| readTimeout                | The timeout (in milliseconds) for reading data from the HTTP connection. Default is 10000ms.                                                                                 | integer        |          | 10000   |
| replaceExistingContent     | Whether to replace the existing content in the DeltaFile. If false, the new content is added while retaining the existing content. Default is false.                         | boolean        |          | false   |
| requestBody                | Request body for POST and PUT requests. Ignored for GET, DELETE, and HEAD.                                                                                                   | string         |          |         |
| requestHeaders             | HTTP headers to set in the request.                                                                                                                                          | string (map)   |          |         |
| responseCodeAnnotationName | The annotation name where the HTTP response code should be stored.                                                                                                           | string         |          |         |
| responseHeadersMetadataKey | Response headers will be joined together and stored in metadata using this key.                                                                                              | string         |          |         |
| tags                       | A list of tags to assign to the fetched content.                                                                                                                             | string (list)  |          |         |
| url                        | The URL to fetch content from.                                                                                                                                               | string         | ✔        |         |

## Output
### Content
If replaceExistingContent is false, existing content will be included before adding the
fetched content.

If tags are provided, they will be assigned to the fetched content.

### Metadata
If responseHeadersMetadataKey is set, all response headers will be set in the named
metadata key.

### Annotations
If responseCodeAnnotationName is set, the response code will be set in the named
annotation.

## Errors
* On an IO error communicating with the given URL
* On a response code not equal to 200
    * If responseCodeAnnotationName is set, the response code will be set in the named
annotation.

