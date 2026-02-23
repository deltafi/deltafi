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

// ABOUTME: Tests for file diff comparison and unified diff generation.
// ABOUTME: Covers new, deleted, modified, and identical file scenarios.
package generator

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestCompareFiles_NewFile(t *testing.T) {
	diff := CompareFiles("test.txt", nil, []byte("new content"))

	if diff == nil {
		t.Fatal("CompareFiles returned nil")
	}
	if diff.Path != "test.txt" {
		t.Errorf("Path = %q, want 'test.txt'", diff.Path)
	}
	if !diff.IsNew {
		t.Error("expected IsNew to be true")
	}
	if diff.IsDeleted {
		t.Error("expected IsDeleted to be false")
	}
	if diff.IsModified {
		t.Error("expected IsModified to be false")
	}
	if diff.IsIdentical {
		t.Error("expected IsIdentical to be false")
	}
	if diff.OldContent != nil {
		t.Error("expected OldContent to be nil")
	}
	if string(diff.NewContent) != "new content" {
		t.Errorf("NewContent = %q, want 'new content'", string(diff.NewContent))
	}
}

func TestCompareFiles_DeletedFile(t *testing.T) {
	diff := CompareFiles("test.txt", []byte("old content"), nil)

	if diff == nil {
		t.Fatal("CompareFiles returned nil")
	}
	if diff.Path != "test.txt" {
		t.Errorf("Path = %q, want 'test.txt'", diff.Path)
	}
	if diff.IsNew {
		t.Error("expected IsNew to be false")
	}
	if !diff.IsDeleted {
		t.Error("expected IsDeleted to be true")
	}
	if diff.IsModified {
		t.Error("expected IsModified to be false")
	}
	if diff.IsIdentical {
		t.Error("expected IsIdentical to be false")
	}
	if string(diff.OldContent) != "old content" {
		t.Errorf("OldContent = %q, want 'old content'", string(diff.OldContent))
	}
	if diff.NewContent != nil {
		t.Error("expected NewContent to be nil")
	}
}

func TestCompareFiles_ModifiedFile(t *testing.T) {
	diff := CompareFiles("test.txt", []byte("old content"), []byte("new content"))

	if diff == nil {
		t.Fatal("CompareFiles returned nil")
	}
	if diff.Path != "test.txt" {
		t.Errorf("Path = %q, want 'test.txt'", diff.Path)
	}
	if diff.IsNew {
		t.Error("expected IsNew to be false")
	}
	if diff.IsDeleted {
		t.Error("expected IsDeleted to be false")
	}
	if !diff.IsModified {
		t.Error("expected IsModified to be true")
	}
	if diff.IsIdentical {
		t.Error("expected IsIdentical to be false")
	}
	if string(diff.OldContent) != "old content" {
		t.Errorf("OldContent = %q, want 'old content'", string(diff.OldContent))
	}
	if string(diff.NewContent) != "new content" {
		t.Errorf("NewContent = %q, want 'new content'", string(diff.NewContent))
	}
}

func TestCompareFiles_IdenticalFile(t *testing.T) {
	content := []byte("same content")
	diff := CompareFiles("test.txt", content, content)

	if diff == nil {
		t.Fatal("CompareFiles returned nil")
	}
	if diff.Path != "test.txt" {
		t.Errorf("Path = %q, want 'test.txt'", diff.Path)
	}
	if diff.IsNew {
		t.Error("expected IsNew to be false")
	}
	if diff.IsDeleted {
		t.Error("expected IsDeleted to be false")
	}
	if diff.IsModified {
		t.Error("expected IsModified to be false")
	}
	if !diff.IsIdentical {
		t.Error("expected IsIdentical to be true")
	}
}

func TestCompareFiles_IdenticalButDifferentSlices(t *testing.T) {
	old := []byte("same content")
	new := []byte("same content")
	diff := CompareFiles("test.txt", old, new)

	if !diff.IsIdentical {
		t.Error("expected IsIdentical to be true for same content in different slices")
	}
}

