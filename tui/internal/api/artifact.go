/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"os"
	"path/filepath"
	"time"
)

// ArtifactManifest holds metadata for a published artifact version.
type ArtifactManifest struct {
	ArtifactName string            `json:"artifactName"`
	Version      string            `json:"version"`
	Description  string            `json:"description,omitempty"`
	ContentType  string            `json:"contentType"`
	CreatedAt    *time.Time        `json:"createdAt,omitempty"`
	ModifiedAt   *time.Time        `json:"modifiedAt,omitempty"`
	Sha256       string            `json:"sha256"`
	Size         int64             `json:"size"`
	Labels       map[string]string `json:"labels,omitempty"`
}

// ArtifactSummary is returned by the list-artifacts endpoint.
type ArtifactSummary struct {
	ArtifactName   string            `json:"artifactName"`
	ActiveVersion  string            `json:"activeVersion,omitempty"`
	ActiveManifest *ArtifactManifest `json:"activeManifest,omitempty"`
	TotalVersions  int               `json:"totalVersions"`
	Aliases        map[string]string `json:"aliases,omitempty"`
}

// ListArtifactsResponse is the response body for GET /artifacts/.
type ListArtifactsResponse struct {
	PluginName string            `json:"pluginName,omitempty"`
	Artifacts  []ArtifactSummary `json:"artifacts"`
}

// ListVersionsResponse is the response body for GET /artifacts/{name}/versions.
type ListVersionsResponse struct {
	PluginName    string              `json:"pluginName,omitempty"`
	ArtifactName  string              `json:"artifactName"`
	ActiveVersion string              `json:"activeVersion,omitempty"`
	Versions      []ArtifactManifest  `json:"versions"`
	Aliases       map[string][]string `json:"aliases,omitempty"`
}

// PublishArtifactResponse is the response body for the publish endpoint.
type PublishArtifactResponse struct {
	PluginName   string `json:"pluginName,omitempty"`
	ArtifactName string `json:"artifactName"`
	Version      string `json:"version"`
	IsActive     bool   `json:"isActive"`
	Sha256       string `json:"sha256"`
}

// RollbackRequest is the request body for the rollback endpoint.
type RollbackRequest struct {
	Version string `json:"version"`
}

// SetAliasRequest is the request body for the set-alias endpoint.
type SetAliasRequest struct {
	Version string `json:"version"`
}

// SetAliasResponse is the response body for the set-alias endpoint.
type SetAliasResponse struct {
	PluginName   string `json:"pluginName,omitempty"`
	ArtifactName string `json:"artifactName"`
	Alias        string `json:"alias"`
	Version      string `json:"version"`
}

// RollbackResponse is the response body for the rollback endpoint.
type RollbackResponse struct {
	PluginName   string `json:"pluginName,omitempty"`
	ArtifactName string `json:"artifactName"`
	RolledBackTo string `json:"rolledBackTo"`
}

// PurgeConfig defines the retention policy for an artifact.
type PurgeConfig struct {
	KeepLatest    int   `json:"keepLatest"`
	MaxAgeMinutes int64 `json:"maxAgeMinutes"`
}

// PurgeResponse is the response body for the purge endpoint.
type PurgeResponse struct {
	PluginName     string   `json:"pluginName,omitempty"`
	ArtifactName   string   `json:"artifactName"`
	PurgedVersions []string `json:"purgedVersions"`
}

// artifactBasePath returns the REST base path for the given owner.
// Maps to /api/v2/artifacts/{owner} for both plugin and shared artifacts.
func artifactBasePath(owner string) string {
	return "/api/v2/artifacts/" + owner
}

// PublishArtifact uploads a new artifact version via multipart form POST.
func (c *Client) PublishArtifact(
	owner, artifactName, version, filePath, contentType, description string,
	makeActive bool, labels map[string]string,
) (*PublishArtifactResponse, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("failed to open file: %w", err)
	}
	defer func() { _ = f.Close() }()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	_ = writer.WriteField("version", version)
	_ = writer.WriteField("makeActive", fmt.Sprintf("%t", makeActive))
	if description != "" {
		_ = writer.WriteField("description", description)
	}
	for k, v := range labels {
		_ = writer.WriteField(k, v)
	}

	if contentType == "" {
		contentType = "application/octet-stream"
	}
	header := make(textproto.MIMEHeader)
	header.Set("Content-Disposition", fmt.Sprintf(`form-data; name="file"; filename=%q`, filepath.Base(filePath)))
	header.Set("Content-Type", contentType)
	part, err := writer.CreatePart(header)
	if err != nil {
		return nil, fmt.Errorf("failed to create form file: %w", err)
	}
	if _, err = io.Copy(part, f); err != nil {
		return nil, fmt.Errorf("failed to write file data: %w", err)
	}
	_ = writer.Close()

	path := artifactBasePath(owner) + "/" + artifactName
	opts := &RequestOpts{
		Headers: map[string]string{
			"Content-Type": writer.FormDataContentType(),
		},
	}

	var result PublishArtifactResponse
	if err = c.Post(path, &buf, &result, opts); err != nil {
		return nil, err
	}
	return &result, nil
}

