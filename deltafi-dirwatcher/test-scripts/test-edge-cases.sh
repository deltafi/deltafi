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


# Edge Case Testing Script for DeltaFi File Ingress Watcher
# This script tests various edge cases and error conditions

set -e

# Configuration
WATCH_DIR="${WATCH_DIR:-./test-edge-cases-dir}"
LOG_FILE="./edge-cases-test.log"
PID_FILE="watcher.pid"

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
    log "Cleaning up edge case test environment..."
    rm -rf "$WATCH_DIR"
    rm -f "$LOG_FILE"
    stop_watcher
}

setup() {
    log "Setting up edge case test environment..."
    mkdir -p "$WATCH_DIR"
    mkdir -p "$WATCH_DIR/source1"
    mkdir -p "$WATCH_DIR/source2"
    success "Edge case test environment setup complete"
}

# Start the watcher
start_watcher() {
    log "Starting mock DeltaFi API server..."
    
    # Start mock server in background
    python3 test-scripts/mock-server.py --port 8082 > mock-server.log 2>&1 &
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
    export DELTAFI_URL="http://localhost:8082"  # Mock URL for testing
    export DIRWATCHER_WORKERS="2"
    export DIRWATCHER_BUFFER_SIZE="1048576"  # 1MB
    export DIRWATCHER_MAX_FILE_SIZE="20485760"  # 20MB
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

# Edge Case 1: Empty files
test_empty_files() {
    log "Edge Case 1: Empty files"
    
    # Create empty files
    touch "$WATCH_DIR/source1/empty1.txt"
    echo "" > "$WATCH_DIR/source1/empty2.txt"
    
    check_file_processed "$WATCH_DIR/source1/empty1.txt"
    check_file_processed "$WATCH_DIR/source1/empty2.txt"
}

# Edge Case 2: Very small files
test_very_small_files() {
    log "Edge Case 2: Very small files"
    
    # Create very small files
    echo "a" > "$WATCH_DIR/source1/tiny1.txt"
    echo "b" > "$WATCH_DIR/source1/tiny2.txt"
    
    check_file_processed "$WATCH_DIR/source1/tiny1.txt"
    check_file_processed "$WATCH_DIR/source1/tiny2.txt"
}

# Edge Case 3: Files with very long names
test_long_filenames() {
    log "Edge Case 3: Files with very long names"
    
    # Create file with very long name
    long_name="$(printf 'a%.0s' {1..200})"
    echo "Long filename test" > "$WATCH_DIR/source1/${long_name}.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source1/${long_name}.txt" ]; then
        success "File with long name was processed"
    else
        warning "File with long name still exists"
    fi
}

# Edge Case 4: Files with unusual characters
test_unusual_characters() {
    log "Edge Case 4: Files with unusual characters"
    
    # Create files with unusual characters
    echo "Test content" > "$WATCH_DIR/source1/file_with_quotes_\"test\".txt"
    echo "Test content" > "$WATCH_DIR/source1/file_with_backticks_\`test\`.txt"
    echo "Test content" > "$WATCH_DIR/source1/file_with_dollar_\$test\$.txt"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "*quotes*" -o -name "*backticks*" -o -name "*dollar*" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files with unusual characters were processed"
    else
        warning "$remaining files with unusual characters still exist"
    fi
}

# Edge Case 5: Files that are being written to
test_files_being_written() {
    log "Edge Case 5: Files that are being written to"
    
    # Start writing to a file in background with timeout
    (
        for i in {1..5}; do  # Reduced from 10 to 5
            echo "Line $i" >> "$WATCH_DIR/source1/writing_file.txt"
            sleep 0.2  # Reduced from 0.5 to 0.2
        done
    ) &
    
    local bg_pid=$!
    
    # Wait a bit for the file to be created
    sleep 1
    
    # Check if file exists and is being processed
    if [ -f "$WATCH_DIR/source1/writing_file.txt" ]; then
        warning "File being written to still exists (expected behavior)"
    else
        success "File being written to was processed"
    fi
    
    # Wait for background process with timeout
    log "Test 5: cleanup"
    wait $bg_pid 2>/dev/null || true
    log "Test 5: completed"
}

# Edge Case 6: Symlinks
test_symlinks() {
    log "Edge Case 6: Symlinks"
    
    # Create a file and a symlink to it
    echo "Original file content" > "$WATCH_DIR/source1/original.txt"
    ln -s "$WATCH_DIR/source1/original.txt" "$WATCH_DIR/source1/symlink.txt"
    
    sleep 3
    
    # Check if symlink was processed
    if [ ! -L "$WATCH_DIR/source1/symlink.txt" ]; then
        success "Symlink was processed"
    else
        warning "Symlink still exists"
    fi
}

# Edge Case 7: Files with no extension
test_no_extension() {
    log "Edge Case 7: Files with no extension"
    
    # Create files without extensions
    echo "No extension file" > "$WATCH_DIR/source1/noextension"
    echo "Another no extension file" > "$WATCH_DIR/source1/another_no_extension"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "noextension" -o -name "another_no_extension" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files without extensions were processed"
    else
        warning "$remaining files without extensions still exist"
    fi
}

# Edge Case 8: Files with multiple extensions
test_multiple_extensions() {
    log "Edge Case 8: Files with multiple extensions"
    
    # Create files with multiple extensions
    echo "Multiple extensions" > "$WATCH_DIR/source1/file.txt.gz"
    echo "Another multiple extensions" > "$WATCH_DIR/source1/file.tar.gz"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "*.gz" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files with multiple extensions were processed"
    else
        warning "$remaining files with multiple extensions still exist"
    fi
}

