# Artifact Registry

The artifact registry gives plugins access to versioned binary files — configuration, rulesets, lookup
tables, ML models — that live outside the plugin code and can be updated independently without
rebuilding or redeploying the plugin.

Artifacts are stored in a dedicated S3-compatible object store, versioned immutably, and delivered to
action code through a simple annotation. When an operator publishes a new version, running plugins are
notified and update in place — no restart required.

## Key Concepts

**Artifact** — a named, versioned binary file associated with an owner. Each published version is
immutable. Examples: `routing-rules.json`, `customer-lookup.csv`, `ml-model.bin`.

**Active alias** — a pointer named `active` that tracks the current version. Publishing a new version
moves `active` to it automatically. Plugin code resolves `active` by default, so it always uses the
most recent version.

**Custom aliases** — you can create additional aliases (e.g. `stable`, `canary`) and point them at
any published version. Plugin code can resolve by alias name.

**Plugin namespace** — each plugin's artifacts are stored under its plugin name (the `group.artifactId`
from its build). Plugins can only publish to their own namespace or shared.

**Shared namespace** — artifacts under the `shared` owner are readable by all plugins. Use this for
organization-wide reference data, common lookup tables, or models consumed by multiple plugins.

**Scope resolution** — by default, resolving an artifact checks the plugin's own namespace first, then
falls back to `shared`. A plugin can override a shared artifact with its own copy when needed.

---

## Using Plugin Artifacts in Plugin Code

Both the Java and Python action-kits provide declarative artifact injection. You declare what
artifact you need; the framework handles fetching, caching, and live updates.

### How it works

**Declaration** — artifacts are declared at the class level (Java: field annotation, Python: class
attribute). The framework injects a live handle at startup.

**Default resources** — an optional default file can be bundled with the plugin. On first deploy
the framework publishes it to the registry automatically; when an operator publishes a real version
the action switches to it without a restart.

**Caching** — by default handles are non-cacheable: every `getBytes()` / `get_bytes()` call
downloads fresh bytes from the registry. The default is `false` because the most common pattern
is to react to updates via a callback and store the parsed result in your own field (a compiled
rule engine, a loaded model, an in-memory index). In that pattern the raw bytes are only needed
once per version, so caching them in the handle as well would waste memory for no benefit. Set
`cacheable = true` / `cacheable=True` when your action calls `getBytes()` on every invocation
without maintaining its own copy — caching then avoids a download on each call.

**Live updates** — when an operator publishes a new version, connected plugins are notified via a
push notification and a background poller as a safety net. Cached entries are refreshed
automatically. Registered callbacks fire after the refresh so application logic (e.g. recompiling
a rule engine) can react without a restart.

**Scope** — controls which namespace is searched:

| Value | Behaviour |
|---|---|
| `PLUGIN_FIRST` | Try plugin namespace first, fall back to shared (default) |
| `PLUGIN_ONLY` | Plugin namespace only; never fall back to shared |
| `SHARED_ONLY` | Shared namespace only; plugin namespace is never checked |

---

## Java Action-Kit

### Basic Example

```java
@Component
public class RoutingTransformAction extends TransformAction<RoutingParameters> {

    private final ArtifactHandle routingRules;

    public RoutingTransformAction(
            // cacheable is true because we call routingRules.getBytes() on every transform call
            @PluginArtifact(
                artifactName = "routing-rules.json",
                defaultResource = "defaults/routing-rules.json",
                cacheable = true
            )
            ArtifactHandle routingRules) {
        super("Route input based on configurable rules");
        this.routingRules = routingRules;
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                        RoutingParameters params,
                                        TransformInput input) {
        RoutingConfig config = parseJson(routingRules.getBytes(), RoutingConfig.class);
        String region = config.resolveRegion(input.content());
        context.addMetadata("targetRegion", region);
        return input.toTransformResult(context);
    }
}
```

Place the default at `src/main/resources/defaults/routing-rules.json` (classpath resource).

### `@PluginArtifact` Reference

```java
public MyAction(
        @PluginArtifact(
            artifactName    = "my-artifact",  // required
            alias           = "active",       // default
            scope           = PLUGIN_FIRST,   // default
            preload         = true,           // default — eager fetch; false = lazy on first access
            cacheable       = false,          // default — always fetch fresh; true = cache in memory
            defaultResource = ""              // classpath resource to seed on first deploy
        )
        ArtifactHandle handle) {
    ...
}
```

