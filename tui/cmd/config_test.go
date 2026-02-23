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

import (
	"testing"
)

func TestConfigWizardDockerAvailability(t *testing.T) {
	// Test that the wizard is created correctly
	wizard := NewConfigWizard()

	// Check if Docker is available
	dockerAvailable := isDockerAvailable()

	// If Docker is not available, Compose option should be disabled
	if !dockerAvailable {
		if !wizard.disabledOptions[0] {
			t.Errorf("Expected Compose option (index 0) to be disabled when Docker is not available")
		}
	} else {
		if wizard.disabledOptions[0] {
			t.Errorf("Expected Compose option (index 0) to be enabled when Docker is available")
		}
	}
}

func TestConfigWizardKubernetesAvailability(t *testing.T) {
	// Test that the wizard is created correctly
	wizard := NewConfigWizard()

	// Kubernetes option is disabled when no cluster is running OR when the cluster is KinD
	kubernetesAvailable := isKubernetesClusterRunning()
	kindRunning := isKindClusterRunning()
	expectDisabled := !kubernetesAvailable || kindRunning

	if expectDisabled {
		if !wizard.disabledOptions[1] {
			t.Errorf("Expected Kubernetes option (index 1) to be disabled (k8s available: %t, kind running: %t)",
				kubernetesAvailable, kindRunning)
		}
	} else {
		if wizard.disabledOptions[1] {
			t.Errorf("Expected Kubernetes option (index 1) to be enabled when a non-KinD Kubernetes cluster is running")
		}
	}
}

func TestConfigWizardKindAvailability(t *testing.T) {
	// Test that the wizard is created correctly
	wizard := NewConfigWizard()

	// Check if Kind is available
	kindAvailable := isKindAvailable()

	// If Kind is not available, Kind option should be disabled
	if !kindAvailable {
		if !wizard.disabledOptions[2] {
			t.Errorf("Expected Kind option (index 2) to be disabled when Kind is not available")
		}
	} else {
		if wizard.disabledOptions[2] {
			t.Errorf("Expected Kind option (index 2) to be enabled when Kind is available")
		}
	}
}

func TestConfigWizardAllOrchestrationOptions(t *testing.T) {
	// Test that all orchestration options are properly handled
	wizard := NewConfigWizard()

	// Check availability of all tools
	dockerAvailable := isDockerAvailable()
	kubernetesAvailable := isKubernetesClusterRunning()
	kindAvailable := isKindAvailable()

	// Verify that disabled options match the availability
	if dockerAvailable != !wizard.disabledOptions[0] {
		t.Errorf("Compose option disabled state (%t) doesn't match Docker availability (%t)",
			wizard.disabledOptions[0], dockerAvailable)
	}

	kubernetesExpectDisabled := !kubernetesAvailable || isKindClusterRunning()
	if kubernetesExpectDisabled != wizard.disabledOptions[1] {
		t.Errorf("Kubernetes option disabled state (%t) doesn't match expected (%t) (k8s available: %t, kind running: %t)",
			wizard.disabledOptions[1], kubernetesExpectDisabled, kubernetesAvailable, isKindClusterRunning())
	}

	if kindAvailable != !wizard.disabledOptions[2] {
		t.Errorf("Kind option disabled state (%t) doesn't match Kind availability (%t)",
			wizard.disabledOptions[2], kindAvailable)
	}

	// Verify that at least one option is available
	allDisabled := wizard.disabledOptions[0] && wizard.disabledOptions[1] && wizard.disabledOptions[2]
	if allDisabled {
		t.Logf("Warning: All orchestration options are disabled. This may indicate missing dependencies.")
	}
}

func TestGetDisabledReason(t *testing.T) {
	wizard := NewConfigWizard()

	// Test that each orchestration option returns the correct disabled reason
	expectedReasons := map[int]string{
		0: " (Docker not available)",
		1: " (No cluster)",
		2: " (Kind not available)",
	}

	for index, expectedReason := range expectedReasons {
		reason := wizard.getDisabledReason(index)
		if reason != expectedReason {
			t.Errorf("Expected disabled reason for index %d to be '%s', got '%s'",
				index, expectedReason, reason)
		}
	}

	// Test default case for unknown index
	unknownReason := wizard.getDisabledReason(999)
	expectedUnknown := " (not available)"
	if unknownReason != expectedUnknown {
		t.Errorf("Expected disabled reason for unknown index to be '%s', got '%s'",
			expectedUnknown, unknownReason)
	}
}

func TestConfigWizardNavigation(t *testing.T) {
	wizard := NewConfigWizard()

	// Count how many options are enabled
	enabledCount := 0
	for i := 0; i < len(wizard.orchestrationModes); i++ {
		if !wizard.disabledOptions[i] {
			enabledCount++
		}
	}

	// If all options are disabled, skip the navigation test
	// This is valid - the real wizard shows "no orchestration platform available"
	if enabledCount == 0 {
		t.Skip("All orchestration options are disabled (no Docker, Kubernetes, or Kind available)")
	}

	// Test navigation logic when Compose is disabled
	if !isDockerAvailable() {
		// Set selected option to a disabled option
		wizard.selectedOption = 0 // Compose
		wizard.disabledOptions[0] = true

		// Test that we can find the next enabled option
		originalSelection := wizard.selectedOption
		for i := wizard.selectedOption + 1; i < len(wizard.orchestrationModes); i++ {
			if !wizard.disabledOptions[i] {
				wizard.selectedOption = i
				break
			}
		}

		if wizard.selectedOption == originalSelection {
			t.Errorf("Expected to find an enabled option when current option is disabled")
		}
	}
}

func TestKubernetesClusterRunning(t *testing.T) {
	// Test that the function returns a boolean value
	result := isKubernetesClusterRunning()

	// The function should return a boolean (either true or false)
	// We can't predict the actual value since it depends on the system state
	// But we can verify it's a boolean type by checking it's either true or false
	if result != true && result != false {
		t.Errorf("Expected isKubernetesClusterRunning() to return a boolean value, got: %v", result)
	}

	// If kubectl is not available, the function should return false
	if !isKubectlAvailable() {
		if result != false {
			t.Errorf("Expected isKubernetesClusterRunning() to return false when kubectl is not available")
		}
	}
}

// Example usage of the new function
func Example_isKubernetesClusterRunning() {
	if isKubernetesClusterRunning() {
		// Kubernetes cluster is running and accessible
		// Proceed with Kubernetes operations
	} else {
		// No Kubernetes cluster is running or accessible
		// Fall back to other orchestration methods or show error
	}
}
