# DeltaFi Plugin Artifact Registry & Plugin Configuration — Design Document

## 1. Overview

This document describes two distinct but related features:

**Feature: DeltaFi Artifact Registry** — A general-purpose, versioned binary artifact store for DeltaFi plugins. Plugins often need access to artifacts — rulesets, lookup tables, compiled models, or reference data — that evolve independently of the plugin code itself. Today there is no first-class mechanism for publishing, versioning, or distributing these artifacts within DeltaFi. The artifact registry provides per-plugin and shared storage backed by S3Proxy, a REST API, action-kit integration, and a push-based notification system.

**Feature: DeltaFi Plugin Initial Configuration** — A mechanism for plugins to declare externalized configuration that operators can modify without rebuilding or redeploying the plugin. This is a separate concern from artifact storage — configuration files are mutable, singleton objects (not versioned artifact streams) with different lifecycle semantics. However, this feature is **implemented on top of the artifact registry's storage infrastructure**, using the same S3Proxy bucket and key namespace as its backend. This avoids introducing a separate storage system for configuration while keeping the two features conceptually distinct.

The features are related but serve different audiences and use cases. The artifact registry is for versioned, immutable binaries managed by CI pipelines or operators. Plugin configuration is for mutable, operator-facing settings that plugins seed with sensible defaults and operators customize in place.

### Implementation Phases

**Phase 1 — Artifact Registry: Storage, APIs, and Action-Kit Integration**

Delivers the artifact registry as a standalone feature: a dedicated S3Proxy instance with a single bucket and path-based namespace for versioned artifact storage (per-plugin and shared prefixes), a REST API for publishing, listing, downloading, rollback, and purge, action-kit annotations and interfaces for transparent artifact access from actions, and a push-based notification system for version changes.

**Phase 2 — Plugin Initial Configuration**

Delivers plugin configuration as a separate feature, built on top of the artifact registry's storage layer. Adds the `@PluginPropertySource` annotation for loading configuration from object storage at startup, dedicated config-object APIs, and hot-reload support. Plugins that use this feature are **required** to provide a default configuration artifact bundled in the classpath, ensuring the plugin is always functional on first deploy.

**Phase 3 — UI**

Adds user interface support for both features: an artifact management panel on each plugin's page, a configuration editor on the plugin page for viewing and modifying plugin configuration files, and a top-level Artifact Service page for managing all artifacts across the system (both shared and per-plugin).

### Design Constraints

S3Proxy does not support S3 object versioning or object tagging. The storage scheme is designed around these limitations, using only features S3Proxy does support: standard object CRUD, key listing, and `x-amz-meta-*` user metadata headers. All versioning, aliasing, and metadata are implemented at the application layer using the object key namespace.

---

# Phase 1 — Artifact Registry: Storage, APIs, and Action-Kit Integration

## 2. Storage Scheme

### 2.1 Storage Model

All artifacts are stored in a single S3 bucket named `deltafi-artifacts`. Isolation between plugins and between shared vs. plugin-specific artifacts is achieved through key path prefixes rather than separate buckets.

The bucket contains two top-level prefixes:

**`plugins/{pluginName}/`** — each plugin's artifacts live under a prefix named by its plugin coordinates (e.g., `plugins/org.deltafi.core-actions/`). The registry service enforces that plugins can only write to their own prefix. A plugin's entire artifact tree can be listed, mirrored, or deleted by operating on its prefix.

**`shared/`** — artifacts that are common across plugins — shared lookup tables, cross-cutting configuration, organization-wide rulesets, or reference data that multiple plugins consume but no single plugin owns. All plugins can read from the shared prefix; writes require explicit authorization (admin or CI pipeline).

When resolving an artifact, the lookup order is:

1. **Plugin prefix first** — if an artifact with the requested name exists under `plugins/{pluginName}/`, it is used.
2. **Shared prefix as fallback** — if the artifact is not found under the plugin prefix, the `shared/` prefix is checked.

This allows a plugin to override a shared artifact with its own version when needed (e.g., a plugin-specific lookup table that overrides the organization-wide default).

Using a single bucket simplifies deployment (one bucket to create, one set of credentials, one backup target) and avoids S3Proxy's bucket-count overhead, while path prefixes provide the same logical isolation.

### 2.2 Key Layout

Within the single bucket, artifacts are organized under a hierarchical key namespace:

```
bucket: deltafi-artifacts
│
├── shared/                                           # shared across all plugins
│   │
│   ├── common-lookup-table/
│   │   ├── 1.0.0/
│   │   │   ├── blob
│   │   │   └── manifest.json
│   │   ├── _aliases/
│   │   │   └── active              # points to the current active version
│   │   └── _config/
│   │       └── purge.json
│   │
│   └── org-wide-rules.json/
│       ├── 1.0.0/
│       │   ├── blob
│       │   └── manifest.json
│       ├── _aliases/
│       │   └── active
│       └── _config/
│           └── purge.json
│
├── plugins/                                          # plugin-specific
│   │
│   ├── org.deltafi.core-actions/
│   │   │
│   │   ├── my-config.zip/
│   │   │   ├── 2.0.0/
│   │   │   │   ├── blob
│   │   │   │   └── manifest.json
│   │   │   ├── 2.1.0/
│   │   │   │   ├── blob
│   │   │   │   └── manifest.json
│   │   │   ├── _aliases/
│   │   │   │   └── active
│   │   │   └── _config/
│   │   │       └── purge.json
│   │   │
│   │   └── lookup-table/
│   │       ├── 1.0.0/
│   │       │   ├── blob
│   │       │   └── manifest.json
│   │       ├── _aliases/
│   │       │   └── active
│   │       └── _config/
│   │           └── purge.json
│   │
│   └── org.deltafi.hello-world/
│       └── ...
```

Full key paths:

| Scope | Key pattern |
|-------|-------------|
| Plugin artifact blob | `plugins/{pluginName}/{artifactName}/{version}/blob` |
| Plugin artifact manifest | `plugins/{pluginName}/{artifactName}/{version}/manifest.json` |
| Plugin artifact alias | `plugins/{pluginName}/{artifactName}/_aliases/{alias}` |
| Plugin purge config | `plugins/{pluginName}/{artifactName}/_config/purge.json` |
| Shared artifact blob | `shared/{artifactName}/{version}/blob` |
| Shared artifact manifest | `shared/{artifactName}/{version}/manifest.json` |
| Shared artifact alias | `shared/{artifactName}/_aliases/{alias}` |
| Shared purge config | `shared/{artifactName}/_config/purge.json` |