### `ArtifactHandle` API

| Method | Return type | Description |
|---|---|---|
| `getArtifactName()` | `String` | Identifier as registered |
| `getBytes()` | `byte[]` | Artifact content |
| `getInputStream()` | `InputStream` | Streaming content (caller must close) |
| `getString()` | `String` | UTF-8 decoded content |
| `getString(charset)` | `String` | Decoded with given charset |
| `getVersion()` | `String` | Concrete version, e.g. `"20240101-120000"` |
| `getDescription()` | `String` | Human-readable description, or `null` |
| `getContentType()` | `String` | MIME type |
| `getSha256()` | `String` | SHA-256 digest |
| `getManifest()` | `ArtifactManifest` | Version, description, contentType, sha256, labels |
| `getLabels()` | `Map<String,String>` | User-defined labels |
| `isReady()` | `boolean` | `true` if artifact resolved successfully |
| `isDefault()` | `boolean` | `true` if seeded from classpath default |
| `isShared()` | `boolean` | `true` if served from shared namespace |
| `getSourcePrefix()` | `String` | `"shared"` or `"plugins/{owner}"` |
| `refresh()` | `void` | Force re-download |
| `onUpdate(Consumer<VersionChangedEvent>)` | `void` | Register callback (with event) |
| `onUpdate(Runnable)` | `void` | Register callback (without event) |

### Reacting to Version Changes

```java
@Component
public class RuleEngineAction extends TransformAction<RuleParameters> {

    private final ArtifactHandle rulesHandle;

    // volatile because the callback fires on the listener thread
    // while transform() runs on action-kit worker threads
    private volatile RuleEngine ruleEngine;

    public RuleEngineAction(
            @PluginArtifact(
                artifactName = "rules.yaml",
                defaultResource = "defaults/rules.yaml"
            )
            ArtifactHandle rulesHandle) {
        super("Evaluate rules against input");
        this.rulesHandle = rulesHandle;
        ruleEngine = RuleEngine.compile(rulesHandle.getBytes());
        rulesHandle.onUpdate(event -> {
            log.info("Rules updated: {} → {}", event.previousVersion(), event.newVersion());
            ruleEngine = RuleEngine.compile(rulesHandle.getBytes());
        });
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                        RuleParameters params,
                                        TransformInput input) {
        return ruleEngine.evaluate(context, input);
    }
}
```

Mark state derived from the artifact `volatile` (or use `AtomicReference`) so worker threads see
the updated value immediately after the callback assigns it.

#### Extracting to a service when processing is expensive or the artifact is needed across actions

The action framework can run multiple instances of the same action class in parallel (one per
configured thread). Each instance registers its own `onUpdate` callback. When a new version
arrives, all callbacks fire sequentially on the listener thread — meaning an expensive operation
like compiling a rule engine would run once per instance.

Move the artifact handle, derived state, and callback into a `@Service` singleton so the
expensive work runs exactly once per update:

```java
@Service
public class RuleEngineService {

    private final ArtifactHandle rulesHandle;
    private volatile RuleEngine ruleEngine;

    public RuleEngineService(
            @PluginArtifact(
                artifactName = "rules.yaml",
                defaultResource = "defaults/rules.yaml"
            )
            ArtifactHandle rulesHandle) {
        this.rulesHandle = rulesHandle;
        ruleEngine = RuleEngine.compile(rulesHandle.getBytes());
        rulesHandle.onUpdate(event -> {
            log.info("Rules updated: {} → {}", event.previousVersion(), event.newVersion());
            ruleEngine = RuleEngine.compile(rulesHandle.getBytes());
        });
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }
}

@Component
public class RuleEngineAction extends TransformAction<RuleParameters> {

    private final RuleEngineService ruleEngineService;

    public RuleEngineAction(RuleEngineService ruleEngineService) {
        super("Evaluate rules against input");
        this.ruleEngineService = ruleEngineService;
    }

    @Override
    public TransformResultType transform(ActionContext context,
                                        RuleParameters params,
                                        TransformInput input) {
        return ruleEngineService.getRuleEngine().evaluate(context, input);
    }
}
```

No matter how many action instances are running, the rule engine compiles once per version update
and all instances immediately see the new engine via the shared service.

