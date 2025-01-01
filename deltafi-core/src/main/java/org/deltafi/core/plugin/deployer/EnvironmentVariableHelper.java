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
package org.deltafi.core.plugin.deployer;

import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.storage.s3.minio.MinioProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Getter
@Service
public class EnvironmentVariableHelper {
    private final String dataDir;
    private final List<String> envVars;

    public EnvironmentVariableHelper(MinioProperties minioProperties, EventQueueProperties eventQueueProperties, Environment environment) {
        this.dataDir = environment.getProperty("DATA_DIR");
        this.envVars = buildEnvVarList(minioProperties, eventQueueProperties, environment);
    }

    private List<String> buildEnvVarList(MinioProperties minioProperties, EventQueueProperties eventQueueProperties,  Environment environment) {
        String coreUrl = environment.getProperty("CORE_URL");
        String sslProtocol = environment.getProperty("SSL_PROTOCOL", "TLSv1.2");
        String keyPassword = environment.getProperty("KEY_PASSWORD");

        List<String> properties = new ArrayList<>(List.of(
                "CORE_URL=" + coreUrl,
                "MINIO_ACCESSKEY=" + minioProperties.getAccessKey(),
                "MINIO_SECRETKEY=" + minioProperties.getSecretKey(),
                "MINIO_URL=" + minioProperties.getUrl(),
                "MINIO_PARTSIZE=" + minioProperties.getPartSize(),
                "REDIS_URL=" + eventQueueProperties.getUrl(),
                "REDIS_PASSWORD=" + (eventQueueProperties.getPassword() == null ? "" : eventQueueProperties.getPassword()),
                "VALKEY_URL=" + eventQueueProperties.getUrl(),
                "VALKEY_PASSWORD=" + (eventQueueProperties.getPassword() == null ? "" : eventQueueProperties.getPassword()),
                "SSL_PROTOCOL=" + sslProtocol));

        // match k8s behavior where this is not injected if it is not set
        if (StringUtils.isNotBlank(keyPassword)) {
            properties.add("KEY_PASSWORD=" + keyPassword);
        }

        return properties;
    }
}
