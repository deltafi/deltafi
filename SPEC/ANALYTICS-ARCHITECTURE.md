# DeltaFi Analytics Architecture

This document describes the analytics system as currently implemented, including design goals, architectural decisions, and tradeoffs.

## Overview

DeltaFi's analytics system tracks data flow metrics (ingress, egress, errors, filters) with support for annotation-based filtering. The system uses Parquet columnar storage with DuckDB for queries, designed to handle billions of records efficiently.

## Design Goals

1. **High Volume Handling**: Support billions of analytics events without overwhelming the system
2. **Time-Series Optimization**: Enable efficient time-range queries for dashboards
3. **Multi-Annotation Filtering**: Allow filtering by multiple dynamic key-value annotations simultaneously
4. **Resource Efficiency**: Minimize CPU/memory impact on the processing workload
5. **Grafana Integration**: HTTP API compatible with Grafana Infinity datasource
6. **Late-Arriving Annotations**: Handle annotations that arrive after initial event recording

## Architecture Components

### Data Flow

```
deltafi-core ──HTTP POST──▶ deltafi-analytics ──writes──▶ raw parquet files
     │                            │                              │
     │                            │        (partitioned by creation_time)
     │                            ├── events/YYYYMMDD/HH/*.parquet
     │                            ├── annotations/YYYYMMDD/HH/*.parquet
     │                            │
     │                            compactor (1 min)
     │                            │
     │                            Stage 1: raw → preagg (preserves DID)
     │                            └── preagg/YYYYMMDD/HH.parquet
     │                                       │
     │                            Stage 2: preagg → final (event_time partitioned)
     │                            └── aggregated/YYYYMMDD/HH.parquet
     │                                       │
     │                            query service (DuckDB)
     │                                       │
     └─────────────────────────── Grafana ◀──┘
```

### 1. Java Client: AnalyticsClient

Located in `deltafi-core`, this client buffers and forwards events to the Go collector.

**Configuration**:
- Buffer size: 10,000 events
- Flush interval: 10 seconds
- Flush trigger: buffer full OR interval elapsed

**Features**:
- Atomic flush submission (prevents duplicate flush tasks piling up)
- Annotation queueing for late-arriving annotations
- Automatic annotation merging when events are written

**Event Types**:
- `INGRESS` - Data arrival (from REST/timed data sources or child creation)
- `EGRESS` - Successful data delivery
- `ERROR` - Processing errors
- `FILTER` - Filtered/rejected files

### 2. Go Analytics Collector

A lightweight Go service (`deltafi-analytics`) that receives events and writes Parquet files.

**HTTP Endpoints**:
- `POST /events` - Receive event batches (requires `creationTime`, returns 400 if missing)
- `POST /annotations` - Receive annotation updates
- `GET /analytics/data-sources` - List distinct data sources
- `GET /analytics/annotation-keys` - List distinct annotation keys
- `GET /analytics/annotation-values?key=X` - List values for an annotation key
- `POST /analytics` - Query analytics data

**Ingest Validation**:
- Event time validation: rejects events with `event_time` > 1 hour old or > 5 minutes in the future
- Annotation validation: rejects annotations for DIDs with `creation_time` > 3 days ago
- These constraints ensure bounded spread between creation_time and event_time partitions

**Writer Configuration**:
- Buffer size: 10,000 events
- Flush interval: 60 seconds
- Output format: Parquet with Snappy compression
- Atomic writes: temp file + rename

**Directory Structure**:
```
/data/analytics/
├── events/                              # Raw events, partitioned by CREATION_TIME
│   ├── 20251130/
│   │   ├── 00/
│   │   │   ├── 120000_123456.parquet
│   │   │   └── 120100_234567.parquet
│   │   └── 01/
│   │       └── ...
│   └── 20251201/
│       └── ...
├── annotations/                         # Raw annotations, partitioned by CREATION_TIME
│   ├── 20251130/
│   │   ├── 00/
│   │   │   └── ...
│   │   └── 01/
│   │       └── ...
│   └── 20251201/
│       └── ...
├── preagg/                              # Pre-aggregated (preserves DID), partitioned by CREATION_TIME
│   ├── 20251130/
│   │   ├── meta.json                    # Per-date metadata (hour mtimes in nanoseconds)
│   │   ├── 00.parquet
│   │   ├── 00.archived                  # Per-hour archive marker
│   │   └── ...
│   └── 20251201/
│       └── ...
└── aggregated/                          # Final aggregates, partitioned by EVENT_TIME
    ├── 20251125.parquet                 # Daily file (archived, hourly bucket resolution)
    ├── 20251126.parquet                 # Daily file (archived, hourly bucket resolution)
    ├── 20251130/                        # Hourly files (rolling, 5-min bucket resolution)
    │   ├── 00.parquet
    │   ├── 00.archived
    │   ├── 01.parquet
    │   └── ...
    └── 20251201/
        └── ...
```