### Requiring an Externally-Provided Artifact

When there is no sensible default, omit `defaultResource`. Startup fails with a clear error until
the artifact is uploaded.

```java
@Component
public class ClassificationAction extends TransformAction<ClassificationParameters> {

    private final ArtifactHandle model;
    private volatile Classifier classifier;

    public ClassificationAction(
            @PluginArtifact(artifactName = "classification-model.bin")
            ArtifactHandle model) {
        super("Classify input using ML model");
        this.model = model;
        classifier = Classifier.load(model.getInputStream());
        log.info("Model loaded: version={}, sha256={}",
                model.getVersion(), model.getSha256());
        model.onUpdate(event -> {
            classifier = Classifier.load(model.getInputStream());
        });
    }
}
```

### Multiple Artifacts

```java
@Component
public class EnrichmentAction extends TransformAction<EnrichmentParameters> {

    // Ships with a default; operators can customize later
    @PluginArtifact(
        artifactName = "enrichment-config.json",
        defaultResource = "defaults/enrichment-config.json"
    )
    private ArtifactHandle config;

    // Must be provided by the operator — no default makes sense
    @PluginArtifact(artifactName = "customer-extract.csv")
    private ArtifactHandle customerData;

    // Platform-wide lookup table in the shared namespace
    @PluginArtifact(
        artifactName = "geo-regions.csv",
        scope = ArtifactScope.SHARED_ONLY
    )
    private ArtifactHandle geoRegions;

    private volatile EnrichmentService service;

    @PostConstruct
    public void initialize() {
        rebuild();
        // Each artifact can be updated independently; all trigger a rebuild
        config.onUpdate(this::rebuild);
        customerData.onUpdate(this::rebuild);
        geoRegions.onUpdate(this::rebuild);
    }

    private void rebuild() {
        service = new EnrichmentService(
            parseJson(config.getBytes(), EnrichmentConfig.class),
            CsvParser.parse(customerData.getBytes()),
            CsvParser.parse(geoRegions.getBytes())
        );
    }
}
```

### Pinning to a Specific Version

Setting `alias` to a concrete version string pins the handle to that exact version. Pinned entries
are never considered stale — `getBytes()` always returns the same bytes without re-downloading.

```java
public MyAction(
        // Always uses version "1.0.0" regardless of what 'active' points to
        @PluginArtifact(artifactName = "stable-model.bin", alias = "1.0.0")
        ArtifactHandle pinnedModel) {
    ...
}
```

### Programmatic Access

For dynamic artifact names or conditional loading, inject `ArtifactRegistryService` directly. This
bean is scoped to the plugin's own namespace and the shared namespace:

```java
@Autowired
private ArtifactRegistryService artifactRegistry;

// Fetch the active version of a plugin-scoped artifact
ArtifactHandle handle = artifactRegistry.getArtifact("routing-rules.json", "active");
byte[] bytes = handle.getBytes();

// Fetch a shared artifact by alias
ArtifactHandle shared = artifactRegistry.getSharedArtifact("geo-regions.csv", "active");

// Publish a new version from action code
artifactRegistry.publishArtifact(
    "output-rules.json",
    new ArtifactManifest(null, "Generated rules", "application/json", null, null),
    new ByteArrayInputStream(rulesJson));
```

`getArtifact()` and `getSharedArtifact()` throw `IOException` if the artifact is not found.
`publishArtifact()` sets the `active` alias by default; pass `makeActive = false` to suppress.

### Go

The Go action kit provides artifact access through the `ArtifactClient`, available on the `ActionContext`. Go does not use annotations — artifacts are accessed programmatically.

#### Fetching Artifacts

```go
func (a *MyAction) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
    // Fetch a plugin-scoped artifact (cached by default)
    fetched, err := input.ActionContext.ArtifactClient.GetArtifact(ctx, "routing-rules.json", "active")
    if err != nil {
        return actionkit.NewErrorResult(input.ActionContext, "Failed to load artifact"), nil
    }

    var config RoutingConfig
    json.Unmarshal(fetched.Data, &config)

    // Fetch a shared artifact
    shared, err := input.ActionContext.ArtifactClient.GetSharedArtifact(ctx, "geo-regions.csv", "active")

    // Stream a large artifact without buffering
    stream, err := input.ActionContext.ArtifactClient.StreamArtifact(ctx, "large-model.bin", "active")
    if err == nil {
        defer stream.Reader.Close()
        // read from stream.Reader
    }

    // ...
}
```

