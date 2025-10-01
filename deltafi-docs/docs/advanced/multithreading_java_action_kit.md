# Multithreading in the Java Action Kit

The Java Action Kit provides a feature that allows you to configure the number of threads per action type via properties.
This feature enhances performance and concurrency for the specified actions.

_Note: This feature is only applicable to the Java Action Kit._

## Configuring Threads per Action

To configure the number of threads for a specific action, add an entry in your application.yaml file under the actions
namespace. For example, to set the number of threads for the org.deltafi.core.action.FilterEgressAction action to 2,
include the following configuration:

```yaml
actions:
  actionThreads:
    org.deltafi.core.action.FilterEgressAction: 2
```

By default, if an action does not have a specific thread count configuration, it will use 1 thread.