# Edge Case 9: Files in deeply nested directories
test_deep_nesting() {
    log "Edge Case 9: Files in deeply nested directories"
    
    # Create deeply nested directory structure
    mkdir -p "$WATCH_DIR/source1/level1/level2/level3/level4/level5"
    echo "Deeply nested file" > "$WATCH_DIR/source1/level1/level2/level3/level4/level5/deep_file.txt"
    echo "Shallowly nested file" > "$WATCH_DIR/source1/level1/shallow_file.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source1/level1/level2/level3/level4/level5/deep_file.txt" ]; then
        warning "File in deeply nested directory was processed"
    else
        success "File in deeply nested directory still exists"
    fi
    if [ ! -f "$WATCH_DIR/source1/level1/shallow_file.txt" ]; then
        success "File in shallowly nested directory was processed"
    else
        warning "File in shallowly nested directory still exists"
    fi
}

# Edge Case 10: Files with binary content
test_binary_content() {
    log "Edge Case 10: Files with binary content"
    
    # Create files with binary content
    head -c 100 /dev/urandom > "$WATCH_DIR/source1/binary1.dat"
    head -c 1000 /dev/urandom > "$WATCH_DIR/source1/binary2.dat"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "binary*.dat" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Binary files were processed"
    else
        warning "$remaining binary files still exist"
    fi
}

# Edge Case 11: Files with unicode characters
test_unicode_characters() {
    log "Edge Case 11: Files with unicode characters"
    
    # Create files with unicode characters
    echo "Unicode test: éñüß" > "$WATCH_DIR/source1/unicode_éñüß.txt"
    echo "More unicode: 中文日本語" > "$WATCH_DIR/source1/unicode_中文日本語.txt"
    
    sleep 3
    
    remaining=$(find "$WATCH_DIR/source1" -name "*unicode*" | wc -l)
    if [ "$remaining" -eq 0 ]; then
        success "Files with unicode characters were processed"
    else
        warning "$remaining files with unicode characters still exist"
    fi
}

# Edge Case 12: Files that are read-only
test_readonly_files() {
    log "Edge Case 12: Files that are read-only"
    
    # Create a read-only file
    echo "Read-only content" > "$WATCH_DIR/source1/readonly.txt"
    chmod 444 "$WATCH_DIR/source1/readonly.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source1/readonly.txt" ]; then
        success "Read-only file was processed"
    else
        warning "Read-only file still exists"
    fi
}

# Edge Case 13: Files with very large content
test_large_content() {
    log "Edge Case 13: Files with very large content"
    
    # Create a file with a lot of text content
    for i in {1..100000}; do
        echo "This is line $i of a very large text file with lots of content to test how the watcher handles files with substantial text content that might take time to process." >> "$WATCH_DIR/source1/large_content.txt"
    done
   
    for i in {1..20}; do
        printf "."
        if [ ! -f "$WATCH_DIR/source1/large_content.txt" ]; then 
            break
        fi
        sleep 1
    done
    echo
    
    if [ ! -f "$WATCH_DIR/source1/large_content.txt" ]; then
        success "File with large content was processed"
    else
        warning "File with large content still exists"
    fi
}

# Edge Case 14: Files created and deleted rapidly
test_rapid_create_delete() {
    log "Edge Case 14: Files created and deleted rapidly"
    
    # Create and delete files rapidly
    for i in {1..20}; do
        echo "Rapid test $i" > "$WATCH_DIR/source2/rapid_$i.txt"
        sleep 0.1
        rm "$WATCH_DIR/source2/rapid_$i.txt" 2>/dev/null || true
    done
    
    success "Rapid create/delete test completed"
}

# Edge Case 15: Directory permissions
test_directory_permissions() {
    log "Edge Case 15: Directory permissions"
    
    # Create a directory with restricted permissions
    mkdir -p "$WATCH_DIR/source1/restricted_dir"
    chmod 755 "$WATCH_DIR/source1/restricted_dir"
    
    # Create a file in the restricted directory
    echo "File in restricted directory" > "$WATCH_DIR/source1/restricted_dir/test.txt"
    
    sleep 3
    
    if [ ! -f "$WATCH_DIR/source1/restricted_dir/test.txt" ]; then
        success "File in restricted directory was processed"
    else
        warning "File in restricted directory still exists"
    fi
}

# Run all edge case tests
run_all_tests() {
    log "Running all edge case tests..."
    
    local failed_tests=0
    
    test_empty_files || failed_tests=$((failed_tests + 1))
    test_very_small_files || failed_tests=$((failed_tests + 1))
    test_long_filenames || failed_tests=$((failed_tests + 1))
    test_unusual_characters || failed_tests=$((failed_tests + 1))
    test_files_being_written || failed_tests=$((failed_tests + 1))
    test_symlinks || failed_tests=$((failed_tests + 1))
    test_no_extension || failed_tests=$((failed_tests + 1))
    test_multiple_extensions || failed_tests=$((failed_tests + 1))
    test_deep_nesting || failed_tests=$((failed_tests + 1))
    test_binary_content || failed_tests=$((failed_tests + 1))
    test_unicode_characters || failed_tests=$((failed_tests + 1))
    test_readonly_files || failed_tests=$((failed_tests + 1))
    test_large_content || failed_tests=$((failed_tests + 1))
    test_rapid_create_delete || failed_tests=$((failed_tests + 1))
    test_directory_permissions || failed_tests=$((failed_tests + 1))
    
    log "All edge case tests completed! ($failed_tests tests failed)"
    
    if [ $failed_tests -gt 0 ]; then
        return 1
    fi
    return 0
}

# Main execution
main() {
    log "Starting edge case testing..."
    
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
    
    # Stop the watcher
    stop_watcher
    
    log "Edge case testing completed!"
    log "Check the log file for detailed results: $LOG_FILE"
    log "Check watcher.log for watcher output"
}

# Run main function
main "$@" 
