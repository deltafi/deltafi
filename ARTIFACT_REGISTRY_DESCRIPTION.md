# DeltaFi Artifact Registry

## What It Does

The DeltaFi Artifact Registry gives plugins access to versioned files — configuration, lookup tables, rulesets, models — that live outside the plugin code and can be updated independently without rebuilding or redeploying the plugin.

The most common use case is **plugin configuration**: your plugin ships with a sensible default configuration file, operators customize it for their environment, and the plugin picks up changes automatically. But the registry handles any kind of binary artifact that a plugin needs at runtime.

Artifacts are stored in a dedicated S3-compatible object store, versioned immutably, and delivered to your action code through a simple annotation. When an operator publishes a new version, running plugins are notified and update in place — no restart required.

## Key Concepts

**Artifact** — a named, versioned binary file associated with a plugin. Each version is immutable once published. Examples: `routing-rules.json`, `customer-lookup.csv`, `ml-model.bin`.

**Active alias** — a pointer to the current active version of an artifact. When you publish a new version, the `active` alias moves to it automatically. Actions track `active` by default, so they always use the most recent version. You can also create custom aliases like `stable` or `canary`.

**Plugin prefix** — each plugin's artifacts are stored under `plugins/{pluginName}/` in the registry. Plugins can only write to their own prefix.

**Shared prefix** — artifacts under `shared/` are readable by all plugins. Use this for organization-wide configuration, common lookup tables, or reference data that multiple plugins consume.

**Scope resolution** — when an action requests an artifact, the registry checks the plugin's own prefix first, then falls back to `shared/`. This means a plugin can override a shared artifact with its own version when needed.

## Managing Artifacts with the API

All examples below use `curl` against a DeltaFi instance at `https://deltafi.example.com`. The API base path is `/api/v2/artifacts`. The first path segment after `/artifacts` is the **owner** — either `shared` or a plugin name like `org.deltafi.my-plugin`.

### Publishing an Artifact

Upload a new version of a configuration file:

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json \
  -F "file=@routing-rules.json" \
  -F "version=1.0.0" \
  -F "description=Initial routing rules for eastern region" \
  -F "region=us-east"
```

Response (`201 Created`):

```json
{
  "pluginName": "org.deltafi.my-plugin",
  "artifactName": "routing-rules.json",
  "version": "1.0.0",
  "isActive": true,
  "sha256": "ab3f9c7e..."
}
```

The file is stored immutably, a manifest is generated with the SHA-256 digest, and the `active` alias points to this version. Any actions tracking this artifact are notified immediately.

Any additional form fields (like `region=us-east` above) are stored as **labels** on the manifest — freeform key-value metadata you can use however you like.

The `version` parameter is optional. If omitted, the registry auto-generates one from the current UTC timestamp (e.g., `20260310.143022.456`).

**Validation rules:** Artifact names, version strings, and alias names must start with a letter or digit and contain only letters, digits, hyphens (`-`), underscores (`_`), and dots (`.`). Maximum length is 512 characters.

### Updating an Artifact

Publish a new version the same way. The previous version is preserved, and `active` moves to the new one:

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json \
  -F "file=@routing-rules-v2.json" \
  -F "version=2.0.0" \
  -F "description=Added western region routing" \
  -F "region=us-east,us-west"
```

To publish a version without making it `active` (for example, a canary release you want to test before promoting):

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json \
  -F "file=@routing-rules-canary.json" \
  -F "version=2.1.0-rc1" \
  -F "description=Canary: new priority algorithm" \
  -F "makeActive=false"
```

You can also set a custom alias at publish time:

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json \
  -F "file=@routing-rules-canary.json" \
  -F "version=2.1.0-rc1" \
  -F "makeActive=false" \
  -F "alias=canary"
```

### Checking What's Published

List all artifacts across all owners:

```bash
curl https://deltafi.example.com/api/v2/artifacts
```

List all artifacts for a specific owner with their current active versions:

```bash
curl https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin
```

