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
// ABOUTME: Action registry for automatic discovery and registration.
// ABOUTME: Actions self-register via init() functions; plugin main iterates All() to register.

package actionkit

// Registration pairs an action with its name and optional metadata.
// Use the builder methods to set description and parameter type.
type Registration struct {
	Name        string
	Action      any
	description string
	paramType   any
	options     *ActionOptions
}

// Register adds an action to the global registry and returns a builder
// for setting optional metadata. Called from init() in each action file.
//
//	func init() {
//	    actionkit.Register("MyAction", &MyAction{}).
//	        Describe("What this action does").
//	        WithParams(MyParams{})
//	}
func Register(name string, action any) *Registration {
	r := &Registration{Name: name, Action: action}
	registered = append(registered, r)
	return r
}

// Describe sets the human-readable description for this action.
func (r *Registration) Describe(desc string) *Registration {
	r.description = desc
	return r
}

// WithParams sets the parameter type used for JSON schema generation.
// Pass a zero-value instance of the params struct.
func (r *Registration) WithParams(paramType any) *Registration {
	r.paramType = paramType
	return r
}

// WithOptions sets the ActionOptions for this action. The options describe
// expected inputs, outputs, error conditions, filter conditions, and notes.
// If options.Description is set, it also populates the top-level description.
func (r *Registration) WithOptions(options ActionOptions) *Registration {
	r.options = &options
	if options.Description != "" && r.description == "" {
		r.description = options.Description
	}
	return r
}

var registered []*Registration

// All returns all registered actions.
func All() []*Registration {
	return registered
}

var registeredSuppliers []LookupTableSupplier

// RegisterSupplier adds a lookup table supplier to the global registry.
// Called from init() in each supplier file.
func RegisterSupplier(supplier LookupTableSupplier) {
	registeredSuppliers = append(registeredSuppliers, supplier)
}

// AllSuppliers returns all registered lookup table suppliers.
func AllSuppliers() []LookupTableSupplier {
	return registeredSuppliers
}

var registeredWatches []artifactWatch

// WatchArtifact registers a plugin-level artifact watch in the global registry.
// If defaultArtifact is non-nil and the artifact does not exist, the default is published.
// The handler is called with the current artifact contents on startup (before registration)
// and again each time the artifact is updated.
func WatchArtifact(name string, defaultArtifact *ArtifactDefault, handler func(*FetchedArtifact, error)) {
	registeredWatches = append(registeredWatches, artifactWatch{
		name: name, defaultArtifact: defaultArtifact, handler: handler,
	})
}

// WatchSharedArtifact is like WatchArtifact but watches the shared namespace.
func WatchSharedArtifact(name string, defaultArtifact *ArtifactDefault, handler func(*FetchedArtifact, error)) {
	registeredWatches = append(registeredWatches, artifactWatch{
		name: name, defaultArtifact: defaultArtifact, handler: handler, shared: true,
	})
}
