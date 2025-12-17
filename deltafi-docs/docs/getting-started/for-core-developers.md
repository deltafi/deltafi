# Core Developer's Guide

This guide is for developers who want to contribute to DeltaFi's core codebase.

## Prerequisites

- Docker Desktop with at least 4 cores and 8GB RAM allocated
- Git

## Setup

### 1. Install DeltaFi

Follow the [Quick Start](/getting-started/quick-start) to install DeltaFi. This installs the **DeltaFi TUI** (Terminal User Interface) - a command-line tool that manages the entire DeltaFi system. See [Understanding the TUI](/operating/TUI#understanding-the-tui) for how it works, or read on for the basics.

When the installation wizard asks about your role, select **Core Development**. When asked about orchestration mode, select **Compose** to start (you can switch later with `deltafi config`).

This will:
- Set up a DeltaFi environment using Docker Compose
- Clone the DeltaFi repository into the `repos/deltafi/` directory

### 2. Verify the Installation

After setup completes:

```bash
deltafi status
```

The DeltaFi UI will be available at [http://local.deltafi.org](http://local.deltafi.org).

## Directory Structure

After installation, your directory will look like:

```
~/deltafi/                    # Install root (DELTAFI_INSTALL_DIR)
├── deltafi                   # TUI binary
├── deltafi.yaml              # Main configuration
├── VERSION
├── config/                   # Runtime configuration
├── data/                     # Runtime data
├── repos/                    # Source repositories
│   └── deltafi/              # Core repository (cloned automatically)
└── site/                     # Site-specific customization
    ├── compose.yaml          # Docker Compose overrides
    └── values.yaml           # Helm values overrides
```

## Development Workflow

### How Deployment Works

Understanding how code gets from your editor to running containers:

**For Compose mode:**
1. `./gradlew install` builds Docker images locally and tags them (e.g., `deltafi/deltafi-core:2.x.x`)
2. Gradle calls `deltafi up --force` to restart services with the new images
3. Docker Compose pulls the locally-tagged images and restarts affected containers

**For KinD mode:**
1. `./gradlew install` builds Docker images locally
2. Gradle loads images into the KinD cluster (`deltafi kind load <image>`)
3. Gradle calls `deltafi up --force` to restart pods with the new images

**Key commands:**
- `deltafi up` - Starts DeltaFi (or restarts with `--force` to pick up new images)
- `deltafi down` - Stops all DeltaFi services
- `deltafi status` - Shows what's running

### Building and Deploying Changes

From the `repos/deltafi/` directory:

```bash
# Build and deploy ALL components
./gradlew install

# Build and deploy just deltafi-core (faster iteration)
./gradlew :deltafi-core:install

# Build and deploy just deltafi-action-kit
./gradlew :deltafi-action-kit:install

# Build the TUI
./gradlew tui

# Publish gradle-plugin to local Maven (for testing plugin changes)
./gradlew :gradle-plugin:publishToMavenLocal
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :deltafi-core:test

# Run a specific test class
./gradlew :deltafi-core:test --tests "SomeTestClass"
```

### Key Directories in the Repository

| Directory | Description |
|-----------|-------------|
| `deltafi-core/` | Core platform (Java/Spring Boot) |
| `deltafi-action-kit/` | SDK for building actions (Java) |
| `deltafi-core-actions/` | Built-in actions |
| `deltafi-common/` | Shared types and utilities |
| `gradle-plugin/` | Gradle plugin for building DeltaFi plugins |
| `tui/` | Command-line interface (Go) |
| `charts/` | Helm charts for Kubernetes |
| `compose/` | Docker Compose configuration |

### Orchestration Modes

DeltaFi supports two orchestration modes: **Compose** (Docker Compose, recommended for most development) and **KinD** (Kubernetes in Docker, for testing Helm charts). Switch between them with `deltafi config`.

**Important**: If you're making changes to orchestration files (`compose/` or `charts/`), you generally need to make parallel changes in both and test in both modes.

## Changing the Core Repository

During initial setup, the wizard prompts for the core repository URL. To change it later (e.g., switching to an internal fork):

1. Edit `config/config.yaml` and update the `coreRepo` value:

```yaml
development:
  repoPath: /path/to/deltafi/repos
  coreRepo: git@gitlab.com:your-org/deltafi.git
```

2. Update the git remote in your existing clone:

```bash
cd repos/deltafi
git remote set-url origin git@gitlab.com:your-org/deltafi.git
git fetch origin
```

## Submitting Changes

### 1. Create a Branch

```bash
cd repos/deltafi
git checkout -b feature/my-feature
```

### 2. Make Changes and Test

```bash
./gradlew install
./gradlew test
```

### 3. Add a Changelog Entry

```bash
bin/changelog -e
```

This creates a file in `CHANGELOG/unreleased/<branch-name>.md`.

### 4. Submit a Merge Request

Push your branch and create a merge request on GitLab targeting `main`.

## Useful Commands

| Command | Description |
|---------|-------------|
| `deltafi status` | Check system health |
| `deltafi up` | Start DeltaFi |
| `deltafi down` | Stop DeltaFi |
| `deltafi dashboard` | View metrics |
| `deltafi search` | Search DeltaFiles |

## Reference

- [TUI Reference](/operating/TUI) - Command-line interface documentation
- [Contributing](/contributing) - Merge request process
- [Architecture](/advanced/architecture) - System design
