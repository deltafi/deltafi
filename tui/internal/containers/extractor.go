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
package containers

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/charmbracelet/bubbles/progress"
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/deltafi/tui/internal/ui/styles"
	ocispec "github.com/opencontainers/image-spec/specs-go/v1"
	"oras.land/oras-go/v2/registry/remote"
)

type progressMsg struct {
	percent    float64
	bytesRead  int64
	totalBytes int64
}

type errMsg struct{ err error }

func (e errMsg) Error() string { return e.err.Error() }

type progressModel struct {
	spinner    spinner.Model
	progress   progress.Model
	percent    float64
	bytesRead  int64
	totalBytes int64
	err        error
}

func initialProgressModel() progressModel {
	s := spinner.New()
	s.Spinner = spinner.Jump
	s.Style = styles.SuccessStyle

	p := progress.New(
		progress.WithGradient(styles.Base.Dark, styles.Blue.Dark),
		progress.WithWidth(40),
		progress.WithoutPercentage(),
	)

	return progressModel{
		spinner:    s,
		progress:   p,
		percent:    0,
		bytesRead:  0,
		totalBytes: 0,
	}
}

func (m progressModel) Init() tea.Cmd {
	return tea.Batch(spinner.Tick)
}

func (m progressModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "q":
			return m, tea.Quit
		}
	case errMsg:
		m.err = msg
		return m, nil
	case progressMsg:
		m.percent = msg.percent
		m.bytesRead = msg.bytesRead
		m.totalBytes = msg.totalBytes
		return m, nil
	case spinner.TickMsg:
		var cmd tea.Cmd
		m.spinner, cmd = m.spinner.Update(msg)
		return m, cmd
	}
	return m, nil
}

func (m progressModel) View() string {
	spin := m.spinner.View()
	prog := m.progress.ViewAs(m.percent)

	if m.percent > 0.999 {
		spin = styles.SuccessStyle.Bold(true).Render("✓")
	}

	// Convert bytes to megabytes with 3 decimal places
	mbRead := float64(m.bytesRead) / (1024 * 1024)
	mbTotal := float64(m.totalBytes) / (1024 * 1024)
	bytes := fmt.Sprintf("%.3f / %.3f MB", mbRead, mbTotal)

	return fmt.Sprintf("\n%s %s %s\n\n", spin, prog, bytes)
}

type ImageExtractor struct {
	outputDir string
}

func NewImageExtractor(outputDir string) *ImageExtractor {
	return &ImageExtractor{outputDir: outputDir}
}

func (e *ImageExtractor) ExtractImage(ctx context.Context, repo string, tag string, platform string) error {
	fmt.Printf("Connecting to repository: %s\n", repo)

	// Connect to registry
	repository, err := remote.NewRepository(repo)
	if err != nil {
		return fmt.Errorf("failed to connect to repository: %w", err)
	}

	// Get the manifest
	manifestDesc, err := repository.Resolve(ctx, tag)
	if err != nil {
		return fmt.Errorf("failed to resolve manifest: %w", err)
	}

	manifestReader, err := repository.Fetch(ctx, manifestDesc)
	if err != nil {
		return fmt.Errorf("failed to fetch manifest: %w", err)
	}
	defer manifestReader.Close()

	manifestBytes, err := io.ReadAll(manifestReader)
	if err != nil {
		return fmt.Errorf("failed to read manifest: %w", err)
	}

	// Check if this is a manifest list (multi-platform)
	var manifestList ocispec.Index
	if err := json.Unmarshal(manifestBytes, &manifestList); err == nil && manifestList.MediaType == "application/vnd.oci.image.index.v1+json" {
		if platform != "" {
			var targetManifest *ocispec.Descriptor
			for _, manifest := range manifestList.Manifests {
				if manifest.Platform != nil {
					platformStr := fmt.Sprintf("%s/%s", manifest.Platform.OS, manifest.Platform.Architecture)
					if manifest.Platform.Variant != "" {
						platformStr += "/" + manifest.Platform.Variant
					}

					if platformStr == platform {
						targetManifest = &manifest
						break
					}
				}
			}

			if targetManifest == nil {
				return fmt.Errorf("platform %s not found in manifest list", platform)
			}

			// Fetch the platform-specific manifest
			manifestReader, err = repository.Fetch(ctx, *targetManifest)
			if err != nil {
				return fmt.Errorf("failed to fetch platform manifest: %w", err)
			}
			defer manifestReader.Close()

			manifestBytes, err = io.ReadAll(manifestReader)
			if err != nil {
				return fmt.Errorf("failed to read platform manifest: %w", err)
			}
		} else {
			// No platform specified, use the first available platform
			if len(manifestList.Manifests) == 0 {
				return fmt.Errorf("no manifests found in manifest list")
			}

			selectedManifest := manifestList.Manifests[0]
			platformStr := "unknown"
			if selectedManifest.Platform != nil {
				platformStr = fmt.Sprintf("%s/%s", selectedManifest.Platform.OS, selectedManifest.Platform.Architecture)
				if selectedManifest.Platform.Variant != "" {
					platformStr += "/" + selectedManifest.Platform.Variant
				}
			}
			fmt.Printf("Auto-selecting platform: %s\n", platformStr)

			// Fetch the selected platform manifest
			manifestReader, err = repository.Fetch(ctx, selectedManifest)
			if err != nil {
				return fmt.Errorf("failed to fetch platform manifest: %w", err)
			}
			defer manifestReader.Close()

			manifestBytes, err = io.ReadAll(manifestReader)
			if err != nil {
				return fmt.Errorf("failed to read platform manifest: %w", err)
			}
		}
	}

	// Parse the image manifest
	var manifest ocispec.Manifest
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return fmt.Errorf("failed to parse manifest: %w", err)
	}

	// Create output directory
	if err := os.MkdirAll(e.outputDir, 0755); err != nil {
		return fmt.Errorf("failed to create output directory: %w", err)
	}

	// Process each layer
	for i, layer := range manifest.Layers {
		fmt.Printf("\nProcessing artifact %d/%d (%.2f MB)...\n", i+1, len(manifest.Layers), float64(layer.Size)/(1024*1024))

		if err := e.extractLayer(ctx, repository, layer); err != nil {
			fmt.Printf("Warning: failed to extract artifact %d: %v\n", i+1, err)
			continue
		}
	}

	return nil
}

