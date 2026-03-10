# Egress Sink

The Egress Sink is a lightweight HTTP service that captures data egressed from DeltaFi flows and saves it to disk. It provides a simple way to inspect egressed output during development, testing, and debugging without needing an external system to receive the data.

## Enabling the Egress Sink

The Egress Sink is enabled by default. To disable it, set `deltafi.egress_sink.enabled` to `false` in `site/values.yaml`:

```yaml
deltafi:
  egress_sink:
    enabled: false
```

## How It Works

When a DeltaFi egress action sends data to the Egress Sink, the service:

1. Reads the `DeltafiMetadata` HTTP header, which contains the `filename` and `flow` name as JSON.
2. Creates a directory for the flow if it doesn't exist.
3. Saves the request body as a file.
4. Optionally saves the metadata alongside the file.

Files are organized by flow name:

```
egress-sink/
├── flow-a/
│   ├── document.pdf
│   ├── document.pdf.POST.metadata.json
│   ├── report.csv
│   └── report.csv.POST.metadata.json
└── flow-b/
    ├── output.json
    └── output.json.POST.metadata.json
```

Metadata files are named `{filename}.{HTTP_METHOD}.metadata.json` and contain the raw JSON from the `DeltafiMetadata` header. If the same filename is sent to the same flow again, the previous file is overwritten.

### Compose

Egressed files are saved to `${DATA_DIR}/egress-sink/` on the host.

### Kubernetes

Egressed files are saved to the `deltafi-egress-sink` PersistentVolumeClaim, mounted at `/data/deltafi/egress-sink` inside the container. The pod is scheduled on nodes labeled `node-role.deltafi.org/storage: "true"`.

## Configuration

Configure the Egress Sink through `site/values.yaml` under `deltafi.egress_sink`:

```yaml
deltafi:
  egress_sink:
    enabled: true
    drop_metadata: false
```

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable or disable the Egress Sink service |
| `drop_metadata` | `false` | When `true`, metadata files are not saved alongside content files |

Set `drop_metadata` to `true` to save disk space when you only need the egressed content and don't need the metadata for debugging.

## HTTP Endpoints

The Egress Sink exposes several endpoints:

### `/` — File Sink (Primary)

Receives egressed data from DeltaFi flows. Requires a `DeltafiMetadata` header containing JSON with `filename` and `flow` fields:

```
POST / HTTP/1.1
DeltafiMetadata: {"filename": "output.pdf", "flow": "pdf-processing"}
Content-Type: application/octet-stream

<file content>
```

Returns HTTP 200 on success, 400 if metadata is missing or invalid, or 500 on write failure.

### `/blackhole` — Discard Endpoint

Accepts and discards request bodies without writing to disk. Supports an optional `latency` query parameter to simulate slow responses:

```
POST /blackhole?latency=0.5s HTTP/1.1
```

Useful for performance testing and throughput measurement.

### `/capture` — Timestamped Capture

Saves request bodies with nanosecond-timestamped filenames (`{timestamp}.bin`) into a `capture/` subdirectory. No metadata header required. Useful for raw data inspection when you don't need flow-based organization.

### `/probe` — Health Check

Returns HTTP 200. Used by container health probes.