Key design properties:

- **Blobs and manifests are immutable.** They are written once to a unique key (e.g., `plugins/{pluginName}/{artifact}/{version}/blob`) and never updated. Two concurrent publishes of different versions write to different keys and cannot collide.
- **Alias files are tiny, last-write-wins.** Updating the `active` pointer is a single PUT to `_aliases/active` under the artifact prefix. If two processes race to set it, the last writer wins, which is the correct semantic — the most recent promotion takes effect.
- **No shared mutable index.** There is no central `index.json` that requires read-modify-write. Version discovery is done by listing keys under a prefix and reading individual manifests. This eliminates the concurrency hazard entirely.
- **Single bucket, path isolation.** All artifacts live in one bucket. Plugin isolation is enforced at the application layer by the registry service, which validates that write operations target the correct `plugins/{pluginName}/` prefix.

### 2.3 Manifest Format

Each version's `manifest.json` is written once at publish time and is never modified:

```json
{
  "artifactName": "my-config.zip",
  "version": "2.1.0",
  "description": "Customer routing rules for the eastern region",
  "contentType": "application/zip",
  "createdAt": "2026-02-23T14:30:00Z",
  "modifiedAt": "2026-02-23T14:30:00Z",
  "sha256": "ab3f9c7e...",
  "size": 4194304,
  "labels": {
    "type": "customer-rules",
    "region": "us-east",
    "priority": "high",
    "builtBy": "ci-pipeline-88"
  }
}
```

- `description` — optional human-readable summary of this version (e.g., what changed, what it contains).
- `contentType` — the MIME type of the blob (e.g., `application/zip`, `application/json`, `application/octet-stream`). Captured from the upload and used when serving downloads.
- `createdAt` — timestamp of initial publish (immutable).
- `modifiedAt` — timestamp of the last metadata update. Initially equal to `createdAt`. Updated if labels or description are patched without re-publishing the blob (see Section 3 for the metadata update API).

The `labels` map is freeform — plugins define whatever key-value pairs are meaningful for their artifacts. Labels are queryable through the API (filter in application code after listing).

### 2.4 Alias Files

An alias file is a minimal JSON pointer:

```json
{"version": "2.1.0"}
```

The registry defines one built-in alias, `active`, which is the version that actions receive by default. Additional aliases (e.g., `stable`, `canary`) can be added as needed by writing to `_aliases/{name}`.

### 2.5 Purge Configuration

Each artifact may have an independent retention policy stored at `_config/purge.json`:

```json
{
  "keepLatest": 5,
  "maxAgeMinutes": 43200
}
```

A version is eligible for purge only when both conditions are met: it is beyond the `keepLatest` count (sorted newest-first) AND it is older than `maxAgeMinutes`. The version currently pointed to by the `active` alias is never purged regardless of age. The system default is `keepLatest: 5, maxAgeMinutes: 43200` (30 days).

---

## 3. Core APIs

All APIs are served by the artifact registry REST controller under a single base path:

```
/api/v2/artifacts
```

The first path segment after `/artifacts` is the **owner** — either `shared` for shared artifacts, or a plugin name (e.g., `org.deltafi.core-actions`). All endpoints use the same URL shape regardless of owner.

Read endpoints require `ArtifactRegistryRead` permission. Write endpoints require `ArtifactRegistryWrite` permission.

### 3.1 Publish a Version

```
POST /{owner}/{artifactName}
Content-Type: multipart/form-data
```

Uploads a new artifact version. The binary is streamed through a SHA-256 digest and written to an immutable key. A manifest is generated and stored alongside it.

**Parameters:**

| Name        | Location | Type    | Required | Description                                      |
|-------------|----------|---------|----------|--------------------------------------------------|
| owner       | path     | string  | yes      | Plugin name or `shared`                          |
| artifactName| path     | string  | yes      | Artifact identifier                              |
| file        | body     | binary  | yes      | The artifact binary                              |
| version     | body     | string  | no       | Version string (must be unique; auto-generated from UTC timestamp if omitted) |
| description | body     | string  | no       | Human-readable summary of this version           |
| makeActive  | body     | boolean | no       | Update the `active` alias (default: `true`)      |
| alias       | body     | string  | no       | Additional alias to create (e.g., `canary`); ignored if blank or equal to `active` |
| *           | body     | string  | no       | All other form fields become labels              |

Artifact names, version strings, and alias names must match `^[a-zA-Z0-9][a-zA-Z0-9._-]*$` and not exceed 512 characters.

**Response** `201 Created`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifactName": "my-config.zip",
  "version": "2.1.0",
  "isActive": true,
  "sha256": "ab3f9c7e..."
}
```

**Error** `409 Conflict` — if the version string already exists for this artifact.
**Error** `400 Bad Request` — if artifact name, version, or alias is invalid.

**Behavior notes:**

- If `description` is null or blank, it inherits the description from the current active version (if one exists).
- The `contentType` is captured from the uploaded file's `Content-Type` header. If not provided, it defaults to `application/octet-stream`.
- `createdAt` and `modifiedAt` are both set to the current timestamp at publish time.
- If `makeActive` is `true` (the default), the `active` alias is updated and a `VersionChangedEvent` is emitted (see Section 5).
- The duplicate version check is not atomic with the write, but this is a policy constraint (CI should not publish the same version twice) rather than a data integrity issue — the worst case is an idempotent overwrite.

### 3.2 List All Artifacts (All Owners)

```
GET /
```

Returns artifacts grouped by owner. Shared artifacts appear first, then plugin owners sorted alphabetically. Owners with no artifacts are omitted.

**Response** `200 OK`:

```json
[
  {
    "pluginName": "shared",
    "artifacts": [...]
  },
  {
    "pluginName": "org.deltafi.core-actions",
    "artifacts": [...]
  }
]
```

### 3.3 List Artifacts (Single Owner)

```
GET /{owner}
```

Returns a summary of every artifact for the owner, including the version currently pointed to by `active`, all aliases, and the total version count.

**Response** `200 OK`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifacts": [
    {
      "artifactName": "my-config.zip",
      "activeVersion": "2.1.0",
      "activeManifest": {
        "artifactName": "my-config.zip",
        "version": "2.1.0",
        "description": "Customer routing rules for the eastern region",
        "contentType": "application/zip",
        "createdAt": "2026-02-23T14:30:00Z",
        "modifiedAt": "2026-02-23T14:30:00Z",
        "sha256": "ab3f9c7e...",
        "size": 4194304,
        "labels": {"type": "customer-rules"}
      },
      "totalVersions": 5,
      "aliases": {"active": "2.1.0", "stable": "2.0.0"}
    }
  ]
}
```