**Partitioning Strategy**:
- **Raw (events, annotations)**: Partitioned by DeltaFile creation_time. Ensures events and annotations for the same DeltaFile land in the same bucket for efficient joins.
- **Preagg**: Partitioned by creation_time (same as raw). Preserves DID for late-arriving annotation joins. Archive markers track which hours have been processed.
- **Aggregated**: Partitioned by **event_time**. This enables efficient time-range queries since the query's time filter directly maps to file paths.

### 3. Compactor

Runs every 1 minute with a two-stage pipeline that separates annotation joining (by creation_time) from final aggregation (by event_time).

**Two-Stage Pipeline**:

**Stage 1: Raw → Preagg** (per creation_time hour)
- Joins events with annotations by DID
- Computes 5-minute event_time_bucket
- Aggregates by DID (preserving DID for late annotation joins)
- Writes to `preagg/YYYYMMDD/HH.parquet` (creation_time partitioned)

**Stage 2: Preagg → Final** (re-partitions to event_time)
- Reads preagg file, extracts distinct event_time hours
- For each event_time hour, aggregates (dropping DID)
- Merges into `aggregated/YYYYMMDD/HH.parquet` (event_time partitioned)

**Why Two Stages?**:
- Annotation joins must happen within creation_time partitions (where events and annotations co-locate)
- Queries filter by event_time, so aggregated files must be partitioned by event_time
- The intermediate preagg stage bridges these two partitioning schemes

**Smart Timestamp Checking**:
The compactor only reprocesses hours that have changed:
1. For each hour, compare source file mtimes against the last processed mtime stored in `meta.json`
2. Skip hours where no source files are newer than the stored mtime
3. Only recompact hours with new events or annotations
4. After compaction, store the latest source file mtime (in nanoseconds) in `meta.json`

**Hour-by-Hour Processing**:
1. For each creation_time date directory with events:
   - Iterate through hours 00-23
   - Check if hour needs compaction (source files newer than preagg)
   - **Stage 1**: Load events + annotations, join by DID, aggregate preserving DID
   - Write preagg file
   - **Stage 2**: Read preagg, extract event_time hours, aggregate dropping DID
   - Write/merge to event_time-partitioned aggregated files

**Aggregated Schema**:
```
bucket          TIMESTAMP    -- 5-minute aligned timestamp
data_source     VARCHAR
event_type      VARCHAR      -- INGRESS, EGRESS, ERROR, FILTER
flow_name       VARCHAR
action_name     VARCHAR
cause           VARCHAR
ingress_type    VARCHAR      -- DATA_SOURCE, CHILD, SURVEY (for INGRESS events)
annotations     MAP(VARCHAR, VARCHAR)
event_count     BIGINT
total_bytes     BIGINT
total_file_count BIGINT
```

**Rolling Compaction Strategy**:

Recent data (creation_time hour < 3 days old):
- Stage 1: Compacts raw → preagg, keeping raw source files
- Stage 2: Preagg → aggregated, merging with existing hourly aggregated files
- Overwrites preagg on each run (source of truth is raw files)
- Allows late-arriving annotations to be picked up
- **Bucket resolution**: 5 minutes
- **File format**: `aggregated/YYYYMMDD/HH.parquet` (24 files per day)

Archived data (creation_time hour ≥ 3 days old):
- Deletes raw files (events + annotations) after compaction
- Deletes preagg file (aggregated is now source of truth)
- Writes `.archived` marker in `aggregated/YYYYMMDD/` directory
- Future late arrivals merge with existing preagg (then Stage 2 runs)

