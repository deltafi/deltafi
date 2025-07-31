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


# Watcher Monitoring Script
# This script helps monitor the watcher's behavior in real-time

set -e

# Configuration
WATCH_DIR="${WATCH_DIR:-./test-watch-dir}"
LOG_FILE="./watcher-monitor.log"
PID_FILE="./watcher.pid"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Helper functions
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}

warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

error() {
    echo -e "${RED}✗ $1${NC}"
}

info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

# Function to check if watcher is running
check_watcher_status() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            success "Watcher is running (PID: $pid)"
            return 0
        else
            error "Watcher PID file exists but process is not running"
            return 1
        fi
    else
        error "Watcher PID file not found"
        return 1
    fi
}

# Function to monitor file system changes
monitor_filesystem() {
    log "Monitoring filesystem changes in $WATCH_DIR"
    
    # Use inotifywait if available, otherwise use a simple loop
    if command -v inotifywait >/dev/null 2>&1; then
        inotifywait -m -r -e create,modify,delete,move "$WATCH_DIR" --format '%T %e %w%f' | while read line; do
            echo -e "${CYAN}[FS]${NC} $line"
        done
    else
        # Fallback: simple polling
        while true; do
            sleep 1
            # This is a simple fallback - inotifywait is much better
            echo -e "${CYAN}[FS]${NC} Polling filesystem..."
        done
    fi
}

# Function to monitor log files
monitor_logs() {
    log "Monitoring watcher logs"
    
    # Monitor the application log file if it exists
    if [ -f "$LOG_FILE" ]; then
        tail -f "$LOG_FILE" | while read line; do
            echo -e "${GREEN}[LOG]${NC} $line"
        done
    else
        warning "Log file not found: $LOG_FILE"
        info "You can specify a different log file with LOG_FILE environment variable"
    fi
}

# Function to show current directory state
show_directory_state() {
    log "Current directory state:"
    echo "Watch directory: $WATCH_DIR"
    echo "Directory exists: $([ -d "$WATCH_DIR" ] && echo "Yes" || echo "No")"
    
    if [ -d "$WATCH_DIR" ]; then
        echo "Files in watch directory:"
        find "$WATCH_DIR" -type f | head -20 | while read file; do
            local size=$(stat -c%s "$file" 2>/dev/null || echo "unknown")
            local modified=$(stat -c%y "$file" 2>/dev/null || echo "unknown")
            echo "  - $file (${size} bytes, modified: $modified)"
        done
        
        echo "Directories in watch directory:"
        find "$WATCH_DIR" -type d | head -10 | while read dir; do
            local count=$(find "$dir" -maxdepth 1 -type f | wc -l)
            echo "  - $dir ($count files)"
        done
    fi
}

