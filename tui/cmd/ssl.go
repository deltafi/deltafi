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
package cmd

import (
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/pem"
	"fmt"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
	"os"
	"strings"
	"time"
)

var sslInput = orchestration.SslInput{}

var sslCmd = &cobra.Command{
	Use:          "ssl",
	Short:        "Manage the DeltaFi SSL configuration",
	Long:         `Manage the DeltaFi SSL configuration`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		_ = cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var sslPrintInfo = &cobra.Command{
	Use:   "info",
	Short: "Print the DeltaFi SSL configuration info",
	Long:  `Print the DeltaFi SSL configuration info`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		result, err := graphql.GetSslInfo()

		if err != nil {
			return wrapInError("Could not get ssl info", err)
		}

		_ = printJSON(result, false)
		return nil
	},
}

var getSslKeyCmd = &cobra.Command{
	Use:               "get [secret-name]",
	Short:             "Get the details of the specified DeltaFi key bundle",
	Long:              `Get the details of the specified DeltaFi key bundle`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: suggestSslSecretNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		secretName := args[0]
		var sslOutput, err = app.GetOrchestrator().GetKeyCert(secretName)
		if err != nil {
			return wrapInError("Error getting the secret named  "+secretName, err)
		}

		return printSslOutput(cmd, sslOutput)
	},
}

var loadSslKeyCmd = &cobra.Command{
	Use:   "load",
	Short: "Load the DeltaFi SSL keys",
	Long:  `Load the DeltaFi SSL keys`,
	RunE: func(cmd *cobra.Command, args []string) error {
		err := validateInput(sslInput)

		if err != nil {
			return err
		}

		sslOutput, err := app.GetOrchestrator().SaveKeyCert(sslInput)
		if err != nil {
			return err
		}

		return printSslOutput(cmd, sslOutput)

	},
}

var deleteSslKeyCmd = &cobra.Command{
	Use:               "delete [secret-name]",
	Short:             "Delete the given DeltaFi SSL keys",
	Long:              `Delete the DeltaFi SSL keys stored in the given secret name`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: suggestSslSecretNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		secretName := args[0]
		sslOutput, err := app.GetOrchestrator().DeleteKeyCert(secretName)
		if err != nil {
			return wrapInError("Error deleting secret named "+secretName, err)
		}

		return printSslOutput(cmd, sslOutput)
	},
}