```json
{
  "pluginName": "org.deltafi.my-plugin",
  "artifacts": [
    {
      "artifactName": "routing-rules.json",
      "activeVersion": "2.0.0",
      "activeManifest": {
        "artifactName": "routing-rules.json",
        "version": "2.0.0",
        "description": "Added western region routing",
        "contentType": "application/json",
        "createdAt": "2026-03-15T10:30:00Z",
        "modifiedAt": "2026-03-15T10:30:00Z",
        "sha256": "ef1a2b3c...",
        "size": 8192,
        "labels": {"region": "us-east,us-west"}
      },
      "totalVersions": 3,
      "aliases": {"active": "2.0.0", "canary": "2.1.0-rc1"}
    }
  ]
}
```

View the full version history for a specific artifact:

```bash
curl https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/versions
```

```json
{
  "pluginName": "org.deltafi.my-plugin",
  "artifactName": "routing-rules.json",
  "activeVersion": "2.0.0",
  "versions": [
    {
      "artifactName": "routing-rules.json",
      "version": "2.0.0",
      "description": "Added western region routing",
      "contentType": "application/json",
      "createdAt": "2026-03-15T10:30:00Z",
      "modifiedAt": "2026-03-15T10:30:00Z",
      "sha256": "ef1a2b3c...",
      "size": 8192,
      "labels": {"region": "us-east,us-west"}
    },
    {
      "artifactName": "routing-rules.json",
      "version": "1.0.0",
      "description": "Initial routing rules for eastern region",
      "contentType": "application/json",
      "createdAt": "2026-03-10T08:00:00Z",
      "modifiedAt": "2026-03-10T08:00:00Z",
      "sha256": "cd9e1b2a...",
      "size": 3900000,
      "labels": {"region": "us-east"}
    }
  ],
  "aliases": {"2.0.0": ["active"], "2.1.0-rc1": ["canary"]}
}
```

### Downloading an Artifact

Download the current active version:

```bash
curl -O https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/versions/active
```

Download a specific version:

```bash
curl -O https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/versions/1.0.0
```

Download via a custom alias:

```bash
curl -O https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/versions/canary
```

The response includes metadata headers: `X-Artifact-Version`, `X-Artifact-Sha256`, `X-Artifact-Description`, `X-Artifact-Labels` (JSON), and `X-Artifact-Source-Prefix`.

### Rolling Back

If a new version causes problems, roll back to a previous version without re-uploading anything:

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/rollback \
  -H "Content-Type: application/json" \
  -d '{"version": "1.0.0"}'
```

The `active` alias moves back to `1.0.0`, and all actions tracking this artifact are notified of the change.

### Managing Aliases

Set an alias to a specific version:

```bash
curl -X PUT \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/aliases/stable \
  -H "Content-Type: application/json" \
  -d '{"version": "1.0.0"}'
```

Delete a custom alias (the `active` alias cannot be deleted):

```bash
curl -X DELETE \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/aliases/canary
```

### Deleting Versions

Remove an old version you no longer need:

```bash
curl -X DELETE \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/versions/1.0.0
```

You cannot delete the version that `active` currently points to — rollback to a different version first.

Delete an entire artifact (all versions, aliases, and config):

```bash
curl -X DELETE \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json
```

### Managing Shared Artifacts

Shared artifacts use the same API under the `shared` owner:

```bash
# Publish a shared lookup table
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/shared/geo-lookup.csv \
  -F "file=@geo-lookup.csv" \
  -F "version=1.0.0" \
  -F "description=Country/region geocoding lookup"

# List all shared artifacts
curl https://deltafi.example.com/api/v2/artifacts/shared
```

### Retention and Purge

Set a retention policy on an artifact to automatically clean up old versions:

```bash
curl -X PUT \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/purge-config \
  -H "Content-Type: application/json" \
  -d '{"keepLatest": 3, "maxAgeMinutes": 10080}'
```

This keeps the 3 most recent versions and anything newer than 7 days (10,080 minutes). A version is only purged when it exceeds both thresholds, and the current `active` version is never purged regardless. The purge scheduler runs periodically, or you can trigger it manually:

```bash
curl -X POST \
  https://deltafi.example.com/api/v2/artifacts/org.deltafi.my-plugin/routing-rules.json/purge