### 3.4 List All Versions (Single Artifact)

```
GET /{owner}/{artifactName}/versions
```

Returns every version of an artifact, newest-first, with complete manifest metadata and a reverse alias map.

**Response** `200 OK`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifactName": "my-config.zip",
  "activeVersion": "2.1.0",
  "versions": [
    {
      "artifactName": "my-config.zip",
      "version": "2.1.0",
      "description": "Customer routing rules for the eastern region",
      "contentType": "application/zip",
      "createdAt": "2026-02-23T14:30:00Z",
      "modifiedAt": "2026-02-23T14:30:00Z",
      "sha256": "ab3f9c7e...",
      "size": 4194304,
      "labels": {"type": "customer-rules"}
    },
    {
      "artifactName": "my-config.zip",
      "version": "2.0.0",
      "description": "Initial customer routing rules",
      "contentType": "application/zip",
      "createdAt": "2026-02-10T08:00:00Z",
      "modifiedAt": "2026-02-10T08:00:00Z",
      "sha256": "cd9e1b2a...",
      "size": 3900000,
      "labels": {"type": "customer-rules"}
    }
  ],
  "aliases": {"2.1.0": ["active"], "2.0.0": ["stable"]}
}
```

**Error** `404 Not Found` — if the artifact does not exist.

### 3.5 Download Artifact

```
GET /{owner}/{artifactName}/versions/{version}
```

Returns the raw artifact binary with the stored `contentType` as the response `Content-Type` header. The version string can be a literal version or any alias name (e.g., `active`, `stable`).

Response headers include: `X-Artifact-Version`, `X-Artifact-Sha256`, `X-Artifact-Description`, `X-Artifact-Labels` (JSON), and `X-Artifact-Source-Prefix`.

**Error** `404 Not Found` — if the version or alias does not exist.

### 3.6 Rollback

```
POST /{owner}/{artifactName}/rollback
Content-Type: application/json

{"version": "2.0.0"}
```

Points the `active` alias to the specified version. The version must exist. This operation emits a `VersionChangedEvent` (see Section 5).

**Response** `200 OK`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifactName": "my-config.zip",
  "rolledBackTo": "2.0.0"
}
```

**Error** `404 Not Found` — if the target version does not exist.

### 3.7 Delete a Version

```
DELETE /{owner}/{artifactName}/versions/{version}
```

Deletes the blob and manifest for a specific version. Returns `204 No Content` on success.

**Error** `409 Conflict` — if the version is the current `active` version. The caller must rollback first.
**Error** `404 Not Found` — if the version does not exist.

### 3.8 Delete Entire Artifact

```
DELETE /{owner}/{artifactName}
```

Deletes all versions, aliases, and configuration for the artifact. Returns `204 No Content`.

**Error** `404 Not Found` — if the artifact does not exist.

### 3.9 Set Alias

```
PUT /{owner}/{artifactName}/aliases/{alias}
Content-Type: application/json

{"version": "2.0.0"}
```

Points the named alias to a specific version. The version must exist. A `VersionChangedEvent` is emitted for all alias changes. Alias names are validated with the same rules as artifact names.

**Response** `200 OK`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifactName": "my-config.zip",
  "alias": "stable",
  "version": "2.0.0"
}
```

### 3.10 Delete Alias

```
DELETE /{owner}/{artifactName}/aliases/{alias}
```

Removes an alias. The `active` alias cannot be deleted. Returns `204 No Content`.

**Error** `409 Conflict` — if attempting to delete the `active` alias.
**Error** `404 Not Found` — if the alias does not exist.

### 3.11 Purge Configuration

**Set:**

```
PUT /{owner}/{artifactName}/purge-config
Content-Type: application/json

{"keepLatest": 3, "maxAgeMinutes": 10080}
```

Returns `204 No Content`.

**Get:**

```
GET /{owner}/{artifactName}/purge-config
```

Returns the configured policy, or the system default (`keepLatest: 5`, `maxAgeMinutes: 43200` / 30 days) if none has been set.

### 3.12 Trigger Manual Purge

```
POST /{owner}/{artifactName}/purge
```

Immediately applies the purge policy. Returns the list of deleted versions.

**Response** `200 OK`:

```json
{
  "pluginName": "org.deltafi.core-actions",
  "artifactName": "my-config.zip",
  "purgedVersions": ["1.0.0-beta", "0.9.0"]
}
```

The automatic purge scheduler runs periodically and applies the same logic across all plugins and artifacts. Orphaned blobs (written without a manifest due to a failed publish) are also cleaned up during purge.

---

## 4. Action-Kit Extensions — Artifact Access

### 4.1 Goal

An action author should be able to declare a dependency on a named artifact and receive its current content with zero boilerplate. The action-kit handles fetching, caching, and refreshing transparently.

### 4.2 Annotation-Driven Declaration

Actions declare artifact dependencies using a `@PluginArtifact` annotation on a field:

```java
public class MlTransformAction extends TransformAction<MlTransformParameters> {

    @PluginArtifact(
        artifactName = "my-config.zip",
        defaultResource = "defaults/my-config.zip"
    )
    private ArtifactHandle config;

