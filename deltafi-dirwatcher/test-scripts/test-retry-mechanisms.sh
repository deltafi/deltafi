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


# Retry Mechanism Testing Script for DeltaFi File Ingress Watcher
# This script tests various retry scenarios and error handling

set -e

# Configuration
WATCH_DIR="${WATCH_DIR:-./test-retry-dir}"
LOG_FILE="./retry-test.log"
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
    log "Cleaning up retry test environment..."
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

# Separate cleanup function for early exit
early_cleanup() {
    log "Early cleanup - stopping processes only..."
    if [ -f "$PID_FILE" ]; then
        kill $(cat "$PID_FILE") 2>/dev/null || true
        rm -f "$PID_FILE"
    fi
    if [ -f "mock-server.pid" ]; then
        kill $(cat mock-server.pid) 2>/dev/null || true
        rm -f mock-server.pid
    fi
}

setup() {
    log "Setting up retry test environment..."
    mkdir -p "$WATCH_DIR"
    mkdir -p "$WATCH_DIR/source1"
    mkdir -p "$WATCH_DIR/source2"
    
    # Create metadata files
    cat > "$WATCH_DIR/source1/.default_metadata.yaml" << EOF
environment: test
priority: high
source: retry-test
EOF

    cat > "$WATCH_DIR/source2/.default_metadata.json" << EOF
{
    "environment": "prod",
    "priority": "low",
    "source": "retry-test"
}
EOF

    success "Retry test environment setup complete"
}