**Daily Consolidation** (when all 24 hours of a day are archived):
- Reads all hourly parquet files from `aggregated/YYYYMMDD/*.parquet`
- Re-aggregates from 5-minute buckets to hourly buckets
- Writes single daily file `aggregated/YYYYMMDD.parquet`
- Deletes the hourly directory (including all `.archived` markers)
- **Bucket resolution**: 1 hour (rolled up from 5 minutes)
- **File format**: `aggregated/YYYYMMDD.parquet` (1 file per day)

**Why Change Resolution on Archive?**
- Historical data doesn't need 5-minute granularity for trend analysis
- Reduces file count by 24x (1 daily vs 24 hourly)
- Reduces row count significantly (12x fewer buckets per day)
- Query performance improves (fewer files to scan)

**Hour-by-Hour Archiving Benefits**:
- Each hour archives independently as it ages past 3 days
- Late arrival merges are scoped to a single hour (~35K rows vs ~850K for a full day)
- More granular progress - partial day archival is possible
- Faster merge operations (24x smaller data per merge)

**Late-Arriving Annotations**:
- Annotations are partitioned by DeltaFile creation time, same as events
- Late-arriving annotations land in the same creation_time bucket as their events
- Stage 1 joins them correctly, Stage 2 re-partitions to event_time
- For archived hours, late arrivals merge with existing preagg, then trigger Stage 2

**Corrupt File Handling**:
- Binary search to identify corrupt Parquet files
- Automatically removes corrupt files and retries compaction

### 4. Query Service

DuckDB-based query engine that reads only aggregated Parquet files.

**Query Features**:
- Time range filtering
- Data source filtering
- Multiple annotation key/value filtering via MAP access
- Group-by annotation key (adds annotation value to data_source label)
- Dynamic time bucketing based on Grafana interval

**Caching**:
- 30-second TTL cache for annotation keys, annotation values, and data sources
- Prevents redundant Parquet scans when Grafana loads dashboard variables

**Example Query** (generated internally):
```sql
SELECT
    time_bucket(INTERVAL '300 seconds', bucket::TIMESTAMP) AS bucket,
    data_source,
    COALESCE(annotations['customer'], 'not present') AS annotation_value,
    SUM(CASE WHEN event_type = 'INGRESS' THEN total_bytes ELSE 0 END) AS ingress_bytes,
    SUM(CASE WHEN event_type = 'INGRESS' THEN total_file_count ELSE 0 END) AS ingress_files,
    SUM(CASE WHEN event_type = 'EGRESS' THEN total_bytes ELSE 0 END) AS egress_bytes,
    SUM(CASE WHEN event_type = 'EGRESS' THEN total_file_count ELSE 0 END) AS egress_files,
    SUM(CASE WHEN event_type = 'ERROR' THEN event_count ELSE 0 END) AS error_files,
    SUM(CASE WHEN event_type = 'FILTER' THEN event_count ELSE 0 END) AS filter_files
FROM read_parquet(['/data/analytics/aggregated/20251130/*.parquet', '/data/analytics/aggregated/20251201/*.parquet'])
WHERE bucket >= '2024-11-30 00:00:00' AND bucket < '2024-12-01 00:00:00'
  AND annotations['region'] IN ('us-east', 'us-west')
GROUP BY 1, 2, 3
```

### 5. Grafana Dashboard

Uses the Infinity datasource to query the analytics collector HTTP API.

**Dashboard**: `parquet-analytics.json`

**Variables**:
- `dataSources` - Multi-select data source filter
- `annotationKey1`, `annotationValues1` - First annotation filter
- `annotationKey2`, `annotationValues2` - Second annotation filter
- `groupByAnnotation` - Annotation key to group results by

**Panels**:
- Ingress bytes/files (time series + pie charts + stats)
- Egress bytes/files (time series + pie charts + stats)
- Error files (time series + pie chart + stat)
- Filter files (time series + pie chart + stat)
- Summary table with totals

**analyticsGroupName Integration**:

The `groupByAnnotation` dropdown defaults based on the system's `analyticsGroupName` property. The analytics collector polls core to fetch this value and returns annotation keys in a specific order:

1. If `analyticsGroupName` is configured AND exists in the data: `[configured_key, "None", other_keys...]`
2. If `analyticsGroupName` is configured but NOT in data: `["None", other_keys...]`
3. If `analyticsGroupName` is not set: `["None", other_keys...]`

