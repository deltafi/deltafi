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
package org.deltafi.core.services;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.security.DnUtil;
import org.deltafi.core.types.CertificateInfo;
import org.deltafi.core.types.CertificateInfo.Fingerprints;
import org.deltafi.core.types.KeyCertPair;
import org.deltafi.core.types.SslInfo;
import org.springframework.boot.autoconfigure.ssl.PemSslBundleProperties;
import org.springframework.boot.autoconfigure.ssl.PropertiesSslBundle;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateInfoService {

    private final Clock clock;

    public SslInfo getSslInfo(String secretName, KeyCertPair keyCertPair) {
        return SslInfo.builder()
                .secretName(secretName)
                .cert(getCertificateInfo(keyCertPair))
                .key(keyCertPair.key())
                .keyPassphrase(keyCertPair.keyPassphrase())
                .usedBy(new HashSet<>())
                .build();
    }

    public CertificateInfo getCertificateInfo(KeyCertPair keyCertPair) {
        try {
            String pemCert = keyCertPair.cert();
            String pemKey = keyCertPair.key();

            if (StringUtils.isAllBlank(pemCert, pemKey)) {
                return null;
            }

            // Create PEM SSL bundle from the provided cert and key
            PemSslBundleProperties properties = new PemSslBundleProperties();
            properties.getKeystore().setVerifyKeys(true);

            if (StringUtils.isNotBlank(pemCert)) {
                properties.getKeystore().setCertificate(pemCert);
            }

            if (StringUtils.isNotBlank(pemKey)) {
                properties.getKeystore().setPrivateKey(pemKey);
            }

            SslBundle sslBundle = PropertiesSslBundle.get(properties);

            KeyStore keyStore = sslBundle.getStores().getKeyStore();
            String alias = keyStore.aliases().nextElement();

            // Get the certificate chain
            Certificate[] certificates = sslBundle.getStores().getKeyStore()
                    .getCertificateChain(alias);

            if (certificates == null || certificates.length == 0) {
                throw new IllegalArgumentException("No certificates found in the provided PEM data");
            }

            Certificate cert = certificates[0];

            if (cert instanceof X509Certificate x509Certificate) {
                return extractCertificateInfo(x509Certificate, pemCert);
            }

            throw new IllegalArgumentException("Unexcepted certificate type " + certificates.getClass().getName());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse certificate: " + e.getMessage(), e);
        }
    }

    private CertificateInfo extractCertificateInfo(X509Certificate cert, String raw) {
        String subjectDN = cert.getSubjectX500Principal().getName();

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime validFrom = cert.getNotBefore().toInstant().atZone(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime validTo = cert.getNotAfter().toInstant().atZone(ZoneOffset.UTC).toOffsetDateTime();

        long untilExpired = Math.max(0, now.until(validTo, ChronoUnit.DAYS));

        String issuerDN = cert.getIssuerX500Principal().getName();

        return CertificateInfo.builder()
                .commonName(extractCommonName(subjectDN))
                .subjectAlternativeNames(extractSANs(cert))
                .validFrom(validFrom)
                .validTo(validTo)
                .isActive(validFrom.isAfter(now))
                .isExpired(validTo.isBefore(now))
                .daysUntilExpiration(untilExpired)
                .issuer(extractCommonName(issuerDN))
                .fingerprints(calculateFingerprints(cert))
                .raw(raw)
                .build();
    }

    private String extractCommonName(String dn) {
        try {
            return DnUtil.extractCommonName(dn);
        } catch (Exception e) {
            return dn;
        }
    }

    private List<String> extractSANs(X509Certificate cert) {
        List<String> sans = new ArrayList<>();
        try {
            Collection<List<?>> sanCollection = cert.getSubjectAlternativeNames();
            if (sanCollection != null) {
                for (List<?> san : sanCollection) {
                    // Type 2 is DNS name
                    if (san.size() >= 2 && san.get(0).equals(2)) {
                        sans.add(san.get(1).toString());
                    }
                }
            }
        } catch (Exception e) {
            // SANs might not be present or parseable
        }
        return sans;
    }

    private Fingerprints calculateFingerprints(X509Certificate cert) {
        String certFingerprint = null;
        String publicKeyFingerprint = null;
        try {
            certFingerprint = calculateFingerprint(cert.getEncoded());
            publicKeyFingerprint = calculateFingerprint(cert.getPublicKey().getEncoded());
        } catch (Exception e) {
            // ignore errors
        }

        return new Fingerprints(certFingerprint, publicKeyFingerprint);
    }

    private String calculateFingerprint(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return Hex.encodeHexString(digest);
        } catch (Exception e) {
            return null;
        }
    }
}