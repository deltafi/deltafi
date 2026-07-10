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
// ABOUTME: Tests proving the content save/load paths stream and stay memory-bounded.
// ABOUTME: Exercises the real S3 client against an httptest S3 server (no mocks).

package actionkit

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

// streamTestContentSize is far larger than any bounded buffer, so a path that
// fully materializes content shows up as hundreds of MiB of live heap while a
// streaming path stays at part-buffer order.
const streamTestContentSize = 200 << 20 // 200 MiB

// streamTestHeapBound is the maximum tolerated growth in live heap while moving
// streamTestContentSize bytes. It sits far below the content size (so a
// full-object buffer fails) yet well above a few part buffers plus GC slack.
const streamTestHeapBound = 80 << 20 // 80 MiB

// wantStreamPartSize is the multipart part size we expect the kit to use,
// matching the Java kit's 5 MiB default.
const wantStreamPartSize = 5 << 20 // 5 MiB

// fillReader yields a fixed byte up to remaining bytes without ever allocating
// the whole stream, so the only heap a writer accrues is its own buffering.
type fillReader struct{ remaining int64 }

func (r *fillReader) Read(p []byte) (int, error) {
	if r.remaining <= 0 {
		return 0, io.EOF
	}
	n := len(p)
	if int64(n) > r.remaining {
		n = int(r.remaining)
	}
	for i := 0; i < n; i++ {
		p[i] = 'a'
	}
	r.remaining -= int64(n)
	return n, nil
}

// s3TestServer is a minimal, streaming S3-compatible server. GET bodies are
// generated on the fly and PUT/part bodies are drained to io.Discard, so the
// server never buffers a whole object. It records each UploadPart body size so
// tests can assert the negotiated part size.
type s3TestServer struct {
	*httptest.Server
	objectSize int64

	mu          sync.Mutex
	partSizes   []int64
	singlePut   int64
	getRanges   []string
	deletedKeys []string
}

// objectByteAt returns the deterministic content byte at an absolute offset, so
// a Range read can be verified to have returned the correct bytes.
func objectByteAt(offset int64) byte {
	return byte(offset % 251)
}

func newS3TestServer(objectSize int64) *s3TestServer {
	s := &s3TestServer{objectSize: objectSize}
	s.Server = httptest.NewServer(http.HandlerFunc(s.handle))
	return s
}

func (s *s3TestServer) recordPart(n int64) {
	s.mu.Lock()
	s.partSizes = append(s.partSizes, n)
	s.mu.Unlock()
}

func (s *s3TestServer) parts() []int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]int64, len(s.partSizes))
	copy(out, s.partSizes)
	return out
}

func (s *s3TestServer) ranges() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]string, len(s.getRanges))
	copy(out, s.getRanges)
	return out
}

func (s *s3TestServer) deletes() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]string, len(s.deletedKeys))
	copy(out, s.deletedKeys)
	return out
}

