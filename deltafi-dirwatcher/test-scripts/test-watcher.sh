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


# Manual Testing Script for DeltaFi File Ingress Watcher
# This script provides various test scenarios to manually verify watcher functionality

set -e

# Configuration
WATCH_DIR="${WATCH_DIR:-./test-watch-dir}"
LOG_FILE="./watcher-test.log"
PID_FILE="./watcher.pid"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}✓ $1${NC}" | tee -a "$LOG_FILE"
}

warning() {
    echo -e "${YELLOW}⚠ $1${NC}" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}✗ $1${NC}" | tee -a "$LOG_FILE"
}

cleanup() {
    log "Cleaning up test environment..."
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") 2>/dev/null || true
        rm -f "$PID_FILE"
    fi
    if [ -f "mock-server.pid" ]; then
        kill $(cat mock-server.pid) 2>/dev/null || true
        rm -f mock-server.pid
    fi
    rm -rf "$WATCH_DIR"
    rm -f "$LOG_FILE"
    rm -f watcher.log mock-server.log
}

# Setup test environment
setup() {
    log "Setting up test environment..."
    mkdir -p "$WATCH_DIR"
    
    # Create test source directories
    mkdir -p "$WATCH_DIR/source1"
    mkdir -p "$WATCH_DIR/source2"
    mkdir -p "$WATCH_DIR/source3"
    
    # Create metadata files
    cat > "$WATCH_DIR/source1/.default_metadata.yaml" << EOF
environment: test
priority: high
source: manual-test
EOF

    cat > "$WATCH_DIR/source2/.default_metadata.json" << EOF
{
    "environment": "prod",
    "priority": "low",
    "source": "manual-test"
}
EOF

    success "Test environment setup complete"
}

# Start the watcher
start_watcher() {
    log "Starting mock DeltaFi API server..."
    
    # Start mock server in background
    python3 test-scripts/mock-server.py --port 8081 > mock-server.log 2>&1 &
    echo $! > mock-server.pid
    
    # Wait for mock server to start
    sleep 2
    
    # Check if mock server is running
    if kill -0 $(cat mock-server.pid) 2>/dev/null; then
        success "Mock server started successfully (PID: $(cat mock-server.pid))"
    else
        error "Failed to start mock server"
        exit 1
    fi
    
    log "Starting watcher..."
    
    # Set required environment variables
    export DIRWATCHER_WATCH_DIR="$WATCH_DIR"
    export DELTAFI_URL="http://localhost:8081"  # Mock URL for testing
    export DIRWATCHER_WORKERS="2"
    export DIRWATCHER_BUFFER_SIZE="1048576"  # 1MB
    export DIRWATCHER_MAX_FILE_SIZE="10485760"  # 10MB
    export DIRWATCHER_RETRY_PERIOD="1"
    export DIRWATCHER_SETTLING_TIME="500"
    
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

# Check if file was processed (look for upload attempts in logs)
check_file_processed() {
    local file_path="$1"
    local file_name=$(basename "$file_path")
    
    # Wait a bit for processing
    sleep 2
    
    # Check if file still exists (it should be processed and potentially deleted)
    if [ ! -f "$file_path" ]; then
        success "File $file_name was processed (deleted)"
        return 0
    else
        # Check logs for upload attempts
        if grep -q "upload\|ingress\|process" watcher.log 2>/dev/null; then
            success "File $file_name was processed (upload attempted)"
            return 0
        else
            warning "File $file_name may not have been processed"
            return 1
        fi
    fi
}

# Test 1: Basic file creation
test_basic_file_creation() {
    log "Test 1: Basic file creation"
    
    # Create a simple file
    echo "This is a test file created at $(date)" > "$WATCH_DIR/source1/test1.txt"
    
    check_file_processed "$WATCH_DIR/source1/test1.txt"
}

# Test 2: Large file creation
test_large_file_creation() {
    log "Test 2: Large file creation"
    
    # Create a larger file (1MB)
    dd if=/dev/zero of="$WATCH_DIR/source1/large_file.dat" bs=1M count=1 2>/dev/null
    
    check_file_processed "$WATCH_DIR/source1/large_file.dat"
}

# Test 3: Rapid file creation
test_rapid_file_creation() {
    log "Test 3: Rapid file creation"
    
    # Create multiple files rapidly
    for i in {1..5}; do
        echo "Rapid test file $i created at $(date)" > "$WATCH_DIR/source2/rapid_$i.txt"
    done
    
    sleep 5
    
    # Count remaining files
    remaining=$(find "$WATCH_DIR/source2" -name "rapid_*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "All rapid files were processed"
    else
        warning "$remaining files still exist"
    fi
}

# Test 4: File with special characters
test_special_characters() {
    log "Test 4: File with special characters"
    
    # Create file with special characters in name
    echo "Special characters test" > "$WATCH_DIR/source1/test file with spaces.txt"
    echo "Another test" > "$WATCH_DIR/source1/test-file-with-dashes.txt"
    echo "Underscore test" > "$WATCH_DIR/source1/test_file_with_underscores.txt"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "*test*" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files with special characters were processed"
    else
        warning "$remaining files with special characters still exist"
    fi
}

# Test 5: Directory creation and file in subdirectory
test_directory_creation() {
    log "Test 5: Directory creation and file in subdirectory"
    
    # Create a new subdirectory
    mkdir -p "$WATCH_DIR/source3/subdir"
    echo "File in subdirectory" > "$WATCH_DIR/source3/subdir/subfile.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source3/subdir/subfile.txt" ]; then
        success "File in subdirectory was processed"
    else
        warning "File in subdirectory still exists"
    fi
}

# Test 6: File deletion during processing
test_file_deletion() {
    log "Test 6: File deletion during processing"
    
    # Create a file and quickly delete it
    echo "This file will be deleted" > "$WATCH_DIR/source1/delete_test.txt"
    sleep 1
    rm "$WATCH_DIR/source1/delete_test.txt"
    
    success "File deletion test completed"
}

# Test 7: Metadata file updates
test_metadata_updates() {
    log "Test 7: Metadata file updates"
    
    # Update metadata file
    cat > "$WATCH_DIR/source1/.default_metadata.yaml" << EOF
environment: updated
priority: medium
source: manual-test
updated_at: $(date)
EOF

    # Create a file after metadata update
    echo "File created after metadata update" > "$WATCH_DIR/source1/post_update.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source1/post_update.txt" ]; then
        success "File created after metadata update was processed"
    else
        warning "File created after metadata update still exists"
    fi
}

