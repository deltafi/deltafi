---
# ABOUTME: Documents REST endpoints for DeltaFile operations.
# ABOUTME: Covers ingress, export, import, annotations, and content retrieval.
---

# DeltaFiles REST API

REST endpoints for ingressing, exporting, importing, annotating, and retrieving content from DeltaFiles.

## Ingress Data

Ingests data into DeltaFi, creating one or more DeltaFiles.

**Endpoint:** `POST /api/v2/deltafile/ingress`

**Permission:** `DeltaFileIngress`

**Request Headers:**

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | MIME type of the incoming data |
| `Filename` | Yes | Name of the file being ingressed |
| `DataSource` | Yes | Name of the data source to receive the data |
| `Metadata` | No | JSON object of metadata key-value pairs |

The `Filename` and `DataSource` values can also be provided via the `Metadata` JSON header or as FlowFile attributes (header takes highest priority).

**Request Body:** Binary data stream

**Response:** `text/plain` — Comma-separated list of created DeltaFile DIDs

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Success — returns DID(s) |
| `400` | Invalid metadata |
| `413` | Insufficient disk space |
| `429` | Rate limited |
| `500` | Internal error |
| `503` | Ingress disabled or system unavailable |

**Example:**

```bash
curl -X POST \
  -H "Content-Type: text/plain" \
  -H "Filename: example.txt" \
  -H "DataSource: my-data-source" \
  -H 'Metadata: {"environment": "production"}' \
  --data-binary @/path/to/example.txt \
  http://deltafi-host/api/v2/deltafile/ingress
```

### Apache NiFi FlowFile Ingress

To ingest NiFi FlowFiles, set the `Content-Type` to one of:
- `application/flowfile`
- `application/flowfile-v1`
- `application/flowfile-v2`
- `application/flowfile-v3`

FlowFile attributes are extracted as metadata. Values in the `Metadata` header take precedence over FlowFile attributes for duplicate keys.

## Export Single DeltaFile

Exports a DeltaFile and its content as a TAR archive.

**Endpoint:** `GET /api/v2/deltafile/export/{did}`

**Permission:** `DeltaFileExport`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `did` | UUID | DeltaFile identifier |

**Response:** TAR file stream with `Content-Disposition` header

**Example:**

```bash
curl -o deltafile.tar http://deltafi-host/api/v2/deltafile/export/550e8400-e29b-41d4-a716-446655440000
```

## Export Multiple DeltaFiles

Exports DeltaFiles matching filter criteria as a TAR archive.

**Endpoint:** `POST /api/v2/deltafile/export/deltaFiles`

**Permission:** `DeltaFileExport`

**Request Body:**

```json
{
  "filter": {
    "createdAfter": "2024-01-01T00:00:00Z",
    "createdBefore": "2024-01-31T23:59:59Z",
    "dataSources": ["my-data-source"]
  }
}
```

**Response:** TAR file stream with timestamp-based filename

## Export Errored DeltaFiles

Exports errored DeltaFiles as a TAR archive with optional acknowledgment.

**Endpoint:** `POST /api/v2/deltafile/export/errors`

**Permission:** `DeltaFileExport`

**Request Body:**

```json
{
  "maxCount": 100,
  "acknowledge": true
}
```

**Response:** TAR file stream with timestamp-based filename

## Import DeltaFiles

Imports DeltaFiles from a TAR archive (previously created by export).

**Endpoint:** `POST /api/v2/deltafile/import`

**Permission:** `DeltaFileImport`

**Request Body:** Binary TAR stream

**Response:**

```json
{
  "importedDids": ["550e8400-e29b-41d4-a716-446655440000"],
  "errors": []
}
```

## Annotate DeltaFile

Adds key-value annotations to a DeltaFile. Existing annotation keys cannot be overwritten.

**Endpoint:** `POST /api/v2/deltafile/annotate/{did}`

**Permission:** `DeltaFileMetadataWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `did` | UUID | DeltaFile identifier |

**Query Parameters:** Key-value pairs to add as annotations

**Response:** `"Success"` or an error message

**Example:**

```bash
curl -X POST "http://deltafi-host/api/v2/deltafile/annotate/550e8400-e29b-41d4-a716-446655440000?status=reviewed&reviewer=alice"
```

## Annotate DeltaFile (Allow Overwrites)

Same as above, but allows overwriting existing annotation keys.

**Endpoint:** `POST /api/v2/deltafile/annotate/{did}/allowOverwrites`

**Permission:** `DeltaFileMetadataWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `did` | UUID | DeltaFile identifier |

**Query Parameters:** Key-value pairs to add or overwrite as annotations

**Response:** `"Success"` or an error message

## Get Content (GET)

Retrieves the binary content of a DeltaFile by content reference.

**Endpoint:** `GET /api/v2/content`

**Permission:** `DeltaFileContentView`

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `content` | String | Base64-encoded JSON `ContentRequest` |

**Response:** Binary file stream with appropriate content headers (`Content-Type`, `Content-Length`, `Content-Disposition`)

**Error Responses:**

| Status | Cause |
|--------|-------|
| `400` | Invalid content request |
| `404` | Content not found |
| `500` | Storage error |

## Get Content (POST)

Same as above, but accepts the content request as a JSON body.

**Endpoint:** `POST /api/v2/content`

**Permission:** `DeltaFileContentView`

**Request Body:**

```json
{
  "did": "550e8400-e29b-41d4-a716-446655440000",
  "segment": {
    "uuid": "660e8400-e29b-41d4-a716-446655440000",
    "offset": 0,
    "size": 1024,
    "did": "550e8400-e29b-41d4-a716-446655440000"
  },
  "name": "example.txt",
  "mediaType": "text/plain"
}
```

**Response:** Binary file stream with appropriate content headers