func (e *ImageExtractor) extractLayer(ctx context.Context, repo *remote.Repository, layer ocispec.Descriptor) error {
	layerReader, err := repo.Fetch(ctx, layer)
	if err != nil {
		return err
	}
	defer layerReader.Close()

	// Create a progress model
	p := tea.NewProgram(initialProgressModel())
	done := make(chan struct{})
	go func() {
		if _, err := p.Run(); err != nil {
			fmt.Printf("Error running progress bar: %v\n", err)
		}
		close(done)
	}()

	// Create a progress reader for the layer download
	progressReader := &progressReader{
		reader:    layerReader,
		total:     layer.Size,
		bytesRead: new(int64),
		progress:  p,
	}

	// Handle compressed layers
	var reader io.Reader = progressReader
	if strings.Contains(layer.MediaType, "gzip") {
		gzReader, err := gzip.NewReader(progressReader)
		if err != nil {
			p.Send(errMsg{err: err})
			return err
		}
		defer gzReader.Close()
		reader = gzReader
	}

	// Read tar archive
	tarReader := tar.NewReader(reader)

	for {
		header, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			p.Send(errMsg{err: err})
			return err
		}

		// Skip directories and special files
		if header.Typeflag == tar.TypeDir {
			continue
		}
		if header.Typeflag != tar.TypeReg {
			continue
		}

		// Clean the file path
		cleanPath := filepath.Clean(header.Name)
		if strings.HasPrefix(cleanPath, "/") {
			cleanPath = cleanPath[1:]
		}

		// Skip if empty path
		if cleanPath == "" || cleanPath == "." {
			continue
		}

		outputPath := filepath.Join(e.outputDir, cleanPath)

		// Create directory structure
		if err := os.MkdirAll(filepath.Dir(outputPath), 0755); err != nil {
			p.Send(errMsg{err: err})
			return err
		}

		// Create and write file
		outFile, err := os.OpenFile(outputPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, os.FileMode(header.Mode))
		if err != nil {
			fmt.Printf("Warning: failed to create file %s: %v\n", outputPath, err)
			continue
		}

		_, err = io.Copy(outFile, tarReader)
		outFile.Close()

		if err != nil {
			fmt.Printf("Warning: failed to write file %s: %v\n", outputPath, err)
			continue
		}
	}

	// Send final progress update and quit
	p.Send(progressMsg{
		percent:    1.0,
		bytesRead:  *progressReader.bytesRead,
		totalBytes: layer.Size,
	})
	p.Quit()
	<-done // Wait for the progress bar program to finish

	return nil
}

type progressReader struct {
	reader    io.Reader
	total     int64
	bytesRead *int64
	progress  *tea.Program
}

func (pr *progressReader) Read(p []byte) (n int, err error) {
	n, err = pr.reader.Read(p)
	if n > 0 {
		*pr.bytesRead += int64(n)
		percent := float64(*pr.bytesRead) / float64(pr.total)
		if percent > 1.0 {
			percent = 1.0
		}
		pr.progress.Send(progressMsg{
			percent:    percent,
			bytesRead:  *pr.bytesRead,
			totalBytes: pr.total,
		})
	}
	return n, err
}
