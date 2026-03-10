---
# ABOUTME: Documents the Artifact Registry REST API.
# ABOUTME: Covers publish, list, versions, download, rollback, aliases, delete, and purge endpoints.
---

# Artifact Registry REST API

Endpoints for managing versioned binary artifacts stored in the DeltaFi artifact registry.

Owner-scoped endpoints use the path `/api/v2/artifacts/{owner}`, where `{owner}` is a plugin name
(e.g. `my-plugin`) or `shared` for organization-wide artifacts.

## Publish Artifact

Uploads a new artifact version via multipart form.

**Endpoint:** `POST /api/v2/artifacts/{owner}/{artifactName}`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier. Must start with a letter or digit and contain only letters, digits, hyphens (`-`), underscores (`_`), and dots (`.`). Maximum 512 characters. |

**Request:** `multipart/form-data`

| Field | Required | Description |
|-------|----------|-------------|
| `file` | Yes | The artifact binary |
| `version` | No | Version string; server generates a timestamp version if omitted |
| `description` | No | Human-readable description |
| `makeActive` | No | `true`/`false` — update the `active` alias (default: `true`) |
| `alias` | No | Additional alias to create pointing to the new version; ignored if equal to `active` |
| Any other field | No | Stored as a label on the manifest |

**Response:** `201 Created`

```json
{
  "pluginName": "my-plugin",
  "artifactName": "routing-rules",
  "version": "20240315.120000.000",
  "isActive": true,
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `201` | Version published |
| `400` | Invalid request |
| `409` | Version already exists |
| `500` | Storage error |

**Example:**

```bash
curl -X POST http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules \
  -F "file=@routing-rules.json" \
  -F "version=2.0.0" \
  -F "description=Q2 routing update" \
  -F "makeActive=true" \
  -F "env=prod"
```

## List All Artifacts

Returns all artifacts across every owner. Shared artifacts appear first, followed by plugin owners in alphabetical order. Owners with no artifacts are omitted.

**Endpoint:** `GET /api/v2/artifacts`

**Permission:** `ArtifactRegistryRead`

**Response:** `200 OK`

```json
[
  {
    "pluginName": "shared",
    "artifacts": [
      {
        "artifactName": "geo-regions",
        "activeVersion": "20240315.120000.000",
        "totalVersions": 3
      }
    ]
  },
  {
    "pluginName": "org.my-plugin",
    "artifacts": [
      {
        "artifactName": "routing-rules",
        "activeVersion": "2.0.0",
        "totalVersions": 5
      }
    ]
  }
]
```

**Example:**

```bash
curl http://deltafi-host/api/v2/artifacts
```

## List Artifacts

Returns all artifacts for an owner with their active version summary.

**Endpoint:** `GET /api/v2/artifacts/{owner}`

**Permission:** `ArtifactRegistryRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |

**Response:** `200 OK`

```json
{
  "pluginName": "my-plugin",
  "artifacts": [
    {
      "artifactName": "routing-rules",
      "activeVersion": "20240315.120000.000",
      "activeManifest": {
        "artifactName": "routing-rules",
        "version": "20240315.120000.000",
        "description": "Q2 routing update",
        "contentType": "application/json",
        "createdAt": "2024-03-15T12:00:00Z",
        "modifiedAt": "2024-03-15T12:00:00Z",
        "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "size": 4096
      },
      "totalVersions": 3,
      "aliases": {
        "active": "20240315.120000.000",
        "stable": "20240101.090000.000"
      }
    }
  ]
}
```

**Example:**

```bash
curl http://deltafi-host/api/v2/artifacts/my-plugin
```

## List Artifact Versions

Returns all versions of an artifact, newest-first, with the alias map.

**Endpoint:** `GET /api/v2/artifacts/{owner}/{artifactName}/versions`

**Permission:** `ArtifactRegistryRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Response:** `200 OK`

```json
{
  "pluginName": "my-plugin",
  "artifactName": "routing-rules",
  "activeVersion": "20240315.120000.000",
  "versions": [
    {
      "artifactName": "routing-rules",
      "version": "20240315.120000.000",
      "description": "Q2 routing update",
      "contentType": "application/json",
      "createdAt": "2024-03-15T12:00:00Z",
      "modifiedAt": "2024-03-15T12:00:00Z",
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "size": 4096
    },
    {
      "artifactName": "routing-rules",
      "version": "20240101.090000.000",
      "contentType": "application/json",
      "createdAt": "2024-01-01T09:00:00Z",
      "modifiedAt": "2024-01-01T09:00:00Z",
      "sha256": "abc123...",
      "size": 3800
    }
  ],
  "aliases": {
    "20240315.120000.000": ["active"],
    "20240101.090000.000": ["stable"]
  }
}
```

The `aliases` field maps each version string to the list of alias names pointing at it. Omitted when
no aliases exist.

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Success |
| `404` | Artifact not found |

**Example:**

```bash
curl http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/versions
```

## Download Artifact

Downloads an artifact by version string or alias name.

**Endpoint:** `GET /api/v2/artifacts/{owner}/{artifactName}/versions/{version}`

**Permission:** `ArtifactRegistryRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |
| `version` | String | Version string or alias name (e.g. `active`, `stable`) |

**Response:** Binary stream with the artifact content