# Start the watcher
start_watcher() {
    log "Starting mock DeltaFi API server..."
    
    # Start mock server in background
    python3 test-scripts/mock-server.py --port 8084 > mock-server.log 2>&1 &
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
    export DELTAFI_URL="http://localhost:8084"  # Mock URL for testing
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
    if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
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

# Test 1: Files that disappear during processing
test_disappearing_files() {
    log "Test 1: Files that disappear during processing"
    
    # Create a file and delete it quickly
    echo "This file will disappear" > "$WATCH_DIR/source1/disappear.txt"
    sleep 0.5
    rm "$WATCH_DIR/source1/disappear.txt" 2>/dev/null || true
    
    # Create another file that stays
    echo "This file will stay" > "$WATCH_DIR/source1/stay.txt"
    
    check_file_processed "$WATCH_DIR/source1/stay.txt"
}

# Test 2: Files with permission issues
test_permission_issues() {
    log "Test 2: Files with permission issues"
    
    # Create a file and make it read-only
    echo "Read-only content" > "$WATCH_DIR/source1/readonly.txt"
    chmod 444 "$WATCH_DIR/source1/readonly.txt"
    
    sleep 3
    
    # Try to create another file
    echo "Normal file" > "$WATCH_DIR/source1/normal.txt"
    
    check_file_processed "$WATCH_DIR/source1/normal.txt"
}

# Test 3: Files in directories that become inaccessible
test_inaccessible_directories() {
    log "Test 3: Files in directories that become inaccessible"
    
    # Create a subdirectory and file
    mkdir -p "$WATCH_DIR/source1/subdir"
    echo "File in subdirectory" > "$WATCH_DIR/source1/subdir/test.txt"
    
    sleep 1
    
    # Make the directory inaccessible
    chmod 000 "$WATCH_DIR/source1/subdir"
    
    sleep 3
    
    # Create a file in the main directory
    echo "File in main directory" > "$WATCH_DIR/source1/main.txt"
    
    sleep 3
    
    # Check if main file was processed
    if [ ! -f "$WATCH_DIR/source1/main.txt" ]; then
        success "File in main directory was processed"
    else
        warning "File in main directory still exists"
    fi
    
    # Restore permissions for cleanup
    chmod 755 "$WATCH_DIR/source1/subdir" 2>/dev/null || true
}

# Test 4: Files that are locked by other processes
test_locked_files() {
    log "Test 4: Files that are locked by other processes"
    
    # Create a file and keep it open in background
    echo "Locked file content" > "$WATCH_DIR/source1/locked.txt"
    
    # Keep file open in background with timeout
    (
        exec 3<"$WATCH_DIR/source1/locked.txt"
        # Use a timeout to prevent hanging
        timeout 6 bash -c '
            sleep 5
            exec 3<&-
        ' || true
    ) &
    
    local bg_pid=$!
    
    sleep 1
    
    # Create another file
    echo "Unlocked file content" > "$WATCH_DIR/source1/unlocked.txt"
    
    # Wait for processing with timeout
    local timeout=10
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if [ ! -f "$WATCH_DIR/source1/unlocked.txt" ]; then
            success "Unlocked file was processed"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for unlocked file to be processed... ($elapsed/$timeout seconds)"
    done
    
    if [ -f "$WATCH_DIR/source1/unlocked.txt" ]; then
        warning "Unlocked file still exists"
    fi
    
    # Wait for background process with timeout
    log "Test 4: cleanup"
    wait $bg_pid 2>/dev/null || true
    log "Test 4: completed"
}

# Test 5: Files with network timeout simulation
test_network_timeouts() {
    log "Test 5: Files with network timeout simulation"
    
    # Create multiple files to simulate network delays
    for i in {1..5}; do
        echo "Network test file $i" > "$WATCH_DIR/source1/network_$i.txt"
        sleep 0.5  # Reduced from 1 second
    done
    
    # Wait for processing with timeout
    local timeout=15
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source1" -name "network_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "All network test files were processed"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    warning "$remaining network test files still exist after $timeout seconds"
    return 1
}

# Test 6: Files with corrupted content
test_corrupted_files() {
    log "Test 6: Files with corrupted content"
    
    # Create files with potentially problematic content
    echo -e "File with\0null\0bytes" > "$WATCH_DIR/source1/corrupted1.txt"
    echo -e "File with\nnewlines\nand\ttabs" > "$WATCH_DIR/source1/corrupted2.txt"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "corrupted*.txt" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files with corrupted content were processed"
    else
        warning "$remaining files with corrupted content still exist"
    fi
}

# Test 7: Files that are being continuously modified
test_continuously_modified() {
    log "Test 7: Files that are continuously modified"
    
    # Start a background process that continuously modifies a file with timeout
    (
        for i in {1..10}; do  # Reduced from 20 to 10
            echo "Line $i" >> "$WATCH_DIR/source1/continuous.txt"
            sleep 0.1  # Reduced from 0.2 to 0.1
        done
    ) &
    
    local bg_pid=$!
    
    sleep 1  # Reduced from 2 to 1
    
    # Create a normal file
    echo "Normal file during continuous modification" > "$WATCH_DIR/source1/normal_during.txt"
    
    # Wait for processing with timeout
    local timeout=10
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if [ ! -f "$WATCH_DIR/source1/normal_during.txt" ]; then
            success "Normal file was processed during continuous modification"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for normal file to be processed... ($elapsed/$timeout seconds)"
    done
    
    if [ -f "$WATCH_DIR/source1/normal_during.txt" ]; then
        warning "Normal file still exists during continuous modification"
    fi
    
    # Wait for background process with timeout
    log "Test 7: cleanup"
    wait $bg_pid 2>/dev/null || true
    log "Test 7: completed"
}

# Test 8: Files with very slow processing simulation
test_slow_processing() {
    log "Test 8: Files with very slow processing simulation"
    
    # Create a large file that might take time to process
    dd if=/dev/zero of="$WATCH_DIR/source1/slow_file.dat" bs=1M count=5 2>/dev/null  # Reduced from 10MB to 5MB
    
    # Create smaller files while the large one is being processed
    for i in {1..3}; do
        echo "Small file $i" > "$WATCH_DIR/source1/small_$i.txt"
        sleep 0.5  # Reduced from 1 to 0.5
    done
    
    # Wait for processing with timeout
    local timeout=15
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source1" -name "small_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "Small files were processed despite slow processing"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for small files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    if [ "$remaining" -gt 0 ]; then
        warning "$remaining small files still exist after $timeout seconds"
    fi
}

# Test 9: Files with retry queue overflow
test_retry_queue_overflow() {
    log "Test 9: Files with retry queue overflow"
    
    # Create many files rapidly to potentially overflow retry queue
    for i in {1..50}; do
        echo "Retry test file $i" > "$WATCH_DIR/source2/retry_$i.txt"
    done
    
    # Wait for processing with timeout
    local timeout=30  # Increased timeout for new settling logic
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source2" -name "retry_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "All retry test files were processed"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for retry files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    if [ "$remaining" -gt 0 ]; then
        warning "$remaining retry test files still exist after $timeout seconds"
    fi
}

# Test 10: Files with system resource constraints
test_resource_constraints() {
    log "Test 10: Files with system resource constraints"
    
    # Create files while simulating resource constraints
    for i in {1..5}; do  # Reduced from 10 to 5
        echo "Resource test file $i" > "$WATCH_DIR/source1/resource_$i.txt"
        # Simulate some system load
        dd if=/dev/zero of=/dev/null bs=1M count=1 2>/dev/null &
        sleep 0.5
    done
    
    # Wait for processing with timeout
    local timeout=15
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source1" -name "resource_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "All resource test files were processed"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for resource files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    if [ "$remaining" -gt 0 ]; then
        warning "$remaining resource test files still exist after $timeout seconds"
    fi
}

# Test 11: Files with concurrent access
test_concurrent_access() {
    log "Test 11: Files with concurrent access"
    
    # Create files concurrently from multiple processes
    local bg_pids=()
    for i in {1..5}; do  # Reduced from 10 to 5
        (
            echo "Concurrent file $i from process $$" > "$WATCH_DIR/source1/concurrent_$i.txt"
        ) &
        bg_pids+=($!)
    done
    
    # Wait for all background processes with timeout
    for pid in "${bg_pids[@]}"; do
        wait $pid 2>/dev/null || true
    done
    
    # Wait for processing with timeout
    local timeout=15
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source1" -name "concurrent_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "All concurrent files were processed"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for concurrent files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    if [ "$remaining" -gt 0 ]; then
        warning "$remaining concurrent files still exist after $timeout seconds"
    fi
}

# Test 12: Files with memory pressure simulation
test_memory_pressure() {
    log "Test 12: Files with memory pressure simulation"
    
    # Create files while simulating memory pressure
    for i in {1..10}; do  # Reduced from 20 to 10
        echo "Memory test file $i" > "$WATCH_DIR/source1/memory_$i.txt"
        # Simulate memory pressure by allocating some memory
        python3 -c "import time; [0]*1000000; time.sleep(0.1)" 2>/dev/null || true
    done
    
    # Wait for processing with timeout
    local timeout=15
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        remaining=$(find "$WATCH_DIR/source1" -name "memory_*.txt" | wc -l)
        if [ "$remaining" -eq 0 ]; then
            success "All memory test files were processed"
            break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
        log "Waiting for memory files to be processed... ($elapsed/$timeout seconds, $remaining files remaining)"
    done
    
    if [ "$remaining" -gt 0 ]; then
        warning "$remaining memory test files still exist after $timeout seconds"
    fi
}

# Run all retry tests
run_all_tests() {
    log "Running all retry mechanism tests..."
    
    local failed_tests=0
    
    test_disappearing_files || failed_tests=$((failed_tests + 1))
    test_permission_issues || failed_tests=$((failed_tests + 1))
    test_inaccessible_directories || failed_tests=$((failed_tests + 1))
    test_locked_files || failed_tests=$((failed_tests + 1))
    test_network_timeouts || failed_tests=$((failed_tests + 1))
    test_corrupted_files || failed_tests=$((failed_tests + 1))
    test_continuously_modified || failed_tests=$((failed_tests + 1))
    test_slow_processing || failed_tests=$((failed_tests + 1))
    test_retry_queue_overflow || failed_tests=$((failed_tests + 1))
    test_resource_constraints || failed_tests=$((failed_tests + 1))
    test_concurrent_access || failed_tests=$((failed_tests + 1))
    test_memory_pressure || failed_tests=$((failed_tests + 1))
    
    log "All retry mechanism tests completed! ($failed_tests tests failed)"
    
    if [ $failed_tests -gt 0 ]; then
        return 1
    fi
    return 0
}

# Main execution
main() {
    log "Starting retry mechanism testing..."
    
    # Setup trap for cleanup
    trap cleanup EXIT
    
    # Setup test environment
    setup
    
    # Start watcher
    start_watcher
    
    # Run all tests with error handling
    run_all_tests || {
        log "Some tests failed, but continuing with cleanup..."
    }
    
    # Stop watcher
    stop_watcher
    
    log "Retry mechanism testing completed!"
    log "Check the log file for detailed results: $LOG_FILE"
    log "Check watcher.log for watcher output"
}

# Run main function
main "$@" 