func (s *s3TestServer) handle(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	switch r.Method {
	case http.MethodPost:
		drain(r.Body)
		switch {
		case q.Has("uploads"): // initiate multipart upload
			writeXML(w, `<?xml version="1.0" encoding="UTF-8"?>`+
				`<InitiateMultipartUploadResult><Bucket>test</Bucket>`+
				`<Key>k</Key><UploadId>test-upload-id</UploadId></InitiateMultipartUploadResult>`)
		case q.Has("uploadId"): // complete multipart upload
			writeXML(w, `<?xml version="1.0" encoding="UTF-8"?>`+
				`<CompleteMultipartUploadResult><Location>x</Location>`+
				`<Bucket>test</Bucket><Key>k</Key><ETag>"final"</ETag></CompleteMultipartUploadResult>`)
		default:
			http.Error(w, "unexpected POST", http.StatusBadRequest)
		}
	case http.MethodPut:
		// The signed-streaming body is wrapped in aws-chunked framing, so the
		// raw body length overcounts the payload. The true payload size is in
		// X-Amz-Decoded-Content-Length when present.
		n := payloadSize(r)
		if q.Has("partNumber") {
			s.recordPart(n)
			w.Header().Set("ETag", fmt.Sprintf("%q", "part-"+q.Get("partNumber")))
		} else {
			s.mu.Lock()
			s.singlePut = n
			s.mu.Unlock()
			w.Header().Set("ETag", `"single"`)
		}
		w.WriteHeader(http.StatusOK)
	case http.MethodHead:
		w.Header().Set("Content-Length", strconv.FormatInt(s.objectSize, 10))
		w.Header().Set("Accept-Ranges", "bytes")
		w.WriteHeader(http.StatusOK)
	case http.MethodGet:
		s.serveObject(w, r)
	case http.MethodDelete:
		s.mu.Lock()
		s.deletedKeys = append(s.deletedKeys, r.URL.Path)
		s.mu.Unlock()
		w.WriteHeader(http.StatusNoContent)
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

// serveObject streams objectSize bytes, honoring a leading byte-range offset,
// without ever allocating the body.
func (s *s3TestServer) serveObject(w http.ResponseWriter, r *http.Request) {
	start := int64(0)
	ranged := false
	if rng := r.Header.Get("Range"); strings.HasPrefix(rng, "bytes=") {
		s.mu.Lock()
		s.getRanges = append(s.getRanges, rng)
		s.mu.Unlock()
		spec := strings.TrimPrefix(rng, "bytes=")
		if before, _, ok := strings.Cut(spec, "-"); ok {
			if v, err := strconv.ParseInt(before, 10, 64); err == nil {
				start = v
				ranged = true
			}
		}
	}
	length := s.objectSize - start
	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Last-Modified", "Wed, 21 Oct 2015 07:28:00 GMT")
	w.Header().Set("ETag", `"object"`)
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.FormatInt(length, 10))
	if ranged {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, s.objectSize-1, s.objectSize))
		w.WriteHeader(http.StatusPartialContent)
	}
	buf := make([]byte, 32*1024)
	pos := start
	remaining := length
	for remaining > 0 {
		n := min(int64(len(buf)), remaining)
		for i := range n {
			buf[i] = objectByteAt(pos + i)
		}
		if _, err := w.Write(buf[:n]); err != nil {
			return // client closed early; nothing more to do
		}
		pos += n
		remaining -= n
	}
}

func writeXML(w http.ResponseWriter, body string) {
	w.Header().Set("Content-Type", "application/xml")
	w.WriteHeader(http.StatusOK)
	_, _ = io.WriteString(w, body)
}

// drain reads r to EOF using a small fixed buffer, returning the byte count.
func drain(r io.Reader) int64 {
	n, _ := io.Copy(io.Discard, r)
	return n
}

// payloadSize drains the request body and returns the true payload size,
// preferring X-Amz-Decoded-Content-Length so aws-chunked signature framing in
// the raw body is not counted.
func payloadSize(r *http.Request) int64 {
	raw := drain(r.Body)
	if dec := r.Header.Get("X-Amz-Decoded-Content-Length"); dec != "" {
		if v, err := strconv.ParseInt(dec, 10, 64); err == nil {
			return v
		}
	}
	return raw
}

func newTestContentService(t *testing.T, serverURL string) *contentService {
	t.Helper()
	cs, err := newContentService(serverURL, "test", "test", "test", false)
	if err != nil {
		t.Fatalf("newContentService: %v", err)
	}
	return cs
}

// peakHeapAllocDelta runs fn on a goroutine while sampling live heap, returning
// the maximum growth over the pre-run baseline. A streaming fn keeps this at
// part-buffer order; a fn that materializes the whole object pushes it to the
// object size.
func peakHeapAllocDelta(t *testing.T, fn func() error) uint64 {
	t.Helper()
	runtime.GC()
	var base runtime.MemStats
	runtime.ReadMemStats(&base)
	peak := base.HeapAlloc

	done := make(chan error, 1)
	go func() { done <- fn() }()

	ticker := time.NewTicker(2 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case err := <-done:
			if err != nil {
				t.Fatalf("operation under measurement failed: %v", err)
			}
			if peak <= base.HeapAlloc {
				return 0
			}
			return peak - base.HeapAlloc
		case <-ticker.C:
			var m runtime.MemStats
			runtime.ReadMemStats(&m)
			if m.HeapAlloc > peak {
				peak = m.HeapAlloc
			}
		}
	}
}

