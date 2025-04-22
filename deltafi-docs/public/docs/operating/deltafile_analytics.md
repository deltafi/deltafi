# DeltaFile Analytics Capability

DeltaFile analytics provide the ability to extract insights on behavior, performance, and volumetrics
of processed DeltaFiles by dataSource and any applied annotations.

## DeltaFile Analytics

The following events result in creation or updates to analytic events:
* Creation of a DeltaFile
* Egress of a DeltaFile
* Annotation of a DeltaFile
* Error events
* Filter events
* Cancellation of a DeltaFile

## Data Retention

Raw analytics data is retained for 3 days, while continuous aggregate views maintain data for up to 30 days.
This allows you to query historical trends even after the raw data has been purged.

## DeltaFile Analytics Grafana Dashboards

DeltaFi ships with three default Grafana dashboards for analytics visualization:
1. DeltaFile Analytics
2. Error Analysis
3. Filter Analysis

These dashboards are located in the Grafana "DeltaFi" folder. They serve as examples of how to visualize
DeltaFi data, and you can customize or extend them as needed.

## Survey API for Analytics

`/api/v2/survey` is the REST API for injecting "survey" data into the DeltaFi analytics database.
The API is used to add an array of DeltaFile analytic entries directly into the analytics
database without needing to process the data through DeltaFi dataSources. This allows for hypothetical,
observed, or orthogonally processed data to be represented alongside the analytic data for
processed DeltaFiles that are automatically added to the database as part of the usual DeltaFi processing.
The "survey" data can be viewed in Grafana dashboards and distinguished from other analytic data
by the pattern of the `did` field (`/^survey-.*/`).

<dl>
  <dt>Endpoint</dt>
  <dd>/api/v2/survey</dd>
  <dt>Method</dt>
  <dd>POST</dd>
  <dt>Request Body</dt>
  <dd>
    The request body should be a JSON array containing survey data.
    Each element in the array is an individual DeltaFile analytic entry.
    The following fields are supported in the analytic entries:
    <ul>
      <li><code>timestamp</code>: Creation timestamp for the entry. Defaults to the current time.</li>
      <li><code>dataSource</code>: The name of the dataSource being surveyed (required)</li>
      <li><code>files</code>: The number of files being surveyed. Must be greater than 0. (required)</li>
      <li><code>ingressBytes</code>: Ingress bytes for the survey entry. Must be greater than or equal to 0. (ingress_bytes is also supported for backward compatibility)</li>
    </ul>
    Any additional fields will be stored as annotations on the survey entry, provided that the annotation
    key is allow-listed (see Analytics Configuration below).
  </dd>
</dl>

### Response

On success, the API returns HTTP 200 with no body.

If there are validation errors, it returns HTTP 400 with a JSON body containing a message for each invalid event:

```json
  {
  "error": [
    {
      "message": "Error message describing the issue",
      "event": { "...": "..." }
    }
  ],
  "timestamp": "2023-08-21T10:00:00Z"
}
```

If analytics are disabled, it returns HTTP 501 with:

```json
  {
    "error": "Survey analytics are disabled",
    "timestamp": "2023-08-21T10:00:00Z"
  }
```

### Example Survey Post

```json
    [
      {
        "dataSource": "Sample",
        "files": 1,
        "timestamp": "2023-08-21T10:00:00Z",
        "ingressBytes": 100,
        "annotation1": "value1",
        "annotation2": "value2"
      },
      {
        "dataSource": "Sample",
        "files": 1,
        "timestamp": "2023-08-21T10:01:00Z",
        "ingressBytes": 200,
        "annotation1": "value1",
        "annotation2": "value3"
      }
    ]
```

You can post the survey to `/api/v2/survey` with a command like:

```bash
# Example Usage
curl -X POST -H "Content-Type: application/json" -d '[
  {
    "dataSource": "Sample",
    "files": 1,
    "ingressBytes": 100,
    "annotation1": "value1"
  }
]' http://your-api-endpoint/api/v2/survey
```

### Timestamp Format