Grafana auto-selects the first item, so the configured group key becomes the default when available. "None" means "don't group by annotation" - the query returns data grouped by data_source only.

This server-controlled ordering approach avoids complex Grafana variable logic and ensures the dashboard respects the system-wide analytics grouping preference.

## Key Design Decisions

### 1. Parquet + DuckDB over TimescaleDB

**Decision**: Use columnar Parquet files with DuckDB queries instead of PostgreSQL/TimescaleDB.

**Pros**:
- Multi-annotation filtering without pre-computed aggregates
- Excellent compression (annotations stored as MAP, not junction tables)
- No database server process to manage
- Column pruning and predicate pushdown
- Simple file-based storage

**Cons**:
- No real-time queries (must wait for compaction)
- Manual retention management (vs TimescaleDB policies)

### 2. Aggregated-Only Queries

**Decision**: Query service reads only aggregated files, never raw event files.

**Reason**: Raw files are numerous and small. Aggregated files are one per hour, pre-joined with annotations.

**Impact**: Dashboard latency is bounded by compaction interval (~2 minutes worst case).

### 3. Hourly Aggregate Files with Smart Timestamp Checking

**Decision**: Store aggregates as hourly files (YYYYMMDD/HH.parquet) instead of daily files, and only recompact hours with changed source files.

**Reason**:
- Typical compaction cycles only process 1 hour instead of 24 (~24x faster)
- Late arrival merges are scoped to single hours (~24x smaller)
- Hour-by-hour archival allows more granular progress
- Better cache locality for DuckDB (smaller working sets)

**Tradeoff**: More files (24 per day instead of 1), but DuckDB handles glob patterns efficiently.

### 4. Rolling Compaction with Archive Markers

**Decision**: Recent data (< 3 days) uses overwrite strategy; archived data uses merge strategy.

**Reason**:
- Recent data may receive late annotations; re-compacting picks them up
- Archived data has source files deleted; late arrivals must merge, not replace

**Marker File**: `.archived` suffix (per-hour: `HH.archived`) indicates source files were deleted. Tells compactor to merge instead of overwrite.

### 5. Annotations as MAP Column

**Decision**: Store all annotations in a single `MAP(VARCHAR, VARCHAR)` column in aggregated files.

**Pros**:
- Any annotation key can be queried without schema changes
- Efficient DuckDB MAP access syntax: `annotations['key']`
- No junction tables or joins at query time

**Cons**:
- Cannot index specific annotation keys
- High-cardinality keys expand MAP size

### 6. Separate Event and Annotation Files

**Decision**: Events and annotations are written to separate directory trees, both partitioned by DeltaFile creation time (date/hour).

**Reason**: Partitioning by DeltaFile creation time ensures events and annotations for the same DeltaFile end up in the same hour bucket. The compactor only needs to look in the matching hour directory when joining events with annotations - no cross-date searching required.

### 7. Two-Stage Compaction Pipeline

**Decision**: Split compaction into two stages - Stage 1 (raw→preagg by creation_time) and Stage 2 (preagg→final by event_time).

**Reason**: There's a fundamental mismatch between how data arrives and how it's queried:
- Events arrive with annotations indexed by DID, requiring joins within creation_time partitions
- Queries filter by event_time, requiring aggregated data partitioned by event_time
- A single-stage approach would either compromise join efficiency or query efficiency

**Tradeoff**: Additional I/O and storage for intermediate preagg files, but bounded by the 1-hour max event_time constraint.

### 8. Ingest-Time Event Age Validation

**Decision**: Reject events with event_time > 1 hour old or > 5 minutes in the future.

**Reason**: Bounds the spread between creation_time and event_time partitions. Without this constraint, an event with creation_time=now but event_time=months_ago would require unbounded historical file scans in Stage 2.

**Impact**: Systems with significant clock drift or batch-imported historical data need different handling.

### 9. 5-Minute Aggregation Buckets

**Decision**: Aggregate into 5-minute time buckets.

**Reason**: Balances query performance with granularity. Dashboards typically display 5-minute or coarser resolution.

## Timing and Latency

| Stage | Interval | Cumulative Latency |
|-------|----------|-------------------|
| Java client flush | 10s or 10k events | 0-10s |
| Go collector flush | 60s or 10k events | 10-70s |
| Compactor run | 60s | 70-130s |
| **Total worst case** | | **~2 minutes** |