    @Override
    public TransformResultType transform(ActionContext context,
                                         MlTransformParameters params,
                                         TransformInput input) {
        byte[] configBytes = config.getBytes();
        String configVersion = config.getVersion();
        Map<String, String> labels = config.getLabels();

        // Use the config...
    }
}
```

The `@PluginArtifact` annotation accepts:

| Attribute         | Type             | Default           | Description                                                      |
|-------------------|------------------|-------------------|------------------------------------------------------------------|
| `artifactName`    | String           | (required)        | Name of the artifact in the registry                             |
| `scope`           | `ArtifactScope`  | `PLUGIN_FIRST`    | Where to look: `PLUGIN_ONLY`, `SHARED_ONLY`, or `PLUGIN_FIRST` (plugin prefix with shared fallback) |
| `alias`           | String           | `"active"`        | Which alias to track (e.g., `"active"`, `"stable"`)              |
| `preload`         | boolean          | `true`            | Fetch on startup vs. on first access                             |
| `cacheable`       | boolean          | `true`            | Cache the blob in memory between invocations                     |
| `defaultResource` | String           | `""`              | Classpath resource to publish as the initial version if the artifact is absent. When empty (the default), startup fails if the artifact is missing. |

The `scope` attribute controls artifact resolution:

- **`PLUGIN_FIRST`** (default) — checks the plugin's own prefix first (`plugins/{pluginName}/`). If the artifact is not found, falls back to the `shared/` prefix. This is the most common mode — it allows plugins to consume shared artifacts while retaining the ability to override with a plugin-specific version.
- **`PLUGIN_ONLY`** — only looks under the plugin's prefix. Use when the artifact is definitively plugin-specific and a shared artifact with the same name should be ignored.
- **`SHARED_ONLY`** — only looks under the `shared/` prefix. Use when the action explicitly depends on an organization-wide artifact and should never use a plugin-local override.

Example — consuming a shared lookup table with plugin-first resolution:

```java
@PluginArtifact(
    artifactName = "geo-lookup-table.csv",
    scope = ArtifactScope.PLUGIN_FIRST
)
private ArtifactHandle geoLookup;

// If org.deltafi.core-actions has its own geo-lookup-table.csv, that's used.
// Otherwise, the one from shared/ is used.
```

Example — consuming an organization-wide ruleset that must come from shared:

```java
@PluginArtifact(
    artifactName = "org-wide-rules.json",
    scope = ArtifactScope.SHARED_ONLY
)
private ArtifactHandle orgRules;
```

### 4.3 The `ArtifactHandle` Interface

`ArtifactHandle` is the programmatic interface injected into action fields:

```java
public interface ArtifactHandle {

    /** The artifact identifier (registry name), e.g. "routing-rules.json". */
    String getArtifactName();

    /** The currently-resolved version string (e.g., "2.1.0"). */
    String getVersion();

    /** Human-readable description of this version, or null. */
    String getDescription();

    /** MIME type of the artifact blob (e.g., "application/zip"). */
    String getContentType();

    /** The raw artifact bytes. Cached if cacheable=true. Never null. */
    byte[] getBytes();

    /** Stream the artifact content. Caller must close. */
    InputStream getInputStream();

    /** Decode artifact bytes as a UTF-8 string. */
    String getString();

    /** Decode artifact bytes using the given charset. */
    String getString(Charset charset);

    /** Full manifest metadata for the current version. */
    ArtifactManifest getManifest();

    /** Label metadata, or null if none were set. */
    Map<String, String> getLabels();

    /** The SHA-256 digest of the current artifact. */
    String getSha256();

    /** Whether the artifact has been resolved and is ready for use. */
    boolean isReady();

    /**
     * Whether the current version was seeded from a classpath default
     * (i.e., has the "default" label).
     */
    boolean isDefault();

    /** "shared" or "plugins/{owner}". */
    String getSourcePrefix();

    /** Whether this artifact was resolved from the shared prefix. */
    boolean isShared();

    /** Force a re-fetch from the registry, bypassing the cache. */
    void refresh();

    /**
     * Register a callback to fire when this artifact's version changes.
     * The handle is refreshed before the callback runs.
     * Returns this handle for fluent chaining.
     */
    ArtifactHandle onUpdate(Consumer<VersionChangedEvent> callback);

    /** Convenience overload for callbacks that don't need the event details. */
    ArtifactHandle onUpdate(Runnable callback);
}
```

**Lifecycle:**

1. At action initialization, the action-kit runtime scans for `@PluginArtifact` fields.
2. For each annotated field, it creates an `ArtifactHandle` instance backed by a shared `ArtifactCacheManager`.
3. The runtime attempts to resolve the alias and fetch the artifact. If the artifact is missing, the `onMissing` policy determines what happens next (see §4.4).
4. If `preload=true` and the artifact is available, it is fetched immediately. Otherwise, it is fetched lazily on first access to `getBytes()` or `getInputStream()`.
5. The handle is injected into the field before the first action invocation.

### 4.4 Missing Artifact Handling at Startup

When a plugin starts and a declared `@PluginArtifact` does not yet exist in the registry, the `defaultResource` attribute controls the behavior.

#### Path 1: No default (empty `defaultResource`)

When `defaultResource` is empty (the default), and `preload = true`, startup fails with an `IllegalStateException` if the artifact cannot be found. The artifact must be published externally before the plugin starts.

```java
@PluginArtifact(artifactName = "my-config.zip")
private ArtifactHandle config;
```

With `preload = false`, startup succeeds but the handle is not resolved until first access (lazy resolution).

#### Path 2: Seed from classpath default

The plugin bundles a classpath resource that serves as the initial default artifact. On first startup, if no version exists in the registry, the framework publishes the default resource and sets it as `active`. The action starts immediately using this default.

```java
@PluginArtifact(
    artifactName = "my-config.zip",
    defaultResource = "defaults/my-config.zip"
)
private ArtifactHandle config;
```

Behavior:

```
Action startup
│
├── Resolve "active" alias for my-config.zip
│
├── Artifact missing?
│   ├── Load classpath:defaults/my-config.zip
│   ├── Publish to registry with label: {"default": "true"}
│   ├── SET active alias
│   ├── Cache the default artifact
│   ├── ArtifactHandle.isReady() returns true
│   ├── ArtifactHandle.isDefault() returns true
│   └── Action starts normally using the default
│
├── Artifact exists and is the seeded default?
│   ├── ArtifactHandle.isReady() returns true
│   ├── ArtifactHandle.isDefault() returns true
│   └── Action starts normally using the default
│
└── Artifact exists and is a non-default version?
    ├── ArtifactHandle.isReady() returns true
    ├── ArtifactHandle.isDefault() returns false
    └── Normal startup (non-default version in use)
