# Post-Installation Checklist

You've installed DeltaFi. Now what? This guide walks through the essential configuration steps to get your system production-ready.

## Pre-UI Setup (TUI)

Before accessing the web UI, configure these TUI settings.

### Set the Admin Password

```bash
deltafi set-admin-password
```

This sets the password for the `admin` user. You'll need this to log into the UI.

### Configure Your Domain

By default, DeltaFi uses `local.deltafi.org`. For production deployments, update your domain in `site/values.yaml`:

```yaml
ingress:
  domain: deltafi.example.com
```

Then apply the changes:

```bash
deltafi up
```

### SSL Certificates (Production)

For HTTPS access, configure TLS in `site/values.yaml`:

```yaml
ingress:
  domain: deltafi.example.com
  tls:
    enabled: true
    secrets:
      default: ssl-secret  #Default setting
```

Create the Kubernetes secret with your certificates:

```bash
deltafi ssl load -c your-cert.crt \
        -k your-key.key --secret-name ssl-secret
```

```bash
deltafi ssl ca-chain load -c your-ca-chain.crt
```

See [Authentication](/config/authentication) for details on client certificate setup.


## Verify the Installation

### Access the UI

Navigate to your configured domain (default: [http://local.deltafi.org](http://local.deltafi.org)) and log in with the admin credentials.

### Add and Configure Users

By default, in `basic` mode the only initial user is Admin.

If your system is going to use `basic` additional users should be created with roles (and permissions) correlating to their responsibilities.

If your system is going to use `cert` authentication additional users will need to be created. You **cannot** access the system with the Admin user and password in cert mode.

See [Authentication](/config/authentication) for details on adding users  certificate setup.

#### Configure Ingres NPEs

Regardless of if your system is configured with `basic` or `cert` it is best practice to configure an NPE Ingress user for your data provider (if using Rest-Data-Sources)

This should be configured the same as a user (see above) but given the role `Ingress Only`. This will allow the NPE permission to post to data sources, and to the annotation and survey APIs

### Authentication Mode

DeltaFi supports three authentication modes:

| Mode | Use Case |
|------|----------|
| `basic` | Username/password (default) |
| `cert` | Client certificate authentication |
| `disabled` | Development only - no authentication |

Configure in `site/values.yaml`:

```yaml
deltafi:
  auth:
    mode: basic  # or cert, disabled
```

### Run the Smoke Test

DeltaFi ships with a built-in smoke test flow. This is the quickest way to verify your installation works end-to-end:

```bash
deltafi integration-test run --like "smoke"
```

Or manually ingest a test file:

```bash
echo "test data" > /tmp/test.txt
deltafi ingress -d smoke /tmp/test.txt --watch
```

If successful, you'll see the DeltaFile progress through ingress → transform → egress.

## Install Plugins

Check what plugins are installed:

```bash
deltafi plugin list
```

A fresh installation includes only the core plugin. Install the plugins your use case requires:

```bash
# Example: Install the XML plugin
deltafi plugin install docker.io/deltafi/deltafi-xml:2.39.0

# List available actions from a plugin
deltafi plugin describe deltafi-xml
```

::: tip Common Mistake
Forgetting to install plugins before trying to configure flows that depend on them. If your flows show as "invalid", check that all required plugins are installed.
:::

## Start Your Flows

Flows are **stopped by default** when plugins are installed. You must explicitly start them.

### List All Flows

```bash
deltafi data-source list
deltafi transform list
deltafi data-sink list
```

Or view the flow graph:

```bash
deltafi graph --all
```

### Start Flows

```bash
# Start a specific data source
deltafi data-source start my-data-source

# Start a transform
deltafi transform start my-transform

# Start a data sink
deltafi data-sink start my-data-sink
```

You can also start/stop flows from the UI under **Configuration → DataSources, Configuration → Transforms, & Configuration → DataSinks**.

### Configure Plugin Variables

Many plugins expose variables that customize behavior. View and set them:

```bash
# List all plugin variables
deltafi variables list

# Set a variable
deltafi variables set <plugin> <variable> <value>
```

You can also configure in the UI under **System → Plugin Variables**.

## Configure System Properties

System properties control DeltaFi's runtime behavior. View them with:

```bash
deltafi properties view
```

Or in the UI under **System → System Properties**.

### Data Retention (Critical)

These settings control how long data lives in your system:

| Property | Default | Description                                         |
|----------|---------|-----------------------------------------------------|
| `ageOffDays` | 13 | Maximum days before DeltaFiles are deleted          |
| `diskSpacePercentThreshold` | 80% | Delete content when storage disk usage exceeds this |
| `metadataDiskSpacePercentThreshold` | 40% | Delete metadata when database disk exceeds this     |
| `deleteFrequency` | PT5M | How often delete policies run                       |

::: tip 
Set `ageOffDays` appropriately for your retention requirements. The default of 13 days may be too short for compliance requirements or too long for high-volume systems. 
:::


### Performance Tuning

| Property | Default | Description | Sizing Guidance |
|----------|---------|-------------|-----------------|
| `coreServiceThreads` | 16 | Threads for core processing | Match to available CPU cores |
| `coreInternalQueueSize` | 64 | Internal event queue depth | Increase for bursty workloads |
| `inMemoryQueueSize` | 5000 | Actions queued in memory before overflow to disk | Higher = more memory, lower latency |
| `cacheSyncDuration` | PT30S | How often to sync DeltaFiles to database | Lower = more durability, higher DB load |

**Sizing `inMemoryQueueSize`:**
- Each queued action consumes memory
- When exceeded, actions overflow to PostgreSQL (cold queue)
- Cold queue is slower but unlimited
- Start with default (5000), increase if you see cold queue growth

```bash
# Check cold queue status
deltafi status
```

### Ingress Controls

| Property | Default | Description |
|----------|---------|-------------|
| `ingressEnabled` | true | Global ingress on/off |
| `ingressDiskSpaceRequirementInMb` | 1000 | Auto-disable ingress below this free space |

::: tip
If free disk space in the storage volume is less than `ingressDiskSpaceRequirementsInMb` *all* ingress will be halted. Ensure your data provider can handle the potential for backpressure
and ensure that your configuration is sufficient to allow any inFlight processing to complete
:::

### Action Timeout

| Property | Default | Description |
|----------|---------|-------------|
| `requeueDuration` | PT5M | Time before requeuing a stuck action |
| `actionExecutionTimeout` | disabled | Kill action pod if exceeded (must be > 30s) |
| `actionExecutionWarning` | disabled | Log warning if action exceeds this duration |

For long-running actions (large file processing), increase `requeueDuration`.

## Set Up Delete Policies

Beyond global age-off, you can create targeted delete policies.

### View Existing Policies

In the UI: **System → Delete Policies**

### Common Delete Policies

**Timed Delete Policy**: Delete DeltaFiles older than N days
- Can target specific flows
- Can delete only content (keep metadata) or both

::: tip
Create a disk space policy **Properties → diskSpacePercentThreshold** as a safety net even if you have timed policies. It prevents disk-full emergencies.
:::

## Configure Auto-Resume (Optional)

Auto-resume policies automatically retry errored DeltaFiles matching certain criteria. See [Auto Resume](/advanced/auto_resume) for details.

```bash
# View auto-resume policies
deltafi resume-policy list
```

## Final Verification

### System Health Check

```bash
deltafi status
```

All services should show healthy. Watch for:
- ❌ Services not running
- ⚠️ Cold queue growth (indicates processing backlog)
- ⚠️ Disk space warnings

### End-to-End Test

1. Ensure your data source flow is **RUNNING**
2. Ingest a test file:
   ```bash
   deltafi ingress -d <your-data-source> <test-file> --watch
   ```
3. Verify it completes successfully through all flows
4. Check the UI for the DeltaFile details

### Dashboard

For ongoing monitoring:

```bash
deltafi dashboard
```

## Quick Reference

```bash
# Status and health
deltafi status
deltafi dashboard

# Flows
deltafi data-source list
deltafi data-source start <name>
deltafi graph --all

# Plugins
deltafi plugin list
deltafi plugin install <image>

# System properties
deltafi properties view
deltafi properties set <name> <value>

# Testing
deltafi integration-test run --like "smoke"
deltafi ingress -d <data-source> <file> --watch
```

## Next Steps

- [Monitoring & Metrics](/operating/metrics) - Set up Grafana dashboards
- [Error Handling](/operating/errors) - Handle errors and retries
- [Flows Reference](/flows) - Deep dive into flow configuration
