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
package cmd

import "testing"

// Regression test for the docs-tree relative-path bug: getChildren must resolve directories at any
// depth. The previous implementation compared an absolute doc.Path against a hardcoded relative
// "docs/<parent>", so children of nested directories (e.g. docs/core-actions) were never found and
// those folders rendered empty. The fix keys off each entry's Parent field instead.
//
// Documents are shaped the way initDocs() builds them: Parent is the entry's directory relative to
// the docs root ("" at the root), file Names have their extension trimmed, and directory Names are
// the folder name. Path is intentionally omitted because the fixed getChildren no longer reads it.
func TestGetChildrenResolvesNestedDirectories(t *testing.T) {
	docs := []Document{
		{Name: "core-actions", IsDir: true, Parent: ""},
		{Name: "Zebra", IsDir: false, Parent: "core-actions"},
		{Name: "sub", IsDir: true, Parent: "core-actions"},
		{Name: "Bar", IsDir: false, Parent: "core-actions/sub"},
		{Name: "alpha", IsDir: true, Parent: "core-actions/sub"},
		{Name: "Deep", IsDir: false, Parent: "core-actions/sub/alpha"},
	}

	children := getChildren(docs, "core-actions")

	// Direct children, sorted directories-first then alphabetically: sub (dir), then Zebra (file).
	if len(children) != 2 {
		t.Fatalf("expected 2 direct children of core-actions, got %d", len(children))
	}
	if children[0].Name != "sub" || !children[0].IsDir {
		t.Errorf("expected first child to be the 'sub' directory, got %q (dir=%v)", children[0].Name, children[0].IsDir)
	}
	if children[1].Name != "Zebra" {
		t.Errorf("expected second child to be 'Zebra', got %q", children[1].Name)
	}

	// Nested: sub must resolve its own children (alpha dir, then Bar file)...
	sub := children[0]
	if len(sub.Children) != 2 {
		t.Fatalf("expected 'sub' to have 2 children, got %d", len(sub.Children))
	}
	if sub.Children[0].Name != "alpha" || !sub.Children[0].IsDir {
		t.Errorf("expected sub's first child to be the 'alpha' directory, got %q (dir=%v)", sub.Children[0].Name, sub.Children[0].IsDir)
	}
	if sub.Children[1].Name != "Bar" {
		t.Errorf("expected sub's second child to be 'Bar', got %q", sub.Children[1].Name)
	}

	// ...and the deeper level must resolve too.
	alpha := sub.Children[0]
	if len(alpha.Children) != 1 || alpha.Children[0].Name != "Deep" {
		t.Errorf("expected 'alpha' to resolve its nested 'Deep' child, got %+v", alpha.Children)
	}
}