```

When a real version is later published and promoted to `active`, the normal notification flow (Section 5) triggers:

1. The cache manager fetches the new version.
2. `isDefault()` transitions from `true` to `false`.
3. `@OnArtifactVersionChanged` callbacks fire, allowing the action to reinitialize.

Key properties of SEED_DEFAULT mode:

- The plugin is **immediately functional** on first deploy — no operator intervention required.
- The default is **published to the plugin's own prefix** (`plugins/{pluginName}/`), not the shared prefix and not kept only in memory. This means it is visible via the API, can be inspected, and provides a known baseline that can be rolled back to.
- The `"source": "default"` label makes it **easy to distinguish** defaults from operator-published artifacts, both in the API and in the UI.
- The default version string `0.0.0-default` sorts **before any real version**, so it is always the oldest version and will be the first candidate for purge once real versions are published.
- If the default resource is missing from the classpath (developer error), startup fails with a clear error message.
- When `scope = PLUGIN_FIRST`, the seeded default in the plugin prefix takes precedence over any artifact of the same name in the shared prefix. If the plugin-local default is later deleted, the shared version (if any) becomes the fallback.

#### Choosing Between No Default and Seed Default

| Scenario | Recommended Approach |
|----------|---------------------|
| Artifact is produced by an external system (CI build output, model training pipeline) | No `defaultResource` — artifact must be published before startup |
| Plugin has a sensible built-in default that works out of the box | Set `defaultResource` |
| Artifact contains secrets or environment-specific configuration | No `defaultResource` |
| Artifact is a lookup table or ruleset with a known initial state | Set `defaultResource` |
| Plugin is for development/testing and needs to start quickly | Set `defaultResource` |
| Plugin must not process data until a human has explicitly provided the artifact | No `defaultResource` |

#### Example: Multiple Artifacts with Mixed Strategies

An action can depend on multiple artifacts with different default behaviors:

```java
public class EnrichmentAction extends TransformAction<EnrichmentParams> {

    // Operator must upload the customer data extract — no sensible default
    @PluginArtifact(artifactName = "customer-extract.csv")
    private ArtifactHandle customerData;

    // Routing rules have a safe built-in default
    @PluginArtifact(
        artifactName = "routing-rules.json",
        defaultResource = "defaults/routing-rules.json"
    )
    private ArtifactHandle routingRules;

    @Override
    public TransformResultType transform(ActionContext context,
                                         EnrichmentParams params,
                                         TransformInput input) {
        // routingRules.isDefault() may be true or false.
    }
}
```

### 4.5 The `ArtifactCacheManager`

The `ArtifactCacheManager` is a singleton per plugin (per JVM) that manages all artifact handles. It is responsible for:

- Maintaining an in-memory cache of artifact blobs, keyed by `(artifactName, alias)`.
- Resolving aliases (`active` → concrete version) by querying the registry API.
- Performing scope-aware resolution (`PLUGIN_FIRST`, `PLUGIN_ONLY`, `SHARED_ONLY`).
- Detecting version changes via Valkey Pub/Sub notifications and periodic polling.
- Invalidating and refreshing entries when a version change is detected.
- Evicting entries based on configurable limits (max entries, max bytes).
- Providing thread-safe access — multiple actions can share the same cached artifact.

When using `PLUGIN_FIRST` scope, the cache manager checks the plugin namespace first. If the artifact is found there, it uses that version. If not, it falls back to the shared namespace. When a plugin-specific version is later published for an artifact that was initially resolved from shared, the cache manager promotes the entry to use the plugin-specific version.

**Cache configuration** (prefix: `deltafi.artifact-registry.cache`):

| Property | Default | Description |
|----------|---------|-------------|
| `maxEntries` | `100` | Maximum artifacts in cache; 0 = unlimited |
| `maxBytes` | `268435456` (256 MB) | Maximum total bytes in cache; 0 = unlimited |
| `pollIntervalSeconds` | `300` (5 min) | How often to poll registry for changes; 0 = disabled |

### 4.6 Direct Programmatic Access (Non-Annotation)

For cases where annotation injection is not desirable (dynamic artifact names, conditional loading), the action-kit provides an injectable client:

```java
public class DynamicAction extends TransformAction<DynamicActionParameters> {

    @Inject
    private ArtifactRegistryClient artifactClient;

    @Override
    public TransformResultType transform(ActionContext context,
                                         DynamicActionParameters params,
                                         TransformInput input) {
        String artifactName = params.getModelName(); // determined at runtime

        ArtifactManifest manifest = artifactClient.getLatestManifest(
                context.getPluginName(), artifactName);

        byte[] data = artifactClient.download(
                context.getPluginName(), artifactName, "active");

        // Use the data...
    }
}
```

The `ArtifactRegistryClient` is a thin HTTP client wrapping the Core API endpoints from Section 3.

---

---
## 5. Version Change Notifications and Callbacks

### 5.1 Problem

When an artifact is updated (new version published as `active`, or a rollback), running actions that depend on that artifact should be informed so they can reload. Polling the registry on every action invocation is wasteful. We need a push-based notification system.

### 5.2 Notification Flow

```
┌──────────────┐     publish/rollback     ┌────────────────────┐
│   CI / API   │ ──────────────────────── │  Artifact Registry │
│   Client     │                          │     Service        │
└──────────────┘                          └────────┬───────────┘
                                                   │
                                          emit VersionChangedEvent
                                                   │
                                          ┌────────▼───────────┐
                                          │   Event Bus        │
                                          │ (Redis Pub/Sub or  │
                                          │  SSE broadcast)    │
                                          └────────┬───────────┘
                                                   │
                              ┌─────────────────┬──┴───────────────┐
                              │                 │                   │
                      ┌───────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐
                      │  Worker A    │  │  Worker B    │  │  Worker C    │
                      │  (plugin     │  │  (plugin     │  │  (plugin     │
                      │   instance)  │  │   instance)  │  │   instance)  │
                      └───────┬──────┘  └──────────────┘  └──────────────┘
                              │
                    ArtifactCacheManager.onVersionChanged()
                              │
                    ┌─────────▼──────────┐
                    │ Registered         │
                    │ ArtifactCallbacks  │
                    └────────────────────┘
```

### 5.3 The `VersionChangedEvent`

When the registry service processes a publish (with `makeActive=true`), a rollback, or an alias change, it emits a `VersionChangedEvent`:

```java
public record VersionChangedEvent(
    String prefix,           // "plugins/{pluginName}" or "shared"
    String artifactName,
    String alias,            // e.g., "active", "stable"
    String previousVersion,  // null on first publish
    String newVersion,
    Instant timestamp
) {
    /** Whether this event originated from the shared prefix. */
    public boolean isShared() {
        return "shared".equals(prefix);
    }
}
```

Events are emitted for both plugin-specific and shared artifact changes. The cache manager uses the `prefix` field to determine which cached entries to invalidate — an event from the shared prefix may affect any plugin using `PLUGIN_FIRST` or `SHARED_ONLY` scope for that artifact.

### 5.4 Event Transport

Events are published via Valkey (Redis-compatible) Pub/Sub on the channel `deltafi:artifact:version-changed`. All worker nodes subscribe. This leverages DeltaFi's existing Valkey dependency and provides reliable fan-out.

If Valkey is temporarily unavailable, the `ArtifactCacheManager` falls back to periodic polling (configurable, default 5 minutes) to detect alias changes.

### 5.5 Registering Callbacks in Actions

Action authors register callbacks using the `onUpdate()` method on `ArtifactHandle`:

```java
public class MlTransformAction extends TransformAction<MlTransformParameters> {

