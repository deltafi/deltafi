# DeltaFile Analytics Capability

DeltaFile analytics provide the ability to extract insights on behavior, performance, and volumetrics
of processed DeltaFiles by flow and any applied annotations. 
The deltafile analytics capability is built on top of Clickhouse, which allows for efficient querying 
and analysis of the data.

## DeltaFile Analytics with Clickhouse

When DeltaFiles are processed to completion, DeltaFi automatically creates an entry in the Clickhouse 
analytics database for each processed file.

The following table is maintained in clickhouse with rows for each DeltaFile processed by DeltaFi:

| Column Name      | Data Type           | Description                                                                                                                     |
|------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------|
| did              | String              | The DID (DeltaFile ID) associated with the DeltaFile.                                                                           |
| timestamp        | DateTime            | The creation timestamp of the DeltaFile.                                                                                        |
| update_timestamp | DateTime            | The timestamp of the last update to the DeltaFile.                                                                              |
| flow             | String              | The flow associated with the DeltaFile.                                                                                         |
| files            | UInt64              | The number of files in the DeltaFile.                                                                                           |
| ingressBytes     | UInt64              | The number of bytes ingressed with the DeltaFile.                                                                               |
| totalBytes       | UInt64              | The total number of bytes in the DeltaFile.                                                                                     |
| errored          | UInt64              | The number of errored files in the DeltaFile.                                                                                   |
| filtered         | UInt64              | The number of filtered files in the DeltaFile.                                                                                  |
| egressed         | UInt64              | The number of egressed files in the DeltaFile.                                                                                  |
| annotations      | Map(String, String) | A map of annotations associated with the DeltaFile, where the key is the annotation name and the value is the annotation value. |

## DeltaFile Analytics Grafana Dashboards

A default Grafana dashboard is maintained for DeltaFile analytics visualization.  The "Clickhouse Flow by Annotation" 
dashboard found in the Grafana DeltaFi dashboard folder can be used to visualize DeltaFile flows grouped
by an arbitrary annotation.  This serves as an example of how to use Grafana to visualize DeltaFi data, 
and can be used as a template for customizing your own analytic dashboards.

## Survey API for Analytics

`/api/v1/survey` is the REST API for injecting "survey" data into the DeltaFi analytics database.
The API is used to add an array of DeltaFile analytic entries directly into the Clickhouse analytics
database without needing to actually process the data through DeltaFi flows.  This allows for hypothetical,
observed, or orthogonally processed data to be represented alongside the analytic data for 
processed DeltaFiles that are automatically added to the database as a part of the usual DeltaFi flow
processing.  The "survey" data can be viewed in Grafana dashboards and the survey data can be distinguished
from the other analytic data in the DeltaFi system by the pattern of the `did` field ( `/^survey-.*/` ).

<dl>
  <dt>Endpoint</dt>
  <dd>/api/v1/survey</dd>
  <dt>Method</dt>
  <dd>POST</dd>
  <dt>Request Body</dt>
  <dd>
    The request body should be a JSON array containing survey data.
    The array elements in the JSON request body are individual DeltaFile analytic entries.
    The following fields are supported in the analytic entries:
    <ul>
      <li><code>timestamp</code>: Creation timestamp for the entry. Defaults to the current time.</li>
      <li><code>update_timestamp</code>: Last update timestamp for the entry. Defaults to the current time.</li>
      <li><code>flow</code>: The name of the flow being surveyed (required)</li>
      <li><code>files</code>: The number of files being surveyed (required)</li>
      <li><code>ingress_bytes</code>: Ingress bytes for the survey entry</li>
      <li><code>errored</code>: Number of errored files for the survey entry</li>
      <li><code>filtered</code>: Number of filtered files for the survey entry</li>
    </ul>
    Any other fields in the analytic entry will be added as annotations to the survey entry.
  </dd>
  <dt>Response</dt>
  <dd>
    The response body should be a JSON array containing the survey data, or an error message if the
    survey data is malformed.
  </dd>
</dl>

### Example Survey Post

```json
[
  {
    "flow": "SampleFlow",
    "files": 1,
    "timestamp": "2023-08-21T10:00:00Z",
    "update_timestamp": "2023-08-21T10:00:00Z",
    "ingress_bytes": 100,
    "errored": 0,
    "filtered": 1,
    "annotation1": "value1",
    "annotation2": "value2"
  },
  {
    "flow": "SampleFlow",
    "files": 1,
    "timestamp": "2023-08-21T10:01:00Z",
    "update_timestamp": "2023-08-21T10:01:00Z",
    "ingress_bytes": 200,
    "errored": 1,
    "filtered": 0,
    "annotation1": "value1",
    "annotation2": "value3"
  }
]
```

A survey can be posted to the `/api/v1/survey` endpoint as follows:
```bash
# Example Usage
curl -X POST -H "Content-Type: application/json" -d '[
  {
    "flow": "SampleFlow",
    "files": 1,
    "ingress_bytes": 100,
    "annotation1": "value1"
  }
]' http://your-api-endpoint/api/v1/survey
```

### Timestamp format

The following formats can be used for timestamp fields:
- ISO 8601 Format: This format represents dates and times in a human-readable and machine-readable way.
  - `2023-08-21T10:00:00Z` (with timezone)
  - `2023-08-21T10:00:00+05:30` (with timezone offset)
- RFC 2822 Format: This format is used for email and internet messaging.
  - `Thu, 21 Aug 2023 10:00:00 +0530`
- Ruby Date-Time String: Ruby's default date-time string format.
  - `Wed Aug 21 10:00:00 UTC 2023`
- Short Date Format: Short date format without the time.
  - `2023-08-21`