# Function to create test files interactively
create_test_files() {
    log "Interactive test file creation"
    
    while true; do
        echo
        echo "Choose an option:"
        echo "1. Create a simple text file"
        echo "2. Create a large file"
        echo "3. Create multiple files rapidly"
        echo "4. Create a file with special characters"
        echo "5. Create a file in a subdirectory"
        echo "6. Delete a file"
        echo "7. Show directory state"
        echo "8. Exit"
        echo
        read -p "Enter your choice (1-8): " choice
        
        case $choice in
            1)
                read -p "Enter filename: " filename
                read -p "Enter content: " content
                echo "$content" > "$WATCH_DIR/source1/$filename"
                success "Created file: $WATCH_DIR/source1/$filename"
                ;;
            2)
                read -p "Enter filename: " filename
                read -p "Enter size in MB: " size
                dd if=/dev/zero of="$WATCH_DIR/source1/$filename" bs=1M count="$size" 2>/dev/null
                success "Created large file: $WATCH_DIR/source1/$filename"
                ;;
            3)
                read -p "Enter number of files: " count
                for i in $(seq 1 "$count"); do
                    echo "Rapid file $i created at $(date)" > "$WATCH_DIR/source2/rapid_$i.txt"
                done
                success "Created $count files rapidly"
                ;;
            4)
                read -p "Enter filename with special chars: " filename
                echo "Special characters test" > "$WATCH_DIR/source1/$filename"
                success "Created file with special characters: $WATCH_DIR/source1/$filename"
                ;;
            5)
                read -p "Enter subdirectory name: " subdir
                read -p "Enter filename: " filename
                mkdir -p "$WATCH_DIR/source3/$subdir"
                echo "File in subdirectory" > "$WATCH_DIR/source3/$subdir/$filename"
                success "Created file in subdirectory: $WATCH_DIR/source3/$subdir/$filename"
                ;;
            6)
                read -p "Enter filename to delete: " filename
                if [ -f "$WATCH_DIR/source1/$filename" ]; then
                    rm "$WATCH_DIR/source1/$filename"
                    success "Deleted file: $WATCH_DIR/source1/$filename"
                else
                    error "File not found: $WATCH_DIR/source1/$filename"
                fi
                ;;
            7)
                show_directory_state
                ;;
            8)
                success "Exiting interactive mode"
                break
                ;;
            *)
                error "Invalid choice. Please enter 1-8."
                ;;
        esac
    done
}

# Function to show system statistics
show_system_stats() {
    log "System statistics:"
    
    # Check if watcher is running
    check_watcher_status
    
    # Show disk usage
    if [ -d "$WATCH_DIR" ]; then
        local usage=$(du -sh "$WATCH_DIR" 2>/dev/null || echo "unknown")
        info "Watch directory disk usage: $usage"
    fi
    
    # Show file count
    if [ -d "$WATCH_DIR" ]; then
        local file_count=$(find "$WATCH_DIR" -type f | wc -l)
        info "Total files in watch directory: $file_count"
    fi
    
    # Show memory usage if watcher is running
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            local mem_usage=$(ps -o rss= -p "$pid" 2>/dev/null || echo "unknown")
            info "Watcher memory usage: ${mem_usage}KB"
        fi
    fi
}

# Function to start monitoring
start_monitoring() {
    log "Starting watcher monitoring..."
    
    # Create watch directory if it doesn't exist
    mkdir -p "$WATCH_DIR"
    mkdir -p "$WATCH_DIR/source1"
    mkdir -p "$WATCH_DIR/source2"
    mkdir -p "$WATCH_DIR/source3"
    
    # Show initial state
    show_directory_state
    show_system_stats
    
    # Start monitoring in background
    monitor_filesystem &
    local fs_pid=$!
    
    monitor_logs &
    local log_pid=$!
    
    # Trap to cleanup background processes
    trap "kill $fs_pid $log_pid 2>/dev/null; exit" INT TERM
    
    # Main monitoring loop
    while true; do
        sleep 30
        echo
        log "Periodic status check..."
        show_system_stats
    done
}

# Function to show help
show_help() {
    echo "Watcher Monitoring Script"
    echo
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  status     - Check watcher status"
    echo "  state      - Show current directory state"
    echo "  stats      - Show system statistics"
    echo "  monitor    - Start continuous monitoring"
    echo "  interactive - Start interactive test file creation"
    echo "  help       - Show this help message"
    echo
    echo "Environment variables:"
    echo "  WATCH_DIR  - Watch directory (default: ./test-watch-dir)"
    echo "  LOG_FILE   - Log file to monitor (default: ./watcher-monitor.log)"
    echo "  PID_FILE   - PID file location (default: ./watcher.pid)"
    echo
}

# Main execution
main() {
    case "${1:-monitor}" in
        status)
            check_watcher_status
            ;;
        state)
            show_directory_state
            ;;
        stats)
            show_system_stats
            ;;
        monitor)
            start_monitoring
            ;;
        interactive)
            create_test_files
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

# Run main function
main "$@" 
