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
// ABOUTME: Tests for registry helpers: WithOptionsYAML, All, suppliers, and artifact watches.
// ABOUTME: Each test isolates the relevant global registry slice and restores it on cleanup.

package actionkit

import "testing"

func TestWithOptionsYAML(t *testing.T) {
	withCleanRegistry(t)
	reg := RegisterTransform("WO", dispatchTransform{})
	reg.WithOptionsYAML("description: Does a thing\nnotes:\n  - note one\n")

	if reg.options == nil {
		t.Fatal("options not set")
	}
	if reg.options.Description != "Does a thing" {
		t.Errorf("Description = %q", reg.options.Description)
	}
	// WithOptions also populates the top-level description when it was empty.
	if reg.description != "Does a thing" {
		t.Errorf("description = %q, want it backfilled from options", reg.description)
	}
}

func TestWithOptionsYAML_PanicsOnInvalidYAML(t *testing.T) {
	withCleanRegistry(t)
	reg := RegisterTransform("WObad", dispatchTransform{})

	defer func() {
		if recover() == nil {
			t.Error("expected panic on invalid YAML")
		}
	}()
	reg.WithOptionsYAML("description: [unterminated\n")
}

func TestAll(t *testing.T) {
	withCleanRegistry(t)
	RegisterTransform("A1", dispatchTransform{})
	RegisterEgress("A2", dispatchEgress{})

	if got := All(); len(got) != 2 {
		t.Errorf("All() len = %d, want 2", len(got))
	}
}

func TestRegisterSupplier_AndAllSuppliers(t *testing.T) {
	prev := registeredSuppliers
	registeredSuppliers = nil
	t.Cleanup(func() { registeredSuppliers = prev })

	RegisterSupplier(&testLookupTableSupplier{table: LookupTableDefinition{Name: "colors"}})

	suppliers := AllSuppliers()
	if len(suppliers) != 1 {
		t.Fatalf("AllSuppliers() len = %d, want 1", len(suppliers))
	}
	if suppliers[0].Table().Name != "colors" {
		t.Errorf("supplier table = %q, want colors", suppliers[0].Table().Name)
	}
}

func TestWatchArtifact_Registers(t *testing.T) {
	prev := registeredWatches
	registeredWatches = nil
	t.Cleanup(func() { registeredWatches = prev })

	handler := func(*FetchedArtifact, error) {}
	WatchArtifact("model", nil, handler)
	WatchSharedArtifact("shared-model", nil, handler)

	if len(registeredWatches) != 2 {
		t.Fatalf("watches = %d, want 2", len(registeredWatches))
	}
	if registeredWatches[0].name != "model" || registeredWatches[0].shared {
		t.Errorf("watch[0] = %+v, want name=model shared=false", registeredWatches[0])
	}
	if registeredWatches[1].name != "shared-model" || !registeredWatches[1].shared {
		t.Errorf("watch[1] = %+v, want name=shared-model shared=true", registeredWatches[1])
	}
}
