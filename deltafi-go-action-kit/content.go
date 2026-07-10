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
// ABOUTME: Content storage types and S3 operations.
// ABOUTME: Defines Segment, ActionContent, ContentService, and helpers for saving/loading content.

package actionkit

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"slices"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/feature/s3/manager"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/google/uuid"
)

// Segment is a reference to a byte range stored in MinIO/S3.
type Segment struct {
	UUID   string `json:"uuid"`
	Offset int64  `json:"offset"`
	Size   int64  `json:"size"`
	DID    string `json:"did"`
}

// objectPath returns the MinIO object path for this segment.
func (s Segment) objectPath() string {
	prefix := s.DID
	if len(prefix) > 3 {
		prefix = prefix[:3]
	}
	return fmt.Sprintf("%s/%s/%s", prefix, s.DID, s.UUID)
}

// ActionContent represents a named piece of content composed of one or more segments.
//
// The unexported actx back-reference lets the load methods find the
// ContentService without forcing every caller to pass it explicitly. It is
// populated automatically by the worker when an action receives content, by
// the SaveContent helpers when a new content is created, and by the
// TestContext.Content helper. It is never marshaled to JSON.
type ActionContent struct {
	Name      string    `json:"name"`
	MediaType string    `json:"mediaType"`
	Segments  []Segment `json:"segments"`
	Tags      []string  `json:"tags,omitempty"`

	actx *ActionContext `json:"-"`
}

// errUnboundContent is returned by load methods when the content has no
// associated ActionContext (e.g. constructed via struct literal in a test
// without going through TestContext.Content). Callers can fall back to
// loading via cs.Load(ctx, content.Segments) directly.
var errUnboundContent = fmt.Errorf("ActionContent has no associated ActionContext: load via the action's ContentService directly")

// contentService returns the wired content service, or an error if the
// content was constructed without an ActionContext back-reference.
func (ac ActionContent) contentService() (ContentServiceBase, error) {
	if ac.actx == nil || ac.actx.ContentService == nil {
		return nil, errUnboundContent
	}
	return ac.actx.ContentService, nil
}

// contentContext returns the context from the wired ActionContext, or
// context.Background() if no ActionContext is set.
func (ac ActionContent) contentContext() context.Context {
	if ac.actx != nil && ac.actx.ctx != nil {
		return ac.actx.ctx
	}
	return context.Background()
}

// LoadBytes fetches and concatenates all segments from storage using the
// ContentService wired to this content's ActionContext.
func (ac ActionContent) LoadBytes() ([]byte, error) {
	cs, err := ac.contentService()
	if err != nil {
		return nil, err
	}
	return cs.Load(ac.contentContext(), ac.Segments)
}

