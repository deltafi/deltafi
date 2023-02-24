# DeltaFile Annotations

A DeltaFile includes a map of queryable metadata called indexed metadata. The annotation endpoint allows additional indexed metadata to be added to an existing DeltaFile. For example, an upstream system can add an annotation to a DeltaFile to acknowledge that it has successfully received the data.

## API

The `DeltaFileMetadataWrite` permission is required to access these endpoints.

The endpoints are:
  - `/deltafile/annotate/{did}` - adds new key value pairs to the indexed metadata, any keys that already exist in the indexed metadata will be ignored
  - `/deltafile/annotate/{did}/allowOverwrites` - adds new key value pairs to the indexed metadata and overwrites the values for any keys that already exist

One or more key value pairs can be sent per request by adding the pairs as query parameters on the URL (i.e. `/deltafile/annotate/{did}?k1=v1&k2=v2`)