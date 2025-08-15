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
	"embed"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/Masterminds/semver/v3"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"

	"github.com/deltafi/tui/internal/ui/styles"
)

const (
	DELTAFI    = "deltafi"
	SSL_SECRET = "ssl-secret"
)

//go:embed kubernetes.values.site.yaml templates_readme.md
var kubernetesEmbeddedFiles embed.FS

type KubernetesOrchestrator struct {
	BaseOrchestrator
	distroPath string
	sitePath   string
	dataPath   string
	namespace  string
}

func NewKubernetesOrchestrator(distroPath string, sitePath string, dataPath string) *KubernetesOrchestrator {
	ko := &KubernetesOrchestrator{
		namespace:  "deltafi",
		distroPath: distroPath,
		sitePath:   sitePath,
		dataPath:   dataPath,
	}
	ko.BaseOrchestrator.Orchestrator = ko
	return ko
}

// Helper for Kubernetes
func (o *KubernetesOrchestrator) GetMasterPod(selector string) (string, error) {
	cmd := exec.Command("kubectl",
		"get", "pod",
		"--selector", selector,
		"-n", o.namespace,
		"-o", "name")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get pod: %v", err)
	}

	podName := strings.TrimSpace(string(output))
	if podName == "" {
		return "", fmt.Errorf("no pod found matching selector: %s", selector)
	}

	return podName, nil
}

func (o *KubernetesOrchestrator) GetMinioName() (string, error) {
	cmd := exec.Command("kubectl", "get", "pod", "-l app=minio", "-o", "name")

	output, err := cmd.Output()
	if err != nil {
		return "", fmt.Errorf("failed to get the minio pod: %v", err)
	}

	podName := strings.TrimSpace(string(output))
	if podName == "" {
		return "", fmt.Errorf("no pod found with a label of app=minio")
	}

	return podName, nil
}

