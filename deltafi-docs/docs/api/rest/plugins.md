---
# ABOUTME: Documents REST endpoints for plugin registration and scaffold generation.
# ABOUTME: Covers the plugin self-registration endpoint and the plugin project generator.
---

# Plugins REST API

Endpoints for plugin registration and project scaffold generation.

## Register Plugin

Called by plugins during startup to register their actions and flow plans with the core.

**Endpoint:** `POST /plugins`

**Permission:** None (open endpoint)

**Request Body:** `PluginRegistration` JSON containing the plugin's coordinates, actions, flow plans, and variables

**Response:** `text/plain` — `null` on success, or an error message

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | Plugin registered successfully |
| `400` | Invalid registration data |

::: info
This endpoint is called automatically by plugins during startup. Manual invocation is rarely needed.
:::

## Generate Plugin

Generates a plugin project scaffold as a downloadable ZIP file.

**Endpoint:** `POST /api/v2/generate/plugin`

**Permission:** `PluginsView`

**Request Body:**

```json
{
  "groupId": "com.example",
  "artifactId": "my-plugin",
  "description": "My DeltaFi Plugin",
  "language": "JAVA"
}
```

Currently only `JAVA` is supported as a language.

**Response:** `application/octet-stream` — ZIP file containing the generated project

**Status Codes:**

| Status | Meaning |
|--------|---------|
| `200` | ZIP file generated |
| `422` | Unsupported language |
| `500` | Generation error |

**Example:**

```bash
curl -X POST http://deltafi-host/api/v2/generate/plugin \
  -H "Content-Type: application/json" \
  -d '{"groupId": "com.example", "artifactId": "my-plugin", "description": "My Plugin", "language": "JAVA"}' \
  -o my-plugin.zip
```