The following formats can be used for timestamp fields:
- ISO 8601 Format (with timezone or offset), for example:
  2023-08-21T10:00:00Z or 2023-08-21T10:00:00+05:30
- RFC 2822 Format, for example:
  Thu, 21 Aug 2023 10:00:00 +0530
- Ruby Date-Time String, for example:
  Wed Aug 21 10:00:00 UTC 2023
- Short Date Format, for example:
  2023-08-21

## Analytics Configuration

Annotation Allow-List:
By default, no annotations submitted with a DeltaFile or survey event are stored in the analytics system.
If you wish to record annotations for grouping or filtering, you must explicitly specify a comma-separated
list of keys in the `allowedAnalyticsAnnotations` property (configured via the GUI or CLI). Only those annotation keys
present in this allow-list will be inserted into the analytics database.

Example configuration in the GUI or CLI:

    allowedAnalyticsAnnotations="annotation1,annotation2,annotation3"

This configuration ensures that only the keys annotation1, annotation2, and annotation3 are recorded in analytics.

Analytics Group Name:
Analytics supports an additional top-level group for classification, controlled by the `analyticsGroupName` property.
When a new analytic event is recorded, if an annotation key matches the analyticsGroupName, its value is used as the
event group. Otherwise, the event group is recorded as "Not Present".

Example configuration in the GUI or CLI:

    analyticsGroupName="annotation1"

If an annotation named annotation1 is present, its value becomes the event group in the analytics table; if not,
"Not Present" is used. This provides an extra grouping dimension on top of the data source.

Note: Analytics configurations will only apply to events recorded after the change. They are not retroactively applied to existing records.

## Querying Analytics Data

The `get_analytics_data` function provides a standardized way to query analytics data with proper handling of annotations and filtering:

```sql
get_analytics_data(
  p_annotation_key_name text,        -- Annotation key name to filter by, use 'All' for all
  p_annotation_values_text text[],   -- Array of annotation value names, include 'All' for all values
  p_datasources_text text[],         -- Array of data source names, include 'All' for all
  p_groups_text text[],              -- Array of group names, include 'All' for all
  p_analytic_ingress_types text[],   -- Array of ingress types (DATA_SOURCE, CHILD, SURVEY), include 'All' for all
  p_start_time timestamptz,          -- Start time for the query
  p_end_time timestamptz,            -- End time for the query
  p_interval_str text                -- Time bucket interval (e.g., '5 minutes')
)
```

### Parameter Usage

- To select all values for a parameter, use 'All' in the array
- For annotation values, the function will automatically handle "No Annotation" data
- For time range parameters in Grafana, use `$__timeFrom()` and `$__timeTo()`
- For interval, use `($__interval_ms || ' ms')` to adapt to zoom level
- Missing or unknown groups are labeled as "No Group"
- 
### Return Values

The function returns a table with the following columns:
- `time`: Timestamp bucket
- `datasource_name`: Name of the data source
- `group_name`: Name of the event group
- `annotation_value`: Value of the annotation (or "No Annotation")
- `ingress_bytes`: Total bytes ingressed
- `ingress_files`: Count of files ingressed
- `egress_bytes`: Total bytes egressed
- `egress_files`: Count of files egressed
- `error_files`: Count of files with errors
- `filter_files`: Count of filtered files

This output format works directly with Grafana's time series and table visualizations.

## Querying Error and Filter Data

The `get_errors_filters_data` function provides detailed analysis of errors and filters with additional dimensions:

```sql
get_errors_filters_data(
  p_annotation_key_name text,        -- Annotation key name to filter by, use 'All' for all
  p_annotation_values_text text[],   -- Array of annotation value names, include 'All' for all values
  p_datasources_text text[],         -- Array of data source names, include 'All' for all
  p_groups_text text[],              -- Array of group names, include 'All' for all
  p_start_time timestamptz,          -- Start time for the query
  p_end_time timestamptz,            -- End time for the query
  p_interval_str text,               -- Time bucket interval (e.g., '5 minutes')
  p_event_type text DEFAULT 'BOTH'   -- Filter by 'ERRORS', 'FILTERS', or 'BOTH'
)
```

