# DeltaFi Metrics

A DeltaFi deployment provides normalized metrics generation and centralized metrics collection and visualization.
Metrics are generated and sent to a Graphite time-series database that is used as a data source for Grafana visualization.

## Default Metrics Generation

DeltaFi automatically generates metrics for action cognizance, ingress and egress volume, and system utilization.
These metrics can be queried from the Graphite database or referenced by Grafana when creating metrics visualizations.

### Metrics Lexicon

The following metrics are generated to the Graphite time-series database by default:

| Name                              | Tags                                               | Description                                                                                           |
|-----------------------------------|----------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| stats_counts.bytes_in             | action, ingressFlow, source                        | Number of bytes ingressed by a flow (only produced on action='ingress')                               |
| stats_counts.files_in             | action, ingressFlow, source                        | Number of files ingressed by an action (action='ingress' provides the ingressed files)                |
| stats_counts.files_errored        | action, ingressFlow, source                        | Number of files errored (action='ingress' provides the ingressed files)                               |
| stats_counts.files_filtered       | action, ingressFlow, source                        | Number of files filtered (action='ingress' provides the ingressed files)                              |
| stats_counts.files_dropped        | action, ingressFlow, source                        | Number of files dropped (action='ingress' provides the ingressed files)                               |
| stats_counts.bytes_out            | action, ingressFlow, source, dataSink, destination | Number of bytes egressed (only produced on action='egress')                                           |
| stats_counts.files_out            | action, ingressFlow, source, dataSink, destination | Number of files egressed (only produced on action='egress')                                           |
| gauge.action_queue.queue_size     | queue_name                                         | An instantaneous gauge for the size of an action_queue (number of DeltaFiles waiting on the queue)    |
| gauge.node.disk.limit             | hostname, service                                  | An instantaneous gauge for the maximum aggregate storage limit per node and service partition (bytes) |
| gauge.node.disk.usage             | hostname, service                                  | An instantaneous gauge for current storage utilization per node and service partition (bytes)                              |
| stats_counts.survey.bytes         | surveyFlow, surveyDirection                        | Number of bytes surveyed on a flow                                                                    |
| stats_counts.survey.files         | surveyFlow, surveyDirection                        | Number of files surveyed on a flow                                                                    |
| stats_counts.survey.subflow.bytes | surveyFlow, surveySubflow, surveyDirection         | Number of bytes surveyed on a subflow                                                                 |
| stats_counts.survey.subflow.files | surveyFlow, surveySubflow, surveyDirection         | Number of files surveyed on a subflow                                                                 |

## Custom Metrics in Action Kits

Each action kit has a custom metric interface built in to the Result class for the action.  To add a new custom
metric from the load action, simply use the add interface to create custom metrics with custom tags.  Note that
the actual metric name in the Graphite data store will be prepended with `stats_counts.` and custom metrics will
automatically be tagged with `action`, `ingressFlow`, and `source`.

For example, in the Java Action Kit:
```java
result.add(
    new Metric("my.custom.metric, 42)
        .addTag("my.tag.1", "important-thing")
        .addTag("my.tag.2", "interesting-thing")
);
```

> Use caution when generating custom metrics.  Each custom metric name-tags permutation will take up space in the
> time series database.

## Metrics Visualization

Grafana is used to generate rich visualizations for the metrics contained in the Graphite data store.  The following
default dashboards are included in a DeltaFi Kubernetes installation:

* System Overview - A general system status dashboard that includes ingress and egress metrics, files errored/dropped/filtered, queue status, action activity, system alerts, and disk utilization trends
* Flow Summary - A view of the ingress and egress activity for one or more flows
* Last Seen on Flows - A list of flows with the most recent flow activity
* Processing Report - A flow level report on flow activity for a given time period

In addition, there are Dashboards with logs, audit logging information, and logging per action.

## Metrics APIs

DeltaFi exposes the full API for Graphite and Grafana, allowing for easy custom querying, scripting, and metrics injection.

- [Graphite REST API](https://graphite-api.readthedocs.io/en/latest/api.html)
- [Grafana REST API](https://grafana.com/docs/grafana/latest/developers/http_api/)

In addition, there are custom APIs that provide summary metric endpoints:

* <a href="/api/v1/metrics/action">`/api/v1/metrics/action`</a> provides a JSON representation of the current action activity
* <a href="/api/v1/metrics/flow.json">`/api/v1/metrics/flow.json`</a> provides a time bound summary report (JSON) of flow ingress/egress activity
* <a href="/api/v1/metrics/flow.csv">`/api/v1/metrics/flow.csv`</a> provides a time bound summary report (CSV) of flow ingress/egress activity
* <a href="/api/v1/metrics/system/nodes">`/api/v1/metrics/system/nodes`</a> provides a JSON summary of the system disk, memory, and CPU status
* <a href="/api/v1/metrics/system/content">`/api/v1/metrics/system/content`</a> provides a JSON summary of the content storage limit and usage
* <a href="/api/v1/metrics/queues">`/api/v1/metrics/queues`</a> provides a JSON summary of the action queue statuses
