# Consolidated Logging in Compose

DeltaFi's Docker Compose orchestration includes a consolidated logging system that collects, processes, and stores logs from all DeltaFi services. This system provides easy access to logs through a web interface and ensures proper log rotation and management.

## Overview

The logging stack consists of three main components:

1. **Vector** - Collects and processes logs from all DeltaFi containers
2. **Dozzle** - Provides a web-based log viewer accessible through the DeltaFi UI
3. **Logrotate** - Manages log file rotation and retention

All logging components are optional and can be enabled or disabled via configuration.

## Enabling Logging

Logging is enabled by default in Compose deployments. To disable it, set the following in your `site/values.yaml`:

```yaml
deltafi:
  logs:
    enabled: false
```

When disabled, the Vector, Dozzle, and Logrotate containers will not be started.

## Log Collection

### Vector Log Aggregation

Vector collects logs from all Docker containers that have the `deltafi-group` label. This ensures that only DeltaFi-related containers have their logs collected, preventing logs from unrelated containers on the system from being included.

Vector processes logs in the following way:

1. **Collection**: Collects logs from all containers with the `deltafi-group` label
2. **Parsing**: Parses JSON-formatted logs and extracts metadata
3. **Routing**: Separates audit logs from regular application logs
4. **Output**: Writes logs to the local filesystem

### Log Output Format

#### Regular Logs

Regular application logs are written to individual files per container:

```
/logs/<container-name>.log
```

Each log entry is a flattened JSON object containing:
- `level` - Log level (INFO, WARN, ERROR, etc.)
- `message` - The log message
- `timestamp` - Timestamp of the log entry
- `container` - Container name
- `stream` - Output stream (stdout/stderr)
- Additional fields from the original log (loggerName, threadName, etc.)

Example log entry:
```json
{
  "container": "deltafi-core-scheduler",
  "level": "WARN",
  "loggerName": "org.deltafi.core.delete.DiskSpaceDelete",
  "message": "No DeltaFiles deleted -- disk is above threshold",
  "stream": "stdout",
  "threadName": "threadPoolTaskScheduler-6",
  "timestamp": "2025-12-03T00:05:32.095521511Z"
}
```

#### Audit Logs

Audit logs are consolidated into a single file:

```
/logs/audit.log
```

Audit logs are automatically detected (logs with `loggerName: "AUDIT"`) and written separately. Each audit log entry contains:
- `level` - Log level
- `message` - The audit message
- `timestamp` - Timestamp of the audit event
- `user` - User who triggered the action
- Additional audit-specific fields

Example audit log entry:
```json
{
  "level": "INFO",
  "message": "created event f2864a64-50cf-45fd-ac89-ce52476fcf9b",
  "timestamp": "2025-12-02T23:48:43.694Z",
  "user": "TUI"
}
```

## Viewing Logs

### Dozzle Web Interface

Dozzle provides a web-based log viewer that is integrated into the DeltaFi UI Administration sidebar. To access it:

1. Log into the DeltaFi UI as an administrator
2. Click on **Logs** in the Administration sidebar (visible only in Compose mode)
3. The Dozzle interface will open, showing real-time logs from all DeltaFi containers

The Dozzle interface allows you to:
- View logs from all containers in real-time
- Filter logs by container name
- Search within logs
- View logs in a scrollable, color-coded format

### Direct File Access

Logs are also available directly on the filesystem at the location specified by `LOGS_DIR` (typically `~/deltafi/logs`). You can access them using standard command-line tools:

```bash
# View logs for a specific container
tail -f ~/deltafi/logs/deltafi-core-scheduler.log

# View audit logs
tail -f ~/deltafi/logs/audit.log

# Search logs
grep "ERROR" ~/deltafi/logs/*.log
```

## Log Rotation

Logrotate automatically manages log file rotation and retention to prevent disk space issues.

### Configuration

Log rotation can be configured in `site/values.yaml`:

```yaml
deltafi:
  logs:
    enabled: true
    logrotate:
      interval: daily          # Rotation interval: hourly, daily, weekly, monthly
      schedule:
        max_size: 50M          # Rotate when file reaches this size
        keep: 30               # Keep regular logs for 30 days
      audit_schedule:
        max_size: 100M         # Rotate audit logs when file reaches this size
        keep: 365              # Keep audit logs for 365 days
```

### Default Behavior

- **Regular logs**: Rotated daily or when they reach 50M in size, kept for 30 days by default
- **Audit logs**: Rotated daily or when they reach 100M in size, kept for 365 days by default
- Rotation occurs based on whichever condition is met first (time interval or size threshold)
- Old log files are automatically compressed and removed after the retention period

## Log Storage Location

Logs are stored in the directory specified by the `LOGS_DIR` environment variable. The default location is `{installDirectory}/logs`, where `installDirectory` is set in your `config.yaml` file (typically `~/.deltafi/config.yaml`).

For example, if your `config.yaml` contains:

```yaml
installDirectory: /Users/myuser/deltafi
```

Then logs will be stored at `/Users/myuser/deltafi/logs`. In a standard installation with `installDirectory: ~/deltafi`, logs will be at `~/deltafi/logs`.

The logs directory will be created automatically when logging is enabled.

## Vector Configuration

Vector's configuration is automatically generated by the DeltaFi TUI and stored at `{CONFIG_DIR}/vector.yaml`. The configuration includes:

- Container label filtering (only `deltafi-group` labeled containers)
- JSON log parsing
- Audit log detection and routing
- Log flattening and cleanup
- File output configuration

The Vector configuration is read-only and managed automatically. Manual modifications are not recommended as they will be overwritten on the next `deltafi up` command.

## Health Monitoring

Vector exposes a health check endpoint on port 8686. The health status can be checked:

```bash
# From the host
curl http://localhost:8686/health

# From within the Docker network
curl http://deltafi-vector:8686/health
```

## Troubleshooting

### Logs Not Appearing

If logs are not appearing in Dozzle or the log files:

1. Verify logging is enabled: Check `deltafi.logs.enabled` in `site/values.yaml`
2. Check Vector container status: `docker ps | grep vector`
3. Verify container labels: Ensure containers have the `deltafi-group` label
4. Check Vector logs: `docker logs deltafi-vector`

### Missing Container Logs

If a specific container's logs are missing:

1. Verify the container has the `deltafi-group` label
2. Check that the container is running: `docker ps`
3. Verify Vector is collecting from that container: Check Vector logs for exclusion messages

### Disk Space Issues

If log files are consuming too much disk space:

1. Check logrotate configuration and retention settings
2. Verify logrotate is running: `docker ps | grep logrotate`
3. Manually rotate logs if needed: `docker exec deltafi-logrotate logrotate -f /etc/logrotate.conf`

## Related Documentation

- [Metrics and Monitoring](/operating/metrics) - System metrics and Grafana dashboards
- [TUI (Text User Interface)](/operating/TUI) - Command-line interface for managing DeltaFi
- [Docker Compose Installation](/getting-started/quick-start) - Compose installation and configuration