func TestGenerateUnifiedDiff_IdenticalFile(t *testing.T) {
	diff := CompareFiles("test.txt", []byte("content"), []byte("content"))
	result := diff.GenerateUnifiedDiff()

	if result != "" {
		t.Errorf("expected empty diff for identical files, got: %q", result)
	}
}

func TestGenerateUnifiedDiff_NewFile(t *testing.T) {
	diff := CompareFiles("test.txt", nil, []byte("line1\nline2\nline3"))
	result := diff.GenerateUnifiedDiff()

	if !strings.Contains(result, "--- /dev/null") {
		t.Error("new file diff should contain '--- /dev/null'")
	}
	if !strings.Contains(result, "+++ test.txt") {
		t.Error("new file diff should contain '+++ test.txt'")
	}
	if !strings.Contains(result, "line1") {
		t.Error("new file diff should contain the content")
	}
}

func TestGenerateUnifiedDiff_DeletedFile(t *testing.T) {
	diff := CompareFiles("test.txt", []byte("line1\nline2"), nil)
	result := diff.GenerateUnifiedDiff()

	if !strings.Contains(result, "--- test.txt") {
		t.Error("deleted file diff should contain '--- test.txt'")
	}
	if !strings.Contains(result, "+++ /dev/null") {
		t.Error("deleted file diff should contain '+++ /dev/null'")
	}
	if !strings.Contains(result, "line1") {
		t.Error("deleted file diff should contain the old content")
	}
}

func TestGenerateUnifiedDiff_ModifiedFile(t *testing.T) {
	old := "line1\nline2\nline3"
	new := "line1\nmodified\nline3"
	diff := CompareFiles("test.txt", []byte(old), []byte(new))
	result := diff.GenerateUnifiedDiff()

	if !strings.Contains(result, "--- test.txt") {
		t.Error("modified file diff should contain '--- test.txt'")
	}
	if !strings.Contains(result, "+++ test.txt") {
		t.Error("modified file diff should contain '+++ test.txt'")
	}
	if !strings.Contains(result, "-line2") {
		t.Error("modified file diff should show removed line")
	}
	if !strings.Contains(result, "+modified") {
		t.Error("modified file diff should show added line")
	}
	if !strings.Contains(result, " line1") {
		t.Error("modified file diff should show unchanged lines with space prefix")
	}
}

func TestGenerateUnifiedDiff_AddedLines(t *testing.T) {
	old := "line1\nline2"
	new := "line1\nline2\nline3\nline4"
	diff := CompareFiles("test.txt", []byte(old), []byte(new))
	result := diff.GenerateUnifiedDiff()

	if !strings.Contains(result, "+line3") {
		t.Error("diff should show added line3")
	}
	if !strings.Contains(result, "+line4") {
		t.Error("diff should show added line4")
	}
}

func TestGenerateUnifiedDiff_RemovedLines(t *testing.T) {
	old := "line1\nline2\nline3\nline4"
	new := "line1\nline2"
	diff := CompareFiles("test.txt", []byte(old), []byte(new))
	result := diff.GenerateUnifiedDiff()

	if !strings.Contains(result, "-line3") {
		t.Error("diff should show removed line3")
	}
	if !strings.Contains(result, "-line4") {
		t.Error("diff should show removed line4")
	}
}