```

The system default purge policy is `keepLatest: 5, maxAgeMinutes: 43200` (30 days).

## Using Artifacts in Plugin Code

The action-kit provides annotation-driven access to artifacts. You declare what you need, and the framework handles fetching, caching, and updating.

### Basic Usage: Configuration File in a Transform Action

The simplest case — your action needs a JSON configuration file, and you want to ship a default that works out of the box:

```java
public class RoutingTransformAction extends TransformAction<RoutingTransformParameters> {

    @PluginArtifact(
        artifactName = "routing-rules.json",
        defaultResource = "defaults/routing-rules.json"
    )
    private ArtifactHandle routingRules;

    @Override
    public TransformResultType transform(ActionContext context,
                                         RoutingTransformParameters params,
                                         TransformInput input) {
        // Parse the current configuration
        RoutingConfig config = parseJson(routingRules.getBytes(), RoutingConfig.class);

        // Route based on the rules
        String region = config.resolveRegion(input.content());
        context.addMetadata("targetRegion", region);

        return input.toTransformResult(context);
    }

    private <T> T parseJson(byte[] bytes, Class<T> type) {
        return new ObjectMapper().readValue(bytes, type);
    }
}
```

Place your default configuration at `src/main/resources/defaults/routing-rules.json` in your plugin project. On first deploy, the framework publishes it to the registry and makes it `active`. Your action starts immediately using it. When an operator publishes a real version, the action automatically switches to the new one.

### Reacting to Version Changes

When your action uses a library or service that needs to be re-initialized when the artifact changes, register a callback using `onUpdate()`:

```java
public class EnrichmentAction extends TransformAction<EnrichmentParameters> {

    @PluginArtifact(
        artifactName = "enrichment-rules.yaml",
        defaultResource = "defaults/enrichment-rules.yaml"
    )
    private ArtifactHandle rulesArtifact;

    // The rule engine is initialized from the artifact and rebuilt on changes.
    // Marked volatile because it is written by the notification thread
    // and read by action processing threads.
    private volatile RuleEngine ruleEngine;

    @PostConstruct
    public void initialize() {
        ruleEngine = RuleEngine.compile(rulesArtifact.getBytes());

        // Register for version change notifications
        rulesArtifact.onUpdate(event -> {
            log.info("Enrichment rules updated: {} → {}",
                    event.previousVersion(), event.newVersion());
            // The ArtifactHandle has already been refreshed before this callback
            // fires — rulesArtifact.getBytes() returns the new version.
            ruleEngine = RuleEngine.compile(rulesArtifact.getBytes());
        });

        log.info("Enrichment rules initialized: version={}, default={}",
                rulesArtifact.getVersion(), rulesArtifact.isDefault());
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                         EnrichmentParameters params,
                                         TransformInput input) {
        List<Enrichment> enrichments = ruleEngine.evaluate(input.content());

        for (Enrichment e : enrichments) {
            context.addMetadata(e.key(), e.value());
        }

        return input.toTransformResult(context);
    }
}
```

Key points about this pattern:

- `@PostConstruct` runs once at startup, after the artifact has been fetched and cached. The `ArtifactHandle` is guaranteed to be ready.
- `onUpdate()` fires on a background thread whenever the artifact's alias moves to a new version. The `ArtifactHandle` is automatically refreshed before the callback runs, so `getBytes()` already returns the new content.
- The `volatile` keyword on `ruleEngine` ensures that action processing threads see the updated reference after the callback writes it.
- If the callback throws an exception, the action continues operating with the previous version.
- You can also use `onUpdate(Runnable)` if you don't need the event details.

### Waiting for an Externally-Provided Artifact

When the artifact must be provided by an operator or CI pipeline and there's no sensible default, omit the `defaultResource`. If `preload=true` (the default), startup will fail until the artifact is published:

```java
public class ClassificationAction extends TransformAction<ClassificationParameters> {

    @PluginArtifact(artifactName = "classification-model.bin")
    private ArtifactHandle model;

    private volatile Classifier classifier;