#### Reacting to Version Changes

Register callbacks at plugin startup to react when artifacts are updated. The `OnUpdateFetch` helper fetches the artifact and passes it to your callback whenever a new version is published:

```go
type MyAction struct {
    config actionkit.SafeStringMap
}

func (a *MyAction) ConfigureArtifacts(client *actionkit.ArtifactClient) {
    client.OnUpdateFetchWithDefault("config.json", "",
        actionkit.ArtifactDefault{
            Data:        []byte(`{"key": "default"}`),
            ContentType: "application/json",
            Description: "Default configuration",
        },
        func(fetched *actionkit.FetchedArtifact, err error) {
            if err != nil {
                log.Printf("Failed to load config: %v", err)
                return
            }
            var cfg map[string]string
            json.Unmarshal(fetched.Data, &cfg)
            a.config.Store(cfg)
        })
}
```

Plugin-level artifact watching (outside of actions) uses `WatchArtifact` in an `init()` function:

```go
func init() {
    actionkit.WatchArtifact("global-config.yaml", &actionkit.ArtifactDefault{
        Data:        []byte("config: []"),
        ContentType: "application/yaml",
        Description: "Global configuration",
    }, func(fetched *actionkit.FetchedArtifact, err error) {
        if err != nil {
            log.Printf("Failed to load config: %v", err)
            return
        }
        log.Printf("Config updated (version=%s)", fetched.Manifest.Version)
    })
}
```

### C++

The C++ action kit provides artifact access through the `ArtifactClient`, available on the `ActionContext`. Like Go, C++ uses programmatic access rather than annotations.

#### Fetching Artifacts

```cpp
// Fetch a plugin-scoped artifact (cached by default)
auto fetched = context.artifact_client->get_artifact("routing-rules.json", "active");
auto config = nlohmann::json::parse(fetched.data);

// Fetch a shared artifact
auto shared = context.artifact_client->get_shared_artifact("geo-regions.csv", "active");
```

#### Reacting to Version Changes

Actions that implement the `ArtifactAware` concept can register callbacks at startup:

```cpp
class MyAction {
public:
    void configure_artifacts(deltafi::ArtifactClient& client) {
        client.on_update_fetch_with_default("config.json",
            deltafi::ArtifactDefault(R"({"key": "default"})", "application/json", "Default configuration"),
            [this](const deltafi::FetchedArtifact* fetched, const std::string& err) {
                if (err.empty() && fetched) {
                    auto cfg = nlohmann::json::parse(fetched->data);
                    config_.store(cfg.get<std::map<std::string, std::string>>());
                }
            });
    }

    // ... action methods use config_
private:
    deltafi::SafeStringMap config_;
};
```

Plugin-level artifact watching (outside of actions) uses the `DELTAFI_WATCH_ARTIFACT` macro:

```cpp
DELTAFI_WATCH_ARTIFACT(
    "global-config.yaml",
    deltafi::ArtifactDefault("config: []", "application/yaml", "Global configuration"),
    [](const deltafi::FetchedArtifact* fetched, const std::string& err) {
        if (err.empty() && fetched) {
            spdlog::info("Config updated (version={})", fetched->manifest.version);
        }
    }
)
```

#### Publishing Artifacts

```cpp
std::vector<uint8_t> data(rules_json.begin(), rules_json.end());
auto resp = context.artifact_client->publish_artifact("output-rules.json", data, "application/json");

// Publish to shared namespace
resp = context.artifact_client->publish_shared_artifact("shared-data.csv", data, "text/csv");
```

#### Publishing Artifacts

```go
data := bytes.NewReader(rulesJSON)
resp, err := input.ActionContext.ArtifactClient.PublishArtifact(ctx, "output-rules.json", data, "application/json", nil)

// Publish to shared namespace
resp, err = input.ActionContext.ArtifactClient.PublishSharedArtifact(ctx, "shared-data.csv", data, "text/csv", nil)
```

### Streaming Large Artifacts

