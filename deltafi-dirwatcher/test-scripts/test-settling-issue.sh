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


# Test script to demonstrate the settling issue with continuously written files
# This shows that files are being processed before writing is complete

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓${NC} $1"
}

warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

error() {
    echo -e "${RED}✗${NC} $1"
}

# Configuration
WATCH_DIR="./test-settling"
PID_FILE="settling-test.pid"
LOG_FILE="./settling-test.log"

# Cleanup function
cleanup() {
    log "Cleaning up test environment..."
    rm -rf "$WATCH_DIR"
    rm -f "$PID_FILE"
    rm -f watcher.log
    rm -f mock-server.log
    rm -f mock-server.pid
}

# Setup test environment
setup() {
    log "Setting up settling test environment..."
    
    # Create test directory
    mkdir -p "$WATCH_DIR/source1"
    
    # Create metadata file
    cat > "$WATCH_DIR/source1/.metadata" << EOF
source=test
type=settling-test
EOF
    
    success "Settling test environment setup complete"
}

# Start watcher and mock server
start_watcher() {
    log "Starting mock DeltaFi API server..."
    
    # Start mock server on port 8085
    python3 test-scripts/mock-server.py --port 8085 > mock-server.log 2>&1 &
    echo $! > mock-server.pid
    
    # Wait for server to start
    sleep 2
    
    if kill -0 $(cat mock-server.pid) 2>/dev/null; then
        success "Mock server started successfully (PID: $(cat mock-server.pid))"
    else
        error "Failed to start mock server"
        exit 1
    fi
    
    log "Starting watcher..."
    
    # Export environment variables for watcher
    export DIRWATCHER_WATCH_DIR="$WATCH_DIR"
    export DELTAFI_URL="http://localhost:8085"
    export DIRWATCHER_WORKERS="5"
    export DIRWATCHER_BUFFER_SIZE="1048576"
    export DIRWATCHER_SETTLING_TIME="2000"  # 2 seconds settling time
    
    # Start watcher in background
    ./deltafi-dirwatcher > watcher.log 2>&1 &
    echo $! > "$PID_FILE"
    
    # Wait for watcher to start
    sleep 3
    
    # Check if watcher is running
    if kill -0 $(cat "$PID_FILE") 2>/dev/null; then
        success "Watcher started successfully (PID: $(cat $PID_FILE))"
    else
        error "Failed to start watcher"
        exit 1
    fi
}

# Stop the watcher
stop_watcher() {
    log "Stopping watcher and mock server..."
    
    # Stop watcher
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") 2>/dev/null || true
        rm -f "$PID_FILE"
        success "Watcher stopped"
    fi
    
    # Stop mock server
    if [ -f "mock-server.pid" ]; then
        kill $(cat mock-server.pid) 2>/dev/null || true
        rm -f mock-server.pid
        success "Mock server stopped"
    fi
}

# Test 1: Demonstrate the settling issue
test_settling_issue() {
    log "Test 1: Demonstrating settling issue with continuously written files"
    
    # Create a file that will be written to continuously
    log "Creating file that will be written to for 10 seconds..."
    
    # Start writing to file in background
    (
        for i in {1..20}; do
            echo "Line $i - $(date '+%H:%M:%S.%3N')" >> "$WATCH_DIR/source1/continuous_write.txt"
            sleep 0.5
        done
        echo "Writing completed at $(date '+%H:%M:%S.%3N')" >> "$WATCH_DIR/source1/continuous_write.txt"
    ) &
    
    local write_pid=$!
    
    # Wait a bit for the file to be created and potentially processed
    sleep 3
    
    # Check if file was processed prematurely
    if [ ! -f "$WATCH_DIR/source1/continuous_write.txt" ]; then
        error "File was processed BEFORE writing was complete!"
        log "This demonstrates the settling issue - the file was processed while still being written to"
    else
        success "File still exists (writing in progress)"
        log "File size: $(wc -l < "$WATCH_DIR/source1/continuous_write.txt") lines"
    fi
    
    # Wait for writing to complete
    wait $write_pid
    
    # Check final state
    sleep 5
    
    if [ ! -f "$WATCH_DIR/source1/continuous_write.txt" ]; then
        success "File was processed after writing completed"
        log "Final file was processed correctly"
    else
        warning "File still exists after writing completed"
        log "Final file size: $(wc -l < "$WATCH_DIR/source1/continuous_write.txt") lines"
        log "This suggests the final file was ignored"
    fi
}

# Test 2: Test with different settling times
test_settling_times() {
    log "Test 2: Testing with different settling times"
    
    # Test with 5 second settling time
    log "Testing with 5 second settling time..."
    
    # Stop current watcher
    stop_watcher
    
    # Start new watcher with longer settling time
    export DIRWATCHER_SETTLING_TIME="5000"  # 5 seconds
    
    ./deltafi-dirwatcher > watcher.log 2>&1 &
    echo $! > "$PID_FILE"
    sleep 3
    
    # Create file with continuous writing
    (
        for i in {1..10}; do
            echo "Line $i - $(date '+%H:%M:%S.%3N')" >> "$WATCH_DIR/source1/long_settle.txt"
            sleep 1
        done
        echo "Writing completed at $(date '+%H:%M:%S.%3N')" >> "$WATCH_DIR/source1/long_settle.txt"
    ) &
    
    local write_pid=$!
    
    # Wait for writing to complete
    wait $write_pid
    
    # Check final state
    sleep 10
    
    if [ ! -f "$WATCH_DIR/source1/long_settle.txt" ]; then
        success "File was processed correctly with longer settling time"
    else
        warning "File still exists with longer settling time"
    fi
}

# Main execution
main() {
    log "Starting settling issue testing..."
    
    # Setup trap for cleanup
    trap cleanup EXIT
    
    # Setup test environment
    setup
    
    # Start watcher
    start_watcher
    
    # Run tests
    test_settling_issue
    test_settling_times
    
    # Stop watcher
    stop_watcher
    
    log "Settling issue testing completed!"
    log "Check the log file for detailed results: $LOG_FILE"
    log "Check watcher.log for watcher output"
}

# Run main function
main "$@" 
