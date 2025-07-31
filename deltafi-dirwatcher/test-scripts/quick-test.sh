#!/bin/bash
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#


# Quick Test Script for DeltaFi File Ingress Watcher
# This script runs a few basic tests to verify the watcher is working

set -e

# Configuration
WATCH_DIR="./quick-test-dir"
PID_FILE="./watcher.pid"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

cleanup() {
    log "Cleaning up..."
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") 2>/dev/null || true
        rm -f "$PID_FILE"
    fi
    if [ -f "mock-server.pid" ]; then
        kill $(cat mock-server.pid) 2>/dev/null || true
        rm -f mock-server.pid
    fi
    rm -rf "$WATCH_DIR"
    rm -f watcher.log mock-server.log
}

# Setup test environment
setup() {
    log "Setting up test environment..."
    mkdir -p "$WATCH_DIR/source1"
    
    # Create metadata file
    cat > "$WATCH_DIR/source1/.default_metadata.yaml" << EOF
environment: test
priority: high
source: quick-test
EOF

    success "Test environment setup complete"
}

# Start the watcher
start_watcher() {
    log "Starting mock DeltaFi API server..."
    
    # Start mock server in background
    python3 test-scripts/mock-server.py --port 8083 > mock-server.log 2>&1 &
    echo $! > mock-server.pid
    
    # Wait for mock server to start
    sleep 2
    
    log "Starting watcher..."
    
    # Set required environment variables
    export DIRWATCHER_WATCH_DIR="$WATCH_DIR"
    export DELTAFI_URL="http://localhost:8083"
    export DIRWATCHER_WORKERS="1"
    export DIRWATCHER_BUFFER_SIZE="1048576"
    export DIRWATCHER_MAX_FILE_SIZE="10485760"
    export DIRWATCHER_RETRY_PERIOD="1"
    export DIRWATCHER_SETTLING_TIME="500"
    
    # Start watcher in background
    ./deltafi-dirwatcher > watcher.log 2>&1 &
    echo $! > "$PID_FILE"
    
    # Wait for watcher to start
    sleep 3
    
    success "Watcher started successfully"
}

# Stop the watcher
stop_watcher() {
    log "Stopping watcher and mock server..."
    
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") 2>/dev/null || true
        rm -f "$PID_FILE"
    fi
    
    if [ -f "mock-server.pid" ]; then
        kill $(cat mock-server.pid) 2>/dev/null || true
        rm -f mock-server.pid
    fi
    
    success "Cleanup complete"
}

# Test basic functionality
test_basic_functionality() {
    log "Testing basic functionality..."
    
    # Create a test file
    echo "This is a test file created at $(date)" > "$WATCH_DIR/source1/test.txt"
    
    # Wait for processing
    sleep 3
    
    # Check if file was processed
    if [ ! -f "$WATCH_DIR/source1/test.txt" ]; then
        success "File was processed and deleted"
    else
        log "File still exists - checking logs..."
        if grep -q "Successfully uploaded file" watcher.log; then
            success "File was processed (upload successful)"
        else
            log "File processing status unclear"
        fi
    fi
}

# Test multiple files
test_multiple_files() {
    log "Testing multiple files..."
    
    # Create multiple files
    for i in {1..3}; do
        echo "Test file $i" > "$WATCH_DIR/source1/multi_$i.txt"
    done
    
    # Wait for processing
    sleep 5
    
    # Count remaining files
    remaining=$(find "$WATCH_DIR/source1" -name "multi_*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "All files were processed"
    else
        log "$remaining files still exist"
    fi
}

# Main execution
main() {
    log "Starting quick test..."
    
    # Setup trap for cleanup
    trap cleanup EXIT
    
    # Setup and start
    setup
    start_watcher
    
    # Run tests
    test_basic_functionality
    test_multiple_files
    
    # Stop watcher
    stop_watcher
    
    log "Quick test completed!"
    log "Check watcher.log for detailed output"
}

# Run main function
main "$@" 