`getArtifact()` buffers the entire artifact in the JVM heap. For large binary artifacts — ML
models, embedding matrices, large lookup tables — use the streaming variants instead. These open
a direct HTTP connection to the registry and return an `InputStream` without copying bytes into
memory:

```java
// Load a 2 GB ONNX model directly into the runtime — no heap buffer
try (ArtifactRegistryService.ArtifactStream s =
        artifactRegistry.streamArtifact("large-model.onnx")) {
    session = onnxEnvironment.createSession(s.stream());
}

// Pipe a large lookup table to disk without buffering
try (ArtifactRegistryService.ArtifactStream s =
        artifactRegistry.streamArtifact("embeddings.bin")) {
    log.info("Streaming {} bytes, content-type: {}",
            s.manifest().size(), s.manifest().contentType());
    Files.copy(s.stream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
}

// Shared artifact — same pattern
try (ArtifactRegistryService.ArtifactStream s =
        artifactRegistry.streamSharedArtifact("geo-regions.csv")) {
    geoIndex = GeoIndex.load(s.stream());
}
```

The `ArtifactStream` implements `Closeable` and **must** be closed when done (the underlying HTTP
connection stays open until then). Streaming bypasses the shared cache — each call opens a new
connection. Use `getArtifact()` for small, frequently-accessed artifacts and `streamArtifact()`
only when memory pressure is a concern.

---

## Python Action-Kit

### Basic Example

```python
import json
from deltafi.transform import TransformAction
from deltafi.artifact import Artifact

class RoutingTransformAction(TransformAction):

    routing_rules = Artifact("routing-rules.json",
                             default_resource="defaults/routing-rules.json")

    def transform(self, context, params, transform_input):
        config = json.loads(self.routing_rules.get_string())
        region = resolve_region(config, transform_input.content)
        context.add_metadata("targetRegion", region)
        return transform_input.to_transform_result(context)
```

`default_resource` is a file path relative to the working directory (not a classpath resource).

### `Artifact` Descriptor Reference

```python
from deltafi.artifact import Artifact, ArtifactScope

handle = Artifact(
    "my-artifact",                     # required
    alias="active",                    # default
    scope=ArtifactScope.PLUGIN_FIRST,  # default
    cacheable=False,                   # default — always fetch fresh; True = cache in memory
    preload=True,                      # default — resolve at startup; False = defer to first access
    default_resource=None,             # file path to seed on first deploy
)
```

### `ArtifactHandle` API

| Method | Return type | Description |
|---|---|---|
| `get_artifact_name()` | `str` | Identifier as registered |
| `get_bytes()` | `bytes` | Artifact content |
| `get_input_stream()` | `io.BytesIO` | Artifact content as a stream |
| `get_string()` | `str` | UTF-8 decoded content |
| `get_string(encoding)` | `str` | Decoded with given encoding |
| `get_version()` | `str` | Concrete version, e.g. `"20240101-120000"` |
| `get_description()` | `str` | Human-readable description, or `None` |
| `get_content_type()` | `str` | MIME type |
| `get_sha256()` | `str` | SHA-256 digest |
| `get_manifest()` | `ArtifactManifest` | Version, description, content_type, sha256, labels |
| `get_labels()` | `dict[str, str]` | User-defined labels |
| `is_ready()` | `bool` | `True` if artifact resolved successfully |
| `is_default()` | `bool` | `True` if seeded from `default_resource` |
| `is_shared()` | `bool` | `True` if served from shared namespace |
| `get_source_prefix()` | `str` | `"shared"` or `"plugins/{owner}"` |
| `refresh()` | `None` | Force re-download |
| `stream()` | `ArtifactStream` | Streaming download (context manager, bypasses cache) |
| `on_update(callback)` | `ArtifactHandle` | Register callback; accepts `VersionChangedEvent` or zero args |

### Reacting to Version Changes

```python
import logging
from deltafi.transform import TransformAction
from deltafi.artifact import Artifact

logger = logging.getLogger(__name__)

class RuleEngineAction(TransformAction):

    rules = Artifact("rules.yaml", default_resource="defaults/rules.yaml",
                     cacheable=True)

    def __init__(self):
        super().__init__('Evaluate rules against input')
        self._rule_engine = RuleEngine.compile(self.rules.get_bytes())
        self.rules.on_update(self._reload)

    def _reload(self, event):
        logger.info("Rules updated: %s → %s", event.previous_version, event.new_version)
        self._rule_engine = RuleEngine.compile(self.rules.get_bytes())

    def transform(self, context, params, transform_input):
        return self._rule_engine.evaluate(context, transform_input)
```

