# Site Configuration

DeltaFi uses a layered configuration system that allows operators to customize their deployment without modifying core files. Site-specific configurations are stored in the `site/` directory within your DeltaFi installation directory.

## Directory Structure

When you run `deltafi up`, the TUI creates and manages the following directory structure:

```
<deltafi-install-dir>/
├── config/                    # Generated configuration files
│   ├── common.env             # Shared environment variables
│   ├── startup.env            # Startup configuration
│   ├── nginx.env              # Nginx configuration
│   ├── dirwatcher.env         # Dirwatcher configuration
│   ├── values.yaml            # Generated merged values
│   └── secrets/               # System secrets (auto-generated)
│       ├── grafana.env        # Grafana admin credentials
│       ├── minio.env          # Object storage access credentials
│       ├── postgres.env       # PostgreSQL credentials
│       ├── valkey.env         # Valkey (Redis) credentials
│       └── ssl.env            # SSL configuration
├── site/                      # User-managed configuration
│   ├── values.yaml            # Site-specific value overrides
│   ├── compose.yaml           # Docker Compose overrides (Compose mode)
│   └── templates/             # Custom Helm templates (Kubernetes mode)
└── data/                      # Runtime data (logs, storage, etc.)
```

## Site Values (site/values.yaml)

The `site/values.yaml` file allows you to override default configuration values. This file is merged with the default values when running `deltafi up`.

### Common Configuration Options

```yaml
# site/values.yaml
deltafi:
  core:
    ssl:
      secret: ssl-secret
  core_worker:
    enabled: true
    replicas: 2           # Number of core worker replicas
  core_actions:
    replicas: 2           # Number of core-actions replicas
  auth:
    mode: disabled        # Options: basic, cert, disabled
    secret: auth-secret
  api:
    workers: 8            # Number of API worker threads
  dirwatcher:
    enabled: true
    workers: 20           # Number of dirwatcher threads
    maxFileSize: 4294967296  # Max file size (4GB default)
  egress_sink:
    enabled: true
    drop_metadata: false
  plugins:
    ssl:
      secret: ssl-secret

ingress:
  domain: local.deltafi.org
  tls:
    enabled: false
    secrets:
      default: ssl-secret
  ui:
    http_port: 80
    https_port: 443
```

## Docker Compose Configuration

When running DeltaFi in Compose mode, you can customize the Docker Compose configuration through the `site/compose.yaml` file.

### site/compose.yaml

This file is merged with the base `docker-compose.yml` and allows you to:
- Add environment variables to existing services
- Expose ports to the host
- Add custom services
- Override service configurations

#### Adding Environment Variables to Services

```yaml
# site/compose.yaml
services:
  # Add environment variables to core services
  core-scheduler:
    environment:
      MY_CUSTOM_VAR: "value"

  # Add environment variables to plugins (service name = plugin image name)
  deltafi-stix:
    environment:
      STIX_API_KEY: "your-api-key"
      STIX_ENDPOINT: "https://api.example.com"
```

#### Exposing Ports

```yaml
# site/compose.yaml
services:
  deltafi-s3proxy:
    ports:
      - 9000:9000
```

#### Adding Custom Services

```yaml
# site/compose.yaml
services:
  dozzle:
    container_name: dozzle
    image: amir20/dozzle:latest
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    ports:
      - 8080:8080
```

### System Secrets

System secrets are stored in `config/secrets/` and are auto-generated on first run. These include credentials for:
- **grafana.env** - Grafana admin user and password
- **minio.env** - Object storage access keys
- **postgres.env** - PostgreSQL database credentials
- **valkey.env** - Valkey (Redis) password

::: warning
These files contain sensitive credentials and should be protected with appropriate file permissions. They are created with mode 0600 (owner read/write only).
:::

## Kubernetes Configuration

When running DeltaFi in Kubernetes mode, customization is done through Helm values and custom templates.

### site/values.yaml

Works the same as in Compose mode, but values are passed to Helm during installation.

### site/templates/

Custom Helm templates placed in this directory are merged with the DeltaFi chart during installation. This allows you to:
- Create additional Kubernetes resources (Secrets, ConfigMaps, Services, etc.)
- Patch existing DeltaFi deployments
- Add custom deployments that integrate with DeltaFi

#### Example: Adding Secrets for a Plugin

```yaml
# site/templates/my-plugin-secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: my-plugin-secrets
type: Opaque
stringData:
  API_KEY: "your-api-key"
  DB_PASSWORD: "your-password"
```

#### Example: Injecting Secrets into a Plugin

```yaml
# site/templates/my-plugin-env.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deltafi-stix  # Must match the plugin's deployment name
spec:
  template:
    spec:
      containers:
        - name: deltafi-stix
          envFrom:
            - secretRef:
                name: my-plugin-secrets
```

### Using Helm Values in Templates

Custom templates have full access to Helm template functions and the values from `site/values.yaml`:

```yaml
# site/templates/custom-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-custom-config
data:
  domain: {{ .Values.ingress.domain }}
  auth-mode: {{ .Values.deltafi.auth.mode }}
```

## Applying Configuration Changes

After modifying any site configuration files, run:

```bash
deltafi up
```

This will:
1. Merge your site values with defaults
2. Regenerate configuration files
3. Apply Docker Compose changes (Compose mode) or Helm changes (Kubernetes mode)
4. Restart affected services as needed

## Best Practices

1. **Version Control**: Keep your `site/` directory under version control (but exclude `config/secrets/`).

2. **Secrets Management**: For production deployments, consider using external secrets management solutions rather than storing credentials in plain text files.

3. **Backup**: Before major changes, back up your `site/` and `config/` directories.

4. **Documentation**: Document any custom configurations specific to your deployment in a README within the `site/` directory.