// --- Unknown-size streaming: bounded memory without a known length ---

func TestContentService_SaveFromReader_UnknownSize_BoundedMemory(t *testing.T) {
	if testing.Short() {
		t.Skip("streaming memory test moves 200 MiB; skipped under -short")
	}
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	var seg Segment
	delta := peakHeapAllocDelta(t, func() error {
		var err error
		seg, err = cs.SaveFromReader(context.Background(), "did", &fillReader{remaining: streamTestContentSize})
		return err
	})
	if seg.Size != streamTestContentSize {
		t.Errorf("seg.Size = %d, want %d (must reflect the bytes actually streamed)", seg.Size, int64(streamTestContentSize))
	}
	if delta >= streamTestHeapBound {
		t.Fatalf("peak heap growth during unknown-size save = %d MiB, want < %d MiB (content is %d MiB) — content appears to be fully buffered",
			delta>>20, int64(streamTestHeapBound)>>20, int64(streamTestContentSize)>>20)
	}
}

// --- Bounded part size ---

func TestContentService_SaveFromReader_UsesBoundedPartSize(t *testing.T) {
	if testing.Short() {
		t.Skip("streaming part-size test moves 200 MiB; skipped under -short")
	}
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	seg, err := cs.SaveFromReader(context.Background(), "did", &fillReader{remaining: streamTestContentSize})
	if err != nil {
		t.Fatalf("SaveFromReader: %v", err)
	}
	if seg.Size != streamTestContentSize {
		t.Errorf("seg.Size = %d, want %d", seg.Size, int64(streamTestContentSize))
	}

	parts := srv.parts()
	if len(parts) == 0 {
		t.Fatal("expected a multipart upload, got none")
	}
	var total int64
	for i, n := range parts {
		total += n
		if n > wantStreamPartSize {
			t.Errorf("part %d size = %d bytes, exceeds bounded part size %d", i, n, int64(wantStreamPartSize))
		}
	}
	if total != streamTestContentSize {
		t.Errorf("uploaded %d bytes across parts, want %d", total, int64(streamTestContentSize))
	}
}

// --- Bounded memory: the content is never fully resident in RAM ---

func TestContentService_SaveFromReader_BoundedMemory(t *testing.T) {
	if testing.Short() {
		t.Skip("streaming memory test moves 200 MiB; skipped under -short")
	}
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	delta := peakHeapAllocDelta(t, func() error {
		_, err := cs.SaveFromReader(context.Background(), "did", &fillReader{remaining: streamTestContentSize})
		return err
	})
	if delta >= streamTestHeapBound {
		t.Fatalf("peak heap growth during save = %d MiB, want < %d MiB (content is %d MiB) — content appears to be fully buffered",
			delta>>20, int64(streamTestHeapBound)>>20, int64(streamTestContentSize)>>20)
	}
}

// TestContentService_SaveFromReader_RecordsStreamedSize proves the stored
// segment size reflects the exact number of bytes streamed. This runs without
// -short, covering the size-recording path the 200 MiB tests skip.
func TestContentService_SaveFromReader_RecordsStreamedSize(t *testing.T) {
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	const streamed = 1234
	seg, err := cs.SaveFromReader(context.Background(), "did", &fillReader{remaining: streamed})
	if err != nil {
		t.Fatalf("SaveFromReader: %v", err)
	}
	if seg.Size != streamed {
		t.Errorf("seg.Size = %d, want %d (segment size must reflect bytes actually streamed)",
			seg.Size, int64(streamed))
	}
}

