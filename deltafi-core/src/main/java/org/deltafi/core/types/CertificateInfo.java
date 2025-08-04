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
package org.deltafi.core.types;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record CertificateInfo(String commonName, List<String> subjectAlternativeNames, OffsetDateTime validFrom,
                              OffsetDateTime validTo, boolean isActive, boolean isExpired, long daysUntilExpiration,
                              String issuer, boolean isValid, Fingerprints fingerprints, String raw) {

    public record Fingerprints(String certificate, String publicKey){}
}