The callback may accept a `VersionChangedEvent` argument, or take no arguments at all. The
callback fires after the cache is refreshed, so `get_bytes()` inside the callback returns the
new content.

### Requiring an Externally-Provided Artifact

When there is no sensible default, omit `default_resource`. Startup fails with a clear error until
the artifact is uploaded.

```python
class ClassificationAction(TransformAction):

    model = Artifact("classification-model.bin")

    def __init__(self):
        super().__init__('Classify input using ML model')
        self._classifier = Classifier.load(self.model.get_bytes())
        logger.info("Model loaded: version=%s sha256=%s",
                    self.model.get_version(), self.model.get_sha256())
        self.model.on_update(lambda event:
            setattr(self, '_classifier', Classifier.load(self.model.get_bytes())))
```

### Multiple Artifacts

```python
from deltafi.artifact import Artifact, ArtifactScope

class EnrichmentAction(TransformAction):

    # Ships with a default; operators can customize later
    config        = Artifact("enrichment-config.json",
                             default_resource="defaults/enrichment-config.json")
    # Must be provided by the operator — no default makes sense
    customer_data = Artifact("customer-extract.csv")
    # Platform-wide lookup table in the shared namespace
    geo_regions   = Artifact("geo-regions.csv", scope=ArtifactScope.SHARED_ONLY)

    def __init__(self):
        super().__init__('Enrich input with external data')
        self._rebuild()
        # Each artifact can be updated independently; all trigger a rebuild
        self.config.on_update(self._rebuild)
        self.customer_data.on_update(self._rebuild)
        self.geo_regions.on_update(self._rebuild)

    def _rebuild(self):
        self._service = EnrichmentService(
            json.loads(self.config.get_string()),
            parse_csv(self.customer_data.get_bytes()),
            parse_csv(self.geo_regions.get_bytes()),
        )
```

Startup completes once all artifacts without a `default_resource` have been resolved. Each
artifact can be updated independently and the callback rebuilds the service from all current
versions.

### Streaming Large Artifacts

`get_bytes()` buffers the entire artifact in memory. For large binary artifacts — ML models,
embedding matrices, large lookup tables — use `stream()` instead. It opens a direct HTTP
connection to the registry and lets you consume the response body without loading it all at once.
Each call to `stream()` bypasses the shared cache and opens a new connection.

```python
# Load a large ONNX model without buffering it in memory
with self.model.stream() as s:
    session = onnx_runtime.create_session(s.read())

# Pipe a large file to disk in chunks
with self.embeddings.stream() as s:
    with open("/data/embeddings.bin", "wb") as f:
        for chunk in s.iter_content(chunk_size=65536):
            f.write(chunk)
```

`ArtifactStream` **must** be used as a context manager — the underlying HTTP connection stays
open until it is closed. `read()` reads all remaining bytes (equivalent to `get_bytes()`);
`iter_content(chunk_size)` iterates in chunks without ever holding the full content in memory.

### Lazy Loading

By default, artifact handles are resolved (and the artifact downloaded) at plugin startup. Set
`preload=False` to defer resolution to the first time the handle is actually accessed. This is
useful for optional artifacts or those needed only on certain code paths.

```python
class AnalysisAction(TransformAction):

    # Only downloaded when first accessed, not at startup
    optional_model = Artifact("optional-model.bin", preload=False)

    def transform(self, context, params, transform_input):
        if params.use_model:
            result = self.optional_model.get_bytes()  # resolved here on first call
            ...
```

`on_update` callbacks registered in `__init__` before the handle is first accessed are buffered
and forwarded automatically once the handle resolves.

---

## Edge Cases and Operator Guidance

### PLUGIN_FIRST: plugin-specific version added after startup

When a plugin starts with `scope = PLUGIN_FIRST` and only a shared artifact exists, the cache
resolves from the shared namespace. When an operator later publishes a plugin-specific version of
the same artifact, a version-change notification arrives and the framework automatically promotes
the cached entry to serve the plugin-specific bytes — no restart required. Registered callbacks
fire normally after the promotion.

