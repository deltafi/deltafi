/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package api

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"time"
)

type Client struct {
	baseURL    string
	HttpClient *http.Client
	dnsMap     map[string]string
}

func NewClient(baseURL string) *Client {
	transport := &http.Transport{
		DialContext: (&net.Dialer{
			Timeout:   60 * time.Second,
			KeepAlive: 60 * time.Second,
		}).DialContext,
	}

	client := &Client{
		baseURL: baseURL,
		HttpClient: &http.Client{
			Timeout:   60 * time.Second,
			Transport: transport,
		},
		dnsMap: make(map[string]string),
	}

	client.AddCustomDNS("deltafi-core-service", "127.0.0.1")

	return client
}

// AddCustomDNS adds a custom DNS resolution for a specific hostname.
func (c *Client) AddCustomDNS(hostname string, ip string) {
	c.dnsMap[hostname] = ip

	transport := c.HttpClient.Transport.(*http.Transport)
	if transport.DialContext == nil {
		transport.DialContext = (&net.Dialer{
			Timeout:   60 * time.Second,
			KeepAlive: 60 * time.Second,
		}).DialContext
	}

	// Create a custom dialer that overrides DNS resolution
	originalDialer := transport.DialContext
	transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		host, port, err := net.SplitHostPort(addr)
		if err != nil {
			return nil, err
		}

		// If the host matches any of our custom DNS entries, use the specified IP
		if ip, exists := c.dnsMap[host]; exists {
			addr = net.JoinHostPort(ip, port)
		}

		return originalDialer(ctx, network, addr)
	}
}

type RequestOpts struct {
	Headers map[string]string
}

// Get makes a GET request to the specified path and unmarshals the response into
// the provided result. The request will be sent with the base URL and any
// additional headers specified in opts. The request will timeout after 30s.
//
// If the request fails or the response status code is not 200, an error will be
// returned.
func (c *Client) Get(path string, result interface{}, opts *RequestOpts) error {
	req, err := http.NewRequest("GET", c.baseURL+path, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %v", err)
	}

	// Add custom headers
	if opts != nil {
		for k, v := range opts.Headers {
			req.Header.Add(k, v)
		}
	}

	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("failed to make request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status code: %d - %s", resp.StatusCode, c.baseURL+path)
	}

	if err := json.NewDecoder(resp.Body).Decode(result); err != nil {
		return fmt.Errorf("failed to decode response: %v", err)
	}

	return nil
}

func (c *Client) Post(path string, requestBody interface{}, result interface{}, opts *RequestOpts) error {
	return c.DoWithBody("POST", path, requestBody, result, opts)
}

func (c *Client) Put(path string, requestBody interface{}, result interface{}, opts *RequestOpts) error {
	return c.DoWithBody("PUT", path, requestBody, result, opts)
}

// Post sends a request and returns a typed response
func (c *Client) DoWithBody(method string, path string, requestBody interface{}, result interface{}, opts *RequestOpts) error {
	var req *http.Request
	var err error

	// Check if the request body is a bytes.Buffer (multipart form data)
	if buf, ok := requestBody.(*bytes.Buffer); ok {
		req, err = http.NewRequest(method, c.baseURL+path, buf)
		if err != nil {
			return fmt.Errorf("failed to create request: %w", err)
		}
		// Don't set Content-Type header for multipart form data
		// The Content-Type will be set by the multipart writer
	} else {
		// Handle JSON request body
		jsonData, err := json.Marshal(requestBody)
		if err != nil {
			return fmt.Errorf("failed to encode request body: %w", err)
		}
		req, err = http.NewRequest(method, c.baseURL+path, bytes.NewBuffer(jsonData))
		if err != nil {
			return fmt.Errorf("failed to create request: %w", err)
		}
		req.Header.Set("Content-Type", "application/json")
	}

	if opts != nil {
		for key, value := range opts.Headers {
			// Skip Content-Type header if it's already set by multipart writer
			if key == "Content-Type" && req.Header.Get("Content-Type") != "" {
				continue
			}
			req.Header.Set(key, value)
		}
	}

	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			fmt.Printf("failed to close responsebody %s", err)
		}
	}(resp.Body)

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("%d - %s", resp.StatusCode, body)
	}

	if result != nil {
		// Read the response body
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return fmt.Errorf("unreadable response body: %w", err)
		}
		// If we're expecting a string, just set it directly
		if strResult, ok := result.(*string); ok {
			*strResult = string(body)
			return nil
		}

		// Otherwise try to decode as JSON
		if err := json.Unmarshal(body, result); err != nil {
			return fmt.Errorf("failed to decode response: %w", err)
		}
	}

	return nil
}

