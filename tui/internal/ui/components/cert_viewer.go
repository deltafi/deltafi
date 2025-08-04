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
package components

import (
	"fmt"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/ui/styles"
	"time"
)

var (
	labelStyle = styles.SubheaderStyle.Width(18).Align(lipgloss.Right)
	valueStyle = styles.BaseStyle
)

func PrintSimpleCertInfo(certInfo graphql.CertFields) error {
	certDetails := certInfo.GetCert()

	fmt.Println(styles.HeaderStyle.Margin(1, 0).Render("🔒 " + certInfo.SecretName + " Certificate Info"))

	printRow("Subject CN", certDetails.CommonName)
	printRow("Issuer CN", certDetails.Issuer)

	fmt.Println()
	printRow("Valid From", certDetails.ValidFrom.Format("2006-01-02 15:04 MST"))
	printRow("Valid Until", certDetails.ValidTo.Format("2006-01-02 15:04 MST"))
	printRow("Status", getStatusText(*certDetails))

	if len(certDetails.SubjectAlternativeNames) > 0 {
		fmt.Println()
		printRow("Subject Alt Names", certDetails.SubjectAlternativeNames[0])
		for _, name := range certDetails.SubjectAlternativeNames[1:] {
			fmt.Printf("%s  %s\n", labelStyle.Render(""), valueStyle.Render(name))
		}
	}

	if certDetails.GetFingerprints() != nil {
		fmt.Println()
		if certDetails.GetFingerprints().GetCertificate() != nil {
			printRow("Cert SHA-256", *certDetails.GetFingerprints().GetCertificate())
		}
		if certDetails.GetFingerprints().GetPublicKey() != nil {
			printRow("Public Key SHA-256", *certDetails.GetFingerprints().GetPublicKey())
		}
	}

	fmt.Println()
	return nil
}

func printRow(label, value string) {
	fmt.Printf("%s: %s\n",
		labelStyle.Render(label),
		valueStyle.Render(value))
}

func getStatusText(cert graphql.CertFieldsCertCertificate) string {
	now := time.Now()
	if now.Before(cert.ValidFrom) {
		return styles.WarningStyle.Render("⚠️  Not Yet Valid")
	} else if now.After(cert.ValidTo) {
		return styles.ErrorStyle.Render("❌ Expired")
	} else if now.Add(30 * 24 * time.Hour).After(cert.ValidTo) {
		return styles.WarningStyle.Render("⚠️  Expires Soon")
	} else {
		return styles.InfoStyle.Render("✅ Valid")
	}
}