    @PluginArtifact(
        artifactName = "my-config.zip",
        defaultResource = "defaults/my-config.zip"
    )
    private ArtifactHandle config;

    private volatile CustomerService customerService;

    @PostConstruct
    public void init() {
        customerService = CustomerService.load(config.getBytes());

        config.onUpdate(event -> {
            log.info("Config updated from {} to {} (was default: {})",
                    event.previousVersion(), event.newVersion(), config.isDefault());
            // The ArtifactHandle has already been refreshed by the cache manager
            // before this callback is invoked
            customerService = CustomerService.load(config.getBytes());
        });

        if (config.isDefault()) {
            log.info("Started with default config — awaiting operator-published version");
        }
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                         MlTransformParameters params,
                                         TransformInput input) {
        return customerService.process(input.content());
    }
}
```

**Callback contract:**

- The `ArtifactHandle` is refreshed (new blob fetched and cached) **before** the callback is invoked. By the time the callback runs, `config.getBytes()` returns the new version.
- Callbacks run on the listener thread, not on the action processing thread. In-flight action invocations continue using the old artifact until they complete.
- Long-running work in callbacks should be dispatched to an executor.
- Multiple callbacks can be registered for the same artifact via multiple `onUpdate()` calls.
- A convenience overload `onUpdate(Runnable)` is available when the event details are not needed.

### 5.7 Graceful Transition

To avoid disrupting in-flight processing, the artifact update follows a two-phase approach:

1. **Prefetch phase**: On receiving the event, the cache manager downloads the new version's blob and manifest into the cache alongside the old version. Both versions coexist.
2. **Swap phase**: The alias resolution is atomically updated to point to the new version. New calls to `ArtifactHandle.getBytes()` now return the new blob.
3. **Callback phase**: Registered callbacks are invoked, giving actions the opportunity to rebuild derived state (initialized services, compiled rulesets, etc.).
4. **Eviction phase**: After a configurable grace period (default: 60 seconds), the old version is evicted from the cache.

In-flight action invocations that already hold a reference to `byte[]` from the old version are unaffected — they complete with the data they already have. Only subsequent invocations see the new version.

---

## 6. Configuration Summary

### 6.1 Registry Service (Core Side)

Artifact storage is configured via `artifact.storage.*` properties (environment variable prefix: `ARTIFACT_STORAGE_*`):

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `artifact.storage.url` | `ARTIFACT_STORAGE_URL` | `""` (use primary MinIO) | S3-compatible endpoint URL |
| `artifact.storage.access-key` | `ARTIFACT_STORAGE_ACCESS_KEY` | `""` | S3 access key (only needed when `url` is set) |
| `artifact.storage.secret-key` | `ARTIFACT_STORAGE_SECRET_KEY` | `""` | S3 secret key (only needed when `url` is set) |
| `artifact.storage.bucket-name` | `ARTIFACT_STORAGE_BUCKET_NAME` | `deltafi-artifacts` | S3 bucket name |

When `artifact.storage.url` is empty, the artifact registry shares the same MinIO/S3Proxy instance as content storage. When set, a dedicated MinIO client is created for artifact storage with its own credentials.

The bucket is auto-created on startup if it does not exist.

Notifications are published via Valkey Pub/Sub on channel `deltafi:artifact:version-changed`.

### 6.2 Action-Kit (Worker Side)

Cache settings are configured via `deltafi.artifact-registry.cache.*` properties:

| Property | Default | Description |
|----------|---------|-------------|
| `deltafi.artifact-registry.cache.max-entries` | `100` | Maximum artifacts in cache; 0 = unlimited |
| `deltafi.artifact-registry.cache.max-bytes` | `268435456` (256 MB) | Maximum total bytes; 0 = unlimited |
| `deltafi.artifact-registry.cache.poll-interval-seconds` | `300` (5 min) | Poll interval for alias drift detection; 0 = disabled |

---

## 7. Failure Modes and Recovery

| Failure | Impact | Recovery |
|---------|--------|----------|
| S3Proxy unavailable | Publishes fail; actions continue with cached artifacts | Retry with backoff; cache provides resilience |
| Valkey unavailable | Notifications not delivered | Actions poll on a configurable interval (default 5 min) as fallback |
| Notification lost | Action uses stale artifact | Periodic alias check detects drift |
| Publish fails mid-write (blob written, manifest not) | Orphaned blob with no manifest | Cleaned up by orphan cleanup during scheduled purge |
| Rollback to deleted version | API returns 404 | Caller must choose an existing version |
| Concurrent publish of same version | Rejected with 409 Conflict | Policy: CI should not publish duplicate versions |
| Artifact missing at startup (no default) | Startup fails with IllegalStateException | Publish the artifact before starting the plugin |
| Artifact missing at startup (with defaultResource) | Default seeded from classpath; action starts normally | Operator can publish a real version at any time |
| Default resource missing from classpath | Startup fails | Developer error — fix the classpath resource and redeploy |

### 7.1 Fallback Polling

If the push notification system is unavailable, the `ArtifactCacheManager` falls back to periodic polling. It queries the registry API on a configurable interval (default: 5 minutes) and compares versions against cached copies. If a version has changed, it triggers the same refresh-callback sequence as the push path.

This ensures that even in degraded conditions (Valkey down, network partition), actions will eventually converge on the correct artifact version.

---

## 8. Security Considerations

- **Permission model**: Read endpoints require `ArtifactRegistryRead` permission. Write endpoints (publish, delete, alias management, purge) require `ArtifactRegistryWrite` permission.
- **Name validation**: Artifact names, version strings, and alias names are validated against `^[a-zA-Z0-9][a-zA-Z0-9._-]*$` with a 512-character limit to prevent path traversal and namespace pollution.
- **Scope enforcement**: The `ArtifactScope` on each `@PluginArtifact` annotation is enforced by the cache manager at resolution time. A `PLUGIN_ONLY` scope will never resolve from the shared prefix, even if a matching artifact exists there.
- **Immutable blobs**: Published artifacts cannot be silently tampered with. The SHA-256 digest in the manifest provides integrity verification.
- **Airgapped deployments**: The entire scheme is self-contained — no external registry dependencies. S3Proxy with a filesystem backend works in fully disconnected environments.


# Phase 2 — Plugin Initial Configuration (NOT YET IMPLEMENTED)

> **Status:** This phase is designed but not yet implemented. The content below describes the planned design.

> **Relationship to Phase 1:** This is a separate feature from the artifact registry. Plugin configuration files are mutable, singleton objects — not versioned artifact streams — with different lifecycle semantics (seed-once, edit-in-place, hot-reload). However, this feature uses the artifact registry's S3Proxy storage infrastructure as its backend, storing configuration objects as flat keys under the plugin's prefix in the same `deltafi-artifacts` bucket. This avoids introducing a separate storage system while keeping the two features conceptually and operationally distinct.

## 9. Plugin Initial Configuration

### 9.1 Motivation

Plugins frequently need external configuration — feature flags, connection strings, tuning parameters, routing rules — that operators want to change without rebuilding and redeploying the plugin. Spring's standard `@PropertySource` only reads from the classpath or local filesystem, neither of which is practical in a containerized environment where the goal is to manage configuration centrally.

This is a different problem from artifact management. Artifacts are versioned, immutable binaries published by CI pipelines — the artifact registry (Phase 1) handles that. Plugin configuration is a mutable, singleton file that the plugin seeds with a sensible default and operators edit in place. The two features share storage infrastructure but serve different purposes.

The `@PluginPropertySource` annotation addresses this: it loads a YAML (or properties) file from the plugin's object storage at startup, making its entries available as Spring properties.

**Plugins that use `@PluginPropertySource` are required to provide a `defaultResource`.** On first deploy, if the configuration object does not yet exist in storage, the framework seeds it from the bundled classpath resource. This ensures the plugin is always functional out of the box — operators can customize the configuration later, but the plugin never fails to start due to a missing configuration file.

### 9.2 Usage

```java
import org.springframework.context.annotation.Configuration;
import org.deltafi.actionkit.config.PluginPropertySource;