    @PostConstruct
    public void initialize() {
        classifier = Classifier.load(model.getInputStream());
        log.info("Classification model loaded: version={}, sha256={}",
                model.getVersion(), model.getSha256());

        model.onUpdate(event -> {
            log.info("Model updated to version {}", event.newVersion());
            classifier = Classifier.load(model.getInputStream());
        });
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                         ClassificationParameters params,
                                         TransformInput input) {
        String category = classifier.classify(input.content());
        context.addMetadata("classification", category);
        return input.toTransformResult(context);
    }
}
```

Alternatively, set `preload = false` for lazy resolution — the artifact is fetched on first access to `getBytes()` or `getInputStream()`.

### Multiple Artifacts with Mixed Strategies

An action can depend on several artifacts, each with its own scope and default behavior:

```java
public class CustomerEnrichmentAction extends TransformAction<CustomerEnrichmentParams> {

    // Configuration ships with the plugin and can be customized later
    @PluginArtifact(
        artifactName = "enrichment-config.json",
        defaultResource = "defaults/enrichment-config.json"
    )
    private ArtifactHandle config;

    // Customer data must be uploaded by the operator — no default
    @PluginArtifact(artifactName = "customer-extract.csv")
    private ArtifactHandle customerData;

    // Shared lookup table managed by the platform team
    @PluginArtifact(
        artifactName = "geo-regions.csv",
        scope = ArtifactScope.SHARED_ONLY
    )
    private ArtifactHandle geoRegions;

    private volatile EnrichmentService service;

    @PostConstruct
    public void initialize() {
        service = new EnrichmentService(
            parseJson(config.getBytes(), EnrichmentConfig.class),
            CsvParser.parse(customerData.getBytes()),
            CsvParser.parse(geoRegions.getBytes())
        );

        if (config.isDefault()) {
            log.info("Using default enrichment config — customize via the artifact registry");
        }

        if (geoRegions.isShared()) {
            log.info("Using shared geo-regions table (version {})", geoRegions.getVersion());
        }

        // Re-build the service when any artifact changes
        Runnable rebuild = this::rebuildService;
        config.onUpdate(rebuild);
        customerData.onUpdate(rebuild);
        geoRegions.onUpdate(rebuild);
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                         CustomerEnrichmentParams params,
                                         TransformInput input) {
        return service.enrich(context, input);
    }

    private void rebuildService() {
        service = new EnrichmentService(
            parseJson(config.getBytes(), EnrichmentConfig.class),
            CsvParser.parse(customerData.getBytes()),
            CsvParser.parse(geoRegions.getBytes())
        );
    }
}
```

### The `@PluginArtifact` Annotation

| Attribute         | Type             | Default           | Description                                                      |
|-------------------|------------------|-------------------|------------------------------------------------------------------|
| `artifactName`    | String           | (required)        | Name of the artifact in the registry                             |
| `scope`           | `ArtifactScope`  | `PLUGIN_FIRST`    | Where to look: `PLUGIN_ONLY`, `SHARED_ONLY`, or `PLUGIN_FIRST` (plugin prefix with shared fallback) |
| `alias`           | String           | `"active"`        | Which alias to track (e.g., `"active"`, `"stable"`)              |
| `preload`         | boolean          | `true`            | Fetch on startup vs. on first access                             |
| `cacheable`       | boolean          | `true`            | Cache the blob in memory between invocations                     |
| `defaultResource` | String           | `""`              | Classpath resource to publish as the initial version if the artifact does not yet exist in the registry. When empty (the default), startup fails if the artifact is missing. |

### The `ArtifactHandle` Interface

| Method              | Return Type            | Description                                           |
|---------------------|------------------------|-------------------------------------------------------|
| `getArtifactName()` | `String`               | Artifact identifier as registered                     |
| `getVersion()`      | `String`               | Resolved version string                               |
| `getDescription()`  | `String`               | Human-readable description, or null                   |
| `getContentType()`  | `String`               | MIME type of the blob                                 |
| `getBytes()`        | `byte[]`               | Raw artifact bytes (never null)                       |
| `getInputStream()`  | `InputStream`          | Fresh stream over the bytes (caller must close)       |
| `getString()`       | `String`               | Bytes decoded as UTF-8                                |
| `getString(Charset)`| `String`               | Bytes decoded with the given charset                  |
| `getManifest()`     | `ArtifactManifest`     | Full manifest metadata                                |
| `getLabels()`       | `Map<String, String>`  | Label metadata, or null                               |
| `getSha256()`       | `String`               | SHA-256 digest of the content                         |
| `isReady()`         | `boolean`              | Whether the artifact has been resolved                |
| `isDefault()`       | `boolean`              | Whether this version was seeded from a default        |
| `getSourcePrefix()` | `String`               | `"shared"` or `"plugins/{owner}"`                     |
| `isShared()`        | `boolean`              | Whether resolved from the shared namespace            |
| `refresh()`         | `void`                 | Force re-fetch from the registry                      |
| `onUpdate(Consumer<VersionChangedEvent>)` | `ArtifactHandle` | Register a version-change callback |
| `onUpdate(Runnable)` | `ArtifactHandle`      | Register a callback without event details             |

## CI/CD Integration

A typical pipeline publishes artifacts alongside plugin builds:

```yaml
# GitLab CI example
publish-config:
  stage: deploy
  script:
    - |
      curl -X POST \
        ${DELTAFI_URL}/api/v2/artifacts/${PLUGIN_NAME}/routing-rules.json \
        -F "file=@config/routing-rules.json" \
        -F "version=${CI_COMMIT_TAG}" \
        -F "description=Built from ${CI_COMMIT_SHORT_SHA}" \
        -F "builtBy=gitlab-ci" \
        -F "pipeline=${CI_PIPELINE_ID}"
  only:
    - tags
