## Inputs
### Content
All existing content is either dropped or passed through, depending on the action configuration.
In either case it is not read by this action.

## Outputs
### Content
Input content is passed through unchanged or dropped, depending on the action configuration.
Fetched content is added to the output.

### Metadata
Metadata is not modified.

### Annotations
If responseCodeAnnotationName is set, an annotation is added containing the HTTP response status code.

### Tags
The fetched content can have tags assigned via the tags parameter.

## Errors
This action fails with an ErrorResult in the following scenarios:
- HTTP request failure (e.g., unreachable server, 404, 500).
- Timeout errors (connect or read timeout).
- Failure to store fetched content (e.g., object storage failure).
- Invalid URL format in the url parameter.

If responseCodeAnnotationName is set, even on failure, the response code is still annotated for debugging.