If the plugin was offline when the notification was sent and misses it, the background poller will
not detect the new plugin-specific version on its own (it polls the currently-cached namespace
only). In that case a pod restart will pick up the plugin-specific version correctly on the next
startup resolve.

### Deleted artifact: graceful degradation

Artifact deletion does not publish a version-change notification. If an artifact is deleted from
the registry while a plugin has it cached, the plugin continues serving the cached bytes
indefinitely.

When the background poller runs and finds the artifact absent from the registry listing it logs a
`WARN` message and retains the cached bytes. If a version-change event is received that triggers
a re-download and the artifact is gone, the same warning is logged and the old bytes are kept.

In either case the plugin operates normally using the last-known content. To restore normal
registry-backed operation: either re-upload a replacement artifact or restart the plugin pod
(which will fail fast if the artifact is truly gone and no default resource is configured).

---

## Managing Artifacts with the TUI

All artifact operations require `--owner`, which is either the plugin name or `shared`.

### Publish

```bash
# Version defaults to a server-generated timestamp; --name defaults to the filename
deltafi artifacts publish --owner my-plugin ./routing-rules.json
deltafi artifacts publish --owner my-plugin ./routing-rules.json \
    --name routing-rules --version 2.0.0 \
    --description "Q2 routing update" \
    --label env=prod

# Publish without updating the active alias (e.g. for staged rollout)
deltafi artifacts publish --owner my-plugin ./routing-rules.json --no-active
```

### List and Inspect

```bash
# List all artifacts with their active version
deltafi artifacts list --owner my-plugin

# View all published versions of a specific artifact
deltafi artifacts versions --owner my-plugin routing-rules
```

### Download

```bash
# Download the active version
deltafi artifacts download --owner my-plugin routing-rules active

# Download a specific version
deltafi artifacts download --owner my-plugin routing-rules 2.0.0 -o rules.json

# Download by custom alias
deltafi artifacts download --owner my-plugin routing-rules stable
```

### Rollback

Move the `active` alias back to a prior version without re-uploading anything:

```bash
deltafi artifacts rollback --owner my-plugin routing-rules 1.0.0
```

### Aliases

Rollback is shorthand for moving `active`. The `set-alias` command lets you move any alias:

```bash
# Point 'stable' at a specific version
deltafi artifacts set-alias --owner my-plugin routing-rules stable 2.0.0

# Remove an alias (the 'active' alias cannot be removed)
deltafi artifacts remove-alias --owner my-plugin routing-rules canary
```

### Delete

```bash
# Delete a specific version (the active version cannot be deleted; roll back first)
deltafi artifacts delete --owner my-plugin routing-rules 1.0.0

# Delete all versions, aliases, and configuration for an artifact
deltafi artifacts delete --owner my-plugin routing-rules

# Skip the confirmation prompt
deltafi artifacts delete --owner my-plugin routing-rules --force
```

### Purge (Retention Policy)

Set a retention policy to automatically clean up old versions:

```bash
# Keep the 3 most recent versions; remove versions older than 7 days (10080 minutes)
deltafi artifacts purge-config set --owner my-plugin routing-rules \
    --keep-latest 3 --max-age-minutes 10080

# View the current policy
deltafi artifacts purge-config get --owner my-plugin routing-rules

# Trigger a purge immediately (without waiting for the scheduled run)
deltafi artifacts purge --owner my-plugin routing-rules
```

**Default policy:** `--keep-latest 5`, `--max-age-minutes 43200` (30 days). Both criteria must be
met for a version to be eligible — outside the most-recent 5 **and** older than 30 days. The
current active version is always protected regardless of age or position.

---

## REST API

The TUI wraps all registry endpoints, but you can call them directly from scripts or CI/CD
pipelines. Full request/response documentation is in the [Artifact Registry REST API](api/rest/artifact-registry.md).

---

## Configuration

### External Artifact Storage

By default, artifact storage shares the same S3-compatible endpoint (the local s3proxy instance) as
content storage. When you want to separate concerns — for example, to store artifacts in a managed
S3 bucket while keeping content storage local — you can point artifact storage at an independent
endpoint.

#### Kubernetes

Set `deltafi.artifactStorage.url` to the external endpoint and `enable.local_artifact_storage` to
`false` in your `site/values.yaml`:

```yaml
enable:
  local_artifact_storage: false   # do not run s3proxy for artifacts

deltafi:
  artifactStorage:
    url: https://s3.us-east-1.amazonaws.com
    bucketName: my-artifact-bucket
    secret: artifact-storage-credentials
```

Setting `local_artifact_storage: false` prevents DeltaFi from trying to create the bucket in the
local storage instance. The local storage pod is still deployed as long as `enable.local_object_storage` is true
(for content storage). If you move both content and artifact storage to external endpoints, set
both flags to `false` and the storage pod will not be created.

**Credentials** — create a Kubernetes Secret with two keys, `accessKey` and `secretKey`, then
reference the secret name in `deltafi.artifactStorage.secret`. The Helm chart injects the
credentials into core pods automatically.

```bash
kubectl create secret generic artifact-storage-credentials \
  --namespace deltafi \
  --from-literal=accessKey=AKIAIOSFODNN7EXAMPLE \
  --from-literal=secretKey=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

#### Docker Compose (standalone)

Set `url`, `bucketName`, and `local_artifact_storage` in `site/values.yaml` as above, then run
`deltafi update` to apply.

**Credentials** — add them to your installation's `config/secrets/minio.env` file, which is
already loaded into the core containers and created with `0600` permissions. The path is
`<installDirectory>/config/secrets/minio.env`, where `installDirectory` is defined in
`~/.deltafi/config.yaml`.

```bash
INSTALL_DIR=$(grep 'installDirectory:' ~/.deltafi/config.yaml | awk '{print $2}')
SECRETS_FILE="${INSTALL_DIR}/config/secrets/minio.env"

cat >> "$SECRETS_FILE" << 'EOF'
ARTIFACT_STORAGE_ACCESS_KEY='AKIAIOSFODNN7EXAMPLE'
ARTIFACT_STORAGE_SECRET_KEY='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
EOF
```

Run `deltafi up` after updating the file so the new credentials are picked up by the
running containers.

---

### Cache tuning

The in-process artifact cache can be tuned via application properties:

```yaml
deltafi:
  artifact-registry:
    cache:
      max-entries: 100          # Maximum number of cached artifacts (0 = unlimited)
      max-bytes: 268435456      # Maximum total cache size in bytes (0 = unlimited, default: 256 MB)
      poll-interval-seconds: 300  # How often to poll the registry for alias drift (0 = disabled)
```

When a limit is exceeded, the least-recently-fetched non-pinned entries are evicted first.
Entries pinned to a concrete version (see [Pinning to a Specific Version](#pinning-to-a-specific-version))
are skipped during eviction.

The cache is kept current through two mechanisms:
- **Push** — version-change notifications arrive immediately and update the affected entry.
- **Poll** — a background thread queries the registry every `poll-interval-seconds` (default 5 minutes) to compare each alias-based entry against the registry's current mapping. Any entry that has drifted is re-fetched and its registered callbacks are fired. This is a safety net for missed notifications.

Multiple injection points that resolve the same artifact share a single cache entry. By default (`cacheable = false`), every `getBytes()` call downloads fresh bytes and updates the shared cache entry as a side effect, so any co-located handles with `cacheable = true` also stay current. Set `cacheable = true` to retain bytes in memory and only re-fetch on version-change notifications or background poll drift detection.

---

## How It Works

Artifacts are stored in a single S3-compatible bucket with path-based isolation:

```
plugins/{pluginName}/{artifactName}/{version}/blob            # blob
plugins/{pluginName}/{artifactName}/{version}/manifest.json   # manifest
plugins/{pluginName}/{artifactName}/_aliases/active           # alias → version pointer
plugins/{pluginName}/{artifactName}/_config/purge.json        # purge config
shared/{artifactName}/...
```

Each artifact version is an immutable blob with an accompanying JSON manifest containing metadata,
labels, and a SHA-256 digest. Alias files contain only the version string they point to.

When a new version is published (or `set-alias` / `rollback` is called), a `VersionChangedEvent`
is broadcast via the platform message bus. Every plugin node subscribed to that artifact:

1. Re-downloads the new version into its local cache
2. Atomically replaces the cached bytes
3. Fires any registered update callbacks

In-flight action invocations are never interrupted — they complete with the bytes they already
hold. Callbacks fire after the cache is updated, so `getBytes()` inside the callback returns the
new content.