// LoadString fetches all segments and returns the content as a string.
func (ac ActionContent) LoadString() (string, error) {
	data, err := ac.LoadBytes()
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// LoadReader returns a streaming io.ReadCloser for this content.
// The caller must close the returned reader when done.
func (ac ActionContent) LoadReader() (io.ReadCloser, error) {
	cs, err := ac.contentService()
	if err != nil {
		return nil, err
	}
	return cs.LoadReader(ac.contentContext(), ac.Segments)
}

// Size returns the total size of all segments in bytes.
func (ac ActionContent) Size() int64 {
	var total int64
	for _, seg := range ac.Segments {
		total += seg.Size
	}
	return total
}

// Copy returns a deep copy of this content, preserving the ActionContext
// back-reference so that LoadBytes/LoadString/LoadReader work on the copy.
func (ac ActionContent) Copy() ActionContent {
	segs := make([]Segment, len(ac.Segments))
	copy(segs, ac.Segments)
	var tags []string
	if ac.Tags != nil {
		tags = make([]string, len(ac.Tags))
		copy(tags, ac.Tags)
	}
	return ActionContent{
		Name:      ac.Name,
		MediaType: ac.MediaType,
		Segments:  segs,
		Tags:      tags,
		actx:      ac.actx,
	}
}

// Subcontent creates a new ActionContent referencing a byte range within this content.
// It adjusts segment offsets and sizes without fetching data from storage.
func (ac ActionContent) Subcontent(offset, size int64) ActionContent {
	return ac.SubcontentNamed(offset, size, ac.Name, ac.MediaType)
}

// SubcontentNamed creates a subcontent with a custom name and media type.
func (ac ActionContent) SubcontentNamed(offset, size int64, name, mediaType string) ActionContent {
	if offset < 0 {
		panic(fmt.Sprintf("subcontent offset must be non-negative, got %d", offset))
	}
	if size < 0 {
		panic(fmt.Sprintf("subcontent size must be non-negative, got %d", size))
	}
	totalSize := ac.Size()
	if offset > totalSize || size > totalSize-offset {
		panic(fmt.Sprintf("subcontent offset + size (%d + %d) exceeds total content size of %d", offset, size, totalSize))
	}
	if size == 0 {
		return ActionContent{
			Name:      name,
			MediaType: mediaType,
			Segments:  nil,
			actx:      ac.actx,
		}
	}

	var segs []Segment
	remaining := size
	pos := int64(0)

	for _, seg := range ac.Segments {
		if remaining <= 0 {
			break
		}

		segEnd := pos + seg.Size
		if segEnd <= offset {
			// Segment is entirely before the requested range
			pos = segEnd
			continue
		}

		// Calculate the overlap between this segment and the requested range
		skipBytes := int64(0)
		if offset > pos {
			skipBytes = offset - pos
		}

		available := seg.Size - skipBytes
		take := min(available, remaining)

		segs = append(segs, Segment{
			UUID:   seg.UUID,
			Offset: seg.Offset + skipBytes,
			Size:   take,
			DID:    seg.DID,
		})

		remaining -= take
		pos = segEnd
	}

	return ActionContent{
		Name:      name,
		MediaType: mediaType,
		Segments:  segs,
		actx:      ac.actx,
	}
}

// Append adds all segments from another ActionContent to the end of this content.
func (ac *ActionContent) Append(other ActionContent) {
	ac.Segments = append(ac.Segments, other.Segments...)
}

// Prepend adds all segments from another ActionContent to the beginning of this content.
func (ac *ActionContent) Prepend(other ActionContent) {
	merged := make([]Segment, 0, len(other.Segments)+len(ac.Segments))
	merged = append(merged, other.Segments...)
	merged = append(merged, ac.Segments...)
	ac.Segments = merged
}

// SetName updates the content name.
func (ac *ActionContent) SetName(name string) {
	ac.Name = name
}

// SetMediaType updates the content media type.
func (ac *ActionContent) SetMediaType(mediaType string) {
	ac.MediaType = mediaType
}

// AddTag appends a tag to this content.
func (ac *ActionContent) AddTag(tag string) {
	ac.Tags = append(ac.Tags, tag)
}

// RemoveTag removes the first occurrence of a tag. Returns true if the tag was found.
func (ac *ActionContent) RemoveTag(tag string) bool {
	for i, t := range ac.Tags {
		if t == tag {
			ac.Tags = append(ac.Tags[:i], ac.Tags[i+1:]...)
			return true
		}
	}
	return false
}

// HasTag returns true if this content has the given tag.
func (ac ActionContent) HasTag(tag string) bool {
	return slices.Contains(ac.Tags, tag)
}

// ContentItem is one in-memory item for SaveContentBatch.
type ContentItem struct {
	Name      string
	MediaType string
	Data      []byte
}

// ContentWrite is one streamed item for SaveWriterContentBatch; Write produces the
// content for the provided io.Writer.
type ContentWrite struct {
	Name      string
	MediaType string
	Write     func(io.Writer) error
}

// ContentServiceBase is the interface for content storage operations.
// ContentService (S3) and InMemoryContentStore (testing) both implement this.
type ContentServiceBase interface {
	Save(ctx context.Context, did string, data []byte) (Segment, error)
	// SaveFromReader streams reader data into storage in bounded parts,
	// returning a Segment whose Size is the number of bytes actually streamed.
	SaveFromReader(ctx context.Context, did string, reader io.Reader) (Segment, error)
	Load(ctx context.Context, segments []Segment) ([]byte, error)
	LoadReader(ctx context.Context, segments []Segment) (io.ReadCloser, error)
	Delete(ctx context.Context, segments []Segment) error
}

// streamingPartSize is the multipart part size used for uploads, matching the
// Java kit's 5 MiB default (also the S3 minimum). The uploader buffers at most
// streamingPartSize per in-flight part, so peak memory for a save is roughly
// (part concurrency) × streamingPartSize, independent of content size.
const streamingPartSize = 5 << 20 // 5 MiB

// contentService stores content in an S3-compatible backend.
type contentService struct {
	client   *s3.Client
	uploader *manager.Uploader
	bucket   string
}

// newContentService creates a contentService connected to the S3 endpoint.
// The endpoint can include a scheme (http:// or https://); if present, it is
// stripped and used to infer TLS (unless useSSL is explicitly true).
func newContentService(endpoint, accessKey, secretKey, bucket string, useSSL bool) (*contentService, error) {
	if after, ok := strings.CutPrefix(endpoint, "https://"); ok {
		endpoint = after
		useSSL = true
	} else if after, ok := strings.CutPrefix(endpoint, "http://"); ok {
		endpoint = after
	}
	scheme := "http"
	if useSSL {
		scheme = "https"
	}

	cfg := aws.Config{
		Region:                     "us-east-1",
		Credentials:                credentials.NewStaticCredentialsProvider(accessKey, secretKey, ""),
		RequestChecksumCalculation: aws.RequestChecksumCalculationWhenRequired,
		ResponseChecksumValidation: aws.ResponseChecksumValidationWhenRequired,
	}
	client := s3.NewFromConfig(cfg, func(o *s3.Options) {
		o.BaseEndpoint = aws.String(scheme + "://" + endpoint)
		o.UsePathStyle = true
	})
	uploader := manager.NewUploader(client, func(u *manager.Uploader) {
		u.PartSize = streamingPartSize
	})
	return &contentService{client: client, uploader: uploader, bucket: bucket}, nil
}

// Save stores data and returns a Segment reference. The upload multiparts when
// the data exceeds the part size, so large objects stay within the backend's
// non-chunked request limit.
func (cs *contentService) Save(ctx context.Context, did string, data []byte) (Segment, error) {
	seg := Segment{
		UUID:   uuid.New().String(),
		Offset: 0,
		Size:   int64(len(data)),
		DID:    did,
	}
	if _, err := cs.uploader.Upload(ctx, &s3.PutObjectInput{
		Bucket: aws.String(cs.bucket),
		Key:    aws.String(seg.objectPath()),
		Body:   bytes.NewReader(data),
	}); err != nil {
		return Segment{}, fmt.Errorf("failed to put object: %w", err)
	}
	return seg, nil
}

// SaveFromReader streams data from an io.Reader into storage and returns a
// Segment reference. The uploader multiparts as needed and never buffers the
// whole object in memory. The recorded segment size is the number of bytes
// actually streamed, so a segment can never misreport the stored object's length.
func (cs *contentService) SaveFromReader(ctx context.Context, did string, reader io.Reader) (Segment, error) {
	seg := Segment{
		UUID:   uuid.New().String(),
		Offset: 0,
		DID:    did,
	}

	counted := &countingReader{r: reader}
	if _, err := cs.uploader.Upload(ctx, &s3.PutObjectInput{
		Bucket: aws.String(cs.bucket),
		Key:    aws.String(seg.objectPath()),
		Body:   counted,
	}); err != nil {
		return Segment{}, fmt.Errorf("failed to put object: %w", err)
	}
	seg.Size = counted.n
	return seg, nil
}

// countingReader tracks how many bytes have been read through it, so an
// unknown-size upload can report the true streamed length.
type countingReader struct {
	r io.Reader
	n int64
}

func (c *countingReader) Read(p []byte) (int, error) {
	n, err := c.r.Read(p)
	c.n += int64(n)
	return n, err
}

// Load fetches and concatenates data from the given segments into memory.
// Callers handling large content should prefer LoadReader.
func (cs *contentService) Load(ctx context.Context, segments []Segment) ([]byte, error) {
	rc, err := cs.LoadReader(ctx, segments)
	if err != nil {
		return nil, err
	}
	return readAllAndClose(rc)
}

// readAllAndClose reads rc to EOF and closes it. It returns the read error if
// one occurred, otherwise the close error: a close failure after a successful
// read can signal a truncated or incomplete stream, so it is surfaced rather
// than dropped.
func readAllAndClose(rc io.ReadCloser) ([]byte, error) {
	data, readErr := io.ReadAll(rc)
	closeErr := rc.Close()
	if readErr != nil {
		return nil, readErr
	}
	if closeErr != nil {
		return nil, closeErr
	}
	return data, nil
}

// segmentRange returns the HTTP Range header value selecting a segment's bytes,
// so the backend serves only the requested range instead of the whole object.
func segmentRange(seg Segment) string {
	return fmt.Sprintf("bytes=%d-%d", seg.Offset, seg.Offset+seg.Size-1)
}

// loadSegment opens a single segment for reading via a server-side Range
// request. Empty segments resolve to an empty reader without a request.
func (cs *contentService) loadSegment(ctx context.Context, seg Segment) (io.ReadCloser, error) {
	if seg.Size <= 0 {
		return io.NopCloser(bytes.NewReader(nil)), nil
	}
	out, err := cs.client.GetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(cs.bucket),
		Key:    aws.String(seg.objectPath()),
		Range:  aws.String(segmentRange(seg)),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to get object %s: %w", seg.objectPath(), err)
	}
	return out.Body, nil
}

