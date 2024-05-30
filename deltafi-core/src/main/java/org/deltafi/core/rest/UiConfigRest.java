/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.rest;

import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.types.UiConfig;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Produces;

@RestController
@Produces(MediaType.APPLICATION_JSON_VALUE)
public class UiConfigRest {

    private final DeltaFiPropertiesService propertiesService;
    private final String uiDomain;
    private final String authMode;
    private final boolean inCluster;

    public UiConfigRest(DeltaFiPropertiesService propertiesService, Environment environment) {
        this.propertiesService = propertiesService;
        uiDomain = environment.getProperty("DELTAFI_UI_DOMAIN");
        authMode = environment.getProperty("AUTH_MODE");
        inCluster = environment.matchesProfiles("kubernetes");
    }

    @NeedsPermission.UIAccess
    @GetMapping("config")
    public UiConfig getConfig(@RequestParam(required = false, value = "skip_cache") Boolean skipCache) {
        DeltaFiProperties properties = propertiesService.getDeltaFiProperties(skipCache);

        String title = properties.getSystemName();
        if (StringUtils.isBlank(title)) {
            title = "DeltaFi";
        }

        UiConfig uiConfig = new UiConfig(properties.getUi());
        uiConfig.setTitle(title);
        uiConfig.setDomain(uiDomain);
        uiConfig.setAuthMode(authMode);
        uiConfig.setClusterMode(inCluster);
        return uiConfig;
    }
}