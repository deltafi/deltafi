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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.SslConfigService;
import org.deltafi.core.types.KeyCertPair;
import org.deltafi.core.types.SslInfo;
import org.deltafi.core.types.SslSettings;

@DgsComponent
@RequiredArgsConstructor
public class SslConfigDatafetcher {

    private final SslConfigService sslConfigService;
    private final CoreAuditLogger auditLogger;

    @DgsQuery
    @NeedsPermission.Admin
    public SslInfo getKeyCert(String secretName) {
        auditLogger.audit("viewed key and cert in the secret named " + secretName);
        return sslConfigService.getKeyCert(secretName);
    }

    @DgsQuery
    @NeedsPermission.Admin
    public SslSettings sslSettings() {
        auditLogger.audit("viewed system SSL info");
        return sslConfigService.getSslSettings();
    }

    @DgsMutation
    @NeedsPermission.Admin
    public SslInfo saveKeyCert(String secretName, KeyCertPair keyCertPair) {
        auditLogger.audit("saved a key and cert in the secret named " + secretName);
        return sslConfigService.saveKeyCert(secretName, keyCertPair);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public SslInfo deleteKeyCert(String secretName) {
        auditLogger.audit("deleted the key and cert in the secret named " + secretName);
        return sslConfigService.deleteKeyCert(secretName);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public String appendToCaChain(String certs) {
        auditLogger.audit("appended certs to the ca chain");
        return sslConfigService.appendToCaChain(certs);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public String saveCaChain(String certs) {
        auditLogger.audit("saved a new set of certs to the ca chain");
        return sslConfigService.saveCaChain(certs);
    }
}
