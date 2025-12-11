# Plugin Operations

This guide covers the operational aspects of managing plugins in DeltaFi, including installation states, lifecycle management, and how plugin state affects data processing.

## Plugin Installation States

Plugins progress through several states during their lifecycle:

| State | Description |
|-------|-------------|
| **PENDING** | Plugin installation has been requested but not yet started |
| **INSTALLING** | Plugin deployment is in progress |
| **INSTALLED** | Plugin is fully deployed and ready to process data |
| **FAILED** | Plugin installation or startup failed |
| **REMOVING** | Plugin is being uninstalled |

## Plugin Lifecycle

### Installing a Plugin

Plugins can be installed through the UI or CLI. When you install a plugin:

1. The plugin enters **PENDING** state
2. DeltaFi pulls the Docker image and creates the deployment
3. The plugin transitions to **INSTALLING** while the container/pod starts
4. Once the plugin registers with core, it becomes **INSTALLED**
5. If startup fails, the plugin enters **FAILED** state

### Upgrading a Plugin

When upgrading a plugin to a new version:

1. The existing plugin remains **INSTALLED** while the new version is prepared
2. The new deployment is created with the updated image
3. Once the new version registers, it replaces the old version
4. Flows continue processing with the new plugin version

During upgrades, data arriving at flows owned by the plugin is automatically queued until the new version is ready.

### Disabling a Plugin

Plugins can be disabled without uninstalling them. A disabled plugin:

- Remains deployed but does not process data
- All flows from the plugin reject incoming data
- Can be re-enabled instantly without reinstallation

Use disable when you need to temporarily stop a plugin's processing without losing its configuration.

### Uninstalling a Plugin

When uninstalling a plugin:

1. The plugin enters **REMOVING** state
2. The container (Docker Compose) or deployment (Kubernetes) is deleted
3. Flow definitions from the plugin are removed
4. The plugin record is deleted from the database

## How Plugin State Affects Data Processing

Plugin state directly affects how data flows behave:

| Plugin State | Flow Behavior |
|--------------|---------------|
| **INSTALLED** (enabled) | Normal processing |
| **PENDING** | Data queues (like paused) |
| **INSTALLING** | Data queues (like paused) |
| **FAILED** | Data queues (like paused) |
| **REMOVING** | Data queues (like paused) |
| **Disabled** | Data rejected (like stopped) |

### Data Queueing During Plugin Operations

When a plugin is not ready (PENDING, INSTALLING, FAILED, or REMOVING):

- Incoming data at data sources is queued, not rejected
- Data published to transform flows owned by the plugin is queued
- Data routed to data sinks owned by the plugin is queued
- Once the plugin becomes ready, queued data automatically resumes processing

This ensures no data is lost during plugin installations, upgrades, or temporary failures.

### Data Rejection for Disabled Plugins

When a plugin is explicitly disabled:

- Data at data sources is rejected with an error
- Data cannot be published to disabled plugin's flows
- This is an intentional stop, similar to a stopped flow

## Monitoring Plugin Health

### Checking Plugin Status

View plugin status in the UI on the Plugins page, or use the CLI:

```bash
deltafi plugin list
```

### Managing Plugins via CLI

**Disable a plugin** (stops container but preserves configuration):
```bash
deltafi plugin disable <plugin-name>
```

**Enable a disabled plugin** (restarts the container):
```bash
deltafi plugin enable <plugin-name>
```

**Retry a failed installation**:
```bash
deltafi plugin retry <plugin-name>
```

**Rollback to the last successful version** (only available after a failed upgrade):
```bash
deltafi plugin rollback <plugin-name>
```

When a plugin is in FAILED state and a previous version was successfully installed, `deltafi plugin list` will show "FAILED (rollback available)" in the Status column.

### Troubleshooting: Kubernetes

**Plugin stuck in INSTALLING:**

```bash
# Check pod status
kubectl get pods -n deltafi

# View pod logs
kubectl logs -n deltafi <pod-name>

# Describe pod for events
kubectl describe pod -n deltafi <pod-name>
```

Common causes: image pull errors, resource constraints, startup crashes

**Plugin in FAILED state:**

```bash
# Check plugin pod logs
kubectl logs -n deltafi -l app=<plugin-name>

# Check for image pull issues
kubectl get events -n deltafi --field-selector reason=Failed
```

### Troubleshooting: Docker Compose

**Plugin stuck in INSTALLING:**

```bash
# List all containers including stopped ones
docker ps -a | grep deltafi

# View container logs
docker logs <container-name>

# Check container status
docker inspect <container-name> --format='{{.State.Status}}'
```

**Plugin in FAILED state:**

```bash
# View container logs for errors
docker logs <container-name>

# Check if image exists locally
docker images | grep <plugin-image>

# Try to pull the image manually
docker pull <plugin-image>
```

**Restarting a plugin container:**

```bash
# Restart a specific plugin container
docker restart <container-name>

# Force recreate if needed
docker stop <container-name> && docker rm <container-name>
# Then reinstall via UI or CLI
```

### Common Issues

**Flows not processing after plugin install:**
- Verify plugin state is INSTALLED (not PENDING/INSTALLING)
- Check that plugin is not disabled
- Verify flows are in RUNNING state

**Plugin image not found:**
- Verify the image name and tag are correct
- Check registry credentials if using a private registry
- Kubernetes: verify imagePullSecrets are configured
- Docker Compose: ensure you're logged into the registry (`docker login`)

## Relationship Between Plugin State and Flow State

Plugin state and flow state are independent:

- **Flow state** (RUNNING/PAUSED/STOPPED) is controlled by the user
- **Plugin state** (INSTALLED/PENDING/etc.) is controlled by the system

The effective behavior combines both:

| Flow State | Plugin State | Result |
|------------|--------------|--------|
| RUNNING | INSTALLED + enabled | Normal processing |
| RUNNING | Not ready | Data queues |
| RUNNING | Disabled | Data rejected |
| PAUSED | Any | Data queues |
| STOPPED | Any | Data rejected |

Users can change flow states while a plugin is being installed. Once the plugin is ready, flows resume based on their configured state.