// LoadReader returns a streaming io.ReadCloser for the given segments.
// Segments are fetched lazily, one at a time, and the caller must close the
// returned reader when done.
func (cs *contentService) LoadReader(ctx context.Context, segments []Segment) (io.ReadCloser, error) {
	if len(segments) == 0 {
		return io.NopCloser(bytes.NewReader(nil)), nil
	}
	return &segmentReader{ctx: ctx, service: cs, segments: segments}, nil
}

// segmentReader opens segment bodies on demand, keeping at most one S3 response
// body open while content is streamed.
type segmentReader struct {
	ctx      context.Context
	service  *contentService
	opener   func(context.Context, Segment) (io.ReadCloser, error)
	segments []Segment
	index    int
	current  io.ReadCloser
	limited  io.Reader
	pending  error
	closed   bool
}

func (sr *segmentReader) Read(p []byte) (int, error) {
	if sr.closed {
		return 0, io.ErrClosedPipe
	}
	if sr.pending != nil {
		err := sr.pending
		sr.pending = nil
		return 0, err
	}
	if len(p) == 0 {
		return 0, nil
	}

	for {
		if sr.current == nil {
			if sr.index >= len(sr.segments) {
				return 0, io.EOF
			}
			seg := sr.segments[sr.index]
			sr.index++
			body, err := sr.open(seg)
			if err != nil {
				return 0, err
			}
			sr.current = body
			sr.limited = io.LimitReader(body, seg.Size)
		}

		n, err := sr.limited.Read(p)
		if err == io.EOF {
			closeErr := sr.current.Close()
			sr.current = nil
			sr.limited = nil
			if n > 0 {
				sr.pending = closeErr
				return n, nil
			}
			if closeErr != nil {
				return 0, closeErr
			}
			continue
		}
		return n, err
	}
}

