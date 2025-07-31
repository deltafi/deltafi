#!/usr/bin/env python3

"""
Mock DeltaFi API Server for Testing
This script creates a simple HTTP server that simulates the DeltaFi API endpoints
used by the file ingress watcher for testing purposes.
"""

import json
import logging
import os
import sys
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import threading
import time

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('mock-server.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

class MockDeltaFiHandler(BaseHTTPRequestHandler):
    """HTTP request handler for mock DeltaFi API"""
    
    def __init__(self, *args, **kwargs):
        self.uploaded_files = []
        self.request_count = 0
        super().__init__(*args, **kwargs)
    
    def log_message(self, format, *args):
        """Override to use our logger"""
        logger.info(f"{self.address_string()} - {format % args}")
    
    def do_POST(self):
        """Handle POST requests (file uploads)"""
        self.request_count += 1
        
        # Parse URL
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        
        logger.info(f"Received POST request to {path}")
        
        if path == "/api/v2/deltafile/ingress":
            return self.handle_ingress()
        else:
            return self.send_error(404, "Endpoint not found")
    
    def do_GET(self):
        """Handle GET requests (status, health checks)"""
        parsed_url = urlparse(self.path)
        path = parsed_url.path
        
        logger.info(f"Received GET request to {path}")
        
        if path == "/health":
            return self.handle_health()
        elif path == "/status":
            return self.handle_status()
        else:
            return self.send_error(404, "Endpoint not found")
    
    def handle_ingress(self):
        """Handle file ingress requests"""
        try:
            # Get content length
            content_length = int(self.headers.get('Content-Length', 0))
            
            # Read the request body
            body = self.rfile.read(content_length)
            
            # Parse headers
            content_type = self.headers.get('Content-Type', '')
            
            # Extract metadata from headers
            metadata = {}
            for header, value in self.headers.items():
                if header.startswith('X-Metadata-'):
                    key = header[11:]  # Remove 'X-Metadata-' prefix
                    metadata[key] = value
            
            # Log the upload attempt
            logger.info(f"File upload attempt - Content-Type: {content_type}, Size: {len(body)} bytes")
            logger.info(f"Metadata: {metadata}")
            
            # Simulate processing time
            time.sleep(0.1)
            
            # Simulate success (90% success rate for testing)
            import random
            if random.random() < 0.9:
                # Success
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                
                response = {
                    "status": "success",
                    "message": "File uploaded successfully",
                    "timestamp": datetime.now().isoformat(),
                    "file_size": len(body),
                    "metadata": metadata
                }
                
                self.wfile.write(json.dumps(response).encode())
                
                # Store file info for status endpoint
                file_info = {
                    "timestamp": datetime.now().isoformat(),
                    "size": len(body),
                    "metadata": metadata,
                    "content_type": content_type
                }
                self.uploaded_files.append(file_info)
                
                logger.info("File upload successful")
            else:
                # Simulate failure
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                
                response = {
                    "status": "error",
                    "message": "Simulated upload failure",
                    "timestamp": datetime.now().isoformat()
                }
                
                self.wfile.write(json.dumps(response).encode())
                logger.warning("File upload failed (simulated)")
                
        except Exception as e:
            logger.error(f"Error handling ingress request: {e}")
            self.send_error(500, f"Internal server error: {str(e)}")
    
    def handle_health(self):
        """Handle health check requests"""
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        
        response = {
            "status": "healthy",
            "timestamp": datetime.now().isoformat(),
            "service": "mock-deltafi-api"
        }
        
        self.wfile.write(json.dumps(response).encode())
    
    def handle_status(self):
        """Handle status requests"""
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        
        response = {
            "status": "running",
            "timestamp": datetime.now().isoformat(),
            "request_count": self.request_count,
            "uploaded_files": len(self.uploaded_files),
            "recent_uploads": self.uploaded_files[-10:] if self.uploaded_files else []
        }
        
        self.wfile.write(json.dumps(response, indent=2).encode())

def start_mock_server(host='localhost', port=8080):
    """Start the mock DeltaFi API server"""
    server_address = (host, port)
    httpd = HTTPServer(server_address, MockDeltaFiHandler)
    
    logger.info(f"Starting mock DeltaFi API server on {host}:{port}")
    logger.info("Available endpoints:")
    logger.info("  POST /api/v2/deltafile/ingress - File upload endpoint")
    logger.info("  GET  /health - Health check")
    logger.info("  GET  /status - Server status")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down mock server...")
        httpd.shutdown()

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Mock DeltaFi API Server")
    parser.add_argument("--host", default="localhost", help="Host to bind to (default: localhost)")
    parser.add_argument("--port", type=int, default=8080, help="Port to bind to (default: 8080)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose logging")
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    start_mock_server(args.host, args.port) 