```

The version string, description, and any additional labels are preserved in the artifact manifest and visible in the DeltaFi UI and API.

## How It Works Under the Hood

Artifacts are stored in a single S3-compatible bucket (`deltafi-artifacts`) with path-based isolation. Each artifact version is an immutable blob with an accompanying JSON manifest containing metadata, labels, and a SHA-256 digest. An `active` alias file points to the current active version.

When a new version is published (or a rollback is performed), a `VersionChangedEvent` is broadcast via Valkey Pub/Sub. Every plugin node subscribed to that artifact receives the notification, prefetches the new version into its local cache, atomically swaps the alias resolution, and fires registered callbacks. In-flight action invocations are never interrupted — they complete with the data they already have.

If Valkey is temporarily unavailable, the cache manager falls back to periodic polling (every 5 minutes by default) to detect alias changes.

## Complete API Reference

Base path: `/api/v2/artifacts`

| Method | Path | Description | Status |
|--------|------|-------------|--------|
| `GET` | `/` | List all artifacts across all owners | `200` |
| `GET` | `/{owner}` | List artifacts for one owner | `200` |
| `POST` | `/{owner}/{artifactName}` | Publish a new version (multipart) | `201` |
| `GET` | `/{owner}/{artifactName}/versions` | List all versions of an artifact | `200` |
| `GET` | `/{owner}/{artifactName}/versions/{version}` | Download (version string or alias) | `200` |
| `POST` | `/{owner}/{artifactName}/rollback` | Move `active` alias to a prior version | `200` |
| `DELETE` | `/{owner}/{artifactName}/versions/{version}` | Delete a specific version | `204` |
| `DELETE` | `/{owner}/{artifactName}` | Delete entire artifact | `204` |
| `PUT` | `/{owner}/{artifactName}/aliases/{alias}` | Set an alias to a version | `200` |
| `DELETE` | `/{owner}/{artifactName}/aliases/{alias}` | Remove an alias | `204` |
| `GET` | `/{owner}/{artifactName}/purge-config` | Get purge policy | `200` |
| `PUT` | `/{owner}/{artifactName}/purge-config` | Set purge policy | `204` |
| `POST` | `/{owner}/{artifactName}/purge` | Trigger immediate purge | `200` |

**Permissions:** Read endpoints require `ArtifactRegistryRead`. Write endpoints require `ArtifactRegistryWrite`.

**Error responses:**

| Status | Condition |
|--------|-----------|
| `400 Bad Request` | Invalid artifact name, version, or alias |
| `404 Not Found` | Artifact or version does not exist |
| `409 Conflict` | Duplicate version, or attempting to delete the active version |
| `500 Internal Server Error` | Storage failure |
