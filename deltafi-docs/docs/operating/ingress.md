# Ingressing Data into DeltaFi

DeltaFi can ingest data in any format, and provides special processing for Apache NiFi FlowFiles.

## Submitting Data
Data is submitted to DeltaFi's Ingress component, which turns that data and its associated metadata into a DeltaFile.

To submit data to DeltaFi, you must send an HTTP POST request to the DeltaFi ingress endpoint at
`http://<deltafi_url>/api/v2/deltafile/ingress`. The data should be included in the body of the request.

In addition to the data, provide the following information:

- Content-Type: (Required) Defines the type of the incoming data, must be provided in the `Content-Type` header

- Filename: (Required) The name of the file being ingressed, can be provided via
    -  The `Filename` header (highest priority)
    - "filename" key in the Metadata header
    - FlowFile attribute named "filename" (lowest priority)

- DataSource: (Required) The name of the data source in which the data should be ingested, can be provided via
    - The `DataSource` header (highest priority)
    - "dataSource" key in the Metadata header
    - FlowFile attribute named "dataSource" (lowest priority)

- Metadata: (Optional) A JSON object containing metadata to attach to the DeltaFile
    - Must be a valid JSON object
    - For FlowFiles: Values in this header will merge with FlowFile attributes, with Metadata header values taking precedence over FlowFile attributes for any duplicate keys

Here's an example of how to ingress a file using curl:

```bash
curl -X POST \
  -H "Content-Type: text/plain" \
  -H "Filename: example.txt" \
  -H "DataSource: default" \
  -H "Metadata: {\"key\": \"value\"}" \
  --data-binary @/path/to/example.txt http://<deltafi_url>/api/v2/deltafile/ingress
```

## Apache NiFi FlowFile Ingress

Supply one of the following values in the Content-Type header to ingest NiFi FlowFiles:
- application/flowfile
- application/flowfile-v1
- application/flowfile-v2
- application/flowfile-v3

The body of the POST request should contain the binary data of the NiFi FlowFile.

## Manual Upload

In addition to the REST APIs, DeltaFi provides an Upload page on the GUI for uploading files with metadata to a flow.
This feature is particularly useful for testing flows or manually inserting a file.

## Future Supported Ingress Methods
DeltaFi plans to support additional methods for ingressing data in the future, including pull-based methods, timers, and
hooks for you to write your own custom Ingress Actions. Stay tuned for updates!