func (o *KubernetesOrchestrator) GetAPIBaseURL() (string, error) {
	return getKubernetesServiceIP("deltafi-core-service", o.namespace)
}
func (o *KubernetesOrchestrator) GetPostgresCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetPostgresExecCmd(args []string) (exec.Cmd, error) {

	podName, err := o.GetMasterPod("application=spilo,spilo-role=master")
	if err != nil {
		return *exec.Command(""), fmt.Errorf("unable to find Postgres pod")
	}

	cmdArgs := []string{
		podName,
		"-c", "postgres",
		"--",
		"psql",
	}

	cmdArgs = append(cmdArgs, args...)
	cmdArgs = append(cmdArgs, "-U", "deltafi", "deltafi")
	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetExecCmd(name string, args []string) (exec.Cmd, error) {
	cmdArgs := []string{name, "--", "sh", "-c"}

	extraCmdArgs := fmt.Sprintf("%s", strings.Join(args, " "))
	cmdArgs = append(cmdArgs, extraCmdArgs)

	return getKubectlExecCmd(cmdArgs)
}

func (o *KubernetesOrchestrator) GetValkeyName() string {
	return "deltafi-valkey-master-0"
}

func (o *KubernetesOrchestrator) Up(args []string) error {

	if err := kubernetesPrerequisites(o.namespace); err != nil {
		return fmt.Errorf("Unable to configure Kubernetes for DeltaFi: %w", err)
	}

	postgresOperatorPath := filepath.Join(o.distroPath, "orchestration", "charts", "postgres-operator")

	if err := postgresOperatorInstall(o.namespace, postgresOperatorPath); err != nil {
		fmt.Println(styles.FAIL("Postgres Operator installation failed"))
		return fmt.Errorf("Unable to install Postgres Operator: %w", err)
	}

	deltafiChartPath := filepath.Join(o.distroPath, "orchestration", "charts", "deltafi")

	siteValuesFile, err := o.SiteValuesFile()
	if err != nil {
		return fmt.Errorf("Unable to get site values file: %w", err)
	}

	siteTemplatesPath, err := o.TemplatesDir()
	if err != nil {
		return fmt.Errorf("Unable to get site templates directory: %w", err)
	}

	if err := deltafiHelmInstall(o.namespace, deltafiChartPath, siteValuesFile, siteTemplatesPath); err != nil {
		fmt.Println(styles.FAIL("DeltaFi installation failed"))
		return fmt.Errorf("Unable to install DeltaFi: %w", err)
	}

	return nil
}

func (o *KubernetesOrchestrator) Down(args []string) error {
	return deltafiHelmUninstall(o.namespace)
}

func (o *KubernetesOrchestrator) Environment() []string {
	mode := "CLUSTER"
	env := os.Environ()
	env = append(env, "DELTAFI_MODE="+mode)
	env = append(env, "DELTAFI_DATA_DIR="+o.dataPath)
	return env
}

func (o *KubernetesOrchestrator) enforceSiteValuesDir() error {
	if err := os.MkdirAll(o.sitePath, 0755); err != nil {
		return fmt.Errorf("error creating site values directory: %w", err)
	}
	return nil
}

func (o *KubernetesOrchestrator) TemplatesDir() (string, error) {
	siteTemplatesDir := filepath.Join(o.sitePath, "templates")

	// make the templates dir if it doesn't exist, drop in the readme and .helmignore files on creation
	if _, err := os.Stat(siteTemplatesDir); os.IsNotExist(err) {
		if err := os.MkdirAll(siteTemplatesDir, 0755); err != nil {
			return "", fmt.Errorf("error creating site templates directory: %w", err)
		}

		readmeContent, err := kubernetesEmbeddedFiles.ReadFile("templates_readme.md")
		if err != nil {
			return "", fmt.Errorf("error reading templates_readme.md: %w", err)
		}

		readmeFile := filepath.Join(siteTemplatesDir, "README.md")
		if err := os.WriteFile(readmeFile, readmeContent, 0644); err != nil {
			return "", fmt.Errorf("error creating templates/README.md file: %w", err)
		}

		helmIgnore := filepath.Join(siteTemplatesDir, ".helmignore")
		if err := os.WriteFile(helmIgnore, []byte("*.md"), 0644); err != nil {
			return "", fmt.Errorf("error creating templats/.helmignore file: %w", err)
		}
	}

	return siteTemplatesDir, nil
}

func (o *KubernetesOrchestrator) Migrate(_ *semver.Version) error {
	return nil
}

func (o *KubernetesOrchestrator) SiteValuesFile() (string, error) {
	if err := o.enforceSiteValuesDir(); err != nil {
		return "", err
	}

	siteValuesFile := filepath.Join(o.sitePath, "values.yaml")
	if _, err := os.Stat(siteValuesFile); os.IsNotExist(err) {
		defaultContent, err := kubernetesEmbeddedFiles.ReadFile("kubernetes.values.site.yaml")
		if err != nil {
			return "", fmt.Errorf("error reading site values template: %w", err)
		}

		if err := os.WriteFile(siteValuesFile, defaultContent, 0644); err != nil {
			return siteValuesFile, fmt.Errorf("error creating default %s file: %w", "values.yaml", err)
		}
	}
	return siteValuesFile, nil
}

func (o *KubernetesOrchestrator) GetKeyCert(secretName string) (*SslOutput, error) {
	existing, err := o.getSecret(secretName)

	if err != nil {
		if errors.IsNotFound(err) {
			return nil, fmt.Errorf("secret %s not found", secretName)
		}
		return nil, fmt.Errorf("error getting the secret named %s: %w", secretName, err)
	}

	return secretToOutput(existing)
}

func (o *KubernetesOrchestrator) SaveKeyCert(input SslInput) (*SslOutput, error) {
	key, err := os.ReadFile(input.KeyFile)

	if err != nil {
		return nil, err
	}

	cert, err := os.ReadFile(input.CertFile)
	if err != nil {
		return nil, err
	}

	data := map[string][]byte{
		corev1.TLSPrivateKeyKey: key,
		corev1.TLSCertKey:       cert,
	}

	if input.KeyPassphrase != "" {
		data["keyPassphrase"] = []byte(input.KeyPassphrase)
	}

	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{
			Name:      input.SecretName,
			Namespace: o.namespace,
			Labels: map[string]string{
				DELTAFI: SSL_SECRET,
			},
		},
		Type: corev1.SecretTypeTLS,
		Data: data,
	}

	err = upsertKubernetesSecret(secret)
	if err != nil {
		return nil, err
	}

	return secretToOutput(secret)
}

