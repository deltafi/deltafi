# DeltaFile Annotations

A DeltaFile includes a map of queryable metadata called annotations. The annotation endpoint allows additional
annotations to be added to an existing DeltaFile. For example, an upstream system can add an annotation to a DeltaFile
to acknowledge that it has successfully received the data.

## API

The `DeltaFileMetadataWrite` permission is required to access these endpoints.

The endpoints are:
  - `/api/v2/deltafile/annotate/{did}` - adds new key value pairs to the annotations, any keys that already exist in the annotations will be ignored
  - `/api/v2/deltafile/annotate/{did}/allowOverwrites` - adds new key value pairs to the annotations and overwrites the values for any keys that already exist

One or more key value pairs can be sent per request by adding the pairs as query parameters on the URL (i.e. `/api/v2/deltafile/annotate/{did}?k1=v1&k2=v2`)
