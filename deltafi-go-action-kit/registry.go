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
// ABOUTME: Action registry with type-safe generic registration.
// ABOUTME: Actions self-register via init() functions; plugin main iterates All() to register.

package actionkit

import (
	"reflect"

	"gopkg.in/yaml.v3"
)

// Registration pairs an action with its name and optional metadata.
// Use the builder methods to set description and parameter defaults.
type Registration struct {
	Name          string
	Action        any
	description   string
	paramDefaults any
	options       *ActionOptions
	dispatcher    *actionDispatcher
}

// Describe sets the human-readable description for this action.
func (r *Registration) Describe(desc string) *Registration {
	r.description = desc
	return r
}

// WithParams sets the parameter defaults for this action. The field values of
// the provided instance are used as defaults when the flow does not specify a
// value. The type must match the generic parameter P used at registration.
func (r *Registration) WithParams(defaults any) *Registration {
	r.paramDefaults = defaults
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

// WithOptionsYAML parses inline YAML into ActionOptions and sets them on the
// registration. Panics if the YAML is invalid.
func (r *Registration) WithOptionsYAML(yamlStr string) *Registration {
	var options ActionOptions
	if err := yaml.Unmarshal([]byte(yamlStr), &options); err != nil {
		panic("WithOptionsYAML: " + err.Error())
	}
	return r.WithOptions(options)
}

var registered []*Registration

// All returns all registered actions.
func All() []*Registration {
	return registered
}

// RegisterTransform registers a transform action. The action struct must have
// a Transform method with signature: func(a *T) Transform(df *Deltafile, params P) *Deltafile.
// Go infers P from the method signature.
//
//	func init() {
//	    actionkit.RegisterTransform("MyAction", &MyAction{}).
//	        Describe("What this action does").
//	        WithParams(MyParams{DefaultField: "value"})
//	}
func RegisterTransform[P any](name string, action interface {
	Transform(*Deltafile, P) *Deltafile
}) *Registration {
	disp := &actionDispatcher{
		method:    methodTransform,
		paramType: reflect.TypeFor[P](),
		transformFn: func(df *Deltafile, params any) *Deltafile {
			return action.Transform(df, params.(P))
		},
	}
	reg := &Registration{
		Name:       name,
		Action:     action,
		dispatcher: disp,
	}
	registered = append(registered, reg)
	return reg
}

// RegisterJoinTransform registers a transform action that supports joining
// multiple DeltaFiles. The action struct must have both Transform and Join
// methods with matching param types.
func RegisterJoinTransform[P any](name string, action interface {
	Transform(*Deltafile, P) *Deltafile
	Join([]*Deltafile, *Deltafile, P) *Deltafile
}) *Registration {
	disp := &actionDispatcher{
		method:    methodTransform,
		paramType: reflect.TypeFor[P](),
		hasJoin:   true,
		transformFn: func(df *Deltafile, params any) *Deltafile {
			return action.Transform(df, params.(P))
		},
		joinFn: func(inputs []*Deltafile, joined *Deltafile, params any) *Deltafile {
			return action.Join(inputs, joined, params.(P))
		},
	}
	reg := &Registration{
		Name:       name,
		Action:     action,
		dispatcher: disp,
	}
	registered = append(registered, reg)
	return reg
}

// RegisterEgress registers an egress action that sends content externally.
func RegisterEgress[P any](name string, action interface {
	Egress(*EgressDeltafile, P) *EgressDeltafile
}) *Registration {
	disp := &actionDispatcher{
		method:    methodEgress,
		paramType: reflect.TypeFor[P](),
		egressFn: func(df *Deltafile, params any) *Deltafile {
			if action.Egress(newEgressDeltafile(df), params.(P)) == nil {
				return nil
			}
			return df
		},
	}
	reg := &Registration{
		Name:       name,
		Action:     action,
		dispatcher: disp,
	}
	registered = append(registered, reg)
	return reg
}

// RegisterIngress registers a timed ingress action that produces new DeltaFiles.
func RegisterIngress[P any](name string, action interface {
	Ingress(*TimedIngressDeltafile, P) *TimedIngressDeltafile
}) *Registration {
	disp := &actionDispatcher{
		method:    methodIngress,
		paramType: reflect.TypeFor[P](),
		ingressFn: func(deltafile *TimedIngressDeltafile, params any) *TimedIngressDeltafile {
			return action.Ingress(deltafile, params.(P))
		},
	}
	reg := &Registration{
		Name:       name,
		Action:     action,
		dispatcher: disp,
	}
	registered = append(registered, reg)
	return reg
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
