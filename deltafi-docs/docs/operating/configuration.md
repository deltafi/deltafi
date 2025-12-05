# Configuration

DeltaFi uses several configuration files to control system behavior. This document covers where they live and what they do.

## Configuration Files Overview

| File | Location | Purpose |
|------|----------|---------|
| `config.yaml` | `~/.deltafi/` | TUI settings (orchestration mode, directories, version) |
| `values.yaml` | `site/` | Compose mode: DeltaFi service configuration |
| `compose.yaml` | `site/` | Compose mode: Docker Compose overrides |
| `kind.values.yaml` | `site/` | KinD mode: Helm values overrides |
| `templates/` | `site/` | KinD/K8s: Custom Helm templates |

## TUI Configuration (`~/.deltafi/config.yaml`)

The TUI configuration file controls how the `deltafi` command operates. It's created by the installation wizard (`deltafi config`) and can be modified manually or by re-running the wizard.

### Location

- Default: `~/.deltafi/config.yaml`
- Override: Set `DELTAFI_CONFIG_PATH` environment variable

### Structure

```yaml
orchestrationMode: Compose    # Compose, Kind, or Kubernetes
deploymentMode: Deployment    # Deployment, PluginDevelopment, or CoreDevelopment
coreVersion: "2.38.0"         # DeltaFi version to run
installDirectory: /Users/me/deltafi
dataDirectory: /Users/me/deltafi/data
siteDirectory: /Users/me/deltafi/site
development:
  repoPath: /Users/me/deltafi/repos
  coreRepo: git@gitlab.com:deltafi/deltafi.git
```

### Fields

| Field | Description |
|-------|-------------|
| `orchestrationMode` | How DeltaFi is deployed: `Compose` (Docker Compose), `Kind` (Kubernetes in Docker), or `Kubernetes` |
| `deploymentMode` | Your role: `Deployment` (operator), `PluginDevelopment`, or `CoreDevelopment` |
| `coreVersion` | The DeltaFi version to run. Changed by `deltafi upgrade`. |
| `installDirectory` | Root directory where DeltaFi is installed (where the `deltafi` binary lives) |
| `dataDirectory` | Where runtime data is stored (databases, content, metrics) |
| `siteDirectory` | Where site-specific customizations live |
| `development.repoPath` | Where source repositories are cloned (for dev modes) |
| `development.coreRepo` | Git URL for the core DeltaFi repository |

### Modifying Configuration

```bash
# Re-run the configuration wizard
deltafi config

# Or edit directly
vim ~/.deltafi/config.yaml
```

## Site Configuration

The `site/` directory contains customizations that persist across upgrades. The files used depend on your orchestration mode.

### Compose Mode

#### `site/values.yaml`

Override DeltaFi service configuration:

```yaml
deltafi:
  core_worker:
    enabled: true
    replicas: 4
  core_actions:
    replicas: 2
  auth:
    mode: basic    # basic, cert, or disabled
  api:
    workers: 16
  dirwatcher:
    enabled: true
    maxFileSize: 4294967296  # 4GB
ingress:
  domain: local.deltafi.org
  tls:
    enabled: true
```

#### `site/compose.yaml`

Override Docker Compose settings directly:

```yaml
services:
  deltafi-minio:
    ports:
      - 9000:9000  # Expose MinIO to host
  deltafi-core:
    environment:
      - JAVA_OPTS=-Xmx4g
```

### KinD Mode

#### `site/kind.values.yaml`

Override Helm values for KinD deployments:

```yaml
deltafi:
  core:
    resources:
      requests:
        memory: "2Gi"
```

#### `site/templates/`

Add custom Kubernetes resources by placing Helm templates in this directory. Templates have full access to the DeltaFi chart's helpers and values.

## Applying Configuration Changes

After modifying site configuration files:

```bash
deltafi up        # Apply changes
deltafi up --force  # Force restart all services
```

After modifying `~/.deltafi/config.yaml`:

```bash
deltafi config    # Re-run wizard to validate
# Or just run any deltafi command - it reads config on each invocation
```
