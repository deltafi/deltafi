/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.deployer;

import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.ssl.SslProperties;
import org.deltafi.common.storage.s3.minio.MinioProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EnvironmentVariableHelper {
    private final List<String> envVars;

    public EnvironmentVariableHelper(MinioProperties minioProperties, EventQueueProperties eventQueueProperties, SslProperties sslProperties, @Value("${CORE_URL:http://deltafi-core-service/api/v2}") String coreUrl) {
        this.envVars = buildEnvVarList(minioProperties, eventQueueProperties, sslProperties, coreUrl);
    }

    public List<String> getEnvVars() {
        return envVars;
    }

    private List<String> buildEnvVarList(MinioProperties minioProperties, EventQueueProperties eventQueueProperties, SslProperties sslProperties, String coreUrl) {
        return List.of(
                "CORE_URL=" + coreUrl,
                "MINIO_ACCESSKEY=" + minioProperties.getAccessKey(),
                "MINIO_SECRETKEY=" + minioProperties.getSecretKey(),
                "MINIO_URL=" + minioProperties.getUrl(),
                "MINIO_PARTSIZE=" + minioProperties.getPartSize(),
                "REDIS_URL=" + eventQueueProperties.getUrl(),
                "REDIS_PASSWORD=" + (eventQueueProperties.getPassword() == null ? "" : eventQueueProperties.getPassword()),
                "VALKEY_URL=" + eventQueueProperties.getUrl(),
                "VALKEY_PASSWORD=" + (eventQueueProperties.getPassword() == null ? "" : eventQueueProperties.getPassword()),
                "SSL_KEYSTORE=" + sslProperties.getKeyStore(),
                "SSL_KEYSTORETYPE=" + sslProperties.getKeyStoreType(),
                "SSL_KEYSTORETPASSWORD=" + sslProperties.getKeyStorePassword(),
                "SSL_PROTOCOL=" + sslProperties.getProtocol(),
                "SSL_TRUSTSTORE=" + sslProperties.getTrustStore(),
                "SSL_TRUSTSTORETYPE=" + sslProperties.getTrustStoreType(),
                "SSL_TRUSTSTOREPASSWORD=" + sslProperties.getTrustStorePassword()
        );
    }
}
