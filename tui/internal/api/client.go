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
	"encoding/json"
	"fmt"
	"net/http"
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

// NewClient creates a new instance of the DeltaFi API Client with the specified baseURL.
// It initializes an HTTP client with a timeout of 30 seconds.
func NewClient(baseURL string) *Client {
	return &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
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

// Do performs the provided HTTP request and returns the response.
//
// The request will be sent with the X-User-Permissions and X-User-Name headers
// set to "Admin" and "deltafi-cli" respectively.
func (c *Client) Do(req *http.Request) (*http.Response, error) {
	req.Header.Add("X-User-Permissions", "Admin")
	req.Header.Add("X-User-Name", "deltafi-cli")
	return c.httpClient.Do(req)
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
