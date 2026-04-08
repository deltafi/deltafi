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
// ABOUTME: Content storage types and MinIO operations.
// ABOUTME: Defines Segment, ActionContent, ContentService, and helpers for saving/loading content.

package actionkit

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"slices"
	"strings"

	"github.com/google/uuid"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
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

// LoadBytes fetches and concatenates all segments from storage using the
// ContentService wired to this content's ActionContext.
func (ac ActionContent) LoadBytes(ctx context.Context) ([]byte, error) {
	cs, err := ac.contentService()
	if err != nil {
		return nil, err
	}
	return cs.Load(ctx, ac.Segments)
}

// LoadString fetches all segments and returns the content as a string.
func (ac ActionContent) LoadString(ctx context.Context) (string, error) {
	data, err := ac.LoadBytes(ctx)
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// LoadReader returns a streaming io.ReadCloser for this content.
// The caller must close the returned reader when done.
func (ac ActionContent) LoadReader(ctx context.Context) (io.ReadCloser, error) {
	cs, err := ac.contentService()
	if err != nil {
		return nil, err
	}
	return cs.LoadReader(ctx, ac.Segments)
}

// Size returns the total size of all segments in bytes.
func (ac ActionContent) Size() int64 {
	var total int64
	for _, seg := range ac.Segments {
		total += seg.Size
	}
	return total
}

// Copy returns a deep copy of this content.
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
	}
}

// Subcontent creates a new ActionContent referencing a byte range within this content.
// It adjusts segment offsets and sizes without fetching data from storage.
func (ac ActionContent) Subcontent(offset, size int64) ActionContent {
	return ac.SubcontentNamed(offset, size, ac.Name, ac.MediaType)
}

// SubcontentNamed creates a subcontent with a custom name and media type.
func (ac ActionContent) SubcontentNamed(offset, size int64, name, mediaType string) ActionContent {
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
	}
}

// Append adds all segments from another ActionContent to the end of this content.
func (ac *ActionContent) Append(other ActionContent) {
	ac.Segments = append(ac.Segments, other.Segments...)
}

// Prepend adds all segments from another ActionContent to the beginning of this content.
func (ac *ActionContent) Prepend(other ActionContent) {
	ac.Segments = append(other.Segments, ac.Segments...)
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

// ContentServiceBase is the interface for content storage operations.
// ContentService (MinIO) and InMemoryContentStore (testing) both implement this.
type ContentServiceBase interface {
	Save(ctx context.Context, did string, data []byte) (Segment, error)
	SaveFromReader(ctx context.Context, did string, reader io.Reader, size int64) (Segment, error)
	Load(ctx context.Context, segments []Segment) ([]byte, error)
	LoadReader(ctx context.Context, segments []Segment) (io.ReadCloser, error)
	Delete(ctx context.Context, segments []Segment) error
}

// ContentService wraps a MinIO client for content storage operations.
type ContentService struct {
	client *minio.Client
	bucket string
}

// NewContentService creates a ContentService connected to MinIO.
// The endpoint can include a scheme (http:// or https://); if present, it is
// stripped and used to infer the Secure setting (unless useSSL is explicitly true).
func NewContentService(endpoint, accessKey, secretKey, bucket string, useSSL bool) (*ContentService, error) {
	if after, ok := strings.CutPrefix(endpoint, "https://"); ok {
		endpoint = after
		useSSL = true
	} else if after, ok := strings.CutPrefix(endpoint, "http://"); ok {
		endpoint = after
	}
	client, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: useSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create minio client: %w", err)
	}
	return &ContentService{client: client, bucket: bucket}, nil
}

// Save stores data in MinIO and returns a Segment reference.
func (cs *ContentService) Save(ctx context.Context, did string, data []byte) (Segment, error) {
	segUUID := uuid.New().String()
	seg := Segment{
		UUID:   segUUID,
		Offset: 0,
		Size:   int64(len(data)),
		DID:    did,
	}

	reader := bytes.NewReader(data)
	_, err := cs.client.PutObject(ctx, cs.bucket, seg.objectPath(), reader, int64(len(data)), minio.PutObjectOptions{})
	if err != nil {
		return Segment{}, fmt.Errorf("failed to put object: %w", err)
	}
	return seg, nil
}

// SaveFromReader stores data from an io.Reader in MinIO and returns a Segment reference.
// The size parameter must match the number of bytes available from the reader.
// Use -1 if the size is unknown; MinIO will buffer internally to determine it.
func (cs *ContentService) SaveFromReader(ctx context.Context, did string, reader io.Reader, size int64) (Segment, error) {
	segUUID := uuid.New().String()
	seg := Segment{
		UUID:   segUUID,
		Offset: 0,
		Size:   size,
		DID:    did,
	}

	info, err := cs.client.PutObject(ctx, cs.bucket, seg.objectPath(), reader, size, minio.PutObjectOptions{})
	if err != nil {
		return Segment{}, fmt.Errorf("failed to put object: %w", err)
	}
	// If size was unknown (-1), update from the actual upload size
	if seg.Size < 0 {
		seg.Size = info.Size
	}
	return seg, nil
}