func TestContentService_LoadReader_UsesServerSideRange(t *testing.T) {
	const objectSize = 4 << 20 // 4 MiB
	srv := newS3TestServer(objectSize)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	const offset = 1 << 20 // 1 MiB
	const size = 64 << 10  // 64 KiB
	segs := []Segment{{UUID: "seg", Offset: offset, Size: size, DID: "did"}}

	rc, err := cs.LoadReader(context.Background(), segs)
	if err != nil {
		t.Fatalf("LoadReader: %v", err)
	}
	defer rc.Close()
	data, err := io.ReadAll(rc)
	if err != nil {
		t.Fatalf("ReadAll: %v", err)
	}

	if int64(len(data)) != size {
		t.Fatalf("read %d bytes, want %d", len(data), int64(size))
	}
	for i := range data {
		if want := objectByteAt(offset + int64(i)); data[i] != want {
			t.Fatalf("byte %d = %d, want %d (offset not honored)", i, data[i], want)
		}
	}

	gotRanges := srv.ranges()
	want := fmt.Sprintf("bytes=%d-%d", int64(offset), int64(offset)+int64(size)-1)
	if len(gotRanges) != 1 || gotRanges[0] != want {
		t.Fatalf("server saw Range headers %v, want exactly [%q] (offset read must fetch only the range, not the whole object)", gotRanges, want)
	}
}

func TestContentService_LoadReader_OpensSegmentsLazily(t *testing.T) {
	const objectSize = 4 << 20 // 4 MiB
	srv := newS3TestServer(objectSize)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	segs := []Segment{
		{UUID: "seg-1", Offset: 0, Size: 8, DID: "did"},
		{UUID: "seg-2", Offset: 1024, Size: 8, DID: "did"},
	}

	rc, err := cs.LoadReader(context.Background(), segs)
	if err != nil {
		t.Fatalf("LoadReader: %v", err)
	}
	defer rc.Close()

	if got := srv.ranges(); len(got) != 0 {
		t.Fatalf("LoadReader opened segments before reading: ranges = %v", got)
	}

	buf := make([]byte, 1)
	if _, err := rc.Read(buf); err != nil {
		t.Fatalf("first Read: %v", err)
	}
	if got := srv.ranges(); len(got) != 1 {
		t.Fatalf("after first Read, opened %d segments, want 1: %v", len(got), got)
	}

	if _, err := io.Copy(io.Discard, rc); err != nil {
		t.Fatalf("drain remaining content: %v", err)
	}
	if got := srv.ranges(); len(got) != 2 {
		t.Fatalf("after drain, opened %d segments, want 2: %v", len(got), got)
	}
}

func TestContentService_LoadReader_BoundedMemory(t *testing.T) {
	if testing.Short() {
		t.Skip("streaming memory test moves 200 MiB; skipped under -short")
	}
	srv := newS3TestServer(streamTestContentSize)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	segs := []Segment{{UUID: "seg", Offset: 0, Size: streamTestContentSize, DID: "did"}}

	var copied int64
	delta := peakHeapAllocDelta(t, func() error {
		rc, err := cs.LoadReader(context.Background(), segs)
		if err != nil {
			return err
		}
		defer rc.Close()
		copied, err = io.Copy(io.Discard, rc)
		return err
	})
	if copied != streamTestContentSize {
		t.Fatalf("read %d bytes, want %d", copied, int64(streamTestContentSize))
	}
	if delta >= streamTestHeapBound {
		t.Fatalf("peak heap growth during load = %d MiB, want < %d MiB (content is %d MiB) — content appears to be fully buffered",
			delta>>20, int64(streamTestHeapBound)>>20, int64(streamTestContentSize)>>20)
	}
}

// --- Delete ---

func TestContentService_Delete_RemovesEachSegment(t *testing.T) {
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	segs := []Segment{
		{UUID: "u1", DID: "did1", Size: 10},
		{UUID: "u2", DID: "did2", Size: 20},
	}
	if err := cs.Delete(context.Background(), segs); err != nil {
		t.Fatalf("Delete: %v", err)
	}

	got := srv.deletes()
	want := []string{"/test/" + segs[0].objectPath(), "/test/" + segs[1].objectPath()}
	if len(got) != len(want) {
		t.Fatalf("server saw %d deletes %v, want %d %v", len(got), got, len(want), want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("delete %d = %q, want %q", i, got[i], want[i])
		}
	}
}

