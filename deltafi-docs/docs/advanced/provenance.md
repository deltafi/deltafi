# Provenance Tracking

Provenance tracking provides exportable DeltaFile lineage records for environments with dynamic scaling and ephemeral instances. When enabled, DeltaFi records the journey of each file through the system - where it came from, what transforms it went through, and where it ended up.

## Use Case

In dynamically scaling DeltaFi deployments, instances may come and go. Once an instance is gone, its DeltaFile metadata is lost. Provenance tracking creates durable, exportable records that survive instance termination.

This is particularly useful for:
- **"Find my data" queries**: Track what happened to a specific file or set of files
- **Compliance**: Maintain audit trails of data processing
- **Debugging**: Understand the path data took through the system
- **Multi-instance environments**: Aggregate lineage across ephemeral instances

## Enabling Provenance

Set the following system properties:

| Property | Default | Description |
|----------|---------|-------------|
| `provenanceEnabled` | `false` | Enable provenance tracking |
| `provenanceAgeOffDays` | `3` | Days to retain provenance data |
| `provenanceAnnotationsAllowed` | (empty) | Comma-separated list of annotation keys to include |

## What Gets Recorded

A provenance record is created each time a DeltaFile flow reaches a terminal state:

- **COMPLETE**: File successfully egressed through a data sink
- **ERROR**: Processing failed at some point
- **FILTERED**: File was filtered out
- **CANCELLED**: Processing was cancelled
- **SPLIT**: File was split into child files (children will have their own provenance records)

Each record contains:

| Field | Description |
|-------|-------------|
| `did` | DeltaFile identifier (UUID) |
| `parent_did` | Parent DeltaFile ID if this is a split child (UUID, empty otherwise) |
| `system_name` | The DeltaFi instance identifier |
| `data_source` | The data source flow that ingested the file |
| `filename` | Original filename |
| `transforms` | List of transform flows the file passed through |
| `data_sink` | Egress flow name (empty for errors before egress) |
| `final_state` | Terminal state (COMPLETE, ERROR, FILTERED, CANCELLED, SPLIT) |
| `created` | When the DeltaFile was created |
| `completed` | When the terminal state was reached |
| `annotations` | Selected annotations (filtered by `provenanceAnnotationsAllowed`) |

### Fan-out Handling

If a DeltaFile fans out to multiple data sinks, a separate provenance record is created for each path. Each record contains only the transforms that led to that specific sink.

## File Output

Provenance records are written as Parquet files to the analytics collector:

```
/data/analytics/provenance/
├── raw/                    # Uncompacted files from periodic flushes
│   └── YYYYMMDD/
│       └── HH/
│           └── *.parquet
└── compacted/              # Hourly compacted files (export target)
    └── {system_name}/      # Partitioned by system name
        └── YYYYMMDD/
            └── HH.parquet
```

The `compacted/` directory is the stable export target. Files are partitioned by system name to avoid collisions when multiple DeltaFi instances share an analytics collector.

## Exporting Provenance

### Flushing Provenance Data

Use the TUI command to flush and compact provenance data:

```bash
# Flush and compact provenance data
deltafi provenance flush

# Flush with verbose output showing compacted files
deltafi provenance flush --verbose
```

The `flush` command:
1. Signals the analytics collector to flush all buffered data
2. Triggers compaction including the current hour
3. Waits for completion before returning

### Graceful Shutdown Sequence

To ensure all provenance data is captured before instance termination:

1. **Stop ingress** - Prevent new data from entering the system
2. **Wait 30 seconds** - Allow deltafi-core to flush buffered provenance records to the analytics collector
3. **Flush and compact** - Run `deltafi provenance flush` to ensure all data is compacted
4. **Export files** - Sync the `provenance/compacted/` directory to external storage
5. **Terminate instance**

Example shutdown script:
```bash
# Stop ingress and wait for core to flush
deltafi ingress disable
sleep 30

# Flush and compact provenance data
deltafi provenance flush

# Export provenance files (partitioned by system name)
aws s3 sync /data/analytics/provenance/compacted/ s3://my-bucket/provenance/

# Safe to terminate
```

The export destination and query mechanism is left to the operator. Common approaches include:
- Syncing to cloud object storage (S3, GCS, Azure Blob)
- Loading into a data warehouse
- Querying with tools like DuckDB, Athena, or Spark

## Grafana Dashboard

A built-in Provenance dashboard is available in Grafana under the DeltaFi folder. It provides:

- **Total Records**: Count of provenance records matching current filters
- **By Final State**: Pie chart showing distribution across COMPLETE, ERROR, FILTERED, CANCELLED
- **By System**: Pie chart showing distribution across system names
- **Recent Provenance Records**: Table showing recent records with all fields including annotations

### Filtering

The dashboard supports filtering by:

| Filter | Description |
|--------|-------------|
| DID | Filter by DeltaFile ID |
| Parent DID | Filter by parent DeltaFile ID (for split children) |
| Data Source | Filter by data source flow name |
| Data Sink | Filter by data sink flow name |
| Filename | Filter by original filename |
| Annotation Key | Filter by a specific annotation key |
| Annotation Value | Filter by the value for the specified annotation key |

All filters are optional. Leave blank to show all records. Annotation filtering requires both key and value to be specified.

Use `Parent DID` to find all the children created when a file was split.

## Example Queries

Once provenance data is loaded into a query engine:

```sql
-- Find a specific file
SELECT * FROM provenance
WHERE filename = 'my-file.txt';

-- Find by annotation (DuckDB MAP access requires [1] to get the value)
SELECT * FROM provenance
WHERE annotations['customer_id'][1] = 'ABC123';

-- Errors from a specific system
SELECT * FROM provenance
WHERE system_name = 'prod-east'
  AND final_state = 'ERROR';

-- Where did files from a data source go?
SELECT data_sink, final_state, count(*) as cnt
FROM provenance
WHERE data_source = 'MyDataSource'
GROUP BY data_sink, final_state;

-- Find all children from a split
SELECT * FROM provenance
WHERE parent_did = '550e8400-e29b-41d4-a716-446655440000';

-- Find a file and its immediate children
SELECT * FROM provenance
WHERE did = '550e8400-e29b-41d4-a716-446655440000'
   OR parent_did = '550e8400-e29b-41d4-a716-446655440000';
```

## Schema

The Parquet schema uses these column types:

```
did:          STRING
parent_did:   STRING (optional)
system_name:  STRING
data_source:  STRING
filename:     STRING
transforms:   LIST<STRING>
data_sink:    STRING (optional)
final_state:  STRING
created:      TIMESTAMP (millisecond)
completed:    TIMESTAMP (millisecond)
annotations:  MAP<STRING, STRING> (optional)
```
