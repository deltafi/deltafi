# Ingressing Data into DeltaFi

DeltaFi can ingest data in any format, and provides special processing for Apache NiFi FlowFiles.

## Submitting Data
Data is submitted to DeltaFi's Ingress component, which turns that data and its associated metadata into a DeltaFile.

To submit data to DeltaFi, you must send an HTTP POST request to the DeltaFi ingress endpoint at `http://<deltafi_url>/deltafile/ingress`. The data should be included in the body of the request.

In addition to the data, also provide the following headers:

- Filename: (Required) The name of the file being ingressed.
- Flow: (Optional) The name of the flow in which the data should be ingested. If this is not supplied, DeltaFi will attempt to determine the flow based on whether the metadata meets any configured advanced routing rules
- Metadata: (Optional) A JSON object containing metadata to attach to the DeltaFile. Must be a valid JSON object.
- Content-Type: Defines the type of the incoming data

Here's an example of how to ingress a file using curl:

```bash
curl -X POST \
  -H "Filename: example.txt" \
  -H "Flow: default" \
  -H "Metadata: {\"key\": \"value\"}" \
  -H "Content-Type: text/plain" \
  --data-binary @/path/to/example.txt http://<deltafi_url>/deltafile/ingress
```

## Apache NiFi FlowFile v1 Ingress

To ingest NiFi FlowFiles, follow the process above with the following exceptions:

- Filename: This is now optional if the filename attribute is stored within the FlowFile metadata.
- Flow: May also be specified within the FlowFile metadata.
- Metadata: Metadata from the FlowFile attributes are appended to any metadata included in the Metadata header.
- Content-Type: DeltaFi supports two formats for flowfiles: application/flowfile and application/flowfile-v1.  

The body of the POST request should contain the binary data of the NiFi FlowFile.

## Manual Upload

In addition to the REST APIs, DeltaFi provides an Upload page on the GUI for uploading files with metadata to a flow. This feature is particularly useful for testing flows or manually inserting a file.

## Future Supported Ingress Methods
DeltaFi plans to support additional methods for ingressing data in the future,
including pull-based methods, timers, and hooks for you to write your own custom Ingress Actions.
Stay tuned for updates!