// Load system properties from plugin object storage: deltafi-java-hello-world/system.yaml
// If it doesn't exist yet, load the default resources/system.yaml into storage and apply those settings
@Configuration
@PluginPropertySource(
    objectName = "deltafi-java-hello-world/system.yaml",
    defaultResource = "system.yaml"
)
public class PluginConfig {}
```

Multiple `@PluginPropertySource` annotations can be stacked to compose configuration from several files:

```java
@Configuration
@PluginPropertySource(
    objectName = "deltafi-java-hello-world/system.yaml",
    defaultResource = "system.yaml"
)
@PluginPropertySource(
    objectName = "deltafi-java-hello-world/routes.yaml",
    defaultResource = "routes.yaml"
)
public class PluginConfig {}
```

### 9.3 Annotation Attributes

| Attribute         | Type    | Default      | Description                                                      |
|-------------------|---------|--------------|------------------------------------------------------------------|
| `objectName`      | String  | (required)   | Key path within the plugin's prefix (e.g., `my-plugin/system.yaml`) |
| `defaultResource` | String  | (required)   | Classpath resource to seed storage on first deploy. The framework enforces that this resource exists at build time. |
| `prefix`          | String  | `""`         | Property prefix to apply (e.g., `"app"` makes `timeout` → `app.timeout`) |
| `order`           | int     | `0`          | Load order when multiple annotations are present (lower = earlier) |

### 9.4 Bootstrap Lifecycle

The `@PluginPropertySource` mechanism executes during the Spring `EnvironmentPostProcessor` phase — before the application context is fully initialized, so that its properties are available for `@Value` injection, `@ConditionalOnProperty` guards, and other property-driven configuration across the entire application.

```
Application startup
│
├── 1. EnvironmentPostProcessor phase
│   │
│   ├── Discover all @PluginPropertySource annotations
│   ├── Sort by `order` attribute
│   │
│   └── For each annotation:
│       │
│       ├── HEAD {objectName} in plugin prefix
│       │
│       ├── If exists in storage:
│       │   └── GET object → parse YAML/properties → add to Spring Environment
│       │
│       └── If not exists in storage:
│           ├── Load classpath:{defaultResource}  (must exist — build-time validated)
│           ├── PUT to plugin prefix as {objectName}
│           └── Parse YAML/properties → add to Spring Environment
│
├── 2. Bean creation phase (properties now available)
│   ├── @Value("${timeout}") resolves from storage-loaded properties
│   └── @ConditionalOnProperty evaluates against storage-loaded properties
│
└── 3. Action initialization (artifact handles, etc.)
```

### 9.5 Storage Layout

Plugin configuration objects are stored in the same `deltafi-artifacts` bucket used by the artifact registry (Phase 1), under the plugin's `plugins/{pluginName}/` prefix. Configuration objects use a flat key path — they are mutable, singleton files with no version hierarchy:

```
bucket: deltafi-artifacts
│
├── plugins/
│   └── org.deltafi.hello-world/
│       │
│       ├── deltafi-java-hello-world/
│       │   └── system.yaml                     # mutable plugin config (Phase 2)
│       │
│       └── my-config.zip/                      # versioned artifact (Phase 1)
│           ├── 2.0.0/
│           │   ├── blob
│           │   └── manifest.json
│           ├── _aliases/
│           │   └── active
│           └── _config/
│               └── purge.json
```

The two features coexist under the same prefix but are easy to distinguish: versioned artifacts always have a `{name}/{version}/blob` structure with manifests and aliases, while configuration objects are simple key-value files at a flat path. They are managed through separate API endpoints (Section 3 for artifacts, Section 9.7 for configuration) and have no interaction with each other.

### 9.6 Runtime Updates

When an operator modifies the configuration file in storage (via the API or direct S3 PUT), the change takes effect on the next plugin restart by default. For hot-reload without restart, the `@PluginPropertySource` system integrates with the notification mechanism from Section 5:

```java
@Configuration
@PluginPropertySource(
    objectName = "deltafi-java-hello-world/system.yaml",
    defaultResource = "system.yaml",
    watchForChanges = true   // subscribe to change notifications
)
public class PluginConfig {}
```

When `watchForChanges=true`:

1. The framework subscribes to storage change events for the `objectName` key.
2. On change, the YAML file is re-fetched and re-parsed.
3. A `PropertySourceChangedEvent` is published to the Spring `ApplicationEventPublisher`.
4. Beans that need to react can listen for this event or use `@RefreshScope` semantics.

```java
@Component
public class RoutingService {