**Response Headers:**

| Header | Description |
|--------|-------------|
| `Content-Type` | MIME type recorded at publish time |
| `Content-Length` | Size in bytes |
| `Content-Disposition` | `attachment; filename="{artifactName}"` |
| `X-Artifact-Version` | Resolved version string |
| `X-Artifact-Sha256` | SHA-256 digest |
| `X-Artifact-Description` | Description, if present |
| `X-Artifact-Labels` | JSON object of labels, if present |
| `X-Artifact-Source-Prefix` | `shared` or `plugins/{owner}` |

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Success |
| `404` | Version or alias not found |

**Example:**

```bash
# Download the active version
curl -O http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/versions/active

# Download a specific version
curl -o rules.json http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/versions/2.0.0
```

## Rollback

Moves the `active` alias to a prior version.

**Endpoint:** `POST /api/v2/artifacts/{owner}/{artifactName}/rollback`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Request Body:**

```json
{
  "version": "20240101.090000.000"
}
```

**Response:** `200 OK`

```json
{
  "pluginName": "my-plugin",
  "artifactName": "routing-rules",
  "rolledBackTo": "20240101.090000.000"
}
```

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Rollback applied |
| `404` | Target version not found |

**Example:**

```bash
curl -X POST http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/rollback \
  -H "Content-Type: application/json" \
  -d '{"version": "20240101.090000.000"}'
```

## Set Alias

Points any alias at a specific version.

**Endpoint:** `PUT /api/v2/artifacts/{owner}/{artifactName}/aliases/{alias}`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |
| `alias` | String | Alias name to create or update |

**Request Body:**

```json
{
  "version": "20240101.090000.000"
}
```

**Response:** `200 OK`

```json
{
  "pluginName": "my-plugin",
  "artifactName": "routing-rules",
  "alias": "stable",
  "version": "20240101.090000.000"
}
```

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Alias set |
| `404` | Target version not found |

**Example:**

```bash
curl -X PUT http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/aliases/stable \
  -H "Content-Type: application/json" \
  -d '{"version": "20240101.090000.000"}'
```

## Remove Alias

Removes an alias. The `active` alias cannot be removed.

**Endpoint:** `DELETE /api/v2/artifacts/{owner}/{artifactName}/aliases/{alias}`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |
| `alias` | String | Alias name to remove |

**Response:** `204 No Content`

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `204` | Alias removed |
| `404` | Alias not found |
| `409` | Attempted to remove the `active` alias |

**Example:**

```bash
curl -X DELETE http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/aliases/canary
```

## Delete Version

Deletes a specific version (blob + manifest). The current `active` version cannot be deleted.

**Endpoint:** `DELETE /api/v2/artifacts/{owner}/{artifactName}/versions/{version}`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |
| `version` | String | Exact version string to delete |

**Response:** `204 No Content`

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `204` | Version deleted |
| `404` | Version not found |
| `409` | Version is currently `active`; roll back first |

**Example:**

```bash
curl -X DELETE http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/versions/20240101.090000.000
```

## Delete Artifact

Deletes an entire artifact — all versions, aliases, and configuration.

**Endpoint:** `DELETE /api/v2/artifacts/{owner}/{artifactName}`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Response:** `204 No Content`

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `204` | Artifact deleted |
| `404` | Artifact not found |

**Example:**

```bash
curl -X DELETE http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules
```

## Get Purge Config

Returns the retention policy for an artifact, or the system default if none has been set.

**Endpoint:** `GET /api/v2/artifacts/{owner}/{artifactName}/purge-config`

**Permission:** `ArtifactRegistryRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Response:** `200 OK`

```json
{
  "keepLatest": 5,
  "maxAgeMinutes": 43200
}
```

**Example:**

```bash
curl http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/purge-config
```

## Set Purge Config

Sets the retention policy for an artifact.

**Endpoint:** `PUT /api/v2/artifacts/{owner}/{artifactName}/purge-config`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Request Body:**

```json
{
  "keepLatest": 3,
  "maxAgeMinutes": 10080
}
```

A version is eligible for purge only when it satisfies **both** criteria: outside the most-recent
`keepLatest` versions **and** older than `maxAgeMinutes`. The current `active` version is always
protected. Set `maxAgeMinutes` to `0` to disable age-based purging.

**Response:** `204 No Content`

**Example:**

```bash
curl -X PUT http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/purge-config \
  -H "Content-Type: application/json" \
  -d '{"keepLatest": 3, "maxAgeMinutes": 10080}'
```

## Trigger Purge

Applies the retention policy to an artifact immediately, without waiting for the scheduled run.

**Endpoint:** `POST /api/v2/artifacts/{owner}/{artifactName}/purge`

**Permission:** `ArtifactRegistryWrite`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `owner` | String | Plugin name or `shared` |
| `artifactName` | String | Artifact identifier |

**Response:** `200 OK`

```json
{
  "pluginName": "my-plugin",
  "artifactName": "routing-rules",
  "purgedVersions": ["20231201.080000.000", "20231115.140000.000"]
}
```

`purgedVersions` is an empty array when no versions were eligible.

**Example:**

```bash
curl -X POST http://deltafi-host/api/v2/artifacts/my-plugin/routing-rules/purge
```
