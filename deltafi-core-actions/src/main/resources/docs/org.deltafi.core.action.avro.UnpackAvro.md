# UnpackAvro
Unpacks the first selected embedded-schema Avro object container file into schema, container metadata, and decoded record content.

## Parameters
| Name                  | Description                                                                  | Allowed Values | Required | Default         |
|-----------------------|------------------------------------------------------------------------------|----------------|:--------:|:---------------:|
| containerMetadataFilenameSuffix | Suffix used when includeContainerMetadataFile is true              | string         |          | .container-metadata.json |
| contentIndexes        | List of content indexes to include or exclude                                | integer (list) |          |                 |
| contentSuffix         | Suffix used when recordOutputMode is NDJSON                                  | string         |          | .content.ndjson |
| contentTags           | List of content tags to include or exclude, matching any                     | string (list)  |          |                 |
| excludeContentIndexes | Exclude specified content indexes                                            | boolean        |          | false           |
| excludeContentTags    | Exclude specified content tags                                               | boolean        |          | false           |
| excludeFilePatterns   | Exclude specified file patterns                                              | boolean        |          | false           |
| excludeMediaTypes     | Exclude specified media types                                                | boolean        |          | false           |
| filePatterns          | List of file patterns to include or exclude, supporting wildcards (*)        | string (list)  |          |                 |
| includeContainerMetadataFile | Whether to write container metadata as a JSON content                 | boolean        |          | true            |
| includeSchemaFile     | Whether to write the Avro schema as a JSON content                           | boolean        |          | false           |
| individualRecordSeparator | Separator inserted before the numbered file index when recordOutputMode is INDIVIDUAL_JSON | string | | . |
| individualRecordSuffix | Suffix used for each numbered record file when recordOutputMode is INDIVIDUAL_JSON | string | | .json |
| jsonArraySuffix       | Suffix used when recordOutputMode is JSON_ARRAY                              | string         |          | .content.json   |
| mediaTypes            | List of media types to consider, supporting wildcards (*)                    | string (list)  |          |                 |
| recordOutputMode      | How decoded Avro records should be written                                   | NDJSON<br/>JSON_ARRAY<br/>INDIVIDUAL_JSON | | NDJSON |
| retainExistingContent | Retain the existing content                                                  | boolean        |          | false           |
| schemaFilenameSuffix  | Suffix used when includeSchemaFile is true                                   | string         |          | .schema.json    |

## Input
### Content
Input content to act on may be selected (or inversely selected using the exclude parameters) with
contentIndexes, mediaTypes, and/or filePatterns. Selected content must be an Avro object container file with an
embedded schema.

If multiple contents are selected, only the first selected content is unpacked. To unpack multiple Avro files
independently, split the DeltaFile before this action.

This action does not support raw binary Avro that relies on an external schema, or Confluent-style wire formats
that carry a schema registry identifier instead of a full embedded Avro container header.

## Output
### Content
The first selected Avro content is replaced by derived contents unless retainExistingContent is true.

Additional selected or unselected content is passed through unchanged.

By default, decoded record content and container metadata are written.

When enabled, the schema is written to `<base>.schema.json`.

When enabled, container metadata is written to `<base>.container-metadata.json` as a flat JSON map of custom
Avro header metadata keys to Base64 values.

Decoded Avro records can be written either as:

- a single newline-delimited JSON content when recordOutputMode is NDJSON
- a single JSON array content when recordOutputMode is JSON_ARRAY
- multiple numbered JSON contents when recordOutputMode is INDIVIDUAL_JSON

The default numbered file pattern is `<base>.1.json`, `<base>.2.json`, etc.

The DeltaFile metadata is also updated with `avroRecordCount`, and with `avroCodec` when present in the Avro
container header.

New content names are built from the input content name with a trailing '.avro' removed when present, then
the configured schema/container metadata suffixes, contentSuffix, or numbered JSON naming is applied.

## Errors
* On selected content that is not a readable embedded-schema Avro object container file
