# Manual Testing Scripts for DeltaFi File Ingress Watcher

This directory contains comprehensive manual testing scripts to verify the watcher functionality in real-world scenarios.

## Overview

The testing scripts are designed to help you manually verify that the watcher system works correctly under various conditions, including:

- **Normal operations**: Basic file creation, processing, and cleanup
- **Edge cases**: Unusual file names, special characters, empty files
- **Error conditions**: Network issues, permission problems, resource constraints
- **Performance**: Stress testing, concurrent operations, large files
- **Retry mechanisms**: Testing error recovery and retry logic

## Scripts Overview

### 1. `test-watcher.sh` - Comprehensive Testing
The main testing script that covers all basic functionality.

**Features:**
- Basic file creation and processing
- Large file handling
- Rapid file creation
- Special character handling
- Directory creation and subdirectory processing
- File deletion during processing
- Metadata file updates
- Concurrent file creation
- File size limits
- Hidden file handling
- Network interruption simulation
- Stress testing

**Usage:**
```bash
# Run all tests
./test-watcher.sh

# Use custom watch directory
WATCH_DIR=/path/to/watch ./test-watcher.sh
```

### 2. `test-edge-cases.sh` - Edge Case Testing
Tests various edge cases and unusual scenarios.

**Features:**
- Empty files
- Very small files
- Files with very long names
- Files with unusual characters
- Files being written to
- Symlinks
- Files with no extensions
- Files with multiple extensions
- Deeply nested directories
- Binary content
- Unicode characters
- Read-only files
- Large content files
- Rapid create/delete operations
- Directory permissions

**Usage:**
```bash
# Run edge case tests
./test-edge-cases.sh

# Use custom watch directory
WATCH_DIR=/path/to/watch ./test-edge-cases.sh
```

### 3. `test-retry-mechanisms.sh` - Retry and Error Testing
Tests retry mechanisms and error handling scenarios.

**Features:**
- Files that disappear during processing
- Permission issues
- Inaccessible directories
- Locked files
- Network timeout simulation
- Corrupted content
- Continuously modified files
- Slow processing simulation
- Retry queue overflow
- System resource constraints
- Concurrent access
- Memory pressure simulation

**Usage:**
```bash
# Run retry mechanism tests
./test-retry-mechanisms.sh

# Use custom watch directory
WATCH_DIR=/path/to/watch ./test-retry-mechanisms.sh
```

### 4. `monitor-watcher.sh` - Real-time Monitoring
Interactive monitoring and testing tool.

**Features:**
- Real-time filesystem monitoring
- Log file monitoring
- Interactive test file creation
- System statistics
- Directory state inspection
- Watcher status checking

**Usage:**
```bash
# Start monitoring
./monitor-watcher.sh monitor

# Check watcher status
./monitor-watcher.sh status

# Show directory state
./monitor-watcher.sh state

# Show system statistics
./monitor-watcher.sh stats

# Interactive test file creation
./monitor-watcher.sh interactive

# Show help
./monitor-watcher.sh help
```

## Prerequisites

### System Requirements
- **Bash**: All scripts require bash shell
- **Core utilities**: `find`, `dd`, `stat`, `chmod`, `rm`, `mkdir`
- **Optional**: `inotifywait` for better filesystem monitoring (Linux)

### Watcher Setup
Before running the tests, ensure the watcher is running:

```bash
# Start the watcher (adjust path as needed)
./deltafi-dirwatcher --watch-dir ./test-watch-dir
```

## Quick Start Guide

### 1. Basic Testing
```bash
# Make scripts executable
chmod +x test-scripts/*.sh

# Run comprehensive tests
./test-scripts/test-watcher.sh
```

### 2. Edge Case Testing
```bash
# Test edge cases
./test-scripts/test-edge-cases.sh
```

### 3. Retry Mechanism Testing
```bash
# Test retry mechanisms
./test-scripts/test-retry-mechanisms.sh
```

### 4. Interactive Monitoring
```bash
# Start monitoring
./test-scripts/monitor-watcher.sh monitor

# In another terminal, create files interactively
./test-scripts/monitor-watcher.sh interactive
```

## Test Scenarios Explained

### Normal Operations
- **Basic file creation**: Simple text files are created and should be processed
- **Large files**: Files up to the configured size limit should be processed
- **Rapid creation**: Multiple files created quickly should all be processed
- **Special characters**: Files with spaces, dashes, underscores should be handled

### Edge Cases
- **Empty files**: Zero-byte files should be processed
- **Long filenames**: Files with very long names should be handled
- **Unicode**: Files with international characters should work
- **Binary content**: Non-text files should be processed
- **Symlinks**: Symbolic links should be handled appropriately

### Error Conditions
- **Disappearing files**: Files deleted during processing should be handled gracefully
- **Permission issues**: Files with restricted permissions should be handled
- **Network timeouts**: Simulated network issues should trigger retries
- **Resource constraints**: System load should not prevent processing

### Performance Testing
- **Stress testing**: Many files created rapidly
- **Concurrent access**: Multiple processes creating files simultaneously
- **Memory pressure**: Processing under memory constraints
- **Large files**: Files that take time to process

## Environment Variables

All scripts support the following environment variables:

- `WATCH_DIR`: Directory to watch (default: `./test-watch-dir`)
- `LOG_FILE`: Log file for monitoring (default: `./watcher-monitor.log`)
- `PID_FILE`: PID file location (default: `./watcher.pid`)

## Expected Behavior

### Successful Processing
- Files should be deleted after successful processing
- No error messages in logs
- Processing time should be reasonable

### Error Handling
- Failed files should be retried
- Error messages should be logged
- System should continue processing other files

### Performance
- Processing should not block indefinitely
- Memory usage should remain reasonable
- CPU usage should not spike excessively

## Troubleshooting

### Common Issues

1. **Scripts not executable**
   ```bash
   chmod +x test-scripts/*.sh
   ```

2. **Watcher not running**
   - Check if the watcher process is running
   - Verify the watch directory exists
   - Check logs for startup errors

3. **Files not being processed**
   - Check watcher logs for errors
   - Verify file permissions
   - Check network connectivity to DeltaFi API

4. **Permission denied errors**
   - Ensure scripts have execute permissions
   - Check directory write permissions
   - Verify user has access to watch directory

### Debugging Tips

1. **Enable verbose logging**
   ```bash
   # Set log level to debug
   export LOG_LEVEL=debug
   ```

2. **Monitor in real-time**
   ```bash
   # Use the monitoring script
   ./test-scripts/monitor-watcher.sh monitor
   ```

3. **Check system resources**
   ```bash
   # Monitor CPU and memory usage
   top -p $(cat watcher.pid)
   ```

4. **Verify file system events**
   ```bash
   # Install inotify-tools for better monitoring
   sudo apt-get install inotify-tools  # Ubuntu/Debian
   brew install inotify-tools          # macOS
   ```

## Integration with CI/CD

These scripts can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions workflow
- name: Test Watcher Functionality
  run: |
    chmod +x test-scripts/*.sh
    ./test-scripts/test-watcher.sh
    ./test-scripts/test-edge-cases.sh
```

## Contributing

When adding new test scenarios:

1. Follow the existing script structure
2. Add appropriate logging and error handling
3. Include cleanup in the script
4. Document the test scenario
5. Update this README if needed

## Support

For issues with the testing scripts:

1. Check the script logs for error messages
2. Verify the watcher is running correctly
3. Test with a simple file creation first
4. Check system resources and permissions
5. Review the watcher application logs 
