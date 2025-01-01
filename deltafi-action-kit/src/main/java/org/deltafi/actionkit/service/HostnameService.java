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
package org.deltafi.actionkit.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.properties.ActionsProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Service object that provides access to the local hostname
 */
@Getter
@Slf4j
public class HostnameService {

    private static final String DEFAULT_HOSTNAME = "UNKNOWN";
    private static final String HOSTNAME_ENV_VAR = "HOSTNAME";
    private static final String COMPUTERNAME_ENV_VAR = "COMPUTERNAME";

    private final String hostname;

    public HostnameService(ActionsProperties actionsProperties) {
        if (actionsProperties.getHostname() != null) {
            this.hostname = actionsProperties.getHostname();
        } else {
            this.hostname = detectHostname();
        }
    }

    private String detectHostname() {
        Map<String, String> env = System.getenv();

        if (env.containsKey(HOSTNAME_ENV_VAR)) {
            return env.get(HOSTNAME_ENV_VAR);
        }

        if (env.containsKey(COMPUTERNAME_ENV_VAR)) {
            return env.get(COMPUTERNAME_ENV_VAR);
        }

        return detectHostnameFromInet();
    }

    private String detectHostnameFromInet() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost.getHostName();
        } catch (UnknownHostException e) {
            log.error("Failed to determine hostname", e);
            return DEFAULT_HOSTNAME;
        }
    }
}