func TestComputeDiffLines(t *testing.T) {
	tests := []struct {
		name     string
		old      []string
		new      []string
		validate func(t *testing.T, result []diffLine)
	}{
		{
			name: "identical lines",
			old:  []string{"a", "b", "c"},
			new:  []string{"a", "b", "c"},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 3 {
					t.Errorf("expected 3 diff lines, got %d", len(result))
					return
				}
				for i, dl := range result {
					if dl.status != "unchanged" {
						t.Errorf("line %d: expected status 'unchanged', got %q", i, dl.status)
					}
				}
			},
		},
		{
			name: "all modified",
			old:  []string{"a", "b", "c"},
			new:  []string{"x", "y", "z"},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 3 {
					t.Errorf("expected 3 diff lines, got %d", len(result))
					return
				}
				for i, dl := range result {
					if dl.status != "modified" {
						t.Errorf("line %d: expected status 'modified', got %q", i, dl.status)
					}
				}
			},
		},
		{
			name: "lines added at end",
			old:  []string{"a", "b"},
			new:  []string{"a", "b", "c", "d"},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 4 {
					t.Errorf("expected 4 diff lines, got %d", len(result))
					return
				}
				if result[0].status != "unchanged" || result[1].status != "unchanged" {
					t.Error("first two lines should be unchanged")
				}
				if result[2].status != "added" || result[3].status != "added" {
					t.Error("last two lines should be added")
				}
			},
		},
		{
			name: "lines removed from end",
			old:  []string{"a", "b", "c", "d"},
			new:  []string{"a", "b"},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 4 {
					t.Errorf("expected 4 diff lines, got %d", len(result))
					return
				}
				if result[0].status != "unchanged" || result[1].status != "unchanged" {
					t.Error("first two lines should be unchanged")
				}
				if result[2].status != "removed" || result[3].status != "removed" {
					t.Error("last two lines should be removed")
				}
			},
		},
		{
			name: "empty old",
			old:  []string{},
			new:  []string{"a", "b"},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 2 {
					t.Errorf("expected 2 diff lines, got %d", len(result))
					return
				}
				for i, dl := range result {
					if dl.status != "added" {
						t.Errorf("line %d: expected status 'added', got %q", i, dl.status)
					}
				}
			},
		},
		{
			name: "empty new",
			old:  []string{"a", "b"},
			new:  []string{},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 2 {
					t.Errorf("expected 2 diff lines, got %d", len(result))
					return
				}
				for i, dl := range result {
					if dl.status != "removed" {
						t.Errorf("line %d: expected status 'removed', got %q", i, dl.status)
					}
				}
			},
		},
		{
			name: "both empty",
			old:  []string{},
			new:  []string{},
			validate: func(t *testing.T, result []diffLine) {
				if len(result) != 0 {
					t.Errorf("expected 0 diff lines, got %d", len(result))
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := computeDiffLines(tt.old, tt.new)
			tt.validate(t, result)
		})
	}
}

func TestComputeDiffLines_LineNumbers(t *testing.T) {
	old := []string{"a", "b", "c"}
	new := []string{"a", "x", "c", "d"}
	result := computeDiffLines(old, new)

	// Check line numbers are assigned correctly
	expected := []struct {
		oldNum int
		newNum int
		status string
	}{
		{1, 1, "unchanged"}, // "a" unchanged
		{2, 2, "modified"},  // "b" -> "x"
		{3, 3, "unchanged"}, // "c" unchanged
		{0, 4, "added"},     // "d" added
	}

	if len(result) != len(expected) {
		t.Fatalf("expected %d lines, got %d", len(expected), len(result))
	}

	for i, exp := range expected {
		if result[i].oldNum != exp.oldNum {
			t.Errorf("line %d: oldNum = %d, want %d", i, result[i].oldNum, exp.oldNum)
		}
		if result[i].newNum != exp.newNum {
			t.Errorf("line %d: newNum = %d, want %d", i, result[i].newNum, exp.newNum)
		}
		if result[i].status != exp.status {
			t.Errorf("line %d: status = %q, want %q", i, result[i].status, exp.status)
		}
	}
}

func TestTruncateOrPad(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		width    int
		expected string
	}{
		{
			name:     "exact length",
			input:    "hello",
			width:    5,
			expected: "hello",
		},
		{
			name:     "needs padding",
			input:    "hi",
			width:    5,
			expected: "hi   ",
		},
		{
			name:     "needs truncation",
			input:    "hello world",
			width:    8,
			expected: "hello...",
		},
		{
			name:     "empty string",
			input:    "",
			width:    5,
			expected: "     ",
		},
		{
			name:     "width of 3 with long string",
			input:    "hello",
			width:    3,
			expected: "...",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := truncateOrPad(tt.input, tt.width)
			if result != tt.expected {
				t.Errorf("truncateOrPad(%q, %d) = %q, want %q",
					tt.input, tt.width, result, tt.expected)
			}
			if len(result) != tt.width {
				t.Errorf("result length = %d, want %d", len(result), tt.width)
			}
		})
	}
}