## Retention

| Data | Format | Retention |
|------|--------|-----------|
| Raw events/annotations | `events/YYYYMMDD/HH/*.parquet` | 3 days (deleted hour-by-hour) |
| Preagg files | `preagg/YYYYMMDD/HH.parquet` | 3 days (deleted with raw files) |
| Rolling aggregated | `aggregated/YYYYMMDD/HH.parquet` | 3 days (5-min bucket resolution) |
| Archived aggregated | `aggregated/YYYYMMDD.parquet` | Indefinite (hourly bucket resolution) |

## Memory Usage

**Java Client Buffer** (10k events): ~7-10 MB
- Per event: ~700 bytes (UUID strings, annotations map, etc.)

**Go Collector Buffer** (10k events): ~5-7 MB
- More compact struct representation

**DuckDB Compactor**: 1 GB limit (configurable)
- Processes one hour at a time for memory efficiency
- Spills to disk if needed

## Migration Strategy

The Parquet analytics system runs in parallel with the existing TimescaleDB analytics during the migration period.

**Parallel Run Approach**:
- System property `parquetAnalyticsEnabled` (default: `false`) controls whether events are sent to the Parquet collector
- When enabled, events are sent to BOTH TimescaleDB and the Parquet collector
- This allows A/B testing between the two systems to validate data accuracy
- Running in parallel for ~30 days builds up historical data in the Parquet system before cutover
- TimescaleDB metrics will be lost when the cutover happens, so pre-populating Parquet data ensures continuity

**Cutover Process**:
1. Enable `parquetAnalyticsEnabled` system property
2. Run both systems in parallel for validation period (recommended: 30+ days)
3. Compare dashboard results between systems
4. When confident, switch Grafana dashboards to use Parquet endpoints
5. Disable TimescaleDB analytics (reduces database load)

## Detailed Walkthrough: DeltaFile Lifecycle Through Analytics

This example traces a single DeltaFile (DID: `abc123`) through the complete analytics pipeline, showing file counts and how mtime-based optimization targets the correct files.

### Scenario