// Do performs the provided HTTP request and returns the response.
//
// The request will be sent with the X-User-Permissions and X-User-Name headers
// set to "Admin" and "TUI" respectively.
func (c *Client) Do(req *http.Request) (*http.Response, error) {
	return c.HttpClient.Do(req)
}

func (c *Client) Versions() (*VersionsResponse, error) {
	var versions VersionsResponse
	err := c.Get("/api/v2/versions", &versions, nil)
	return &versions, err
}

func (c *Client) Status() (*StatusResponse, error) {
	var status StatusResponse
	err := c.Get("/api/v2/status", &status, nil)
	return &status, err
}

func (c *Client) Me() (*MeResponse, error) {
	var me MeResponse
	err := c.Get("/api/v2/me", &me, nil)
	return &me, err
}

func (c *Client) Events() ([]Event, error) {
	var events []Event
	err := c.Get("/api/v2/events", &events, nil)
	return events, err
}

func (c *Client) Event(id string) (*Event, error) {
	var event Event
	err := c.Get("/api/v2/events/"+id, &event, nil)
	return &event, err
}

func (c *Client) CreatEvent(event Event) (*Event, error) {
	var newEvent Event
	err := c.Post("/api/v2/events", event, &newEvent, nil)
	return &newEvent, err
}

type PasswordUpdate struct {
	Password string `json:"password"`
}

type Permission string
type Role struct {
	ID          string       `json:"id"`
	Name        string       `json:"name"`
	Permissions []Permission `json:"permissions"`
	CreatedAt   string       `json:"createdAt"`
	UpdatedAt   string       `json:"updatedAt"`
}

type UserResponse struct {
	ID          string       `json:"id"`
	DN          string       `json:"dn"`
	Name        string       `json:"name"`
	Username    string       `json:"username"`
	CreatedAt   string       `json:"createdAt"`
	UpdatedAt   string       `json:"updatedAt"`
	Roles       []Role       `json:"roles"`
	Permissions []Permission `json:"permissions"`
}

func (c *Client) SetAdminPassword(password PasswordUpdate) (*UserResponse, error) {
	adminID := "00000000-0000-0000-0000-000000000000"
	response := &UserResponse{}

	err := c.Put(fmt.Sprintf("/api/v2/users/%s", adminID), password, response, nil)

	return response, err
}

// PostToFile sends a POST request and writes the response directly to a file
func (c *Client) PostToFile(path string, requestBody interface{}, outfile string, opts *RequestOpts) error {
	var req *http.Request
	var err error

	// Handle JSON request body
	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		return fmt.Errorf("failed to encode request body: %w", err)
	}
	req, err = http.NewRequest("POST", c.baseURL+path, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	if opts != nil {
		for key, value := range opts.Headers {
			req.Header.Set(key, value)
		}
	}

	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("%d - %s", resp.StatusCode, body)
	}

	// Create the output file
	out, err := os.Create(outfile)
	if err != nil {
		return fmt.Errorf("failed to create output file: %w", err)
	}
	defer out.Close()

	// Copy the response body to the file
	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return fmt.Errorf("failed to write response to file: %w", err)
	}

	return nil
}