# Test 8: Concurrent file creation
test_concurrent_creation() {
    log "Test 8: Concurrent file creation"
    
    # Create files concurrently using background processes
    for i in {1..3}; do
        (
            echo "Concurrent file $i" > "$WATCH_DIR/source2/concurrent_$i.txt"
        ) &
    done
    
    wait
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source2" -name "concurrent_*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "All concurrent files were processed"
    else
        warning "$remaining concurrent files still exist"
    fi
}

# Test 9: File size limits
test_file_size_limits() {
    log "Test 9: File size limits"
    
    # Create a very large file (should exceed default limits)
    dd if=/dev/zero of="$WATCH_DIR/source1/very_large.dat" bs=1M count=20 2>/dev/null
    
    sleep 3
    
    if [ -f "$WATCH_DIR/source1/very_large.dat" ]; then
        success "Large file correctly rejected (still exists)"
    else
        warning "Large file was processed - check size limits"
    fi
}

# Test 10: Hidden files
test_hidden_files() {
    log "Test 10: Hidden files"
    
    # Create hidden files
    echo "Hidden file content" > "$WATCH_DIR/source1/.hidden_file.txt"
    echo "Another hidden file" > "$WATCH_DIR/source1/.another_hidden.txt"
    
    sleep 3
    
    # Hidden files should not be processed (except metadata files)
    if [ -f "$WATCH_DIR/source1/.hidden_file.txt" ] && [ -f "$WATCH_DIR/source1/.another_hidden.txt" ]; then
        success "Hidden files correctly ignored"
    else
        warning "Hidden files were processed - check configuration"
    fi
}

# Test 11: Network interruption simulation
test_network_interruption() {
    log "Test 11: Network interruption simulation"
    
    # Create files during potential network issues
    for i in {1..3}; do
        echo "Network test file $i" > "$WATCH_DIR/source2/network_$i.txt"
        sleep 1
    done
    
    sleep 5
    
    remaining=$(find "$WATCH_DIR/source2" -name "network_*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files processed despite potential network issues"
    else
        warning "$remaining files still exist - may indicate retry issues"
    fi
}

# Test 12: Stress test
test_stress() {
    log "Test 12: Stress test"
    
    # Create many files quickly
    for i in {1..20}; do
        echo "Stress test file $i created at $(date)" > "$WATCH_DIR/source3/stress_$i.txt"
    done
    
    sleep 10
    
    remaining=$(find "$WATCH_DIR/source3" -name "stress_*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "All stress test files were processed"
    else
        warning "$remaining stress test files still exist"
    fi
}

# Monitor watcher logs
monitor_logs() {
    log "Monitoring watcher logs..."
    tail -f watcher.log &
    TAIL_PID=$!
    
    # Run all tests
    test_basic_file_creation
    test_large_file_creation
    test_rapid_file_creation
    test_special_characters
    test_directory_creation
    test_file_deletion
    test_metadata_updates
    test_concurrent_creation
    test_file_size_limits
    test_hidden_files
    test_network_interruption
    test_stress
    
    # Stop log monitoring
    kill $TAIL_PID 2>/dev/null || true
}

# Main execution
main() {
    log "Starting manual watcher tests..."
    
    # Setup trap for cleanup
    trap cleanup EXIT
    
    # Setup test environment
    setup
    
    # Start the watcher
    start_watcher
    
    # Run tests
    monitor_logs
    
    # Stop the watcher
    stop_watcher
    
    log "Manual testing completed!"
    log "Check the log file for detailed results: $LOG_FILE"
    log "Check watcher.log for watcher output"
}

# Run main function
main "$@" 
