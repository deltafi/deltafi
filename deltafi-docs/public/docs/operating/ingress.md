# Ingressing Data into DeltaFi

DeltaFi can ingest data in any format, and provides special processing for Apache NiFi FlowFiles.

## Submitting Data
Data is submitted to DeltaFi's Ingress component, which turns that data and its associated metadata into a DeltaFile.

To submit data to DeltaFi, you must send an HTTP POST request to the DeltaFi ingress endpoint at
`http://<deltafi_url>/deltafile/ingress`. The data should be included in the body of the request.

In addition to the data, also provide the following headers:
- Content-Type: (Required) Defines the type of the incoming data
- Filename: (Optional) The name of the file being ingressed. Must be supplied in this header, in the "filename" key of
the Metadata header, or as a FlowFile attribute named "filename".
- Flow: (Optional) The name of the flow in which the data should be ingested. If this is not supplied in this header,
in the "flow" key of the Metadata header, or as a FlowFile attribute named "flow", DeltaFi will attempt to determine the
flow based on whether the metadata meets any configured advanced routing rules.
- Metadata: (Optional) A JSON object containing metadata to attach to the DeltaFile. Must be a valid JSON object. This
metadata will merge with any FlowFile attributes, overriding any existing keys.

Here's an example of how to ingress a file using curl:

```bash
curl -X POST \
  -H "Content-Type: text/plain" \
  -H "Filename: example.txt" \
  -H "Flow: default" \
  -H "Metadata: {\"key\": \"value\"}" \
  --data-binary @/path/to/example.txt http://<deltafi_url>/deltafile/ingress
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
DeltaFi plans to support additional methods for ingressing data in the future,
including pull-based methods, timers, and hooks for you to write your own custom Ingress Actions.
Stay tuned for updates!