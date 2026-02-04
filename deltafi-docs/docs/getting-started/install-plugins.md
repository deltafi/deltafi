# Extending DeltaFi with Plugins

DeltaFi comes with a core plugin that contains useful actions, passthrough flows, and a smoke test.  DeltaFi is extensible through a plugin system that allows Java and Python implementation of actions and preconfigured flows.  Plugins consist of one or more of the following:
- Actions which perform discrete operations on a DeltaFile
- Flows that incorporate a series of actions (delivered by the plugin or by other plugins)
- Plugin variables that allow flow configuration for flows delivered by the plugin
- Integration tests for the actions and flows

Plugins can be installed to extend the capabilities of DeltaFi.  The DeltaFi community publishes
publicly available plugins and custom plugins can be developed using the DeltaFi Action Kit.
For developing your own plugins, see the [Plugin Developer's Guide](/getting-started/for-plugin-developers).

## Installing a plugin

A plugin is published as a docker container and can be installed from the DeltaFi TUI or from the DeltaFi UI.

The following TUI command will install the deltafi-xml plugin that is available on Docker Hub:

```bash
deltafi plugin install docker.io/deltafi/deltafi-xml:__VERSION__
```

Additional commands that are useful in managing plugins:

```bash
deltafi plugin list # List all installed plugins
deltafi plugin describe <plugin-name> # Describe the plugin including actions, variables
deltafi plugin uninstall <plugin-name> # Remove a plugin
```

## Installing Plugins from Private Registries

If you need to install plugins from a private or authenticated Docker registry (such as AWS ECR, Azure Container Registry, or a private Docker registry), you must first configure Docker authentication credentials.

**Note**: Private registry authentication is only supported when running DeltaFi in **Compose mode**. Kubernetes deployments should use image pull secrets instead (see the `--secret` flag in `deltafi plugin install --help`).

### Configuring Registry Authentication

Use the `docker-login` command to configure authentication for a private registry:

```bash
deltafi docker-login <registry> --username <username> [--password-stdin]
```

**Best Practice**: Use `--password-stdin` to provide the password securely without exposing it in shell history:

```bash
# AWS ECR example
aws ecr get-login-password --region us-east-1 | \
  deltafi docker-login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Generic registry with environment variable
echo "$REGISTRY_PASSWORD" | deltafi docker-login --username myuser \
  --password-stdin registry.example.com
```

After configuring authentication, you must restart DeltaFi for the changes to take effect:

```bash
deltafi down && deltafi up
```

### Installing from the Authenticated Registry

Once authentication is configured and DeltaFi is restarted, you can install plugins from the private registry:

```bash
# Install from AWS ECR
deltafi plugin install 123456789012.dkr.ecr.us-east-1.amazonaws.com/my-plugin:1.0.0

# Install from private registry
deltafi plugin install registry.example.com/my-company/my-plugin:2.1.0
```

**Note**: The registry hostname in the image name must match the registry configured with `docker-login`.

For complete details on the `docker-login` command, see the [TUI Reference](/operating/TUI#docker-login).