func TestReadFileIfExists(t *testing.T) {
	t.Run("file exists", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "test.txt")
		testContent := []byte("test content")

		err := os.WriteFile(testFile, testContent, 0644)
		if err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		content, err := ReadFileIfExists(testFile)
		if err != nil {
			t.Fatalf("ReadFileIfExists returned error: %v", err)
		}
		if string(content) != string(testContent) {
			t.Errorf("content = %q, want %q", string(content), string(testContent))
		}
	})

	t.Run("file does not exist", func(t *testing.T) {
		content, err := ReadFileIfExists("/nonexistent/file/path/test.txt")
		if err != nil {
			t.Fatalf("ReadFileIfExists returned error for nonexistent file: %v", err)
		}
		if content != nil {
			t.Errorf("expected nil content for nonexistent file, got %q", string(content))
		}
	})

	t.Run("empty file", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "empty.txt")

		err := os.WriteFile(testFile, []byte{}, 0644)
		if err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		content, err := ReadFileIfExists(testFile)
		if err != nil {
			t.Fatalf("ReadFileIfExists returned error: %v", err)
		}
		if len(content) != 0 {
			t.Errorf("expected empty content, got %q", string(content))
		}
	})
}

func TestFileDiff_MutuallyExclusiveFlags(t *testing.T) {
	tests := []struct {
		name        string
		old         []byte
		new         []byte
		expectNew   bool
		expectDel   bool
		expectMod   bool
		expectIdent bool
	}{
		{"new file", nil, []byte("content"), true, false, false, false},
		{"deleted file", []byte("content"), nil, false, true, false, false},
		{"modified file", []byte("old"), []byte("new"), false, false, true, false},
		{"identical file", []byte("same"), []byte("same"), false, false, false, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			diff := CompareFiles("test.txt", tt.old, tt.new)

			// Count how many flags are true
			flagCount := 0
			if diff.IsNew {
				flagCount++
			}
			if diff.IsDeleted {
				flagCount++
			}
			if diff.IsModified {
				flagCount++
			}
			if diff.IsIdentical {
				flagCount++
			}

			if flagCount != 1 {
				t.Errorf("expected exactly one flag to be true, got %d (IsNew=%v, IsDeleted=%v, IsModified=%v, IsIdentical=%v)",
					flagCount, diff.IsNew, diff.IsDeleted, diff.IsModified, diff.IsIdentical)
			}

			if diff.IsNew != tt.expectNew {
				t.Errorf("IsNew = %v, want %v", diff.IsNew, tt.expectNew)
			}
			if diff.IsDeleted != tt.expectDel {
				t.Errorf("IsDeleted = %v, want %v", diff.IsDeleted, tt.expectDel)
			}
			if diff.IsModified != tt.expectMod {
				t.Errorf("IsModified = %v, want %v", diff.IsModified, tt.expectMod)
			}
			if diff.IsIdentical != tt.expectIdent {
				t.Errorf("IsIdentical = %v, want %v", diff.IsIdentical, tt.expectIdent)
			}
		})
	}
}

func BenchmarkCompareFiles(b *testing.B) {
	old := []byte(strings.Repeat("line\n", 1000))
	new := []byte(strings.Repeat("line\n", 500) + strings.Repeat("modified\n", 500))

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		CompareFiles("test.txt", old, new)
	}
}

func BenchmarkGenerateUnifiedDiff(b *testing.B) {
	old := []byte(strings.Repeat("line\n", 1000))
	new := []byte(strings.Repeat("line\n", 500) + strings.Repeat("modified\n", 500))
	diff := CompareFiles("test.txt", old, new)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		diff.GenerateUnifiedDiff()
	}
}

func BenchmarkComputeDiffLines(b *testing.B) {
	old := make([]string, 1000)
	new := make([]string, 1000)
	for i := range 1000 {
		old[i] = "line"
		if i < 500 {
			new[i] = "line"
		} else {
			new[i] = "modified"
		}
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		computeDiffLines(old, new)
	}
}
