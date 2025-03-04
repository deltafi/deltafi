# DeltaFi File Ingress Service

A service that watches directories for new files and forwards them to a specified HTTP endpoint.

## Features

- Watches directories for new files
- Supports multiple data source directories
- Configurable via environment variables
- Graceful shutdown handling
- File size limits and validation
- Automatic file cleanup after successful upload
- Automatic retry of failed uploads
- Structured logging
- Concurrent file processing
- Default metadata support per data source directory

## Default Metadata

Each data source directory can have a default metadata file that automatically applies metadata to all files in that directory and its subdirectories. This is useful for adding common metadata to files based on their source directory.

### Supported Formats

- YAML: `.default_metadata.yaml`
- JSON: `.default_metadata.json`

### Example Structure

```yaml
# .default_metadata.yaml
environment: production
priority: high
classification: unclassified
```

or

```json
{
  "environment": "production",
  "priority": "high",
  "classification": "unclassified"
}
```

### Behavior

- Default metadata is loaded when the directory is first watched
- Changes to metadata files are detected and applied immediately
- New files added to the directory or its subdirectories automatically receive the current metadata
- Metadata is inherited from parent directories if no metadata file exists in the current directory
- Metadata files in subdirectories override parent directory metadata
- Metadata is cleaned up when directories are removed
- The metadata is included in the upload request under the "metadata" field
- Hidden files and directories (starting with ".") are ignored unless they are metadata files or immediate children of the watch directory

### Example Directory Structure

```
watched-dir/
├── source1/
│   ├── .default_metadata.yaml  # Applied to all files in source1/ and subdirectories
│   ├── file1.txt
│   ├── subdir1/
│   │   ├── .default_metadata.yaml  # Overrides source1's metadata for this subdirectory
│   │   └── file2.txt
│   └── subdir2/
│       └── file3.txt  # Uses source1's metadata
└── source2/
    ├── .default_metadata.json
    └── file4.txt
```

In this example:
- `file1.txt` and `file3.txt` use metadata from `source1/.default_metadata.yaml`
- `file2.txt` uses metadata from `subdir1/.default_metadata.yaml`
- `file4.txt` uses metadata from `source2/.default_metadata.json`

## Configuration

Configuration is provided via environment variables:

- `DELTAFI_WATCH_DIR` - Directory to watch for new files (default: "/watched-dir")
- `DELTAFI_URL` - Base URL of the DeltaFi core service (default: "http://deltafi-core-service")
- `DELTAFI_INGRESS_API` - API endpoint path for file ingress (default: "/api/v2/deltafile/ingress")
- `DELTAFI_WORKERS` - Number of concurrent file processing workers (default: 5)
- `DELTAFI_BUFFER_SIZE` - Buffer size for file streaming in bytes (default: 32MB)
- `DELTAFI_MAX_FILE_SIZE` - Maximum allowed file size in bytes (default: 2GB)
- `DELTAFI_RETRY_PERIOD` - Period in seconds between retry attempts for failed uploads (default: 300)

## Usage

1. Create the watch directory:
   ```bash
   mkdir -p watched-dir
   ```

2. Start the service:
   ```bash
   go run cmd/deltafi-file-ingress/main.go
   ```

3. Create data source directories inside the watch directory:
   ```bash
   mkdir -p watched-dir/source1
   mkdir -p watched-dir/source2
   ```

4. Place files in the data source directories to be processed.

## Building

```bash
go build -o deltafi-file-ingress cmd/deltafi-file-ingress/main.go
```

## Docker

Build the Docker image:
```bash
docker build -t deltafi-file-ingress .
```

Run the container:
```bash
docker run -v /path/to/watch:/watched-dir \
  -e DELTAFI_URL=http://deltafi-core-service \
  -e DELTAFI_INGRESS_API=/api/v2/deltafile/ingress \
  -e DELTAFI_MAX_FILE_SIZE=2147483648 \
  -e DELTAFI_RETRY_PERIOD=300 \
  deltafi-file-ingress
```

## Docker Compose

Example docker-compose.yml:
```yaml
version: '3.8'

services:
  file-ingress:
    build: .
    volumes:
      - ./watched-dir:/watched-dir
    environment:
      - DELTAFI_URL=http://deltafi-core-service
      - DELTAFI_INGRESS_API=/api/v2/deltafile/ingress
      - DELTAFI_WORKERS=10
      - DELTAFI_BUFFER_SIZE=33554432
      - DELTAFI_MAX_FILE_SIZE=2147483648
      - DELTAFI_RETRY_PERIOD=300
    restart: unless-stopped
```

Run with Docker Compose:
```bash
docker-compose up -d
```

## Development

Requirements:
- Go 1.23.5 or later
- Make (optional)

## License

Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

Licensed under the Apache License, Version 2.0