// --- Empty content ---

func TestContentService_EmptyContent(t *testing.T) {
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)

	seg, err := cs.Save(context.Background(), "did", []byte{})
	if err != nil {
		t.Fatalf("Save empty: %v", err)
	}
	if seg.Size != 0 {
		t.Errorf("seg.Size = %d, want 0", seg.Size)
	}

	// Loading a zero-size segment must yield empty data without issuing an
	// (unsatisfiable, 416-inducing) Range request.
	data, err := cs.Load(context.Background(), []Segment{seg})
	if err != nil {
		t.Fatalf("Load empty: %v", err)
	}
	if len(data) != 0 {
		t.Errorf("loaded %d bytes, want 0", len(data))
	}
	if rngs := srv.ranges(); len(rngs) != 0 {
		t.Errorf("empty segment should issue no Range request, got %v", rngs)
	}
}

// --- SaveWriterContent ---

// newTestDeltafile builds a Deltafile wired to an InMemoryContentStore so that
// round-trip save/load tests work without a real S3 server.
func newTestDeltafile(t *testing.T) (*Deltafile, *TestContext) {
	t.Helper()
	tc := NewTestContext("test-did")
	return tc.NewDeltafile(), tc
}

func TestSaveWriterContent_StreamsCallbackOutput(t *testing.T) {
	df, _ := newTestDeltafile(t)
	content, err := df.SaveWriterContent("out.txt", "text/plain", func(w io.Writer) error {
		_, err := io.WriteString(w, "hello from writer")
		return err
	})
	if err != nil {
		t.Fatalf("SaveWriterContent: %v", err)
	}
	got, err := content.LoadString()
	if err != nil {
		t.Fatalf("LoadString: %v", err)
	}
	if got != "hello from writer" {
		t.Fatalf("got %q, want %q", got, "hello from writer")
	}
	if len(df.GetNewContentList()) != 1 {
		t.Fatalf("expected content appended to output list")
	}
}

func TestSaveWriterContent_PropagatesCallbackError(t *testing.T) {
	df, _ := newTestDeltafile(t)
	want := errors.New("boom")
	_, err := df.SaveWriterContent("out.txt", "text/plain", func(w io.Writer) error {
		_, _ = io.WriteString(w, "partial")
		return want
	})
	if !errors.Is(err, want) {
		t.Fatalf("expected callback error to propagate, got %v", err)
	}
	if len(df.GetNewContentList()) != 0 {
		t.Fatalf("failed save must not append content")
	}
}

func TestSaveWriterContent_BoundedMemory(t *testing.T) {
	if testing.Short() {
		t.Skip("streaming memory test moves 200 MiB; skipped under -short")
	}
	srv := newS3TestServer(0)
	defer srv.Close()
	cs := newTestContentService(t, srv.URL)
	actx := &ActionContext{DID: "did", ContentService: cs}

	var content *ActionContent
	delta := peakHeapAllocDelta(t, func() error {
		var err error
		content, err = SaveContentFromWriter(context.Background(), cs, actx, "big.bin", "application/octet-stream", func(w io.Writer) error {
			buf := make([]byte, 64<<10) // 64 KiB chunk, reused each iteration
			for written := int64(0); written < streamTestContentSize; written += int64(len(buf)) {
				if _, err := w.Write(buf); err != nil {
					return err
				}
			}
			return nil
		})
		return err
	})
	if delta >= streamTestHeapBound {
		t.Fatalf("peak heap growth during writer save = %d MiB, want < %d MiB (content is %d MiB) — content appears to be fully buffered",
			delta>>20, int64(streamTestHeapBound)>>20, int64(streamTestContentSize)>>20)
	}
	if content.Size() != streamTestContentSize {
		t.Errorf("size = %d, want %d", content.Size(), streamTestContentSize)
	}
}
