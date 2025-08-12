# DeltaFi Action Kit

This project provides a Python implementation of the DeltaFi Action Kit. The DeltaFi Action Kit is a setup of modules which simplify the creation of a DeltaFi Plugin.

## Development Setup

This project uses [uv](https://github.com/astral-sh/uv) for dependency management and building. uv is a fast Python package installer and resolver.

### Prerequisites

- Python 3.13 or higher
- uv (will be installed automatically via the build process)

### Building and Testing

The project uses Gradle to orchestrate the build process with uv:

```bash
# Build the project
./gradlew assemble

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

### Manual Development

For manual development without Gradle:

```bash
cd src

# Install uv if not already installed
brew install uv || pip install uv

# Install dependencies
uv pip install -e .[test]

# Run tests
uv run pytest

# Build package
uv build
```