func (o *KubernetesOrchestrator) DeleteKeyCert(secretName string) (*SslOutput, error) {
	k8sClient, ctx, err := getK8sClient()
	if err != nil {
		return nil, err
	}

	existing, err := o.getSecret(secretName)

	if err != nil {
		return nil, fmt.Errorf("error checking if the %s secret exists: %w", secretName, err)
	}

	if existing == nil {
		return nil, fmt.Errorf("secret %s not found", secretName)
	}

	label := existing.ObjectMeta.Labels["deltafi"]

	if label != SSL_SECRET {
		return nil, fmt.Errorf("secret %s was not created by DeltaFi, it must be manually deleted", secretName)
	}

	err = k8sClient.CoreV1().Secrets(o.namespace).Delete(ctx, secretName, metav1.DeleteOptions{})
	if err != nil {
		return nil, fmt.Errorf("error encountered while deleting %s: %w", secretName, err)
	}

	return secretToOutput(existing)
}

func (o *KubernetesOrchestrator) AppendToCaChain(certs string) (string, error) {
	currentChain, err := o.GetCaChain()
	if err != nil {
		return "", err
	}

	caChain := ""
	if currentChain != "" {
		caChain = currentChain + "\n" + certs
	} else {
		caChain = certs
	}

	return o.SaveCaChain(caChain)
}

func (o *KubernetesOrchestrator) GetCaChain() (string, error) {
	secretName := o.caChainSecretName()
	caChainSecret, err := o.getSecret(secretName)

	if err != nil {
		if errors.IsNotFound(err) {
			return "", nil
		}
		return "", fmt.Errorf("error getting the current ca chain in secret - %s: %w", secretName, err)
	}

	return string(caChainSecret.Data["ca.crt"]), nil
}

func (o *KubernetesOrchestrator) SaveCaChain(caChain string) (string, error) {
	secretName := o.caChainSecretName()
	data := map[string][]byte{
		"ca.crt": []byte(caChain),
	}

	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{
			Name:      secretName,
			Namespace: o.namespace,
		},
		Type: corev1.SecretTypeOpaque,
		Data: data,
	}

	err := upsertKubernetesSecret(secret)
	return caChain, err
}

func (o *KubernetesOrchestrator) caChainSecretName() string {
	siteValues, err := o.SiteValuesFile()

	if err != nil {
		return "auth-secret"
	}

	config, err := parseConfigFromYAML(siteValues)

	if err != nil {
		return "auth-secret"
	}

	return config.GetDeltafiAuthSecret()
}

func (o *KubernetesOrchestrator) getSecret(secretName string) (*corev1.Secret, error) {
	k8sClient, ctx, err := getK8sClient()
	if err != nil {
		return nil, err
	}

	return k8sClient.CoreV1().Secrets(o.namespace).Get(ctx, secretName, metav1.GetOptions{})
}

func (o *KubernetesOrchestrator) GetSecretNames() ([]string, error) {
	k8sClient, ctx, err := getK8sClient()
	if err != nil {
		return nil, err
	}

	labelSelector := DELTAFI + "=" + SSL_SECRET

	// List secrets with label selector
	secrets, err := k8sClient.CoreV1().Secrets(o.namespace).List(ctx, metav1.ListOptions{
		LabelSelector: labelSelector,
	})
	if err != nil {
		return nil, err
	}

	var secretNames []string
	for _, secret := range secrets.Items {
		secretNames = append(secretNames, secret.Name)
	}

	return secretNames, nil
}

func secretToOutput(secret *corev1.Secret) (*SslOutput, error) {
	sslOutput := &SslOutput{
		SecretName: secret.Name,
		Key:        string(secret.Data[corev1.TLSPrivateKeyKey]),
		Cert:       string(secret.Data[corev1.TLSCertKey]),
	}

	return sslOutput, nil
}
