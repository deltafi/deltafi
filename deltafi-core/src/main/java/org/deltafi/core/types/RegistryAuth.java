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
package org.deltafi.core.types;

import com.github.dockerjava.api.model.AuthConfig;
import org.apache.commons.lang3.StringUtils;

/**
 * Represents authentication configuration for a Docker registry.
 * Mirrors the structure found in Docker's config.json file.
 */
public record RegistryAuth(
        String username,
        String password,
        String auth,
        String identitytoken,
        String registrytoken
) {
    /**
     * Convert this auth config to a docker-java AuthConfig object.
     *
     * @param registryAddress The registry address to associate with this auth
     * @return Configured AuthConfig, or null if no valid credentials
     */
    public AuthConfig toAuthConfig(String registryAddress) {
        // Prefer token-based auth (more secure, used by Docker Hub and OAuth registries)
        if (StringUtils.isNotBlank(identitytoken)) {
            return new AuthConfig()
                    .withIdentityToken(identitytoken)
                    .withRegistryAddress(registryAddress);
        }

        if (StringUtils.isNotBlank(registrytoken)) {
            return new AuthConfig()
                    .withRegistrytoken(registrytoken)
                    .withRegistryAddress(registryAddress);
        }

        // Fall back to username/password auth
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            return new AuthConfig()
                    .withUsername(username)
                    .withPassword(password)
                    .withRegistryAddress(registryAddress);
        }

        return null;
    }
}
