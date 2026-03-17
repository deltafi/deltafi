# Multithreading in Action Kits

## Java

The Java Action Kit provides a feature that allows you to configure the number of threads per action type via properties.
This feature enhances performance and concurrency for the specified actions.

### Configuring Threads per Action

To configure the number of threads for a specific action, add an entry in your application.yaml file under the actions
namespace. For example, to set the number of threads for the org.deltafi.core.action.FilterEgressAction action to 2,
include the following configuration:

```yaml
actions:
  actionThreads:
    org.deltafi.core.action.FilterEgressAction: 2
```

By default, if an action does not have a specific thread count configuration, it will use 1 thread.

## Go

The Go action kit supports configurable worker counts per action. Set the worker count globally or per action.

### Global Worker Count

Set the `ACTION_THREADS` environment variable in your Dockerfile:

```dockerfile
ENV ACTION_THREADS=2
```

### Per-Action Worker Count

Set an environment variable using the pattern `ACTIONS_ACTIONTHREADS_{UPPERCASED_ACTION_NAME}`, where dots and hyphens in the action name are replaced with underscores:

```dockerfile
ENV ACTIONS_ACTIONTHREADS_ORG_DELTAFI_MY_PLUGIN_MYTRANSFORMACTION=4
```

Or set it programmatically before calling `RunPlugin`:

```go
plugin.SetWorkerCount("org.myorg.my-plugin.MyTransformAction", 4)
```

By default, if an action does not have a specific thread count configuration, it will use 1 worker goroutine.