### Parameter Usage

- To select all values for a parameter, use 'All' in the array
- For annotation values, the function will automatically handle "No Annotation" data
- For time range parameters in Grafana, use `$__timeFrom()` and `$__timeTo()`
- For interval, use `($__interval_ms || ' ms')` to adapt to zoom level
- Missing or unknown groups are labeled as "No Group"

### Return Values

The function returns a table with the following columns:
- `time`: Timestamp bucket
- `datasource_name`: Name of the data source
- `group_name`: Name of the event group
- `annotation_value`: Value of the annotation (or "No Annotation")
- `flow_name`: Name of the flow where the error or filter occurred
- `action_name`: Name of the action where the error or filter occurred
- `cause`: Error or filter cause
- `error_files`: Count of files with errors
- `filter_files`: Count of filtered files

## Grafana Integration

Both functions are designed to work with Grafana's visualization capabilities. For optimal performance in time series visualizations, you should:

- Call the function directly for the data you need
- Apply time_bucket_gapfill in your query if needed for visualization

### Example Grafana Queries

```sql
-- Time series visualization query with gap filling
SELECT
  time_bucket_gapfill(($__interval_ms || ' ms')::interval, "time") AS gapped_time,
  CASE
    WHEN $annotationKey = 'All' THEN datasource_name || ' - ' || group_name
    ELSE datasource_name || ' - ' || group_name || ' - ' || annotation_value
    END AS series_name,
  ingress_bytes,
  ingress_files,
  egress_bytes,
  egress_files,
  error_files,
  filter_files
FROM get_analytics_data(
        $annotationKey,
        ARRAY[$annotationValues],
        ARRAY[$dataSources],
        ARRAY[$groups],
        $__timeFrom(),
  $__timeTo(),
  ($__interval_ms || ' ms')
)

-- Summary table query (no gap filling needed)
SELECT
  datasource_name AS "Data Source",
  group_name AS "Group",
  CASE
    WHEN $annotationKey != 'All' THEN annotation_value
    END AS "Annotation Value",
  SUM(ingress_bytes) AS "Ingress Bytes",
  SUM(ingress_files) AS "Ingress Files",
  SUM(egress_bytes) AS "Egress Bytes",
  SUM(egress_files) AS "Egress Files",
  SUM(error_files) AS "Errored",
  SUM(filter_files) AS "Filtered"
FROM get_analytics_data(
        $annotationKey,
        ARRAY[$annotationValues],
        ARRAY[$dataSources],
        ARRAY[$groups],
        $__timeFrom(),
  $__timeTo(),
  '100 years' -- Using a very large interval for summary
)
GROUP BY
  datasource_name,
  group_name,
  CASE
    WHEN $annotationKey != 'All' THEN annotation_value
    END
ORDER BY "Data Source", "Group", "Annotation Value"

-- Error analysis with gap filling
SELECT
  time_bucket_gapfill(($__interval_ms || ' ms')::interval, "time") AS gapped_time,
  datasource_name || ' - ' || cause AS series_name,
  SUM(error_files) AS errors
FROM get_errors_filters_data(
        'All',
        ARRAY['All'],
        ARRAY[$dataSources],
        ARRAY['All'],
        $__timeFrom(),
  $__timeTo(),
  ($__interval_ms || ' ms'),
  'ERRORS'
)
GROUP BY gapped_time, datasource_name, cause
ORDER BY gapped_time, series_name
```

## Performance Considerations

The analytics functions have been optimized for performance:
- Time bucketing is applied early in the query pipeline to reduce data volume
- Gap filling is left to the caller to avoid unnecessary processing for aggregate queries
- Dimensions are joined efficiently to minimize row multiplication
- "No Annotation" values are handled properly with minimal overhead

For best performance:

- Use reasonable time intervals appropriate to your data density 
- Apply additional filtering (datasources, groups, annotations) when possible 
- For summary tables, use a large interval (e.g., '100 years') to avoid unnecessary bucketing 
- Only use gap filling for time series visualizations where it's needed