func (sr *segmentReader) open(seg Segment) (io.ReadCloser, error) {
	if sr.opener != nil {
		return sr.opener(sr.ctx, seg)
	}
	return sr.service.loadSegment(sr.ctx, seg)
}

func (sr *segmentReader) Close() error {
	if sr.closed {
		return nil
	}
	sr.closed = true
	if sr.current != nil {
		err := sr.current.Close()
		sr.current = nil
		sr.limited = nil
		return err
	}
	if sr.pending != nil {
		err := sr.pending
		sr.pending = nil
		return err
	}
	return nil
}

// Delete removes the objects for the given segments from storage.
func (cs *contentService) Delete(ctx context.Context, segments []Segment) error {
	for _, seg := range segments {
		if _, err := cs.client.DeleteObject(ctx, &s3.DeleteObjectInput{
			Bucket: aws.String(cs.bucket),
			Key:    aws.String(seg.objectPath()),
		}); err != nil {
			return fmt.Errorf("failed to remove object %s: %w", seg.objectPath(), err)
		}
	}
	return nil
}

// SaveContent stores data and returns an ActionContent with a single segment.
// The segment is tracked on the ActionContext for orphan cleanup, and the
// returned content is wired back to the same ActionContext so it can be loaded
// later via content.LoadBytes(ctx).
func SaveContent(ctx context.Context, cs ContentServiceBase, actx *ActionContext, name, mediaType string, data []byte) (*ActionContent, error) {
	seg, err := cs.Save(ctx, actx.DID, data)
	if err != nil {
		return nil, err
	}
	actx.trackSegment(seg)
	return &ActionContent{Name: name, MediaType: mediaType, Segments: []Segment{seg}, actx: actx}, nil
}