A DeltaFile is created on Day 0 at 10:30 AM, experiences an error on Day 1 at 2:15 PM, and completes with egress + new annotation on Day 2 at 9:45 AM. Each event arrives in real-time (event_time ≈ now), but they all share the same creation_time (the DeltaFile's birth).

### Timeline

**Day 0, 10:30 AM** - DeltaFile created, INGRESS event
- `creation_time = Day0 10:30` (used for partitioning)
- `event_time = Day0 10:30` (same as creation for INGRESS)

**Day 1, 2:15 PM** - ERROR event during processing
- `creation_time = Day0 10:30` (same DeltaFile)
- `event_time = Day1 14:15` (when error occurred)

**Day 2, 9:45 AM** - EGRESS + annotation added
- EGRESS event: `creation_time = Day0 10:30`, `event_time = Day2 09:45`
- Annotation: `customer=acme` on DID `abc123`

### File Locations After Each Event

**After Day 0 INGRESS:**
```
events/Day0/10/103000_001.parquet       # Contains INGRESS event (mtime=Day0 10:30)
annotations/                             # Empty (no annotations yet)
```

**After Day 1 ERROR:**
```
events/Day0/10/103000_001.parquet       # INGRESS (mtime=Day0 10:30)
events/Day0/10/141500_002.parquet       # ERROR event (mtime=Day1 14:15) - SAME dir!
annotations/                             # Still empty
```

Note: ERROR event lands in `Day0/10/` because it's partitioned by **creation_time**, not event_time.

**After Day 2 EGRESS + Annotation:**
```
events/Day0/10/103000_001.parquet       # INGRESS (mtime=Day0 10:30)
events/Day0/10/141500_002.parquet       # ERROR (mtime=Day1 14:15)
events/Day0/10/094500_003.parquet       # EGRESS (mtime=Day2 09:45)
annotations/Day0/10/094500_004.parquet  # customer=acme annotation (mtime=Day2 09:45)
```

### Compaction Runs

**After Day 0 (first compaction cycle after 10:30):**

1. Compactor checks `events/Day0/10/` - mtime of `meta.json` indicates no prior compaction
2. **Stage 1**: Loads events, no annotations to join, aggregates preserving DID
3. Writes `preagg/Day0/10.parquet` with 1 row (INGRESS for DID abc123)
4. **Stage 2**: Reads preagg, extracts event_time=Day0/10, aggregates dropping DID
5. Writes `aggregated/Day0/10.parquet` with 1 row (INGRESS bucket)
6. Updates `meta.json` with latest source mtime

**After Day 1 ERROR (next compaction):**

1. Compactor checks `events/Day0/10/` - sees file with mtime=Day1 14:15 > last processed
2. **Stage 1**: Loads all 2 events, still no annotations
3. Writes `preagg/Day0/10.parquet` with 2 rows (INGRESS + ERROR for same DID)
4. **Stage 2**:
   - Extracts event_time hours: Day0/10 (INGRESS), Day1/14 (ERROR)
   - Writes/merges `aggregated/Day0/10.parquet` (INGRESS)
   - Writes/merges `aggregated/Day1/14.parquet` (ERROR)

**After Day 2 EGRESS + Annotation:**

1. Compactor checks `events/Day0/10/` - sees file with mtime=Day2 09:45 > last processed
2. Also checks `annotations/Day0/10/` - has new annotation file
3. **Stage 1**:
   - Loads 3 events + 1 annotation
   - Joins annotation to events by DID - all 3 events get `customer=acme`
   - Aggregates: 3 rows (same DID, 3 different event_time buckets)
4. **Stage 2**:
   - Extracts event_time hours: Day0/10, Day1/14, Day2/09
   - Merges into `aggregated/Day0/10.parquet` (INGRESS with annotation)
   - Merges into `aggregated/Day1/14.parquet` (ERROR with annotation)
   - Writes `aggregated/Day2/09.parquet` (EGRESS with annotation)

### How mtime Targeting Works

The compactor uses mtime to avoid reprocessing unchanged data:

1. **First check**: Compare directory mtime against last-processed timestamp in `meta.json`
2. **Skip if no changes**: If no source files are newer than last processed, skip entirely
3. **Targeted reprocessing**: Only recompact hours where source files changed

Example flow:
```
meta.json for Day0/10: { "10": 1701432600000000000 }  # Day0 10:30 in nanoseconds

Day 1 compaction check:
  - events/Day0/10/141500_002.parquet mtime = Day1 14:15 (newer!)
  - Last processed = Day0 10:30
  - Result: NEEDS RECOMPACTION

Day 2 compaction check (after Day 1 compaction):
  - meta.json updated to: { "10": 1701518100000000000 }  # Day1 14:15
  - events/Day0/10/094500_003.parquet mtime = Day2 09:45 (newer!)
  - Result: NEEDS RECOMPACTION
```

### Archival and Daily Rollup (Day 3+)

Once `Day0/10` is >3 days old:

1. **Archive Hour**:
   - Deletes `events/Day0/10/*.parquet`
   - Deletes `annotations/Day0/10/*.parquet`
   - Deletes `preagg/Day0/10.parquet`
   - Writes `aggregated/Day0/10.archived` marker

2. **Check All Hours Archived**: When all 24 markers exist for Day0:

3. **Daily Consolidation**:
   - Reads `aggregated/Day0/*.parquet` (all hourly files)
   - Re-aggregates from 5-minute to hourly buckets
   - Writes `aggregated/Day0.parquet` (single daily file)
   - Deletes `aggregated/Day0/` directory

### Final State (After Full Archival)

```
events/                                  # Empty for Day0
annotations/                             # Empty for Day0
preagg/                                  # Empty for Day0
aggregated/
├── Day0.parquet                         # Daily file, hourly buckets
├── Day1/                                # Still rolling (not yet 3 days old)
│   └── 14.parquet
├── Day2/
│   └── 09.parquet
└── metadata.json
```

### Key Insights

1. **All events for one DeltaFile land in the same creation_time bucket** - even if event_time spans days
2. **Annotations join correctly** because they're partitioned by the same creation_time
3. **Event_time partitioning in aggregated/** enables efficient time-range queries
4. **mtime tracking** prevents redundant recompaction of unchanged data
5. **Hour-by-hour archival** allows incremental progress (don't wait for full day)
6. **Daily rollup** reduces file count and changes resolution for historical data
