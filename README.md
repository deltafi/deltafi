<img src="public/images/deltafi-logo-side-by-side.svg" width="500">

# DeltaFi: The Open Source Platform for Data Transformation

[![SYSTOLIC](public/images/powered.svg)](https://www.systolic.com)
[![Apache 2.0](public/images/apache2.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Contributor Covenant](public/images/contributor-covenant.svg)](CODE_OF_CONDUCT.md)
[![Docker Pulls](https://img.shields.io/docker/pulls/deltafi/deltafi-api?color=yellow&logo=docker)](https://hub.docker.com/u/deltafi)

DeltaFi is a flexible, code-light data transformation and normalization platform.

**The problem**: Data pipelines are messy. Sources change formats without warning. When something breaks, tracing the issue is painful.

**DeltaFi helps by**:
- Tracking every piece of data through the system with full provenance
- Letting you inspect data and metadata at each transformation step
- Providing resume/replay when errors occur - fix the problem, rerun the data
- Keeping business logic simple - most work is configuration, not code

**📚 Documentation: [docs.deltafi.org](https://docs.deltafi.org)**

## Quick Start

For **operators** (running DeltaFi without development):

```bash
# Set your install location and version
DELTAFI_INSTALL_DIR=~/deltafi
VERSION=2.38.0  # Check gitlab.com/deltafi/deltafi/-/releases

# Extract the TUI binary from Docker
mkdir -p $DELTAFI_INSTALL_DIR
docker run --rm -v "${DELTAFI_INSTALL_DIR}":/deltafi deltafi/deltafi:${VERSION}-darwin-arm64

# Run the installation wizard
"${DELTAFI_INSTALL_DIR}"/deltafi
```

For **plugin developers** or **core developers**, use the same installation steps and select your role when the wizard asks.

See the [Quick Start Guide](https://docs.deltafi.org/getting-started/quick-start) for detailed instructions.

## Repository Structure

| Directory | Description |
|-----------|-------------|
| `tui/` | **Command-line interface** - Installation, orchestration, and runtime commands |
| `deltafi-core/` | Core platform services (Java/Spring Boot) |
| `deltafi-action-kit/` | SDK for building custom actions (Java) |
| `deltafi-core-actions/` | Built-in transform, egress, and ingress actions |
| `deltafi-common/` | Shared types and utilities |
| `deltafi-docs/` | Documentation source (VitePress) |
| `deltafi-ui/` | Web GUI (Vue.js) |
| `charts/` | Helm charts for Kubernetes deployment |
| `compose/` | Docker Compose configuration |

## Getting Started Guides

| I want to... | Guide |
|--------------|-------|
| **Run DeltaFi** and process data | [Operator's Guide](https://docs.deltafi.org/getting-started/for-operators) |
| **Build plugins** with custom actions | [Plugin Developer's Guide](https://docs.deltafi.org/getting-started/for-plugin-developers) |
| **Contribute** to DeltaFi core | [Core Developer's Guide](https://docs.deltafi.org/getting-started/for-core-developers) |

## Documentation

- [Concepts](https://docs.deltafi.org/concepts) - Core architecture and terminology
- [TUI Reference](https://docs.deltafi.org/operating/TUI) - Command-line interface
- [Plugin Development](https://docs.deltafi.org/plugins) - Building custom actions
- [Quick Start](https://docs.deltafi.org/getting-started/quick-start) - Installation guide

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to get involved.

## Contact

- **Slack**: Request an invitation at deltafi@systolic.com
- **GitLab**: [gitlab.com/deltafi](https://gitlab.com/deltafi)
- **Email**: info@deltafi.org