    @Value("${routing.defaultRegion:us-east}")
    private String defaultRegion;

    @EventListener(PropertySourceChangedEvent.class)
    public void onConfigChanged(PropertySourceChangedEvent event) {
        if (event.getObjectName().endsWith("system.yaml")) {
            // Re-read updated property values
            this.defaultRegion = event.getProperty("routing.defaultRegion", "us-east");
            log.info("Routing config updated: defaultRegion={}", defaultRegion);
        }
    }
}
```

### 9.7 API for Plugin Configuration Objects

The registry API provides dedicated endpoints for managing these mutable configuration objects, separate from the versioned artifact APIs:

**Upload / Update:**

```
PUT /api/v2/plugins/{pluginName}/config/{objectName}
Content-Type: application/x-yaml  (or application/json, text/plain, etc.)

<raw file body>
```

**Download:**

```
GET /api/v2/plugins/{pluginName}/config/{objectName}
```

**Delete:**

```
DELETE /api/v2/plugins/{pluginName}/config/{objectName}
```

**List all config objects:**

```
GET /api/v2/plugins/{pluginName}/config
```

Response `200 OK`:

```json
{
  "pluginName": "org.deltafi.hello-world",
  "configObjects": [
    {
      "objectName": "deltafi-java-hello-world/system.yaml",
      "contentType": "application/x-yaml",
      "size": 2048,
      "lastModified": "2026-02-23T16:00:00Z"
    }
  ]
}
```

These are simple mutable objects — no versioning, no manifests, no aliases. For data that needs version history, use the versioned artifact registry (Sections 2 and 3) instead.

---

# Phase 3 — UI (NOT YET IMPLEMENTED)

> **Status:** This phase is designed but not yet implemented. The content below describes the planned design.

## 10. User Interface

Phase 3 adds visual management surfaces for both features — the artifact registry (Phase 1) and plugin configuration (Phase 2) — across three areas of the DeltaFi UI.

### 10.1 Plugin Page — Artifact Management Panel (Artifact Registry)

Each plugin's detail page gains an **Artifacts** tab that provides full lifecycle management for plugin-specific artifacts. This surfaces the artifact registry feature (Phase 1).

**Artifact list view:**

- Table of all artifacts under the plugin's prefix, showing: artifact name, active version, version count, content type, size, and `createdAt` timestamp.
- Visual indicator if the current active version was seeded from a default (`"default": "true"` label).
- Visual indicator if any action is in BLOCK mode waiting for a missing artifact.

**Per-artifact detail view:**

- Version history table: all versions newest-first, with version string, description, labels, size, SHA-256, and timestamps.
- The current `active` version is highlighted.
- **Upload new version**: file picker with version string input, optional description, and key-value label editor. Toggle for "make active."
- **Rollback**: select any existing version from the list and promote it to `active` with a confirmation dialog.
- **Delete version**: remove a non-active version with confirmation. Disabled for the current `active` version (rollback first).
- **Purge config**: view and edit `keepLatest` and `maxAge` settings for this artifact. Manual purge trigger button.

**Upload flow:**

1. User selects file, enters version string and optional labels.
2. UI calls `POST /api/v2/plugins/{pluginName}/artifacts/{artifactName}/versions`.
3. On success, the version list refreshes and the new version appears (highlighted if `makeLatest`).
4. If the artifact was previously missing and an action was blocked, the UI shows a status transition notification.

### 10.2 Plugin Page — Configuration Editor (Plugin Configuration)

For plugins that declare `@PluginPropertySource` configuration, the plugin page gains a **Configuration** tab. This surfaces the plugin configuration feature (Phase 2).

**Configuration file list:**

- Lists all config objects under the plugin's prefix (from `GET /api/v2/plugins/{pluginName}/config`).
- Shows: object name, content type, size, and last modified timestamp.
- Badge indicating if the current content matches the original default (i.e., has never been modified by an operator).

**Inline editor:**

- For YAML and JSON config files, the UI provides a syntax-highlighted in-browser editor (using CodeMirror or Monaco).
- Users can edit the configuration directly and save.
- **Save** calls `PUT /api/v2/plugins/{pluginName}/config/{objectName}` with the updated content.
- If the plugin declared `watchForChanges=true`, a note indicates the change will take effect without restart.
- **Reset to default**: reverts the config object to the plugin's bundled default resource. Calls DELETE followed by a plugin restart (which re-seeds from classpath).

**Validation:**

- YAML syntax validation on save (client-side).
- Optional schema validation if the plugin provides a JSON Schema for its configuration (future enhancement).

### 10.3 Artifact Service Page — System-Wide Management (Both Features)

A new top-level **Artifact Service** page provides a unified view across all plugins and the shared prefix, covering both artifact registry and plugin configuration management.

**Dashboard view:**

- Summary cards: total artifact count, total storage used, artifacts using defaults, artifacts with blocked actions.
- Per-prefix breakdown: shared prefix and each plugin prefix listed with artifact count and total size.

**Shared artifact management:**

- Full artifact management interface (identical to the per-plugin panel in §10.1) for the `shared/` prefix.
- Upload, version, rollback, delete, and purge shared artifacts.
- List of plugins consuming each shared artifact (derived from action-kit `@PluginArtifact` declarations with `SHARED_ONLY` or `PLUGIN_FIRST` scope).

**Cross-plugin artifact browser:**

- Searchable, filterable table of all artifacts across all prefixes.
- Filter by: plugin name, artifact name, label key/value, content type, version age.
- Bulk operations: trigger purge across all artifacts, export artifact manifests as JSON.

**Health and status:**

- List of actions in BLOCK mode awaiting artifacts, with direct links to upload the needed artifact.
- List of artifacts still using seeded defaults, with a visual prompt to upload a production version.
- Purge schedule status: next run time, last run results, total versions purged.

### 10.4 Notifications and Real-Time Updates

The UI subscribes to `VersionChangedEvent` notifications (via SSE or WebSocket from the registry service) to provide live updates:

- When a new version is published, the artifact list refreshes automatically.
- When a blocked action transitions to ready, the status indicator updates without page reload.
- When a purge runs, the version count updates in real time.
- Toast notifications for significant events: "New version 2.1.0 published for my-config.zip", "Action MlTransformAction is now ready".
