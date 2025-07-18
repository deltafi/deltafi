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
package orchestration

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/deltafi/tui/internal/ui/styles"
	"helm.sh/helm/v3/pkg/action"
	"helm.sh/helm/v3/pkg/chart/loader"
	"helm.sh/helm/v3/pkg/chartutil"
	"helm.sh/helm/v3/pkg/cli"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/labels"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/tools/clientcmd"
)

// TODO: This is a temporary function to get a kubectl exec command.  Replace with client-go
func getKubectlExecCmd(args []string) (exec.Cmd, error) {
	cmdArgs := []string{"exec"}
	if isTty() {
		cmdArgs = append(cmdArgs, "-it")
	} else {
		cmdArgs = append(cmdArgs, "-i")
	}
	cmdArgs = append(cmdArgs, "-n", "deltafi")
	cmdArgs = append(cmdArgs, args...)
	return *exec.Command("kubectl", cmdArgs...), nil
}

// TODO: This is a temporary function to get a kubernetes service IP.  Replace with client-go
func getKubernetesServiceIP(service string, namespace string) (string, error) {
	cmd := exec.Command("kubectl",
		"get", "service", service,
		"-n", namespace,
		"-o", "jsonpath={.spec.clusterIP}")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get service IP: %v", err)
	}

	return strings.TrimSpace(string(output)), nil
}

// Helper to create a secret if it does not exist
func createKubernetesSecret(ctx context.Context, clientset *kubernetes.Clientset, namespace, name string, data map[string][]byte) error {
	existing, err := clientset.CoreV1().Secrets(namespace).Get(ctx, name, metav1.GetOptions{})
	if err == nil && existing != nil {
		// Secret already exists, don't update it
		return nil
	}
	// Create if not found
	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{
			Name:      name,
			Namespace: namespace,
		},
		Type: corev1.SecretTypeOpaque,
		Data: data,
	}
	_, err = clientset.CoreV1().Secrets(namespace).Create(ctx, secret, metav1.CreateOptions{})
	if err != nil {
		return fmt.Errorf("failed to create secret %s: %w", name, err)
	}
	fmt.Println(styles.OK(fmt.Sprintf("Created secret %s", name)))
	return nil
}

func deltafiHelmUninstall(namespace string) error {
	settings := cli.New()
	actionConfig := new(action.Configuration)
	if err := actionConfig.Init(settings.RESTClientGetter(), namespace, os.Getenv("HELM_DRIVER"), func(format string, v ...interface{}) {
		// Helm logging callback - we can ignore this for now
	}); err != nil {
		return fmt.Errorf("failed to initialize Helm configuration: %w", err)
	}

	uninstall := action.NewUninstall(actionConfig)
	_, err := uninstall.Run("deltafi")
	if err != nil {
		return fmt.Errorf("failed to uninstall deltafi: %w", err)
	}
	fmt.Println(styles.OK("DeltaFi helm chart uninstalled"))
	return nil
}

func deleteNamespace(namespace string) error {
	ctx := context.Background()

	// Set up Kubernetes client
	config, err := clientcmd.BuildConfigFromFlags("", clientcmd.RecommendedHomeFile)
	if err != nil {
		return fmt.Errorf("failed to get Kubernetes config: %w", err)
	}
	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		return fmt.Errorf("failed to create Kubernetes client: %w", err)
	}

	// Delete the namespace
	err = clientset.CoreV1().Namespaces().Delete(ctx, namespace, metav1.DeleteOptions{})
	if err != nil {
		return fmt.Errorf("failed to delete namespace %s: %w", namespace, err)
	}
	fmt.Println(styles.OK(fmt.Sprintf("Deleted namespace %s", namespace)))
	return nil
}