// Load fetches and concatenates data from the given segments.
func (cs *ContentService) Load(ctx context.Context, segments []Segment) ([]byte, error) {
	var buf bytes.Buffer
	for _, seg := range segments {
		obj, err := cs.client.GetObject(ctx, cs.bucket, seg.objectPath(), minio.GetObjectOptions{})
		if err != nil {
			return nil, fmt.Errorf("failed to get object %s: %w", seg.objectPath(), err)
		}

		data, err := readSegment(obj, seg)
		if err != nil {
			return nil, fmt.Errorf("failed to read object %s: %w", seg.objectPath(), err)
		}
		buf.Write(data)
	}
	return buf.Bytes(), nil
}

// readSegment reads size bytes at the given offset from obj, ensuring close errors are checked.
func readSegment(obj *minio.Object, seg Segment) (data []byte, err error) {
	defer func() {
		if cerr := obj.Close(); cerr != nil && err == nil {
			err = fmt.Errorf("close: %w", cerr)
		}
	}()

	if seg.Offset > 0 {
		if _, err := obj.Seek(seg.Offset, io.SeekStart); err != nil {
			return nil, fmt.Errorf("seek: %w", err)
		}
	}

	var buf bytes.Buffer
	if _, err := io.CopyN(&buf, obj, seg.Size); err != nil {
		return nil, fmt.Errorf("read: %w", err)
	}
	return buf.Bytes(), nil
}

// LoadReader returns a streaming io.ReadCloser for the given segments.
// The caller must close the returned reader when done.
func (cs *ContentService) LoadReader(ctx context.Context, segments []Segment) (io.ReadCloser, error) {
	if len(segments) == 0 {
		return io.NopCloser(bytes.NewReader(nil)), nil
	}

	readers := make([]io.Reader, 0, len(segments))
	closers := make([]io.Closer, 0, len(segments))

	for _, seg := range segments {
		obj, err := cs.client.GetObject(ctx, cs.bucket, seg.objectPath(), minio.GetObjectOptions{})
		if err != nil {
			// Close any already-opened objects before returning
			for _, c := range closers {
				c.Close()
			}
			return nil, fmt.Errorf("failed to get object %s: %w", seg.objectPath(), err)
		}
		closers = append(closers, obj)

		if seg.Offset > 0 {
			if _, err := obj.Seek(seg.Offset, io.SeekStart); err != nil {
				for _, c := range closers {
					c.Close()
				}
				return nil, fmt.Errorf("failed to seek in object %s: %w", seg.objectPath(), err)
			}
		}

		readers = append(readers, io.LimitReader(obj, seg.Size))
	}

	return &multiCloser{
		Reader:  io.MultiReader(readers...),
		closers: closers,
	}, nil
}

// multiCloser wraps a Reader and closes multiple underlying resources on Close.
type multiCloser struct {
	io.Reader
	closers []io.Closer
}

func (mc *multiCloser) Close() error {
	var firstErr error
	for _, c := range mc.closers {
		if err := c.Close(); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}

// Delete removes the objects for the given segments from MinIO.
func (cs *ContentService) Delete(ctx context.Context, segments []Segment) error {
	for _, seg := range segments {
		if err := cs.client.RemoveObject(ctx, cs.bucket, seg.objectPath(), minio.RemoveObjectOptions{}); err != nil {
			return fmt.Errorf("failed to remove object %s: %w", seg.objectPath(), err)
		}
	}
	return nil
}

// SaveContent stores data in MinIO and returns an ActionContent with a single segment.
// The segment is tracked on the ActionContext for orphan cleanup, and the
// returned content is wired back to the same ActionContext so it can be loaded
// later via content.LoadBytes(ctx).
func SaveContent(ctx context.Context, cs ContentServiceBase, actx *ActionContext, data []byte, name, mediaType string) (*ActionContent, error) {
	seg, err := cs.Save(ctx, actx.DID, data)
	if err != nil {
		return nil, err
	}
	actx.trackSegment(seg)
	return &ActionContent{
		Name:      name,
		MediaType: mediaType,
		Segments:  []Segment{seg},
		actx:      actx,
	}, nil
}

// SaveContentString stores a string in MinIO and returns an ActionContent with a single segment.
func SaveContentString(ctx context.Context, cs ContentServiceBase, actx *ActionContext, s, name, mediaType string) (*ActionContent, error) {
	return SaveContent(ctx, cs, actx, []byte(s), name, mediaType)
}

// SaveContentFromReader stores data from an io.Reader in MinIO and returns an ActionContent.
// The size parameter must match the number of bytes available from the reader.
// Use -1 if the size is unknown; MinIO will buffer internally to determine it.
func SaveContentFromReader(ctx context.Context, cs ContentServiceBase, actx *ActionContext, reader io.Reader, size int64, name, mediaType string) (*ActionContent, error) {
	seg, err := cs.SaveFromReader(ctx, actx.DID, reader, size)
	if err != nil {
		return nil, err
	}
	actx.trackSegment(seg)
	return &ActionContent{
		Name:      name,
		MediaType: mediaType,
		Segments:  []Segment{seg},
		actx:      actx,
	}, nil
}

// bindContents sets the actx back-reference on every content in the slice.
// Used to wire contents that arrived via JSON deserialization (e.g. from the
// Redis queue) so the convenience load methods work without explicit plumbing.
func bindContents(actx *ActionContext, contents []ActionContent) {
	for i := range contents {
		contents[i].actx = actx
	}
}
