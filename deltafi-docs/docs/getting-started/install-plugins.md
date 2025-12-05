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