func deltafiHelmInstall(namespace string, deltafiChartPath string, siteValuesFile string, additionalValuesFiles ...string) error {

	fmt.Println(styles.HEADER("Installing DeltaFi helm chart"))
	// Check the path to the DeltaFi chart
	if _, err := os.Stat(deltafiChartPath); os.IsNotExist(err) {
		return fmt.Errorf("deltafi chart not found at %s", deltafiChartPath)
	}

	// Set up Helm client
	settings := cli.New()
	actionConfig := new(action.Configuration)
	if err := actionConfig.Init(settings.RESTClientGetter(), namespace, os.Getenv("HELM_DRIVER"), func(format string, v ...interface{}) {
		// Helm logging callback - we can ignore this for now
	}); err != nil {
		return fmt.Errorf("failed to initialize Helm configuration: %w", err)
	}

	// Check if Chart.lock exists, if not build dependencies
	chartLockPath := filepath.Join(deltafiChartPath, "Chart.lock")
	if _, err := os.Stat(chartLockPath); os.IsNotExist(err) {
		fmt.Println("Chart.lock not found - please run 'helm dependencies build' in the chart directory manually")
		// Note: Helm dependency building via Go client is complex,
		// so we'll rely on manual dependency building for now
	}

	fmt.Println(styles.WAIT("Starting helmchart upgrade"))

	// Check if the release already exists
	histClient := action.NewHistory(actionConfig)
	histClient.Max = 1
	_, err := histClient.Run("deltafi")
	releaseExists := err == nil

	// Load the chart
	chart, err := loader.Load(deltafiChartPath)
	if err != nil {
		return fmt.Errorf("failed to load chart: %w", err)
	}

	// Load default values from values.yaml
	valuesPath := filepath.Join(deltafiChartPath, "values.yaml")
	values, err := os.ReadFile(valuesPath)
	if err != nil {
		return fmt.Errorf("failed to read values.yaml: %w", err)
	}

	// Parse default values
	mergedVals, err := chartutil.ReadValues(values)
	if err != nil {
		return fmt.Errorf("failed to parse values.yaml: %w", err)
	}

	// Merge additional values files in order (they override default values)
	for _, additionalValuesFile := range additionalValuesFiles {
		if additionalValuesFile != "" {
			additionalValuesData, err := os.ReadFile(additionalValuesFile)
			if err != nil {
				return fmt.Errorf("failed to read additional values file %s: %w", additionalValuesFile, err)
			}

			// Parse additional values
			additionalVals, err := chartutil.ReadValues(additionalValuesData)
			if err != nil {
				return fmt.Errorf("failed to parse additional values file %s: %w", additionalValuesFile, err)
			}

			// Merge additional values (they override previous values)
			mergedVals = chartutil.CoalesceTables(additionalVals, mergedVals)
		}
	}

	siteValuesData, err := os.ReadFile(siteValuesFile)
	if err != nil {
		return fmt.Errorf("failed to read site values file: %w", err)
	}

	// Parse site values
	siteVals, err := chartutil.ReadValues(siteValuesData)
	if err != nil {
		return fmt.Errorf("failed to parse site values file: %w", err)
	}

	// Merge site values (they override all previous values)
	mergedVals = chartutil.CoalesceTables(siteVals, mergedVals)

	if releaseExists {
		// Release exists, use upgrade
		upgrade := action.NewUpgrade(actionConfig)
		upgrade.Namespace = namespace
		upgrade.Wait = true
		upgrade.Timeout = 10 * time.Minute

		// Upgrade the chart
		_, err = upgrade.Run("deltafi", chart, mergedVals)
		if err != nil {
			fmt.Println(styles.FAIL(fmt.Sprintf("Failed to upgrade deltafi: %s", err)))
			return fmt.Errorf("failed to upgrade deltafi: %w", err)
		}
	} else {
		// Release doesn't exist, use install
		install := action.NewInstall(actionConfig)
		install.Namespace = namespace
		install.CreateNamespace = true
		install.Wait = true
		install.Timeout = 10 * time.Minute
		install.ReleaseName = "deltafi"

		// Install the chart
		_, err = install.Run(chart, mergedVals)
		if err != nil {
			fmt.Println(styles.FAIL(fmt.Sprintf("Failed to install deltafi: %s", err)))
			return fmt.Errorf("failed to install deltafi: %w", err)
		}
	}

	fmt.Println(styles.OK("Helmchart upgrade complete"))
	fmt.Println()
	return nil
}