// ListAllArtifacts lists all artifacts across every owner (shared first, then plugin owners).
func (c *Client) ListAllArtifacts() ([]ListArtifactsResponse, error) {
	var result []ListArtifactsResponse
	if err := c.Get("/api/v2/artifacts", &result, nil); err != nil {
		return nil, err
	}
	return result, nil
}

// ListArtifacts lists all artifacts for the given owner.
func (c *Client) ListArtifacts(owner string) (*ListArtifactsResponse, error) {
	var result ListArtifactsResponse
	if err := c.Get(artifactBasePath(owner), &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}

// ListArtifactVersions lists all versions of an artifact.
func (c *Client) ListArtifactVersions(owner, artifactName string) (*ListVersionsResponse, error) {
	var result ListVersionsResponse
	path := artifactBasePath(owner) + "/" + artifactName + "/versions"
	if err := c.Get(path, &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}

// DownloadArtifact returns a ReadCloser for the artifact blob and the suggested filename.
// The caller is responsible for closing the returned ReadCloser.
func (c *Client) DownloadArtifact(owner, artifactName, version string) (io.ReadCloser, string, error) {
	url := c.baseURL + artifactBasePath(owner) + "/" + artifactName + "/versions/" + version
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, "", fmt.Errorf("failed to create request: %w", err)
	}

	resp, err := c.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("request failed: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		_ = resp.Body.Close()
		return nil, "", fmt.Errorf("download failed (%d): %s", resp.StatusCode, body)
	}

	filename := extractFilename(resp.Header)
	if filename == "" {
		filename = artifactName
	}
	return resp.Body, filename, nil
}

// RollbackArtifact rolls the 'active' alias back to a prior version.
func (c *Client) RollbackArtifact(owner, artifactName, version string) (*RollbackResponse, error) {
	path := artifactBasePath(owner) + "/" + artifactName + "/rollback"
	var result RollbackResponse
	if err := c.Post(path, RollbackRequest{Version: version}, &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}

// DeleteArtifactVersion deletes a specific artifact version (blob + manifest).
func (c *Client) DeleteArtifactVersion(owner, artifactName, version string) error {
	url := c.baseURL + artifactBasePath(owner) + "/" + artifactName + "/versions/" + version
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("%d - %s", resp.StatusCode, body)
	}
	return nil
}

// DeleteArtifact deletes all versions of an artifact (blob, manifests, aliases, and config).
func (c *Client) DeleteArtifact(owner, artifactName string) error {
	url := c.baseURL + artifactBasePath(owner) + "/" + artifactName
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("%d - %s", resp.StatusCode, body)
	}
	return nil
}

// DeleteAlias removes an alias from an artifact.
func (c *Client) DeleteAlias(owner, artifactName, alias string) error {
	url := c.baseURL + artifactBasePath(owner) + "/" + artifactName + "/aliases/" + alias
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	resp, err := c.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("%d - %s", resp.StatusCode, body)
	}
	return nil
}

// SetAlias sets an alias to point to a specific version of an artifact.
func (c *Client) SetAlias(owner, artifactName, alias, version string) (*SetAliasResponse, error) {
	path := artifactBasePath(owner) + "/" + artifactName + "/aliases/" + alias
	var result SetAliasResponse
	if err := c.Put(path, SetAliasRequest{Version: version}, &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}

// GetPurgeConfig retrieves the purge configuration for an artifact.
func (c *Client) GetPurgeConfig(owner, artifactName string) (*PurgeConfig, error) {
	var result PurgeConfig
	path := artifactBasePath(owner) + "/" + artifactName + "/purge-config"
	if err := c.Get(path, &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}

// SetPurgeConfig stores a new purge configuration for an artifact.
func (c *Client) SetPurgeConfig(owner, artifactName string, config PurgeConfig) error {
	path := artifactBasePath(owner) + "/" + artifactName + "/purge-config"
	return c.Put(path, config, nil, nil)
}

// PurgeArtifact triggers an immediate purge run for an artifact.
func (c *Client) PurgeArtifact(owner, artifactName string) (*PurgeResponse, error) {
	path := artifactBasePath(owner) + "/" + artifactName + "/purge"
	var result PurgeResponse
	if err := c.Post(path, struct{}{}, &result, nil); err != nil {
		return nil, err
	}
	return &result, nil
}