// SaveContentString stores a string and returns an ActionContent with a single segment.
func SaveContentString(ctx context.Context, cs ContentServiceBase, actx *ActionContext, name, mediaType, data string) (*ActionContent, error) {
	return SaveContent(ctx, cs, actx, name, mediaType, []byte(data))
}

// SaveContentFromReader streams data from an io.Reader into storage and returns
// an ActionContent whose size reflects the bytes actually streamed.
func SaveContentFromReader(ctx context.Context, cs ContentServiceBase, actx *ActionContext, name, mediaType string, reader io.Reader) (*ActionContent, error) {
	seg, err := cs.SaveFromReader(ctx, actx.DID, reader)
	if err != nil {
		return nil, err
	}
	actx.trackSegment(seg)
	return &ActionContent{Name: name, MediaType: mediaType, Segments: []Segment{seg}, actx: actx}, nil
}

// SaveContentFromWriter streams content produced by fn into storage and returns
// an ActionContent whose size reflects the bytes actually streamed. fn runs on a
// separate goroutine and writes to the provided io.Writer; data streams to storage
// as it is written and is never fully buffered. If fn returns an error the save is
// aborted and that error is returned; a storage failure likewise unblocks fn.
func SaveContentFromWriter(ctx context.Context, cs ContentServiceBase, actx *ActionContext, name, mediaType string, fn func(io.Writer) error) (*ActionContent, error) {
	pr, pw := io.Pipe()
	go func() {
		// CloseWithError(nil) is a normal EOF close.
		pw.CloseWithError(fn(pw))
	}()

	content, err := SaveContentFromReader(ctx, cs, actx, name, mediaType, pr)
	if err != nil {
		pr.CloseWithError(err) // unblock fn if the upload failed before draining
		return nil, err
	}
	return content, nil
}

// bindContents sets the actx back-reference on every content in the slice.
// Used to wire contents that arrived via JSON deserialization (e.g. from the
// Redis queue) so the convenience load methods work without explicit plumbing.
func bindContents(actx *ActionContext, contents []ActionContent) {
	for i := range contents {
		contents[i].actx = actx
	}
}