var caChainCmd = &cobra.Command{
	Use:          "ca-chain",
	Short:        "Manage the DeltaFi Certificate Authority Chain",
	Long:         `Manage the DeltaFi Certificate Authority Chain`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		_ = cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var getCaChainCmd = &cobra.Command{
	Use:   "get",
	Short: "Get the DeltaFi Certificate Authority Chain",
	Long:  `Get the DeltaFi Certificate Authority Chain`,
	RunE: func(cmd *cobra.Command, args []string) error {
		caChain, err := app.GetOrchestrator().GetCaChain()
		if err != nil {
			return wrapInError("Failed to get CA Chain", err)
		}

		if caChain == "" {
			fmt.Println(styles.WarningStyle.Render("No Certificate Authority Chain found"))
			return nil
		}

		return printCaChain(cmd, caChain)
	},
}

var appendToCaChainCmd = &cobra.Command{
	Use:   "append",
	Short: "Append a certificate to the CA chain",
	Long:  `Append a certificate to the CA chain, preserving existing entries in the CA chain`,
	RunE: func(cmd *cobra.Command, args []string) error {
		path := cmd.Flag("cert").Value.String()
		data, err := os.ReadFile(path)

		if err != nil {
			return err
		}

		caChain, err := app.GetOrchestrator().AppendToCaChain(string(data))

		return printCaChain(cmd, caChain)
	},
}

var loadCaChainCmd = &cobra.Command{
	Use:   "load",
	Short: "Load the CA chain",
	Long:  `Load the CA chain, replacing the existing CA chain`,
	RunE: func(cmd *cobra.Command, args []string) error {
		path := cmd.Flag("ca-chain").Value.String()
		data, err := os.ReadFile(path)

		if err != nil {
			return err
		}

		caChain, err := app.GetOrchestrator().SaveCaChain(string(data))
		if err != nil {
			return wrapInError("Failed to save CA chain", err)
		}

		return printCaChain(cmd, caChain)
	},
}

func validateInput(sslInput orchestration.SslInput) error {
	// make sure the files exist and can be read
	err := checkFiles(sslInput.KeyFile, sslInput.CertFile)

	if err != nil {
		return err
	}

	// verify the key/cert match
	_, err = tls.LoadX509KeyPair(sslInput.CertFile, sslInput.KeyFile)
	if err != nil {
		return wrapInError("Key/cert mismatch", err)
	}

	return nil
}

func checkFiles(files ...string) error {
	var missing []string
	for _, file := range files {
		readable, reason := isFileReadable(file)
		if !readable {
			missing = append(missing, reason)
		}
	}
	if len(missing) > 0 {
		return newError("Missing one or more files", strings.Join(missing, ", "))
	}
	return nil
}

func suggestSslSecretNames(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchSslSecretNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchSslSecretNames() ([]string, error) {
	return app.GetOrchestrator().GetSecretNames()
}

func printCert(cmd *cobra.Command, cert graphql.CertFields) error {
	format, _ := cmd.Flags().GetString("format")
	if format == "pretty" {
		return components.PrintSimpleCertInfo(cert)
	} else {
		return prettyPrint(cmd, cert)
	}
}

func printSslOutput(cmd *cobra.Command, sslOutput *orchestration.SslOutput) error {
	cert, err := loadCert(sslOutput.Cert)
	if err != nil {
		return err
	}

	certHash := sha256.Sum256(cert.Raw)
	certHashStr := hex.EncodeToString(certHash[:])

	publicKeyHashStr := ""
	pubKeyDER, err := x509.MarshalPKIXPublicKey(cert.PublicKey)
	if err == nil {
		publicKeyHash := sha256.Sum256(pubKeyDER)
		publicKeyHashStr = hex.EncodeToString(publicKeyHash[:])
	}

	fingerprints := &graphql.CertFieldsCertCertificateFingerprints{
		Certificate: &certHashStr,
		PublicKey:   &publicKeyHashStr,
	}

	now := time.Now()
	duration := cert.NotAfter.Sub(now).Hours()
	days := 0
	if duration > 0 {
		days = int(duration / 24)
	}

	valid := now.Before(cert.NotBefore) && now.After(cert.NotAfter)

	certDetails := &graphql.CertFieldsCertCertificate{
		CommonName:              cert.Subject.CommonName,
		SubjectAlternativeNames: cert.DNSNames,
		Issuer:                  cert.Issuer.CommonName,
		ValidFrom:               cert.NotBefore,
		ValidTo:                 cert.NotAfter,
		IsExpired:               now.After(cert.NotAfter),
		IsValid:                 valid,
		DaysUntilExpiration:     &days,
		Fingerprints:            fingerprints,
		Raw:                     &sslOutput.Cert,
	}

	certInfo := &graphql.CertFields{
		SecretName: sslOutput.SecretName,
		Cert:       certDetails,
		UsedBy:     make([]*string, 0),
	}
	return printCert(cmd, *certInfo)
}

// TODO - pretty print chain options
func printCaChain(_ *cobra.Command, caChain string) error {
	fmt.Println(caChain)
	return nil
}

func sslFormatFlags(cmds ...*cobra.Command) {
	for _, cmd := range cmds {
		cmd.Flags().StringP("format", "o", "pretty", "Output format (pretty|json|yaml)")
		cmd.Flags().BoolP("plain", "p", false, "Plain output format")
		_ = cmd.RegisterFlagCompletionFunc("format", sslFormatCompletion)
	}
}

func sslFormatCompletion(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	return []string{"pretty", "json", "yaml"}, cobra.ShellCompDirectiveNoFileComp
}

func loadCert(certPEM string) (*x509.Certificate, error) {
	block, _ := pem.Decode([]byte(certPEM))
	if block == nil {
		return nil, fmt.Errorf("failed to decode certificate PEM")
	}

	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("failed to parse certificate: %w", err)
	}

	return cert, nil
}

func init() {
	rootCmd.AddCommand(sslCmd)
	sslCmd.AddCommand(loadSslKeyCmd, sslPrintInfo, getSslKeyCmd, deleteSslKeyCmd, caChainCmd)
	caChainCmd.AddCommand(getCaChainCmd, loadCaChainCmd, appendToCaChainCmd)

	loadSslKeyCmd.Flags().StringVarP(&sslInput.KeyFile, "key", "k", "", "path to private key associated with given certificate")
	loadSslKeyCmd.Flags().StringVarP(&sslInput.CertFile, "cert", "c", "", "path to PEM encoded public key certificate")
	loadSslKeyCmd.Flags().StringVar(&sslInput.KeyPassphrase, "key-password", "", "optional password for the key if it is encrypted")
	loadSslKeyCmd.Flags().StringVar(&sslInput.SslProtocol, "ssl-protocol", "TLSv1.2", "optional SSL protocol to use, defaults to TLSv1.2")
	loadSslKeyCmd.Flags().StringVar(&sslInput.SecretName, "secret-name", "ssl-secret", "optional the name of the secret, defaults to ssl-secret")

	loadCaChainCmd.Flags().StringP("ca-chain", "c", "", "path to PEM encoded CA certificate chain")
	_ = loadCaChainCmd.MarkFlagRequired("ca-chain")
	appendToCaChainCmd.Flags().StringP("cert", "c", "", "path to PEM encoded certificate to add to the chain")
	_ = appendToCaChainCmd.MarkFlagRequired("cert")

	_ = loadSslKeyCmd.MarkFlagRequired("key")
	_ = loadSslKeyCmd.MarkFlagRequired("cert")

	sslFormatFlags(loadSslKeyCmd, getSslKeyCmd, deleteSslKeyCmd)
}