func postgresOperatorInstall(namespace string, postgresOperatorPath string) error {
	fmt.Println(styles.HEADER("Initializing Postgres Operator"))
	ctx := context.Background()

	// Set up Kubernetes client
	config, err := clientcmd.BuildConfigFromFlags("", clientcmd.RecommendedHomeFile)
	if err != nil {
		return fmt.Errorf("failed to get Kubernetes config: %w", err)
	}
	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		return fmt.Errorf("failed to create Kubernetes client: %w", err)
	}

	// Set up Helm client
	settings := cli.New()
	actionConfig := new(action.Configuration)
	if err := actionConfig.Init(settings.RESTClientGetter(), namespace, os.Getenv("HELM_DRIVER"), func(format string, v ...interface{}) {
		// Helm logging callback - we can ignore this for now
	}); err != nil {
		return fmt.Errorf("failed to initialize Helm configuration: %w", err)
	}

	// Construct the path to postgres-operator chart
	if _, err := os.Stat(postgresOperatorPath); os.IsNotExist(err) {
		return fmt.Errorf("postgres-operator chart not found at %s", postgresOperatorPath)
	}

	// Check if the release already exists
	histClient := action.NewHistory(actionConfig)
	histClient.Max = 1
	_, err = histClient.Run("postgres-operator")
	releaseExists := err == nil

	fmt.Println(styles.WAIT("Waiting for Postgres Operator availability..."))
	if releaseExists {
		// Release exists, use upgrade
		upgrade := action.NewUpgrade(actionConfig)
		upgrade.Namespace = namespace
		upgrade.Wait = true
		upgrade.Timeout = 10 * time.Minute

		// Load the chart
		chart, err := loader.Load(postgresOperatorPath)
		if err != nil {
			return fmt.Errorf("failed to load chart: %w", err)
		}

		// Upgrade the chart
		_, err = upgrade.Run("postgres-operator", chart, nil)
		if err != nil {
			fmt.Println(styles.FAIL(fmt.Sprintf("Failed to upgrade postgres-operator: %s", err)))
			return fmt.Errorf("failed to upgrade postgres-operator: %w", err)
		}
	} else {
		// Release doesn't exist, use install
		install := action.NewInstall(actionConfig)
		install.Namespace = namespace
		install.CreateNamespace = true
		install.Wait = true
		install.Timeout = 10 * time.Minute
		install.ReleaseName = "postgres-operator"

		// Load the chart
		chart, err := loader.Load(postgresOperatorPath)
		if err != nil {
			return fmt.Errorf("failed to load chart: %w", err)
		}

		// Install the chart
		_, err = install.Run(chart, nil)
		if err != nil {
			fmt.Println(styles.FAIL(fmt.Sprintf("Failed to install postgres-operator: %s", err)))
			return fmt.Errorf("failed to install postgres-operator: %w", err)
		}
	}

	// Wait for the postgres-operator pod to be ready
	selector := labels.Set{"app.kubernetes.io/name": "postgres-operator"}.String()
	timeout := 180 * time.Second
	deadline := time.Now().Add(timeout)

	for time.Now().Before(deadline) {
		pods, err := clientset.CoreV1().Pods(namespace).List(ctx, metav1.ListOptions{
			LabelSelector: selector,
		})
		if err != nil {
			return fmt.Errorf("failed to list postgres-operator pods: %w", err)
		}

		if len(pods.Items) > 0 {
			pod := pods.Items[0]
			if pod.Status.Phase == corev1.PodRunning {
				// Check if all containers are ready
				allReady := true
				for _, container := range pod.Status.ContainerStatuses {
					if !container.Ready {
						allReady = false
						break
					}
				}
				if allReady {
					fmt.Println(styles.OK("Postgres Operator is ready"))
					fmt.Println()

					return nil
				}
			}
		}

		time.Sleep(2 * time.Second)
	}

	return fmt.Errorf("timeout waiting for postgres-operator to be ready after %v", timeout)
}

func kubernetesPrerequisites(namespace string) error {
	fmt.Println(styles.HEADER("Initializing k8s prerequisites"))
	ctx := context.Background()

	// Set up Kubernetes client
	config, err := clientcmd.BuildConfigFromFlags("", clientcmd.RecommendedHomeFile)
	if err != nil {
		return fmt.Errorf("failed to get Kubernetes config: %w", err)
	}
	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		return fmt.Errorf("failed to create Kubernetes client: %w", err)
	}

	// 1. Ensure namespace exists
	ns := &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{Name: namespace}}
	_, err = clientset.CoreV1().Namespaces().Get(ctx, namespace, metav1.GetOptions{})
	if err != nil {
		_, err = clientset.CoreV1().Namespaces().Create(ctx, ns, metav1.CreateOptions{})
		if err != nil {
			fmt.Println(styles.FAIL(fmt.Sprintf("Failed to create namespace: %s", err)))
			return fmt.Errorf("failed to create namespace: %w", err)
		}
		fmt.Println(styles.OK(fmt.Sprintf("Created namespace %s", namespace)))
	}

	// 2. minio-keys secret
	minioSecretName := "minio-keys"
	minioRootUser := "deltafi"
	minioRootPassword := randomPassword(40)
	minioSecretData := map[string][]byte{
		"rootUser":     []byte(minioRootUser),
		"rootPassword": []byte(minioRootPassword),
	}
	if err := createKubernetesSecret(ctx, clientset, namespace, minioSecretName, minioSecretData); err != nil {
		fmt.Println(styles.FAIL(fmt.Sprintf("Failed to create/update secret %s: %s", minioSecretName, err)))
		return err
	}

	// 3. valkey-password secret
	valkeySecretName := "valkey-password"
	valkeyPassword := randomPassword(16)
	valkeySecretData := map[string][]byte{
		"redis-password":  []byte(valkeyPassword),
		"valkey-password": []byte(valkeyPassword),
	}
	if err := createKubernetesSecret(ctx, clientset, namespace, valkeySecretName, valkeySecretData); err != nil {
		fmt.Println(styles.FAIL(fmt.Sprintf("Failed to create/update secret %s: %s", valkeySecretName, err)))
		return err
	}

	fmt.Println(styles.OK("Kubernetes prerequisites completed"))
	fmt.Println()

	return nil
}
